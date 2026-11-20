const app = getApp();
const { api } = require('../../utils/api.js');

const REGIONS = ['全国', '华东', '华北', '华南', '西南', '西北'];

Page({
    data: {
        viewState: 'loading',
        errorMsg: '',
        productId: '',
        product: null,
        regions: REGIONS,
        regionIndex: 0,
        region: '全国',
        addresses: [],
        addressTexts: [],
        addressIndex: -1,
        addressId: '',
        freight: null,
        freightLoading: false,
        submitting: false
    },

    onLoad(options) {
        this.setData({ productId: options.productId || '' });
        this.loadPage();
    },
    onPullDownRefresh() { this.loadPage(() => wx.stopPullDownRefresh()); },
    onShow() {
        if (this.data.viewState === 'ready') this.reloadAddresses();
    },

    loadPage(done) {
        this.setData({ viewState: 'loading', errorMsg: '' });
        const pid = this.data.productId;
        if (!pid) {
            this.setData({ viewState: 'error', errorMsg: '缺少商品参数' });
            if (done) done();
            return;
        }
        app.ensureLogin().then(() => {
            return Promise.all([
                api(app).productDetail(pid),
                api(app).addresses()
            ]);
        }).then(res => {
            const product = res[0] || {};
            const addresses = res[1] || [];
            const addressTexts = addresses.map(a => a.receiver + ' ' + a.phone + ' ' + (a.province || '') + (a.city || '') + (a.district || '') + (a.detail || ''));
            let addressIndex = -1;
            for (let i = 0; i < addresses.length; i++) {
                if (addresses[i].isDefault == 1) { addressIndex = i; break; }
            }
            if (addressIndex < 0 && addresses.length > 0) addressIndex = 0;
            this.setData({
                product: product,
                addresses: addresses,
                addressTexts: addressTexts,
                addressIndex: addressIndex,
                addressId: addressIndex >= 0 ? addresses[addressIndex].id : '',
                viewState: 'ready'
            });
            this.calcFreight();
            if (done) done();
        }).catch(err => {
            this.setData({ viewState: 'error', errorMsg: (err && err.message) || '加载失败' });
            if (done) done();
        });
    },

    reloadAddresses() {
        api(app).addresses().then(list => {
            const addresses = list || [];
            const addressTexts = addresses.map(a => a.receiver + ' ' + a.phone + ' ' + (a.province || '') + (a.city || '') + (a.district || '') + (a.detail || ''));
            let idx = this.data.addressIndex;
            if (idx < 0 || idx >= addresses.length) idx = addresses.length > 0 ? 0 : -1;
            this.setData({
                addresses: addresses,
                addressTexts: addressTexts,
                addressIndex: idx,
                addressId: idx >= 0 ? addresses[idx].id : ''
            });
        }).catch(err => wx.showToast({ icon: 'none', title: (err && err.message) || '地址加载失败' }));
    },

    retry() { this.loadPage(); },

    onRegionChange(e) {
        const index = parseInt(e.detail.value, 10);
        this.setData({ regionIndex: index, region: REGIONS[index] });
        this.calcFreight();
    },

    onAddressChange(e) {
        const index = parseInt(e.detail.value, 10);
        this.setData({ addressIndex: index, addressId: this.data.addresses[index].id });
    },

    calcFreight() {
        this.setData({ freightLoading: true });
        api(app).calcFreight({ productId: this.data.productId, region: this.data.region }).then(res => {
            this.setData({ freight: res, freightLoading: false });
        }).catch(err => {
            this.setData({ freightLoading: false });
            wx.showToast({ icon: 'none', title: (err && err.message) || '运费试算失败' });
        });
    },

    goAddress() {
        wx.navigateTo({ url: '/pages/address/address' });
    },

    submitClaim() {
        if (!this.data.addressId) {
            wx.showToast({ icon: 'none', title: '请先选择或新增收货地址' });
            return;
        }
        if (!this.data.freight) {
            wx.showToast({ icon: 'none', title: '运费计算中，请稍候' });
            return;
        }
        this.setData({ submitting: true });
        api(app).claimSubmit({
            productId: this.data.productId,
            addressId: this.data.addressId,
            region: this.data.region
        }).then(res => {
            this.setData({ submitting: false });
            wx.showModal({
                title: '申领已创建',
                content: '需支付运费 ' + res.freightAmount + ' 元（模拟支付），是否确认支付？',
                success: r => {
                    if (r.confirm) this.confirmPay(res.claimNo);
                }
            });
        }).catch(err => {
            this.setData({ submitting: false });
            wx.showToast({ icon: 'none', title: (err && err.message) || '提交失败' });
        });
    },

    confirmPay(claimNo) {
        wx.showLoading({ title: '支付中' });
        api(app).claimConfirmPay(claimNo).then(() => {
            wx.hideLoading();
            wx.showModal({
                title: '申领成功',
                content: '运费支付完成，我们会尽快为您发货。',
                showCancel: false,
                success: () => wx.switchTab({ url: '/pages/claim/claim' })
            });
        }).catch(err => {
            wx.hideLoading();
            wx.showToast({ icon: 'none', title: (err && err.message) || '支付失败' });
        });
    }
});
