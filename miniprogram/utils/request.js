/**
 * 网络请求封装（本地 / 微信云托管 双通道）
 * - 本地：wx.request 访问 app.globalData.baseUrl
 * - 云托管：wx.cloud.callContainer（免域名备案、免鉴权；容器路径需带 context-path /api）
 * - 自动携带 X-Token；统一解析后端 Result {code,message,data}
 */

/**
 * 图片相对地址 → 绝对地址。
 * - 云模式：/uploads/xxx 拼对象存储域名前缀（后端 cos 模式多返回完整 https，此处原样保留）
 * - 本地模式：拼 baseUrl
 */
function normalizeImageUrls(value, app) {
    if (value == null) return value;
    if (typeof value === 'string') {
        if (value.indexOf('/uploads/') < 0) return value;
        let base = app.globalData.baseUrl;
        if (app.globalData.useCloud) {
            // 云模式：对象存储里图片路径就是 uploads/xxx（上传时 cloudPath），直接拼桶的固定域名；
            // 不能再拼 /api —— 云端没有后端静态映射，示例商品的 /uploads/samples/ 需先传到 COS 同名目录。
            base = String(app.globalData.cloud.fileBase || '').replace(/\/$/, '');
        }
        return value.replace(/(["'\s(]|^)\/uploads\//g, (m, prefix) => prefix + base + '/uploads/');
    }
    if (Array.isArray(value)) return value.map(v => normalizeImageUrls(v, app));
    if (typeof value === 'object') {
        Object.keys(value).forEach(k => { value[k] = normalizeImageUrls(value[k], app); });
    }
    return value;
}

function clearLogin(app) {
    wx.removeStorageSync('token');
    wx.removeStorageSync('userInfo');
    if (app.globalData) {
        app.globalData.token = null;
        app.globalData.userInfo = null;
    }
}

function handleBody(body, app, resolve, reject) {
    if (body && body.code === 0) {
        resolve(normalizeImageUrls(body.data, app));
    } else if (body && body.code === 401) {
        // 同时清内存与存储登录态，避免 ensureLogin 误判已登录导致后续请求一直失败
        clearLogin(app);
        reject(new Error(body.message || '请先登录'));
    } else {
        reject(new Error((body && body.message) || '请求失败'));
    }
}

function requestLocal(app, options, header, settle) {
    const { url, method, data } = options;
    wx.request({
        url: app.globalData.baseUrl + url,
        method: method,
        data: data || {},
        header: header,
        success(res) {
            if (res.statusCode !== 200) { settle.reject(new Error('网络错误(' + res.statusCode + ')')); return; }
            handleBody(res.data, app, settle.resolve, settle.reject);
        },
        fail() { settle.reject(new Error('连不上后台(' + app.globalData.baseUrl + ')：确认手机与电脑同WiFi、后台已启动、IP正确')); }
    });
}

function requestCloud(app, options, header, settle) {
    const { url, method, data } = options;
    // callContainer 的 path 是容器内路径，必须带 context-path /api
    const path = '/api' + url;
    const ccHeader = Object.assign({}, header, { 'X-WX-SERVICE': app.globalData.cloud.service });
    wx.cloud.callContainer({
        config: { env: app.globalData.cloud.env },
        path: path,
        method: method,
        data: data || {},
        header: ccHeader,
        success(res) {
            if (res.statusCode !== 200) { settle.reject(new Error('云托管错误(' + res.statusCode + ')')); return; }
            handleBody(res.data, app, settle.resolve, settle.reject);
        },
        fail(err) { settle.reject(new Error('云托管调用失败：' + ((err && err.errMsg) || '请检查环境ID/服务名/服务是否已部署'))); }
    });
}

function request(app, options) {
    const { url, method = 'GET', data, needAuth = true } = options;
    const header = { 'content-type': 'application/json' };
    if (needAuth && app.globalData.token) header['X-Token'] = app.globalData.token;

    return new Promise((resolve, reject) => {
        const settle = { resolve, reject };
        if (app.globalData.useCloud) requestCloud(app, { url, method, data }, header, settle);
        else requestLocal(app, { url, method, data }, header, settle);
    });
}

module.exports = { request };
