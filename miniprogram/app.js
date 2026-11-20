const auth = require('./utils/auth.js');

/**
 * 后端地址配置
 * - 默认使用电脑的局域网 IP，开发者工具模拟器与真机均可访问
 *   （真机必须手机与电脑连同一个 WiFi，且电脑 IP 不变）
 * - 若电脑 IP 变化（换 WiFi / 重连网络），真机调试时在控制台执行：
 *     wx.setStorageSync('greenyi_api_base', 'http://新的IP:8080/api')
 *   然后重新编译；缓存优先级高于下方常量。
 *   恢复默认：wx.removeStorageSync('greenyi_api_base')
 * - 电脑 IP 查看：PowerShell 执行 ipconfig，取 WLAN 的 IPv4 地址。
 */
const DEFAULT_BASE_URL = 'http://173.129.1.155:8080/api';

function resolveBaseUrl() {
    const override = wx.getStorageSync('greenyi_api_base');
    return override || DEFAULT_BASE_URL;
}

App({
    globalData: {
        appName: '绿益公益',
        baseUrl: resolveBaseUrl(),
        userInfo: null,
        token: null
    },

    onLaunch() {
        // 恢复本地登录态
        const token = wx.getStorageSync('token');
        const userInfo = wx.getStorageSync('userInfo');
        if (token) this.globalData.token = token;
        if (userInfo) this.globalData.userInfo = userInfo;
    },

    // 确保登录：无 token 时 wx.login 换取后端 token；可传 {nickname, avatarUrl, phone} 补全资料
    ensureLogin(profile) {
        return auth.ensureLogin(this, profile);
    },

    // 退出登录：清空本地登录态（后端 JWT 无状态，前端清除即生效）
    logout() {
        auth.logout(this);
    },

    // 调试用：运行时切换后端地址（真机预览临时改 IP 用）
    setApiBase(url) {
        wx.setStorageSync('greenyi_api_base', url);
        this.globalData.baseUrl = url;
    }
});
