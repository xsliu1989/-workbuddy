const app = getApp();
const { api } = require('../../utils/api.js');
const imageLocal = require('../../utils/image-local.js');

Page({
    data: {
        viewState: 'loading',
        errorMsg: '',
        id: '',
        mode: 'detail', // detail / list
        contents: [],
        content: null,
        isVideo: false,
        timeText: '',
        shareCount: 0
    },

    onLoad(options) {
        const id = options.id || '';
        this.setData({ id: id, mode: id ? 'detail' : 'list' });
        if (id) this.loadDetail();
        else this.loadList();
    },
    onPullDownRefresh() {
        if (this.data.mode === 'detail') this.loadDetail(() => wx.stopPullDownRefresh());
        else this.loadList(() => wx.stopPullDownRefresh());
    },

    loadList(done) {
        this.setData({ viewState: 'loading', errorMsg: '' });
        api(app).contentList(1, 50).then(res => {
            const all = (res && res.records) || [];
            return imageLocal.hydrateCovers(all.filter(c => c.kind !== 'OA')).then(list => {
                this.setData({ contents: list, viewState: 'ready' });
                if (done) done();
            });
        }).catch(err => {
            this.setData({ viewState: 'error', errorMsg: (err && err.message) || '加载失败' });
            if (done) done();
        });
    },

    loadDetail(done) {
        this.setData({ viewState: 'loading', errorMsg: '' });
        const id = this.data.id;
        if (!id) {
            this.setData({ viewState: 'error', errorMsg: '缺少内容参数' });
            if (done) done();
            return;
        }
        api(app).contentDetail(id).then(res => {
            const c = res || {};
            return imageLocal.toLocal(c.coverUrl).then(path => {
                c.coverUrl = path || '';
                this.setData({
                    content: c,
                    isVideo: c.kind === 'VIDEO',
                    timeText: this.formatTime(c.publishedAt),
                    shareCount: c.shareCount || 0,
                    viewState: 'ready'
                });
                if (done) done();
            });
        }).catch(err => {
            this.setData({ viewState: 'error', errorMsg: (err && err.message) || '加载失败' });
            if (done) done();
        });
    },

    retry() {
        if (this.data.mode === 'detail') this.loadDetail();
        else this.loadList();
    },

    openItem(e) {
        wx.navigateTo({ url: '/pages/article/article?id=' + e.currentTarget.dataset.id });
    },

    share() {
        app.ensureLogin().then(() => api(app).shareContent(this.data.id)).then(() => {
            this.setData({ shareCount: this.data.shareCount + 1 });
            wx.showToast({ icon: 'none', title: '分享成功，感谢传播' });
        }).catch(err => wx.showToast({ icon: 'none', title: (err && err.message) || '分享失败' }));
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
