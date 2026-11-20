const app = getApp();
const { api } = require('../../utils/api.js');

Page({
    data: {
        viewState: 'loading',
        errorMsg: '',
        addresses: [],
        addressId: null,
        selectedAddress: null,
        requestPoints: '',
        preview: null,
        submitting: false
    },

    onLoad() { this.loadData(); },

    /** 从地址管理页新增/编辑/删除地址返回后，重新加载地址并重算费用 */
    onShow() {
        if (this.data.viewState === 'ready') this.refreshAddresses();
    },

    loadData() {
        this.setData({ viewState: 'loading', errorMsg: '' });
        app.ensureLogin().then(() => {
            return api(app).addresses();
        }).then(res => {
            const addresses = (res && Array.isArray(res)) ? res : [];
            this.setData({ addresses: addresses });
            this.applyDefaultAddress(addresses, null);
            return this.refreshPreview(true);
        }).then(() => {
            this.setData({ viewState: 'ready' });
        }).catch(err => {
            this.setData({ viewState: 'error', errorMsg: err.message || '加载失败' });
        });
    },

    retryLoad() { this.loadData(); },

    /** 统一用地址对象里的 id 赋值，避免字符串/数字类型不一致导致的选中失败 */
    applyDefaultAddress(addresses, preferId) {
        let chosen = null;
        if (preferId !== null && preferId !== undefined) {
            for (let i = 0; i < addresses.length; i++) {
                if (addresses[i].id == preferId) { chosen = addresses[i]; break; }
            }
        }
        if (!chosen) {
            for (let i = 0; i < addresses.length; i++) {
                if (addresses[i].isDefault === 1) { chosen = addresses[i]; break; }
            }
        }
        if (!chosen && addresses.length > 0) chosen = addresses[0];
        this.setData({
            addressId: chosen ? chosen.id : null,
            selectedAddress: chosen
        });
    },

    refreshAddresses() {
        return api(app).addresses().then(res => {
            const addresses = (res && Array.isArray(res)) ? res : [];
            this.setData({ addresses: addresses });
            this.applyDefaultAddress(addresses, this.data.addressId);
            return this.refreshPreview(true);
        }).catch(() => {});
    },

    pointsValue() {
        const v = this.data.requestPoints;
        return (v === '' || v === null || v === undefined) ? null : v;
    },

    refreshPreview(silent) {
        return api(app).checkoutPreview({
            requestPoints: this.pointsValue(),
            addressId: this.data.addressId
        }).then(res => {
            this.setData({ preview: res });
            return res;
        }).catch(err => {
            if (!silent) wx.showToast({ icon: 'none', title: err.message });
            throw err;
        });
    },

    calcAgain() {
        this.refreshPreview(false).catch(() => {});
    },

    onAddressTap(e) {
        const id = e.currentTarget.dataset.id;
        const addresses = this.data.addresses;
        let chosen = null;
        for (let i = 0; i < addresses.length; i++) {
            if (addresses[i].id == id) { chosen = addresses[i]; break; }
        }
        if (!chosen) return;
        this.setData({ addressId: chosen.id, selectedAddress: chosen });
        this.calcAgain();
    },

    goAddress() {
        wx.navigateTo({ url: '/pages/address/address' });
    },

    onPointsInput(e) { this.setData({ requestPoints: e.detail.value }); },
    onPointsBlur() { this.calcAgain(); },

    useAllPoints() {
        const p = this.data.preview;
        if (!p || !p.discount) return;
        this.setData({ requestPoints: String(p.discount.usablePoints) });
        this.calcAgain();
    },

    clearPoints() {
        this.setData({ requestPoints: '' });
        this.calcAgain();
    },

    submitOrder() {
        const d = this.data;
        if (d.submitting) return;
        if (!d.addressId) {
            wx.showModal({
                title: '提示',
                content: '请先添加并选择收货地址',
                confirmText: '去添加',
                cancelText: '取消',
                success: (r) => { if (r.confirm) this.goAddress(); }
            });
            return;
        }
        this.setData({ submitting: true });
        wx.showLoading({ title: '提交中' });
        api(app).createOrder({
            requestPoints: this.pointsValue(),
            addressId: d.addressId
        }).then(res => {
            wx.hideLoading();
            this.setData({ submitting: false });
            const orderNo = (res && res.orderNo) || '';
            wx.showModal({
                title: '订单已创建',
                content: '订单号：' + orderNo + '\n实付：¥' + res.payAmount + '\n确认后模拟完成支付。',
                confirmText: '确认支付',
                cancelText: '稍后支付',
                success: (r) => {
                    if (r.confirm) {
                        this.payOrder(orderNo);
                    } else {
                        wx.showToast({ icon: 'none', title: '订单待支付，可在我的订单中查看' });
                    }
                }
            });
        }).catch(err => {
            wx.hideLoading();
            this.setData({ submitting: false });
            wx.showToast({ icon: 'none', title: err.message });
        });
    },

    payOrder(orderNo) {
        wx.showLoading({ title: '支付中' });
        api(app).confirmPay(orderNo).then(() => {
            wx.hideLoading();
            wx.showModal({
                title: '支付成功',
                content: '感谢您的支持，公益商品将尽快为您寄出。',
                showCancel: false,
                confirmText: '返回商城',
                success: () => { wx.switchTab({ url: '/pages/mall/mall' }); }
            });
        }).catch(err => {
            wx.hideLoading();
            wx.showToast({ icon: 'none', title: err.message });
        });
    }
});
