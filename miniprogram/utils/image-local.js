/**
 * 真机图片兼容层
 *
 * 微信小程序的 <image> 组件在真机（开发版/体验版/正式版）都不允许加载 http:// 图片，
 * 但 wx.request 在开发模式下可以访问后端的 http 接口。
 * 因此这里用 wx.request 以 arraybuffer 取回图片字节，写入小程序本地用户目录，
 * 再把本地文件路径交给 <image>，从而绕开真机的 HTTP 图片限制。
 *
 * 注意：正式上线仍需后端提供 HTTPS 图片地址，并在小程序后台配置 downloadFile 合法域名。
 */
const fs = wx.getFileSystemManager();
const CACHE_DIR = wx.env.USER_DATA_PATH + '/greenyi-image-cache';
const MAX_CACHE = 200;

let dirReady = false;
let cacheMap = null;
const pending = {};

function map() {
    if (!cacheMap) {
        try { cacheMap = wx.getStorageSync('greenyi_image_cache') || {}; }
        catch (e) { cacheMap = {}; }
    }
    return cacheMap;
}

function saveMap() {
    try { wx.setStorageSync('greenyi_image_cache', cacheMap); }
    catch (e) { /* 存储满时忽略，内存缓存仍然有效 */ }
}

function ensureDir() {
    if (dirReady) return;
    try { fs.mkdirSync(CACHE_DIR, true); }
    catch (e) {
        try { fs.mkdirSync(CACHE_DIR); }
        catch (e2) { /* 目录已存在 */ }
    }
    dirReady = true;
}

function fileExists(path) {
    try {
        fs.accessSync(path);
        return true;
    } catch (e) {
        return false;
    }
}

function hashOf(url) {
    let h = 5381;
    for (let i = 0; i < url.length; i++) h = (((h << 5) + h) + url.charCodeAt(i)) & 0x7fffffff;
    return h.toString(36);
}

function extensionOf(contentType, url) {
    const ct = String(contentType || '').toLowerCase();
    if (ct.indexOf('jpeg') >= 0 || ct.indexOf('jpg') >= 0) return 'jpg';
    if (ct.indexOf('png') >= 0) return 'png';
    if (ct.indexOf('gif') >= 0) return 'gif';
    if (ct.indexOf('webp') >= 0) return 'webp';
    const m = String(url || '').match(/\.([a-zA-Z0-9]+)(?:\?|$)/);
    const ext = m ? m[1].toLowerCase() : 'png';
    return (ext === 'jpeg') ? 'jpg' : ext.replace(/[^a-z0-9]/g, '') || 'png';
}

/** 把单个网络图片 URL 转换为本地文件路径；失败时返回空串，让页面显示占位图 */
function toLocal(url) {
    if (!url) return Promise.resolve('');
    if (typeof url !== 'string') return Promise.resolve('');
    if (url.indexOf('data:') === 0 || url.indexOf('wxfile://') === 0 || url.charAt(0) === '/') {
        return Promise.resolve(url); // 本地路径 / base64 不动
    }
    if (url.indexOf('http://') !== 0 && url.indexOf('https://') !== 0) return Promise.resolve(url);

    const stored = map()[url];
    if (stored && fileExists(stored)) return Promise.resolve(stored);
    if (pending[url]) return pending[url];

    pending[url] = new Promise(resolve => {
        wx.request({
            url: url,
            method: 'GET',
            responseType: 'arraybuffer',
            success(res) {
                if (res.statusCode !== 200 || !res.data || !res.data.byteLength) {
                    resolve('');
                    return;
                }
                ensureDir();
                const header = res.header || {};
                const contentType = header['Content-Type'] || header['content-type'] || '';
                const path = CACHE_DIR + '/' + hashOf(url) + '.' + extensionOf(contentType, url);
                try {
                    fs.writeFileSync(path, res.data);
                    const m = map();
                    m[url] = path;
                    const keys = Object.keys(m);
                    if (keys.length > MAX_CACHE) delete m[keys[0]];
                    saveMap();
                    resolve(path);
                } catch (e) {
                    resolve('');
                }
            },
            fail() { resolve(''); }
        });
    }).then(path => {
        delete pending[url];
        return path;
    });
    return pending[url];
}

/** 把一批 URL 转换为本地路径，顺序与入参一致 */
function toLocalList(urls) {
    const list = Array.isArray(urls) ? urls : (urls ? String(urls).split(/[,，\n]/).map(s => s.trim()).filter(Boolean) : []);
    return Promise.all(list.map(toLocal)).then(paths => paths.filter(Boolean));
}

/** 商品列表/内容列表：把 coverUrl 换成真机可用的本地路径 */
function hydrateCovers(records) {
    const list = Array.isArray(records) ? records : [];
    if (list.length === 0) return Promise.resolve(list);
    return Promise.all(list.map(item => {
        if (!item) return Promise.resolve(item);
        if (!item.coverUrl) return Promise.resolve(item);
        return toLocal(item.coverUrl).then(path => {
            if (path) item.coverUrl = path;
            return item;
        });
    }));
}

/** 富文本里的 <img src> 换成真机可用的本地路径 */
function hydrateDescription(html) {
    if (!html) return Promise.resolve(html || '');
    const source = String(html);
    const tagRe = /<img[^>]*\bsrc\s*=\s*(["'])(.*?)\1/gi;
    const urls = [];
    let match;
    while ((match = tagRe.exec(source)) !== null) urls.push(match[2]);
    if (urls.length === 0) return Promise.resolve(source);
    return Promise.all(urls.map(toLocal)).then(paths => {
        let i = 0;
        return source.replace(/<img[^>]*\bsrc\s*=\s*(["'])(.*?)\1/gi, (full, quote, src) => {
            const local = paths[i++];
            return local ? full.replace(src, local) : full;
        });
    });
}

module.exports = {
    toLocal: toLocal,
    toLocalList: toLocalList,
    hydrateCovers: hydrateCovers,
    hydrateDescription: hydrateDescription
};
