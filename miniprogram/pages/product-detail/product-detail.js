const app = getApp();
const { api } = require('../../utils/api.js');
const { coverOf } = require('../../utils/product.js');

Page({
    data: {
        viewState: 'loading',
        errorMsg: '',
        id: '',
        product: null,
        isClaim: false,
        priceText: '',
        images: [],
        coverUrl: '',
        qty: 1,
        buying: false,
        adding: false
    },

    onLoad(options) {
        this.setData({ id: options.id || '' });
        this.load();
    },
    onPullDownRefresh() { this.load(() => wx.stopPullDownRefresh()); },

    load(done) {
        this.setData({ viewState: 'loading', errorMsg: '' });
        const id = this.data.id;
        if (!id) {
            this.setData({ viewState: 'error', errorMsg: '缺少商品参数' });
            if (done) done();
            return;
        }
        api(app).productDetail(id).then(res => {
            const p = res || {};
            const isClaim = p.kind === 'CLAIM';
            const images = this.parseImages(p.images);
            const cover = coverOf(p);
            this.setData({
                product: p,
                isClaim: isClaim,
                priceText: isClaim ? '免费申领（仅付运费）' : ('¥' + p.price),
                images: images.length ? images : (cover ? [cover] : []),
                coverUrl: cover,
                viewState: 'ready'
            });
            if (done) done();
        }).catch(err => {
            this.setData({ viewState: 'error', errorMsg: (err && err.message) || '加载失败' });
            if (done) done();
        });
    },

    parseImages(images) {
        if (!images) return [];
        if (Array.isArray(images)) return images;
        return String(images).split(/[,，\n]/).map(s => s.trim()).filter(Boolean);
    },

    retry() { this.load(); },

    previewImage(e) {
        const src = e.currentTarget.dataset.src;
        const urls = this.data.images.length ? this.data.images : (this.data.coverUrl ? [this.data.coverUrl] : []);
        if (!src || urls.length === 0) return;
        wx.previewImage({ current: src, urls: urls });
    },

    changeQty(e) {
        const delta = Number(e.currentTarget.dataset.delta) || 0;
        let qty = this.data.qty + delta;
        const p = this.data.product || {};
        const stock = Number(p.stock || 0);
        if (qty < 1) qty = 1;
        if (stock > 0 && qty > stock) {
            wx.showToast({ icon: 'none', title: '库存不足' });
            qty = stock;
        }
        this.setData({ qty: qty });
    },

    /** 免费申领品跳转申领页 */
    goClaim() {
        wx.navigateTo({ url: '/pages/claim-order/claim-order?productId=' + this.data.id });
    },

    addCart() {
        if (this.data.adding) return;
        this.setData({ adding: true });
        app.ensureLogin().then(() => api(app).cartAdd({ productId: this.data.id, quantity: this.data.qty })).then(() => {
            this.setData({ adding: false });
            wx.showToast({ title: '已加入购物车', icon: 'success' });
        }).catch(err => {
            this.setData({ adding: false });
            wx.showToast({ icon: 'none', title: err.message });
        });
    },

    /** 立即购买：仅选中该商品，复用购物车结算链路 */
    buyNow() {
        if (this.data.buying) return;
        this.setData({ buying: true });
        const productId = this.data.id;
        const qty = this.data.qty;
        app.ensureLogin().then(() => api(app).cartList()).then(res => {
            const list = (res && Array.isArray(res)) ? res : [];
            const tasks = [];
            let target = null;
            for (let i = 0; i < list.length; i++) {
                if (list[i].productId == productId) { target = list[i]; break; }
            }
            if (target) {
                tasks.push(api(app).cartUpdate({ id: target.id, quantity: qty, selected: 1 }));
            } else {
                tasks.push(api(app).cartAdd({ productId: productId, quantity: qty }));
            }
            for (let i = 0; i < list.length; i++) {
                const it = list[i];
                if ((!target || it.id != target.id) && Number(it.selected) === 1) {
                    tasks.push(api(app).cartUpdate({ id: it.id, selected: 0 }));
                }
            }
            return Promise.all(tasks);
        }).then(() => {
            this.setData({ buying: false });
            wx.navigateTo({ url: '/pages/checkout/checkout' });
        }).catch(err => {
            this.setData({ buying: false });
            wx.showToast({ icon: 'none', title: err.message });
        });
    },

    goCart() { wx.navigateTo({ url: '/pages/cart/cart' }); }
});
