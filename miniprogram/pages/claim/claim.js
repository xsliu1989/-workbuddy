const app = getApp();
const { api } = require('../../utils/api.js');
const { coverOf } = require('../../utils/product.js');
const imageLocal = require('../../utils/image-local.js');

Page({
    data: {
        viewState: 'loading',
        tab: 'goods',
        products: [],
        claims: [],
        errorMsg: ''
    },

    onLoad(options) {
        // 支持从「我的」页直接定位到「我的申领」标签
        const tab = options && options.tab === 'mine' ? 'mine' : 'goods';
        this.setData({ tab: tab });
        if (tab === 'mine') this.loadClaims();
        else this.loadGoods();
    },
    onPullDownRefresh() { this.reload(() => wx.stopPullDownRefresh()); },

    reload(done) {
        if (this.data.tab === 'goods') this.loadGoods(done);
        else this.loadClaims(done);
    },

    loadGoods(done) {
        this.setData({ viewState: 'loading', errorMsg: '' });
        api(app).products(1, 50, 'CLAIM').then(res => {
            const products = ((res && res.records) || []).map(function (p) {
                if (!p.coverUrl) p.coverUrl = coverOf(p);
                return p;
            });
            return imageLocal.hydrateCovers(products).then(list => {
                this.setData({ products: list, viewState: 'ready' });
                if (done) done();
            });
        }).catch(err => {
            this.setData({ viewState: 'error', errorMsg: (err && err.message) || '加载失败' });
            if (done) done();
        });
    },

    loadClaims(done) {
        this.setData({ viewState: 'loading', errorMsg: '' });
        const self = this;
        const render = list => {
            const claims = (list || []).map(item => Object.assign({}, item, {
                timeText: self.formatTime(item.createdAt)
            }));
            self.setData({ claims: claims, viewState: 'ready' });
            if (done) done();
        };
        const fail = err => {
            self.setData({ viewState: 'error', errorMsg: (err && err.message) || '加载失败' });
            if (done) done();
        };
        app.ensureLogin().then(() => api(app).myClaims()).then(render).catch(err => {
            // 登录态过期时 request.js 已清空 token，这里自动重新登录并重试一次，避免“点了没反应”
            const msg = (err && err.message) || '';
            if (msg.indexOf('登录') >= 0 || msg.indexOf('401') >= 0) {
                app.globalData.token = null;
                app.globalData.userInfo = null;
                return app.ensureLogin().then(() => api(app).myClaims()).then(render).catch(fail);
            }
            fail(err);
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
