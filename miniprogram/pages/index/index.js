const app = getApp();
const { api } = require('../../utils/api.js');
const imageLocal = require('../../utils/image-local.js');

const STATUS = { PUBLISHED: '报名中', ONGOING: '进行中', FINISHED: '已结束', CANCELLED: '已取消' };

Page({
    data: {
        viewState: 'loading',
        contents: [],
        oaArticles: [],
        activities: [],
        quickEntries: [
            { key: 'donate', icon: '捐', label: '物资捐赠', color: '#2e7d5b', path: '/pages/donation/donation' },
            { key: 'mall', icon: '购', label: '公益商城', color: '#3b82f6', path: '/pages/mall/mall' },
            { key: 'claim', icon: '领', label: '免费申领', color: '#f59e0b', path: '/pages/claim/claim' },
            { key: 'activity', icon: '志', label: '志愿活动', color: '#8b5cf6', path: '/pages/activity/activity' },
            { key: 'honor', icon: '誉', label: '荣誉档案', color: '#ec4899', path: '/pages/honor/honor' },
            { key: 'points', icon: '分', label: '我的积分', color: '#14b8a6', path: '/pages/points/points' },
            { key: 'records', icon: '录', label: '捐赠记录', color: '#0ea5e9', path: '/pages/donation-list/donation-list' },
            { key: 'verify', icon: '验', label: '证书验真', color: '#ef4444', path: '/pages/cert-verify/cert-verify' }
        ]
    },

    onLoad() { this.loadData(); },
    onPullDownRefresh() { this.loadData(() => wx.stopPullDownRefresh()); },

    loadData(done) {
        this.setData({ viewState: 'loading' });
        let pending = 2;
        const finishOne = () => {
            pending -= 1;
            if (pending === 0) {
                this.setData({ viewState: 'ready' });
                if (done) done();
            }
        };

        // 公益动态 + 公众号精选（kind=OA 的条目从动态中拆出）
        api(app).contentList(1, 50).then(res => {
            const all = (res && res.records) || [];
            const contents = all.filter(c => c.kind !== 'OA');
            const oaArticles = all.filter(c => c.kind === 'OA').slice(0, 6);
            return Promise.all([
                imageLocal.hydrateCovers(contents),
                imageLocal.hydrateCovers(oaArticles)
            ]).then(list => {
                this.setData({ contents: list[0], oaArticles: list[1] });
            });
        }).catch(() => {}).then(finishOne);

        // 公益活动：优先展示报名中/进行中的活动
        api(app).activityList(1, 20).then(res => {
            const all = ((res && res.records) || []).map(a => Object.assign({}, a, {
                statusText: STATUS[a.status] || a.status,
                startText: this.formatTime(a.startTime)
            }));
            const open = all.filter(a => a.status === 'PUBLISHED' || a.status === 'ONGOING');
            return imageLocal.hydrateCovers((open.length ? open : all).slice(0, 3)).then(list => {
                this.setData({ activities: list });
            });
        }).catch(() => {}).then(finishOne);
    },

    goPage(e) {
        const url = e.currentTarget.dataset.path;
        // tabBar 页面必须用 switchTab，否则 navigateTo 会失败
        const tabPages = [
            '/pages/index/index',
            '/pages/donation/donation',
            '/pages/mall/mall',
            '/pages/activity/activity',
            '/pages/mine/mine'
        ];
        if (tabPages.indexOf(url) >= 0) {
            wx.switchTab({ url: url, fail: () => wx.showToast({ icon: 'none', title: '页面暂未开放' }) });
        } else {
            wx.navigateTo({ url: url, fail: () => wx.showToast({ icon: 'none', title: '页面暂未开放' }) });
        }
    },

    openArticle(e) {
        wx.navigateTo({ url: '/pages/article/article?id=' + e.currentTarget.dataset.id });
    },

    openActivity(e) {
        wx.navigateTo({ url: '/pages/activity-detail/activity-detail?id=' + e.currentTarget.dataset.id });
    },

    /**
     * 打开公众号文章：
     * - 基础库支持时优先用 wx.openOfficialAccountArticle（需公众号与小程序已关联）
     * - 否则回退到 web-view 页面承载
     */
    openOaArticle(e) {
        const url = e.currentTarget.dataset.url;
        if (!url) return;
        if (typeof wx.openOfficialAccountArticle === 'function') {
            wx.openOfficialAccountArticle({
                url: url,
                fail: () => this.openWebView(url)
            });
        } else {
            this.openWebView(url);
        }
    },

    openWebView(url) {
        wx.navigateTo({ url: '/pages/webview/webview?url=' + encodeURIComponent(url) });
    },

    openChat() {
        wx.showModal({
            title: '公益小助手',
            content: '请到「我的」页面使用 AI 客服问答，或直接咨询平台志愿者。',
            showCancel: false
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
