/**
 * 登录态管理
 *
 * 关键点：wx.login 返回的 code 是一次性的，后端 mock 模式若用 code 派生 openid，
 * 退出后重新登录就会被当成新用户（资料/积分/订单全丢）。因此这里持久化一个
 * clientId 并随每次登录上报，后端 mock 模式用它作为稳定身份；退出登录时不清掉它。
 * 真实微信模式下后端以官方 openid 为准，clientId 会被忽略。
 */

const CLIENT_ID_KEY = 'greenyi_client_id';

function genClientId() {
    return 'c' + Date.now().toString(36)
        + Math.random().toString(36).slice(2, 10)
        + Math.random().toString(36).slice(2, 10);
}

/** 获取（必要时生成）稳定的客户端标识；退出登录不会清除 */
function getClientId() {
    let id = wx.getStorageSync(CLIENT_ID_KEY);
    if (id) return id;
    // 兼容升级前已登录过的用户：沿用旧的 openid，避免身份断裂
    const old = wx.getStorageSync('userInfo');
    if (old && old.openid) id = old.openid;
    if (!id) id = genClientId();
    wx.setStorageSync(CLIENT_ID_KEY, id);
    return id;
}

/**
 * 确保登录：无 token 时 wx.login 换取后端 token；可传 {nickname, avatarUrl, phone} 补全资料。
 * resolve 后端返回的完整资料对象（含 token/nickname/phone/avatarUrl），便于登录页预填。
 */
function ensureLogin(app, profile) {
    if (app.globalData.token) return Promise.resolve(app.globalData.userInfo || {});
    profile = profile || {};
    return new Promise((resolve, reject) => {
        wx.login({
            success(loginRes) {
                if (!loginRes.code) { reject(new Error('微信登录失败')); return; }
                wx.request({
                    url: app.globalData.baseUrl + '/auth/login',
                    method: 'POST',
                    data: {
                        code: loginRes.code,
                        clientId: getClientId(),
                        nickname: profile.nickname || '',
                        avatarUrl: profile.avatarUrl || '',
                        phone: profile.phone || ''
                    },
                    header: { 'content-type': 'application/json' },
                    success(res) {
                        const body = res.data;
                        if (body && body.code === 0) {
                            const data = body.data || {};
                            const token = data.token;
                            app.globalData.token = token;
                            app.globalData.userInfo = data;
                            wx.setStorageSync('token', token);
                            wx.setStorageSync('userInfo', data);
                            resolve(data);
                        } else {
                            reject(new Error((body && body.message) || '登录失败'));
                        }
                    },
                    fail() { reject(new Error('无法连接后台服务')); }
                });
            },
            fail() { reject(new Error('微信登录失败')); }
        });
    });
}

function isLogged(app) { return !!app.globalData.token; }

/** 退出登录：只清登录态，保留 clientId，保证下次登录仍是同一个账号 */
function logout(app) {
    app.globalData.token = null;
    app.globalData.userInfo = null;
    wx.removeStorageSync('token');
    wx.removeStorageSync('userInfo');
}

module.exports = { ensureLogin, isLogged, logout, getClientId };
