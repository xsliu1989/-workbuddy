const app = getApp();
const { api } = require('../../utils/api.js');

Page({
    data: {
        certNo: '',
        verifyCode: '',
        viewState: 'ready',
        result: null,
        errorMsg: ''
    },

    onLoad(query) {
        if (query && query.certNo) {
            this.setData({ certNo: query.certNo });
        }
    },
    onShareAppMessage() { return { title: '绿益公益 · 证书验真', path: '/pages/cert-verify/cert-verify' }; },

    onCertNoInput(e) { this.setData({ certNo: e.detail.value }); },
    onCodeInput(e) { this.setData({ verifyCode: e.detail.value }); },

    doVerify() {
        const certNo = (this.data.certNo || '').trim();
        if (!certNo) {
            wx.showToast({ icon: 'none', title: '请输入证书编号' });
            return;
        }
        this.setData({ viewState: 'loading', result: null, errorMsg: '' });
        const self = this;
        api(app).verifyCert(certNo, (this.data.verifyCode || '').trim()).then(res => {
            self.setData({ result: res || {}, viewState: 'ready' });
        }).catch(err => {
            self.setData({
                viewState: 'error',
                errorMsg: (err && err.message) || '验真失败，请稍后重试'
            });
        });
    },

    reset() {
        this.setData({ certNo: '', verifyCode: '', result: null, errorMsg: '', viewState: 'ready' });
    }
});
