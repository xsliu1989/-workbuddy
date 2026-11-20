const app = getApp();
const { api } = require('../../utils/api.js');

const STATUS_LABELS = {
    PENDING_PAY: '待支付', PAID: '待发货', SHIPPED: '已发货',
    RETURN_APPLYING: '退货中', COMPLETED: '已完成', REFUNDED: '已退款'
};

function fmtTime(v) {
    if (!v) return '';
    return String(v).replace('T', ' ').slice(0, 16);
}

Page({
    data: {
        viewState: 'loading',
        errorMsg: '',
        order: null,
        items: [],
        steps: [],
        showAfterSale: false
    },

    onLoad(options) {
        this.orderId = options.id;
        this.load();
    },
    onPullDownRefresh() { this.load(() => wx.stopPullDownRefresh()); },

    load(done) {
        this.setData({ viewState: 'loading', errorMsg: '' });
        api(app).orderDetail(this.orderId).then(res => {
            if (!res) throw new Error('订单不存在');
            const paid = res.payStatus === 'PAID';
            const shipped = res.status === 'SHIPPED' || res.status === 'COMPLETED';
            const completed = res.status === 'COMPLETED';
            const steps = [
                { title: '提交订单', time: fmtTime(res.createdAt), desc: '订单已创建，等待付款', done: true },
                { title: '支付成功', time: fmtTime(res.paidAt), desc: paid ? '感谢你的支持' : '等待支付', done: paid },
                { title: '商家发货', time: '', desc: res.logisticsNo ? ('物流单号 ' + res.logisticsNo) : '商品寄出后可查看物流单号', done: shipped },
                { title: '订单完成', time: '', desc: completed ? '已完成' : '等待收货确认', done: completed }
            ];
            if (res.status === 'RETURN_APPLYING' || res.status === 'REFUNDED') {
                steps.push({
                    title: '退货处理',
                    time: '',
                    desc: res.status === 'RETURN_APPLYING' ? '已提交退货申请，等待平台审核' : '退货完成，货款与积分已退回',
                    done: true
                });
            }
            this.setData({
                order: Object.assign({}, res, { statusLabel: STATUS_LABELS[res.status] || res.status || '未知' }),
                items: res.items || [],
                steps: steps,
                viewState: 'ready'
            });
            if (done) done();
        }).catch(err => {
            this.setData({ viewState: 'error', errorMsg: err.message || '加载失败' });
            if (done) done();
        });
    },

    retryLoad() { this.load(); },

    copyOrderNo() {
        if (!this.data.order) return;
        wx.setClipboardData({
            data: String(this.data.order.orderNo),
            success: () => wx.showToast({ title: '订单号已复制' })
        });
    },

    copyLogistics() {
        const no = this.data.order && this.data.order.logisticsNo;
        if (!no) return;
        wx.setClipboardData({
            data: String(no),
            success: () => wx.showToast({ title: '物流单号已复制' })
        });
    },

    openAfterSale() { this.setData({ showAfterSale: true }); },
    closeAfterSale() { this.setData({ showAfterSale: false }); },

    applyRefundOnly() {
        this.closeAfterSale();
        const self = this;
        wx.showModal({
            title: '仅退款',
            content: '确认申请仅退款？退款后积分与库存将回退。',
            success(r) {
                if (!r.confirm) return;
                wx.showLoading({ title: '提交中' });
                api(app).refundOrder(self.data.order.id).then(() => {
                    wx.hideLoading(); wx.showToast({ title: '退款成功' }); self.load();
                }).catch(err => { wx.hideLoading(); wx.showToast({ icon: 'none', title: err.message }); });
            }
        });
    },

    applyReturnGoods() {
        this.closeAfterSale();
        const self = this;
        wx.showModal({
            title: '退货退款',
            content: '确认提交退货申请？提交后请将商品寄回，平台审核通过后退款。',
            success(r) {
                if (!r.confirm) return;
                wx.showLoading({ title: '提交中' });
                api(app).applyReturn(self.data.order.id).then(() => {
                    wx.hideLoading(); wx.showToast({ title: '退货申请已提交' }); self.load();
                }).catch(err => { wx.hideLoading(); wx.showToast({ icon: 'none', title: err.message }); });
            }
        });
    },

    goMall() { wx.switchTab({ url: '/pages/mall/mall' }); }
});
