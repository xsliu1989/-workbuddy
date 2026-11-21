/**
 * 图片上传（本地 / 云端公网域名 双通道），返回可直接展示的图片 URL。
 * - 本地：wx.uploadFile 到 app.globalData.baseUrl + /upload/image（后端 local 模式写磁盘）
 * - 云端：wx.uploadFile 到 cloud.publicBase + /upload/image（后端 cos 模式上传 COS，返回完整 https URL）
 *   之所以不走 wx.cloud.uploadFile：当前环境未在小程序云开发账号下绑定，云上传会失败。
 *
 * 云端后端 UPLOAD_MODE=cos、桶公有读，返回形如 https://bucket.cos.../uploads/xxx 的永久链接。
 */

function postUpload(base, filePath, header) {
    return new Promise((resolve, reject) => {
        wx.uploadFile({
            url: String(base).replace(/\/$/, '') + '/api/upload/image',
            filePath: filePath,
            name: 'file',
            header: header || {},
            success(res) {
                let body = res.data;
                if (typeof body === 'string') {
                    try { body = JSON.parse(body); } catch (e) { reject(new Error('上传返回异常：' + String(body).slice(0, 80))); return; }
                }
                if (body && body.code === 0) {
                    let url = body.data && (body.data.url || body.data);
                    // 后端 local 模式返回 /uploads/ 相对路径，拼成绝对地址；cos 模式返回完整 https，原样保留
                    if (url && typeof url === 'string' && url.indexOf('/uploads/') === 0) url = base.replace(/\/$/, '') + '/api' + url;
                    resolve(url);
                } else {
                    reject(new Error((body && body.message) || '上传失败'));
                }
            },
            fail(err) { reject(new Error('上传失败：' + ((err && err.errMsg) || '请检查网络与 uploadFile 合法域名'))); }
        });
    });
}

function uploadImage(app, filePath) {
    const header = {};
    if (app.globalData.token) header['X-Token'] = app.globalData.token;
    if (app.globalData.useCloud) {
        const base = String(app.globalData.cloud.publicBase || '').replace(/\/$/, '');
        if (!base) return Promise.reject(new Error('云端公网域名未配置（cloud.publicBase）'));
        return postUpload(base, filePath, header);
    }
    return postUpload(app.globalData.baseUrl, filePath, header);
}

module.exports = { uploadImage };
