/**
 * 网络请求封装
 * - 自动携带 X-Token
 * - 统一解析后端 Result {code,message,data}
 * - code=0 返回 data，否则 reject(Error)
 */
/**
 * 把后端返回的图片相对地址（/uploads/...）转成小程序可访问的绝对地址。
 * 递归处理对象/数组，兼容旧的完整 http URL 与富文本里的 <img src="/uploads/...">。
 */
function normalizeImageUrls(value, app) {
    if (value == null) return value;
    if (typeof value === 'string') {
        if (value.indexOf('/uploads/') < 0) return value;
        const base = app.globalData.baseUrl; // 形如 http://IP:8080/api
        // 已经是绝对地址的不重复拼接
        return value.replace(/(["'\s(]|^)\/uploads\//g, (m, prefix) => prefix + base + '/uploads/');
    }
    if (Array.isArray(value)) return value.map(v => normalizeImageUrls(v, app));
    if (typeof value === 'object') {
        Object.keys(value).forEach(k => { value[k] = normalizeImageUrls(value[k], app); });
    }
    return value;
}

function request(app, options) {
    const { url, method = 'GET', data, needAuth = true } = options;
    const header = { 'content-type': 'application/json' };
    if (needAuth && app.globalData.token) header['X-Token'] = app.globalData.token;

    return new Promise((resolve, reject) => {
        wx.request({
            url: app.globalData.baseUrl + url,
            method: method,
            data: data || {},
            header: header,
            success(res) {
                if (res.statusCode !== 200) {
                    reject(new Error('网络错误(' + res.statusCode + ')'));
                    return;
                }
                const body = res.data;
                if (body && body.code === 0) {
                    resolve(normalizeImageUrls(body.data, app));
                } else if (body && body.code === 401) {
                    // 同时清掉内存中的登录态，否则 ensureLogin 会因 globalData.token 仍在而跳过登录，
                    // 之后所有需要登录的操作（如我的申领）都会一直失败，看起来像“点击没反应”。
                    wx.removeStorageSync('token');
                    wx.removeStorageSync('userInfo');
                    if (app.globalData) {
                        app.globalData.token = null;
                        app.globalData.userInfo = null;
                    }
                    reject(new Error(body.message || '请先登录'));
                } else {
                    reject(new Error((body && body.message) || '请求失败'));
                }
            },
            fail() {
                reject(new Error('连不上后台(' + app.globalData.baseUrl + ')：确认手机与电脑同WiFi、后台已启动、IP正确'));
            }
        });
    });
}

module.exports = { request };
