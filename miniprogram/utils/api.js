/**
 * API 汇总：所有业务接口集中于此，页面只调用本文件方法
 */
const { request } = require('./request.js');

function api(app) {
    const call = (url, method, data, needAuth) => request(app, { url, method, data, needAuth });

    return {
        // 认证
        resolvePhone: (code) => call('/auth/phone', 'POST', { code }, false),

        // 图片上传：filePath 为本地临时路径，返回 /uploads/... 相对地址（已转绝对地址）
        uploadImage: (filePath) => new Promise((resolve, reject) => {
            const header = {};
            if (app.globalData.token) header['X-Token'] = app.globalData.token;
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
        }),

        // 首页内容/推荐
        contentList: (current, size, kind) => call('/content/list?current=' + current + '&size=' + size + (kind ? '&kind=' + kind : ''), 'GET', null, false),
        contentDetail: (id) => call('/content/' + id, 'GET', null, false),
        shareContent: (id) => call('/content/' + id + '/share', 'POST'),
        recommend: (scene, enabled, limit) => call('/user/ai/recommend?scene=' + scene + '&enabled=' + enabled + '&limit=' + limit, 'GET'),

        // 捐赠
        donationSubmit: (data) => call('/donation/submit', 'POST', data),
        donationMine: () => call('/donation/mine', 'GET'),
        donationEstimate: (data) => call('/donation/estimate', 'POST', data),

        // 商城/购物车/订单
        products: (current, size, kind) => call('/shop/products?current=' + current + '&size=' + size + (kind ? '&kind=' + kind : ''), 'GET', null, false),
        productDetail: (id) => call('/shop/products/' + id, 'GET', null, false),
        cartList: () => call('/shop/cart', 'GET'),
        cartAdd: (data) => call('/shop/cart', 'POST', data),
        cartUpdate: (data) => call('/shop/cart', 'PUT', data),
        cartRemove: (id) => call('/shop/cart/' + id, 'DELETE'),
        checkoutPreview: (data) => call('/shop/checkout-preview', 'POST', data),
        createOrder: (data) => call('/shop/order', 'POST', data),
        confirmPay: (orderNo) => call('/shop/pay/confirm', 'POST', { orderNo }),
        myOrders: () => call('/shop/orders', 'GET'),
        orderDetail: (id) => call('/shop/orders/' + id, 'GET'),
        refundOrder: (id) => call('/shop/orders/' + id + '/refund', 'POST'),
        applyReturn: (id) => call('/shop/orders/' + id + '/return-apply', 'POST'),

        // 免费申领
        calcFreight: (data) => call('/claim/freight', 'POST', data),
        claimSubmit: (data) => call('/claim/submit', 'POST', data),
        claimConfirmPay: (claimNo) => call('/claim/pay/confirm', 'POST', { claimNo }),
        myClaims: () => call('/claim/mine', 'GET'),

        // 活动
        activityList: (current, size) => call('/activity/list?current=' + current + '&size=' + size, 'GET', null, false),
        activityDetail: (id) => call('/activity/' + id, 'GET', null, false),
        activitySignup: (id, data) => call('/activity/' + id + '/signup', 'POST', data),
        mySignups: () => call('/activity/mine/signups', 'GET'),

        // 用户/积分/荣誉/证书
        profile: () => call('/user/profile', 'GET'),
        updateProfile: (data) => call('/user/profile', 'PUT', data),
        addresses: () => call('/user/addresses', 'GET'),
        saveAddress: (data) => call('/user/address', 'POST', data),
        deleteAddress: (id) => call('/user/address/' + id, 'DELETE'),
        points: () => call('/user/points', 'GET'),
        pointLedger: () => call('/user/points/ledger', 'GET'),
        pointsPreview: (data) => call('/user/points/preview', 'POST', data),
        honor: () => call('/user/honor', 'GET'),
        certificates: () => call('/user/certificates', 'GET'),
        verifyCert: (certNo, verifyCode) => call('/certificate/verify?certNo=' + certNo + '&verifyCode=' + (verifyCode || ''), 'GET', null, false),
        aiChat: (question) => call('/user/ai/chat', 'POST', { question })
    };
}

module.exports = { api };
