const app = getApp();
const { api } = require('../../utils/api.js');

Page({
    data: {
        viewState: 'loading',
        tab: 'goods',
        products: [],
        claims: [],
        errorMsg: ''
    },

    onLoad() { this.loadGoods(); },
    onPullDownRefresh() { this.reload(() => wx.stopPullDownRefresh()); },

    reload(done) {
        if (this.data.tab === 'goods') this.loadGoods(done);
        else this.loadClaims(done);
    },

    loadGoods(done) {
        this.setData({ viewState: 'loading', errorMsg: '' });
        api(app).products(1, 50, 'CLAIM').then(res => {
            this.setData({ products: (res && res.records) || [], viewState: 'ready' });
            if (done) done();
        }).catch(err => {
            this.setData({ viewState: 'error', errorMsg: (err && err.message) || '加载失败' });
            if (done) done();
        });
    },

    loadClaims(done) {
        this.setData({ viewState: 'loading', errorMsg: '' });
        app.ensureLogin().then(() => api(app).myClaims()).then(list => {
            const claims = (list || []).map(item => Object.assign({}, item, {
                timeText: this.formatTime(item.createdAt)
            }));
            this.setData({ claims: claims, viewState: 'ready' });
            if (done) done();
        }).catch(err => {
            this.setData({ viewState: 'error', errorMsg: (err && err.message) || '加载失败' });
            if (done) done();
        });
    },

    switchTab(e) {
        const tab = e.currentTarget.dataset.tab;
        if (tab === this.data.tab) return;
        this.setData({ tab: tab });
        if (tab === 'goods') this.loadGoods();
        else this.loadClaims();
    },

    retry() { this.reload(); },

    openOrder(e) {
        wx.navigateTo({ url: '/pages/claim-order/claim-order?productId=' + e.currentTarget.dataset.id });
    },

    formatTime(v) {
        if (!v) return '';
        if (typeof v === 'number') {
            const d = new Date(v);
            const p = function (n) { return n < 10 ? '0' + n : '' + n; };
            return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' ' + p(d.getHours()) + ':' + p(d.getMinutes());
        }
        return String(v).replace('T', ' ').slice(0, 16);
    }
});
