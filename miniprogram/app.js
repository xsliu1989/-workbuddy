const auth = require('./utils/auth.js');

/**
 * 后端地址配置（本地模式）
 * - 默认电脑局域网 IP；换 WiFi 可在控制台执行 wx.setStorageSync('greenyi_api_base', 'http://新IP:8080/api')
 */
const DEFAULT_BASE_URL = 'http://173.129.1.155:8080/api';

/**
 * 微信云托管配置（云模式）
 * - publicBase：云端服务的「公网访问域名」+ /api 之外的根地址。数据请求与图片上传都走它，
 *   用 wx.request / wx.uploadFile 直连（需要在小程序后台把它加入 request/uploadFile 合法域名）。
 *   之所以不走 wx.cloud.callContainer：当前环境未在小程序云开发账号下绑定，callContainer 会报 Invalid host。
 * - env / service：保留作备用（callContainer 通道），只有在小程序后台开通云开发并绑定该环境后才可用。
 * - fileBase：对象存储固定访问域名（需 bucket 公有读），用于拼图片地址。
 * - 云模式开关存 storage 'greenyi_use_cloud'；默认已开启（正式发布）。
 *   临时切回本地调试，在控制台执行：wx.setStorageSync('greenyi_use_cloud', false)
 *   再执行 wx.clearStorageSync() 清掉旧的云配置缓存，然后重新编译。
 * - 当前云环境参数（已按实际环境填好）：
 *     publicBase = https://springboot-cvbe-351648-4-1385789266.sh.run.tcloudbase.com
 *     env        = prod-d1gzglnv95abda853
 *     service    = springboot
 *     fileBase   = https://lvyi-1385789266.cos.ap-guangzhou.myqcloud.com（桶需公有读）
 */
const CLOUD_DEFAULTS = {
    publicBase: 'https://springboot-cvbe-351648-4-1385789266.sh.run.tcloudbase.com',
    env: 'prod-d1gzglnv95abda853',
    service: 'springboot',
    fileBase: 'https://lvyi-1385789266.cos.ap-guangzhou.myqcloud.com'
};

function read(key, fallback) {
    try { return wx.getStorageSync(key) || fallback; } catch (e) { return fallback; }
}

function resolveCloudConfig() {
    return {
        publicBase: read('greenyi_cloud_publicbase', CLOUD_DEFAULTS.publicBase),
        env: read('greenyi_cloud_env', CLOUD_DEFAULTS.env),
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
        // 云模式下数据走公网域名直连，不再依赖 wx.cloud.callContainer。
        // 仍初始化 wx.cloud 仅为了图片上传等潜在的兼容场景；即使未开通云开发也不影响主链路。
        if (this.globalData.useCloud && wx.cloud) {
            try { wx.cloud.init({ env: this.globalData.cloud.env, traceUser: true }); } catch (e) { /* 忽略：主链路不依赖 */ }
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
