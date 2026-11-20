const app = getApp();
const { api } = require('../../utils/api.js');

Page({
    data: {
        viewState: 'loading',
        categories: [
            { label: '衣物被褥', value: '衣物被褥' },
            { label: '图书文具', value: '图书文具' },
            { label: '家用电器', value: '家用电器' },
            { label: '家具用品', value: '家具用品' },
            { label: '数码电子', value: '数码电子' },
            { label: '玩具乐器', value: '玩具乐器' },
            { label: '其他物资', value: '其他物资' }
        ],
        categoryIndex: 0,
        quantity: 1,
        description: '',
        photos: '',
        photoList: [],
        uploadingPhotos: false,
        deliveryTypes: [
            { label: '线下交投点', value: 'OFFLINE' },
            { label: '上门取件', value: 'PICKUP' },
            { label: '快递邮寄', value: 'MAIL' }
        ],
        deliveryIndex: 0,
        estimateState: 'idle',
        estimate: null,
        submitting: false
    },

    onLoad() { this.initPage(); },
    onPullDownRefresh() { this.initPage(() => wx.stopPullDownRefresh()); },

    initPage(done) {
        this.setData({ viewState: 'loading' });
        app.ensureLogin().then(() => {
            this.setData({ viewState: 'ready' });
            if (done) done();
        }).catch(() => {
            this.setData({ viewState: 'error' });
            if (done) done();
        });
    },

    retryLoad() { this.initPage(); },

    onCategoryChange(e) { this.setData({ categoryIndex: e.detail.value }); },
    onDeliveryChange(e) { this.setData({ deliveryIndex: e.detail.value }); },
    onQuantityInput(e) { this.setData({ quantity: e.detail.value }); },
    onDescInput(e) { this.setData({ description: e.detail.value }); },

    // 选择并上传照片（最多 6 张）
    choosePhotos() {
        const self = this;
        if (this.data.uploadingPhotos) return;
        wx.chooseMedia({
            count: 6 - this.data.photoList.length,
            mediaType: ['image'],
            sourceType: ['album', 'camera'],
            sizeType: ['compressed'],
            success(res) {
                const files = (res.tempFiles || []).map(f => f.tempFilePath);
                if (!files.length) return;
                self.setData({ uploadingPhotos: true });
                wx.showLoading({ title: '上传中…' });
                const urls = [];
                files.reduce((chain, path) => chain.then(() => api(app).uploadImage(path).then(url => urls.push(url))), Promise.resolve())
                    .then(() => {
                        wx.hideLoading();
                        const list = self.data.photoList.concat(urls);
                        self.setData({ photoList: list, photos: list.join(','), uploadingPhotos: false });
                    })
                    .catch(err => {
                        wx.hideLoading();
                        self.setData({ uploadingPhotos: false });
                        wx.showToast({ icon: 'none', title: (err && err.message) || '照片上传失败' });
                    });
            }
        });
    },

    removePhoto(e) {
        const idx = Number(e.currentTarget.dataset.index);
        const list = this.data.photoList.slice();
        list.splice(idx, 1);
        this.setData({ photoList: list, photos: list.join(',') });
    },

    previewPhoto(e) {
        const src = e.currentTarget.dataset.src;
        wx.previewImage({ current: src, urls: this.data.photoList });
    },

    doEstimate() {
        const d = this.data;
        if (!d.description || !d.description.trim()) {
            wx.showToast({ icon: 'none', title: '请先填写物资描述' });
            return;
        }
        this.setData({ estimateState: 'loading' });
        api(app).donationEstimate({
            category: d.categories[d.categoryIndex].value,
            description: d.description
        }).then(res => {
            this.setData({ estimate: res || {}, estimateState: 'ready' });
        }).catch(err => {
            this.setData({ estimateState: 'error' });
            wx.showToast({ icon: 'none', title: err.message });
        });
    },

    retryEstimate() { this.doEstimate(); },

    submitDonation() {
        const d = this.data;
        if (!d.description || !d.description.trim()) {
            wx.showToast({ icon: 'none', title: '请先填写物资描述' });
            return;
        }
        const quantity = parseInt(d.quantity, 10);
        if (!quantity || quantity < 1) {
            wx.showToast({ icon: 'none', title: '数量至少为 1' });
            return;
        }
        if (d.submitting) return;
        this.setData({ submitting: true });
        wx.showLoading({ title: '提交中' });
        api(app).donationSubmit({
            category: d.categories[d.categoryIndex].value,
            quantity: quantity,
            description: d.description,
            photos: d.photos,
            deliveryType: d.deliveryTypes[d.deliveryIndex].value
        }).then(res => {
            wx.hideLoading();
            this.setData({ submitting: false });
            const no = (res && res.donationNo) || '';
            wx.showModal({
                title: '提交成功',
                content: '捐赠单号：' + no + '\n请留意后续接收与评估通知。',
                confirmText: '查看记录',
                cancelText: '继续捐赠',
                success: (r) => {
                    if (r.confirm) {
                        wx.navigateTo({ url: '/pages/donation-list/donation-list' });
                    } else {
                        this.setData({ description: '', photos: '', photoList: [], quantity: 1, estimate: null, estimateState: 'idle' });
                    }
                }
            });
        }).catch(err => {
            wx.hideLoading();
            this.setData({ submitting: false });
            wx.showToast({ icon: 'none', title: err.message });
        });
    },

    goRecords() {
        wx.navigateTo({ url: '/pages/donation-list/donation-list' });
    }
});
