const app = getApp();
const { api } = require('../../utils/api.js');
const imageLocal = require('../../utils/image-local.js');
const timeUtil = require('../../utils/time.js');

const STATUS = { PUBLISHED: '报名中', ONGOING: '进行中', FINISHED: '已结束', CANCELLED: '已取消' };

Page({
    data: {
        viewState: 'loading',
        errorMsg: '',
        id: '',
        activity: null,
        statusText: '',
        startText: '',
        endText: '',
        timeText: '',
        // 报名表单
        showForm: false,
        formMode: 'self',        // self=本人报名, other=替他人报名
        formName: '',
        formPhone: '',
        formError: '',
        submitting: false,
        hasMyPhone: false
    },

    onLoad(options) {
        this.setData({ id: options.id || '' });
        this.loadDetail();
    },
    onPullDownRefresh() { this.loadDetail(() => wx.stopPullDownRefresh()); },

    loadDetail(done) {
        this.setData({ viewState: 'loading', errorMsg: '' });
        const id = this.data.id;
        if (!id) {
            this.setData({ viewState: 'error', errorMsg: '缺少活动参数' });
            if (done) done();
            return;
        }
        api(app).activityDetail(id).then(res => {
            const a = res || {};
            return Promise.all([
                imageLocal.toLocal(a.coverUrl),
                imageLocal.hydrateDescription(a.description)
            ]).then(result => {
                a.coverUrl = result[0] || '';
                a.description = timeUtil.toRichText(result[1]);
                this.setData({
                    activity: a,
                    statusText: STATUS[a.status] || a.status,
                    startText: timeUtil.formatTime(a.startTime),
                    endText: timeUtil.formatTime(a.endTime),
                    timeText: timeUtil.rangeText(a.startTime, a.endTime),
                    viewState: 'ready'
                });
                if (done) done();
            });
        }).catch(err => {
            this.setData({ viewState: 'error', errorMsg: (err && err.message) || '加载失败' });
            if (done) done();
        });
    },

    retry() { this.loadDetail(); },

    /** 点击报名：先确保登录并带出本人资料，打开表单 */
    openSignup() {
        const a = this.data.activity || {};
        if (a.status === 'FINISHED') { wx.showToast({ icon: 'none', title: '活动已结束' }); return; }
        if (a.signedCount != null && a.quota != null && a.signedCount >= a.quota) {
            wx.showToast({ icon: 'none', title: '报名名额已满' });
            return;
        }
        wx.showLoading({ title: '请稍候…' });
        app.ensureLogin().then(() => api(app).profile()).then(profile => {
            wx.hideLoading();
            const p = profile || {};
            this._profile = p;
            const hasPhone = !!(p.phone && /^1\d{10}$/.test(String(p.phone)));
            this.setData({
                showForm: true,
                formMode: 'self',
                formName: p.nickname || '',
                formPhone: hasPhone ? String(p.phone) : '',
                hasMyPhone: hasPhone,
                formError: ''
            });
        }).catch(err => {
            wx.hideLoading();
            wx.showToast({ icon: 'none', title: (err && err.message) || '无法获取报名信息' });
        });
    },

    closeForm() { this.setData({ showForm: false, formError: '' }); },
    noop() {},

    /** 切换本人/替他人报名 */
    switchMode(e) {
        const mode = e.currentTarget.dataset.mode;
        if (mode === this.data.formMode) return;
        const p = this._profile || {};
        if (mode === 'self') {
            const hasPhone = !!(p.phone && /^1\d{10}$/.test(String(p.phone)));
            this.setData({ formMode: 'self', formName: p.nickname || '', formPhone: hasPhone ? String(p.phone) : '', hasMyPhone: hasPhone, formError: '' });
        } else {
            this.setData({ formMode: 'other', formName: '', formPhone: '', formError: '' });
        }
    },

    onNameInput(e) { this.setData({ formName: e.detail.value, formError: '' }); },
    onPhoneInput(e) { this.setData({ formPhone: e.detail.value, formError: '' }); },

    /** 提交报名 */
    submitSignup() {
        if (this.data.submitting) return;
        const name = (this.data.formName || '').trim();
        const phone = (this.data.formPhone || '').trim();
        if (!name) { this.setData({ formError: '请填写报名人姓名' }); return; }
        if (!/^1\d{10}$/.test(phone)) { this.setData({ formError: '请填写正确的 11 位手机号' }); return; }

        this.setData({ submitting: true, formError: '' });
        api(app).activitySignup(this.data.id, { name: name, phone: phone }).then(() => {
            this.setData({ submitting: false, showForm: false });
            const old = this.data.activity || {};
            this.setData({ activity: Object.assign({}, old, { signedCount: (old.signedCount || 0) + 1 }) });
            wx.showModal({
                title: '报名成功',
                content: '报名人：' + name + '\n手机号：' + phone + '\n请按活动时间准时参加，服务时长将计入荣誉档案。',
                showCancel: false
            });
        }).catch(err => {
            this.setData({ submitting: false, formError: (err && err.message) || '报名失败，请重试' });
        });
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
