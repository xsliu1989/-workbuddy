/**
 * 网络请求封装（本地 / 微信云托管 双通道）
 * - 本地：wx.request 访问 app.globalData.baseUrl
 * - 云托管：优先 wx.cloud.callContainer（走小程序云开发环境 ID，免域名/免备案，真机/异地直接可用），
 *   失败自动回退 wx.request 直连公网域名（需在小程序后台把公网域名加入 request 合法域名）。
 *   callContainer 的 path 需带 context-path /api，header 带 X-WX-SERVICE。
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

// 云通道 ①：云函数反向代理（新建，免域名/免备案；真机/异地可用的首选通道）
function requestCloudViaFunction(app, options, header, settle) {
    const { url, method, data } = options;
    if (!wx.cloud || !wx.cloud.callFunction) {
        settle.reject(new Error('云函数不可用（wx.cloud 未初始化）'));
        return;
    }
    wx.cloud.callFunction({
        name: 'apiProxy',
        data: { path: url, method: method, data: data || {}, headers: header },
        success(res) {
            const r = res && res.result;
            if (!r || r.statusCode == null) { settle.reject(new Error('云函数无有效返回：' + JSON.stringify(res).slice(0, 120))); return; }
            if (r.statusCode !== 200) { settle.reject(new Error('云函数代理错误(' + r.statusCode + ')：' + (r.error || ''))); return; }
            handleBody(r.body, app, settle.resolve, settle.reject);
        },
        fail(err) {
            const msg = ((err && err.errMsg) || JSON.stringify(err)).slice(0, 160);
            settle.reject(new Error('云函数调用失败：' + msg + '（若提示 function not found，请先在开发者工具上传 apiProxy 云函数）'));
        }
    });
}

// 云通道 ②：callContainer（走小程序云开发环境，免域名/免备案）
function requestCloudViaContainer(app, options, header, settle) {
    const { url, method, data } = options;
    const cfg = app.globalData.cloud || {};
    if (!wx.cloud || !wx.cloud.callContainer || !cfg.env || !cfg.service) {
        settle.reject(new Error('callContainer 不可用（基础库/环境ID/服务名缺失）'));
        return;
    }
    // callContainer 的 path 是容器内路径，必须带 context-path /api
    const path = '/api' + url;
    const ccHeader = Object.assign({}, header, { 'X-WX-SERVICE': cfg.service });
    wx.cloud.callContainer({
        config: { env: cfg.env },
        path: path,
        method: method,
        data: data || {},
        header: ccHeader,
        success(res) {
            if (res.statusCode !== 200) { settle.reject(new Error('callContainer 状态(' + res.statusCode + ')')); return; }
            handleBody(res.data, app, settle.resolve, settle.reject);
        },
        fail(err) { settle.reject(new Error('callContainer 失败：' + ((err && err.errMsg) || ''))); }
    });
}

// 云通道 ③：公网域名直连（兜底；需在小程序后台配 request 合法域名）
function requestCloudViaHttp(app, options, header, settle) {
    const { url, method, data } = options;
    const base = String((app.globalData.cloud || {}).publicBase || '').replace(/\/$/, '');
    if (!base) { settle.reject(new Error('云端公网域名未配置（cloud.publicBase）')); return; }
    wx.request({
        url: base + '/api' + url,
        method: method,
        data: data || {},
        header: header,
        success(res) {
            if (res.statusCode !== 200) { settle.reject(new Error('云端错误(' + res.statusCode + ')：' + JSON.stringify(res.data).slice(0, 100))); return; }
            handleBody(res.data, app, settle.resolve, settle.reject);
        },
        fail(err) {
            settle.reject(new Error('连不上云端(' + base + ')：' + ((err && err.errMsg) || '请确认公网域名可访问、已加入 request 合法域名')));
        }
    });
}

function requestCloud(app, options, header, settle) {
    // 通道优先级：①云函数代理（首选，免域名）→ ②callContainer → ③公网域名。
    // 云函数是本项目让异地用户免域名访问的核心方案；另两条仅在云函数不可用时兜底。
    requestCloudViaFunction(app, options, header, {
        resolve: settle.resolve,
        reject(err1) {
            console.warn('[云通道] 云函数代理失败，回退 callContainer：', err1 && err1.message);
            requestCloudViaContainer(app, options, header, {
                resolve: settle.resolve,
                reject(err2) {
                    console.warn('[云通道] callContainer 失败，回退公网域名：', err2 && err2.message);
                    requestCloudViaHttp(app, options, header, settle);
                }
            });
        }
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
