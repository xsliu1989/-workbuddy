/**
 * apiProxy 云函数：把小程序的 API 请求转发到云端后端。
 *
 * 背景：后端部署在腾讯云侧云托管，公网域名无法加入小程序「合法域名」；
 * 小程序云开发环境（cloud1）里又没有这个云托管服务，callContainer 报 INVALID_HOST。
 * 解法：小程序用 wx.cloud.callFunction 调用本函数（走微信内部通道，免域名/免备案），
 *       本函数在云端用 https 请求后端公网域名——服务器端不受小程序合法域名限制。
 *
 * 入参 event:
 *   path    : 接口路径，如 /content/list?current=1&size=5（不含 /api 前缀）
 *   method  : GET / POST / PUT / DELETE
 *   data    : 参数（GET/DELETE 拼到 query；其余作为 JSON body）
 *   headers : 需透传的请求头（如 X-Token、content-type）
 * 返回:
 *   { statusCode, body }  body 为后端响应（JSON 已解析，非 JSON 原样返回文本）
 *
 * 仅用 Node.js 内置 https 模块，无第三方依赖，上传时选「不上传 node_modules」即可。
 */
const https = require('https');

// 后端公网域名（已实测返回真实数据）
const BACKEND_HOST = 'greenyi2-351648-4-1385789266.sh.run.tcloudbase.com';

exports.main = async (event = {}) => {
    let path = event.path || '/';
    if (path.charAt(0) !== '/') path = '/' + path;
    const method = (event.method || 'GET').toUpperCase();
    const data = event.data || {};
    const headers = Object.assign({}, event.headers || {});

    let targetPath = '/api' + path; // 后端 context-path 是 /api
    let body = null;

    if (method === 'GET' || method === 'DELETE') {
        if (data && typeof data === 'object') {
            const qs = Object.keys(data)
                .filter(k => data[k] !== undefined && data[k] !== null && data[k] !== '')
                .map(k => encodeURIComponent(k) + '=' + encodeURIComponent(data[k]))
                .join('&');
            if (qs) targetPath += (targetPath.indexOf('?') >= 0 ? '&' : '?') + qs;
        }
    } else {
        body = JSON.stringify(data);
        if (!headers['content-type']) headers['content-type'] = 'application/json';
    }
    if (body) headers['content-length'] = Buffer.byteLength(body, 'utf8');

    return new Promise((resolve) => {
        const req = https.request({
            hostname: BACKEND_HOST,
            path: targetPath,
            method: method,
            headers: headers,
            timeout: 15000
        }, (res) => {
            const chunks = [];
            res.on('data', (c) => chunks.push(c));
            res.on('end', () => {
                const text = Buffer.concat(chunks).toString('utf8');
                let parsed = text;
                try { parsed = JSON.parse(text); } catch (e) { /* 非 JSON 原样返回 */ }
                resolve({ statusCode: res.statusCode, body: parsed });
            });
        });
        req.on('timeout', () => {
            try { req.destroy(); } catch (e) { /* 忽略 */ }
            resolve({ statusCode: 0, body: null, error: '后端响应超时(15s)' });
        });
        req.on('error', (err) => {
            resolve({ statusCode: 0, body: null, error: String((err && err.message) || err) });
        });
        if (body) req.write(body);
        req.end();
    });
};
