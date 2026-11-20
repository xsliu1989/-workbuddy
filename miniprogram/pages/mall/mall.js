const app = getApp();
const { api } = require('../../utils/api.js');
const { coverOf } = require('../../utils/product.js');

function decorate(p) {
    p.priceText = p.kind === 'CLAIM' ? '免费申领' : ('¥' + p.price);
    if (!p.coverUrl) p.coverUrl = coverOf(p); // cover_url 为空时用图片列表/详情图兜底
    return p;
}

Page({
    data: {
        viewState: 'loading',
        activeKind: 'SALE',
        products: [],
        current: 1,
        total: 0,
        hasMore: false
    },

    onLoad() { this.loadProducts(); },
    onPullDownRefresh() { this.loadProducts(() => wx.stopPullDownRefresh()); },

    loadProducts(done) {
        const kind = this.data.activeKind;
        this.setData({ viewState: 'loading' });
        api(app).products(1, 12, kind).then(res => {
            const records = ((res && res.records) || []).map(decorate);
            const total = (res && res.total) || 0;
            this.setData({
                products: records,
                current: 1,
                total: total,
                hasMore: records.length < total,
                viewState: 'ready'
            });
            if (done) done();
        }).catch(() => {
            this.setData({ viewState: 'error' });
            if (done) done();
        });
    },

    retryLoad() { this.loadProducts(); },

    onKindTap(e) {
        const kind = e.currentTarget.dataset.kind;
        if (kind === this.data.activeKind) return;
        this.setData({ activeKind: kind });
        this.loadProducts();
    },

    loadMore() {
        const d = this.data;
        const next = d.current + 1;
        api(app).products(next, 12, d.activeKind).then(res => {
            const records = ((res && res.records) || []).map(decorate);
            const all = d.products.concat(records);
            this.setData({
                products: all,
                current: next,
                total: (res && res.total) || d.total,
                hasMore: all.length < ((res && res.total) || d.total)
            });
        }).catch(err => wx.showToast({ icon: 'none', title: err.message }));
    },

    goCart() {
        wx.navigateTo({ url: '/pages/cart/cart' });
    },

    /** 点商品卡片进入独立详情页 */
    onProductTap(e) {
        const id = e.currentTarget.dataset.id;
        wx.navigateTo({ url: '/pages/product-detail/product-detail?id=' + id });
    },

    onQuickAdd(e) {
        this.addToCart(e.currentTarget.dataset.id, 1);
    },

    onQuickBuy(e) {
        this.buyNow(e.currentTarget.dataset.id, 1);
    },

    addToCart(productId, quantity) {
        app.ensureLogin().then(() => {
            return api(app).cartAdd({ productId: productId, quantity: quantity || 1 });
        }).then(() => {
            wx.showToast({ title: '已加入购物车', icon: 'success' });
        }).catch(err => {
            wx.showToast({ icon: 'none', title: err.message });
        });
    },

    /** 立即购买：仅选中该商品后进入结算，复用购物车结算链路 */
    buyNow(productId, quantity) {
        const qty = quantity || 1;
        app.ensureLogin().then(() => {
            return api(app).cartList();
        }).then(res => {
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
            wx.navigateTo({ url: '/pages/checkout/checkout' });
        }).catch(err => {
            wx.showToast({ icon: 'none', title: err.message });
        });
    }
});
