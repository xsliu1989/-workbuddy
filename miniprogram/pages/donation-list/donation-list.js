const app = getApp();
const { api } = require('../../utils/api.js');

Page({
    data: {
        viewState: 'loading',
        list: []
    },

    onLoad() { this.loadData(); },
    onPullDownRefresh() { this.loadData(() => wx.stopPullDownRefresh()); },

    loadData(done) {
        this.setData({ viewState: 'loading' });
        app.ensureLogin().then(() => {
            return api(app).donationMine();
        }).then(res => {
            const raw = (res && Array.isArray(res)) ? res : [];
            const list = raw.map(function (x) {
                const hasValue = x.valueAmount !== null && x.valueAmount !== undefined;
                return {
                    id: x.id,
                    donationNo: x.donationNo,
                    category: x.category,
                    quantity: x.quantity,
                    status: x.status,
                    statusLabel: x.statusLabel || x.status,
                    createdAt: x.createdAt,
                    valueText: hasValue ? ('¥' + x.valueAmount) : '待评估',
                    badgeClass: (x.status === 'PENDING_RECEIVE' || x.status === 'REVIEWING') ? 'tag tag-warn' : 'tag'
                };
            });
            this.setData({ list: list, viewState: 'ready' });
            if (done) done();
        }).catch(() => {
            this.setData({ viewState: 'error' });
            if (done) done();
        });
    },

    retryLoad() { this.loadData(); },

    goDonate() {
        wx.switchTab({ url: '/pages/donation/donation' });
    }
});
