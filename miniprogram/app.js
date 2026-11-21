const auth = require('./utils/auth.js');

/**
 * 后端地址配置（本地模式）
 * - 默认电脑局域网 IP；换 WiFi 可在控制台执行 wx.setStorageSync('greenyi_api_base', 'http://新IP:8080/api')
 */
const DEFAULT_BASE_URL = 'http://173.129.1.155:8080/api';

/**
 * 微信云托管配置（云模式）
 * - 数据请求策略：callContainer 优先（用小程序云开发环境 ID，免域名/免备案，异地用户扫码即可看），
 *   失败自动回退公网域名 wx.request。两条通道互为兜底。
 * - env / service：callContainer 参数。env 必须是「小程序云开发环境 ID」（微信开发者工具 → 云开发控制台可见），
 *   不是腾讯云侧的环境名。之前误填 prod-d1gzglnv95abda853 导致 Invalid host，已改为真实环境 ID。
 * - publicBase：云端「公网访问域名」根地址，作为 callContainer 失败时的兜底通道。
 * - fileBase：对象存储固定访问域名（需 bucket 公有读），用于拼图片地址；图片显示走 COS，配 downloadFile 合法域名。
 * - 云模式开关存 storage 'greenyi_use_cloud'；默认已开启（正式发布）。
 *   临时切回本地调试，在控制台执行：wx.setStorageSync('greenyi_use_cloud', false)
 *   再执行 wx.clearStorageSync() 清掉旧的云配置缓存，然后重新编译。
 * - 当前参数（已按实际环境填好）：
 *     publicBase = https://greenyi2-351648-4-1385789266.sh.run.tcloudbase.com
 *     env        = cloud1-2gau8nf2b470fa10（小程序云开发环境 ID）
 *     service    = greenyi2（云托管服务名）
 *     fileBase   = https://lvyi-1385789266.cos.ap-guangzhou.myqcloud.com（桶需公有读）
 */
const CLOUD_DEFAULTS = {
    publicBase: 'https://greenyi2-351648-4-1385789266.sh.run.tcloudbase.com',
    // 小程序云开发环境 ID（来自微信开发者工具云开发控制台），不是腾讯云侧环境名 prod-xxx
    env: 'cloud1-2gau8nf2b470fa10',
    service: 'greenyi2',
    fileBase: 'https://lvyi-1385789266.cos.ap-guangzhou.myqcloud.com'
};

function read(key, fallback) {
    try { return wx.getStorageSync(key) || fallback; } catch (e) { return fallback; }
}

function resolveCloudConfig() {
    // 防御：真机若残留旧的错误环境 ID（腾讯云侧 prod-xxx 环境名），忽略它，避免覆盖正确的云开发环境 ID
    let env = read('greenyi_cloud_env', CLOUD_DEFAULTS.env);
    if (typeof env === 'string' && env.indexOf('prod-') === 0) {
        try { wx.removeStorageSync('greenyi_cloud_env'); } catch (e) { /* 忽略 */ }
        env = CLOUD_DEFAULTS.env;
    }
    return {
        publicBase: read('greenyi_cloud_publicbase', CLOUD_DEFAULTS.publicBase),
        env: env,
        service: read('greenyi_cloud_service', CLOUD_DEFAULTS.service),
        fileBase: read('greenyi_cloud_filebase', CLOUD_DEFAULTS.fileBase)
    };
}

App({
    globalData: {
        appName: '绿益公益',
        baseUrl: read('greenyi_api_base', DEFAULT_BASE_URL),
        // 默认走云托管；本地调试可在控制台 wx.setStorageSync('greenyi_use_cloud', false) 关闭
        useCloud: read('greenyi_use_cloud', true) === true,
        cloud: resolveCloudConfig(),
        userInfo: null,
        token: null
    },

    onLaunch() {
        // 云模式下数据优先走 wx.cloud.callContainer（需 wx.cloud.init 到正确的云开发环境 ID）。
        // 现在云开发已开通、环境 ID 已校准，callContainer 应可直接用；失败时 request.js 会自动回退公网域名。
        if (this.globalData.useCloud && wx.cloud) {
            try { wx.cloud.init({ env: this.globalData.cloud.env, traceUser: true }); } catch (e) { /* 忽略：失败会回退公网域名 */ }
        }
        const token = wx.getStorageSync('token');
        const userInfo = wx.getStorageSync('userInfo');
        if (token) this.globalData.token = token;
        if (userInfo) this.globalData.userInfo = userInfo;
    },

    ensureLogin(profile) {
        return auth.ensureLogin(this, profile);
    },

    logout() {
        auth.logout(this);
    },

    // 本地模式：运行时切换后端地址
    setApiBase(url) {
        wx.setStorageSync('greenyi_api_base', url);
        this.globalData.baseUrl = url;
    },

    // 切换云托管通道：setCloudMode(true, {publicBase, env, service, fileBase})
    setCloudMode(on, cfg) {
        cfg = cfg || {};
        if (on) {
            wx.setStorageSync('greenyi_use_cloud', true);
            const merged = Object.assign({}, resolveCloudConfig(), cfg);
            if (cfg.publicBase) wx.setStorageSync('greenyi_cloud_publicbase', cfg.publicBase);
            if (cfg.env) wx.setStorageSync('greenyi_cloud_env', cfg.env);
            if (cfg.service) wx.setStorageSync('greenyi_cloud_service', cfg.service);
            if (cfg.fileBase) wx.setStorageSync('greenyi_cloud_filebase', cfg.fileBase);
            this.globalData.cloud = merged;
            if (wx.cloud) { try { wx.cloud.init({ env: merged.env, traceUser: true }); } catch (e) { /* 忽略 */ } }
        } else {
            wx.removeStorageSync('greenyi_use_cloud');
        }
        this.globalData.useCloud = !!on;
    }
});
