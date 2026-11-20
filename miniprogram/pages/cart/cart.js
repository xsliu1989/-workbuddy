const app = getApp();
const { api } = require('../../utils/api.js');
const { coverOf } = require('../../utils/product.js');

Page({
    data: {
        viewState: 'loading',
        items: [],
        allSelected: false,
        selectedCount: 0,
        totalText: '0.00'
    },

    onLoad() { this.loadData(); },
    onPullDownRefresh() { this.loadData(() => wx.stopPullDownRefresh()); },

    loadData(done) {
        this.setData({ viewState: 'loading' });
        app.ensureLogin().then(() => {
            return api(app).cartList();
        }).then(res => {
            const raw = (res && Array.isArray(res)) ? res : [];
            const items = raw.map(function (x) {
                const price = x.price || 0;
                const qty = x.quantity || 0;
                return {
                    id: x.id,
                    productId: x.productId,
                    name: x.name,
                    price: price,
                    coverUrl: x.coverUrl || coverOf(x),
                    quantity: qty,
                    stock: x.stock || 0,
                    checked: (x.selected === 1 || x.selected === true),
                    lineText: (price * qty).toFixed(2)
                };
            });
            this.setData({ items: items, viewState: 'ready' });
            this.recalc();
            if (done) done();
        }).catch(() => {
            this.setData({ viewState: 'error' });
            if (done) done();
        });
    },

    retryLoad() { this.loadData(); },

    recalc() {
        const items = this.data.items;
        let count = 0;
        let total = 0;
        for (let i = 0; i < items.length; i++) {
            if (items[i].checked) {
                count++;
                total += items[i].price * items[i].quantity;
            }
        }
        this.setData({
            selectedCount: count,
            totalText: total.toFixed(2),
            allSelected: items.length > 0 && count === items.length
        });
    },

    findItem(id) {
        const items = this.data.items;
        for (let i = 0; i < items.length; i++) {
            if (items[i].id == id) return items[i];
        }
        return null;
    },

    onToggleSelect(e) {
        const target = this.findItem(e.currentTarget.dataset.id);
        if (!target) return;
        const next = !target.checked;
        api(app).cartUpdate({ id: target.id, selected: next ? 1 : 0 }).then(() => {
            target.checked = next;
            this.setData({ items: this.data.items });
            this.recalc();
        }).catch(err => wx.showToast({ icon: 'none', title: err.message }));
    },

    onChangeQty(e) {
        const target = this.findItem(e.currentTarget.dataset.id);
        if (!target) return;
        const delta = Number(e.currentTarget.dataset.delta);
        const qty = target.quantity + delta;
        if (qty < 1) {
            wx.showToast({ icon: 'none', title: '数量至少为 1' });
            return;
        }
        if (qty > target.stock) {
            wx.showToast({ icon: 'none', title: '库存不足' });
            return;
        }
        api(app).cartUpdate({ id: target.id, quantity: qty }).then(() => {
            target.quantity = qty;
            target.lineText = (target.price * qty).toFixed(2);
            this.setData({ items: this.data.items });
            this.recalc();
        }).catch(err => wx.showToast({ icon: 'none', title: err.message }));
    },

    onRemove(e) {
        const id = e.currentTarget.dataset.id;
        wx.showModal({
            title: '提示',
            content: '确定移除该商品吗？',
            success: (r) => {
                if (r.confirm) {
                    api(app).cartRemove(id).then(() => {
                        wx.showToast({ title: '已移除', icon: 'success' });
                        this.loadData();
                    }).catch(err => wx.showToast({ icon: 'none', title: err.message }));
                }
            }
        });
    },

    onToggleAll() {
        const items = this.data.items;
        if (items.length === 0) return;
        const target = !this.data.allSelected;
        const tasks = items.map(function (it) {
            return api(app).cartUpdate({ id: it.id, selected: target ? 1 : 0 });
        });
        Promise.all(tasks).then(() => {
            for (let i = 0; i < items.length; i++) items[i].checked = target;
            this.setData({ items: items });
            this.recalc();
        }).catch(err => wx.showToast({ icon: 'none', title: err.message }));
    },

    goCheckout() {
        if (this.data.selectedCount === 0) {
            wx.showToast({ icon: 'none', title: '请先选择商品' });
            return;
        }
        wx.navigateTo({ url: '/pages/checkout/checkout' });
    },

    goMall() {
        wx.switchTab({ url: '/pages/mall/mall' });
    }
});
