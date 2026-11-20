const app = getApp();
const { api } = require('../../utils/api.js');

const TYPE_LABELS = { EARN: '获得', USE: '使用', REFUND: '退回' };

Page({
    data: {
        viewState: 'loading',
        account: null,
        allLedgers: [],
        ledgers: [],
        typeFilter: 'ALL',
        filters: [
            { key: 'ALL', label: '全部' },
            { key: 'EARN', label: '获得' },
            { key: 'USE', label: '使用' },
            { key: 'REFUND', label: '退回' }
        ]
    },

    onLoad() { this.loadData(); },
    onPullDownRefresh() { this.loadData(() => wx.stopPullDownRefresh()); },
    onShareAppMessage() { return { title: '我的绿益公益积分', path: '/pages/points/points' }; },

    loadData(done) {
        this.setData({ viewState: 'loading' });
        const self = this;
        app.ensureLogin().then(() => Promise.all([
            api(app).points(),
            api(app).pointLedger()
        ])).then(results => {
            const account = results[0] || {};
            const raw = results[1] || [];
            const list = raw.map(item => {
                const amount = Number(item.amount || 0);
                return {
                    id: item.id,
                    typeLabel: TYPE_LABELS[item.changeType] || '变动',
                    changeType: item.changeType,
                    amountText: amount > 0 ? ('+' + amount) : String(amount),
                    isGain: amount >= 0,
                    balanceAfter: item.balanceAfter,
                    remark: item.remark || '—',
                    refNo: item.refNo || '',
                    createdAt: item.createdAt
                };
            });
            self.setData({ account: account, allLedgers: list, viewState: 'ready' });
            self.applyFilter(self.data.typeFilter);
            if (done) done();
        }).catch(err => {
            self.setData({ viewState: 'error' });
            wx.showToast({ icon: 'none', title: (err && err.message) || '加载失败' });
            if (done) done();
        });
    },

    onFilter(e) {
        const type = e.currentTarget.dataset.type;
        this.setData({ typeFilter: type });
        this.applyFilter(type);
    },

    applyFilter(type) {
        const all = this.data.allLedgers || [];
        const ledgers = type === 'ALL' ? all : all.filter(item => item.changeType === type);
        this.setData({ ledgers: ledgers });
    }
});
