const auth = require('./utils/auth.js');

/**
 * 后端地址配置（本地模式）
 * - 默认电脑局域网 IP；换 WiFi 可在控制台执行 wx.setStorageSync('greenyi_api_base', 'http://新IP:8080/api')
 */
const DEFAULT_BASE_URL = 'http://173.129.1.155:8080/api';

/**
 * 微信云托管配置（云模式）
 * - env：云托管环境 ID，形如 prod-1gxxxxxx；在云托管控制台「环境」页复制
 * - service：服务名（新建服务时填的名字），用于 callContainer 的 X-WX-SERVICE
 * - fileBase：对象存储固定访问域名（需 bucket 公有读），形如 https://xxx.tcloudbaseapp.com
 * - 开启后：普通接口走 wx.cloud.callContainer（免域名备案），图片走 wx.cloud.uploadFile 直传对象存储
 * - 云模式开关存 storage 'greenyi_use_cloud' = true；控制台执行：
 *     wx.setStorageSync('greenyi_use_cloud', true)
 *     wx.setStorageSync('greenyi_cloud_env', '你的环境ID')
 *     wx.setStorageSync('greenyi_cloud_service', '你的服务名')
 *     wx.setStorageSync('greenyi_cloud_filebase', 'https://你的对象存储域名')
 *   然后重新编译。关闭：wx.removeStorageSync('greenyi_use_cloud')
 */
const CLOUD_DEFAULTS = {
    env: 'REPLACE_WITH_ENV_ID',
    service: 'greenyi',
    fileBase: 'REPLACE_WITH_FILE_BASE'
};

function read(key, fallback) {
    try { return wx.getStorageSync(key) || fallback; } catch (e) { return fallback; }
}

function resolveCloudConfig() {
    return {
        env: read('greenyi_cloud_env', CLOUD_DEFAULTS.env),
        service: read('greenyi_cloud_service', CLOUD_DEFAULTS.service),
        fileBase: read('greenyi_cloud_filebase', CLOUD_DEFAULTS.fileBase)
    };
}

App({
    globalData: {
        appName: '绿益公益',
        baseUrl: read('greenyi_api_base', DEFAULT_BASE_URL),
        useCloud: read('greenyi_use_cloud', false) === true,
        cloud: resolveCloudConfig(),
        userInfo: null,
        token: null
    },

    onLaunch() {
        if (this.globalData.useCloud) {
            if (wx.cloud) {
                wx.cloud.init({ env: this.globalData.cloud.env, traceUser: true });
            } else {
                console.warn('[云托管] 当前基础库不支持 wx.cloud，请在微信开发者工具中开启云开发');
            }
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

    // 切换云托管通道：setCloudMode(true, {env, service, fileBase})
    setCloudMode(on, cfg) {
        cfg = cfg || {};
        if (on) {
            wx.setStorageSync('greenyi_use_cloud', true);
            const merged = Object.assign({}, resolveCloudConfig(), cfg);
            if (cfg.env) wx.setStorageSync('greenyi_cloud_env', cfg.env);
            if (cfg.service) wx.setStorageSync('greenyi_cloud_service', cfg.service);
            if (cfg.fileBase) wx.setStorageSync('greenyi_cloud_filebase', cfg.fileBase);
            this.globalData.cloud = merged;
            if (wx.cloud) wx.cloud.init({ env: merged.env, traceUser: true });
        } else {
            wx.removeStorageSync('greenyi_use_cloud');
        }
        this.globalData.useCloud = !!on;
    }
});
