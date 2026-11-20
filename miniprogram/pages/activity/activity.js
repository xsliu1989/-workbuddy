const app = getApp();
const { api } = require('../../utils/api.js');

const STATUS = { PUBLISHED: '报名中', ONGOING: '进行中', FINISHED: '已结束', CANCELLED: '已取消' };
const SIGN_STATUS = { SIGNED_UP: '已报名', CHECKED_IN: '已签到', CERT_ISSUED: '已发证' };

Page({
    data: {
        viewState: 'loading',
        tab: 'list',
        activities: [],
        signups: [],
        errorMsg: ''
    },

    onLoad() { this.loadList(); },
    onPullDownRefresh() { this.reload(() => wx.stopPullDownRefresh()); },

    reload(done) {
        if (this.data.tab === 'list') this.loadList(done);
        else this.loadMine(done);
    },

    loadList(done) {
        this.setData({ viewState: 'loading', errorMsg: '' });
        api(app).activityList(1, 50).then(res => {
            const records = (res && res.records) || [];
            const activities = records.map(a => Object.assign({}, a, {
                statusText: STATUS[a.status] || a.status,
                startText: this.formatTime(a.startTime),
                endText: this.formatTime(a.endTime)
            }));
            this.setData({ activities: activities, viewState: 'ready' });
            if (done) done();
        }).catch(err => {
            this.setData({ viewState: 'error', errorMsg: (err && err.message) || '加载失败' });
            if (done) done();
        });
    },

    loadMine(done) {
        this.setData({ viewState: 'loading', errorMsg: '' });
        app.ensureLogin().then(() => api(app).mySignups()).then(list => {
            const signups = (list || []).map(s => Object.assign({}, s, {
                statusText: SIGN_STATUS[s.status] || s.status,
                personText: (s.signupName || '报名人') + (s.phone ? ' · ' + s.phone : '')
            }));
            this.setData({ signups: signups, viewState: 'ready' });
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
        if (tab === 'list') this.loadList();
        else this.loadMine();
    },

    retry() { this.reload(); },

    openDetail(e) {
        wx.navigateTo({ url: '/pages/activity-detail/activity-detail?id=' + e.currentTarget.dataset.id });
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
