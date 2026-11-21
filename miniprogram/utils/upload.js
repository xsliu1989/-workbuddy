/**
 * 图片上传（本地 / 微信云托管 双通道），返回可直接展示的图片 URL。
 * - 本地：wx.uploadFile 到后端 /upload/image（后端 local 模式写磁盘，返回 /uploads/...）
 * - 云托管：wx.cloud.uploadFile 直传对象存储拿 fileID，再 getTempFileURL 换 https URL。
 *   这样绕开 callContainer 100K 请求体限制，也无需后端公网域名。
 *
 * 注意：getTempFileURL 返回的是带签名的临时链接（默认 2 小时有效）。
 * 生产如需永久链接，把对象存储桶设为公有读，并在 app.js 配置 cloud.fileBase，
 * 此时可把 fileID 直接拼成 https 固定域名返回（见下方 PUBLIC_READ 开关）。
 */

// true = 桶已设为公有读，直接用固定域名拼永久链接；false = 用临时签名链接
// 云端已配公有读 COS 桶，这里必须为 true，否则图片链接 2 小时后过期会裂图
const PUBLIC_READ = true;

function localUpload(app, filePath, header) {
    return new Promise((resolve, reject) => {
        wx.uploadFile({
            url: app.globalData.baseUrl + '/upload/image',
            filePath: filePath,
            name: 'file',
            header: header,
            success(res) {
                let body = res.data;
                if (typeof body === 'string') {
                    try { body = JSON.parse(body); } catch (e) { reject(new Error('上传返回异常')); return; }
                }
                if (body && body.code === 0) {
                    let url = body.data.url;
                    if (url && url.indexOf('/uploads/') === 0) url = app.globalData.baseUrl + url;
                    resolve(url);
                } else {
                    reject(new Error((body && body.message) || '上传失败'));
                }
            },
            fail() { reject(new Error('上传失败，请检查网络与后台服务')); }
        });
    });
}

function cloudPathOf(filePath) {
    const ext = (filePath.match(/\.([a-zA-Z0-9]+)(?:\?|$)/) || [])[1] || 'png';
    const stamp = Date.now().toString(36);
    const rand = Math.random().toString(36).slice(2, 10);
    return 'uploads/' + stamp + '-' + rand + '.' + ext;
}

function cloudUpload(app, filePath) {
    return new Promise((resolve, reject) => {
        const cloudPath = cloudPathOf(filePath);
        wx.cloud.uploadFile({
            cloudPath: cloudPath,
            filePath: filePath,
            success(up) {
                const fileID = up.fileID;
                if (PUBLIC_READ && app.globalData.cloud.fileBase && app.globalData.cloud.fileBase.indexOf('http') === 0) {
                    resolve(String(app.globalData.cloud.fileBase).replace(/\/$/, '') + '/' + cloudPath);
                    return;
                }
                wx.cloud.getTempFileURL({
                    fileList: [fileID],
                    success(r) {
                        const item = (r.fileList || [])[0];
                        const url = item && (item.tempFileURL || item.download_url);
                        if (url) resolve(url);
                        else reject(new Error('获取图片链接失败'));
                    },
                    fail(err) { reject(new Error('获取图片链接失败：' + ((err && err.errMsg) || ''))); }
                });
            },
            fail(err) { reject(new Error('上传图片到对象存储失败：' + ((err && err.errMsg) || ''))); }
        });
    });
}

function uploadImage(app, filePath) {
    if (app.globalData.useCloud) return cloudUpload(app, filePath);
    const header = {};
    if (app.globalData.token) header['X-Token'] = app.globalData.token;
    return localUpload(app, filePath, header);
}

module.exports = { uploadImage };
