const app = getApp();
const { api } = require('../../utils/api.js');

const STATUS_LABELS = {
    PENDING_PAY: '待支付', PAID: '待发货', SHIPPED: '已发货',
    RETURN_APPLYING: '退货中', COMPLETED: '已完成', REFUNDED: '已退款'
};
const PAY_LABELS = { UNPAID: '待支付', PAID: '已支付', REFUNDED: '已退款' };
const TABS = [
    { key: 'ALL', label: '全部' },
    { key: 'PENDING_PAY', label: '待支付' },
    { key: 'PAID', label: '待发货' },
    { key: 'SHIPPED', label: '已发货' },
    { key: 'RETURN_APPLYING', label: '退货中' },
    { key: 'COMPLETED', label: '已完成' }
];

function fmtTime(v) {
    if (!v) return '';
    return String(v).replace('T', ' ').slice(0, 16);
}

Page({
    data: {
        viewState: 'loading',
        errorMsg: '',
        orders: [],
        filtered: [],
        filter: 'ALL',
        tabs: TABS
    },

    onLoad() { this.load(); },
    onShow() { if (this.data.viewState === 'ready') this.load(); },
    onPullDownRefresh() { this.load(() => wx.stopPullDownRefresh()); },

    load(done) {
        this.setData({ viewState: 'loading', errorMsg: '' });
        app.ensureLogin().then(() => api(app).myOrders()).then(res => {
            const list = (Array.isArray(res) ? res : []).map(o => ({
                id: o.id,
                orderNo: o.orderNo,
                status: o.status,
                statusLabel: STATUS_LABELS[o.status] || o.status || '未知',
                payLabel: PAY_LABELS[o.payStatus] || o.payStatus || '-',
                amountText: Number(o.payAmount || 0).toFixed(2),
                itemSummary: o.itemSummary || '-',
                dateText: fmtTime(o.createdAt),
                logisticsNo: o.logisticsNo || ''
            }));
            const key = this.data.filter;
            this.setData({
                orders: list,
                filtered: this.applyFilter(list, key),
                viewState: 'ready'
            });
            if (done) done();
        }).catch(err => {
            this.setData({ viewState: 'error', errorMsg: err.message || '加载失败' });
            if (done) done();
        });
    },

    applyFilter(list, key) {
        return key === 'ALL' ? list : list.filter(x => x.status === key);
    },

    retryLoad() { this.load(); },

    onFilterTap(e) {
        const key = e.currentTarget.dataset.key;
        this.setData({ filter: key, filtered: this.applyFilter(this.data.orders, key) });
    },

    goDetail(e) {
        wx.navigateTo({ url: '/pages/order-detail/order-detail?id=' + e.currentTarget.dataset.id });
    },

    goMall() { wx.switchTab({ url: '/pages/mall/mall' }); }
});
