const app = getApp();
const { api } = require('../../utils/api.js');

const LEVEL_LABELS = ['', '爱心志愿者', '公益之星', '公益大使'];

Page({
    data: {
        viewState: 'loading',
        logged: false,
        profile: null,
        avatarLetter: '绿',
        levelLabel: '',
        entries: [
            { key: 'honor', icon: '誉', label: '荣誉档案', color: '#ec4899', action: 'navigate', path: '/pages/honor/honor' },
            { key: 'points', icon: '分', label: '我的积分', color: '#14b8a6', action: 'navigate', path: '/pages/points/points' },
            { key: 'verify', icon: '验', label: '证书验真', color: '#ef4444', action: 'navigate', path: '/pages/cert-verify/cert-verify' },
            { key: 'address', icon: '址', label: '地址管理', color: '#3b82f6', action: 'navigate', path: '/pages/address/address' },
            { key: 'orders', icon: '单', label: '我的订单', color: '#0ea5e9', action: 'navigate', path: '/pages/orders/orders' },
            { key: 'records', icon: '录', label: '捐赠记录', color: '#2e7d5b', action: 'navigate', path: '/pages/donation-list/donation-list' },
            { key: 'claims', icon: '领', label: '我的申领', color: '#f59e0b', action: 'claims' },
            { key: 'chat', icon: '援', label: 'AI客服', color: '#8b5cf6', action: 'chat' }
        ],

        showClaims: false,
        claimsState: 'loading',
        claims: [],

        showChat: false,
        chatInput: '',
        chatSending: false,
        chatAnchor: '',
        chatSeq: 0,
        messages: []
    },

    onLoad() { this.init(); },
    onShow() {
        // 从其他页面登录后返回时自动刷新
        if (app.globalData.token && !this.data.logged) this.init();
    },
    onPullDownRefresh() { this.init(() => wx.stopPullDownRefresh()); },
    onShareAppMessage() { return { title: '绿益公益 · 让每一份善意都被看见', path: '/pages/index/index' }; },

    init(done) {
        if (!app.globalData.token) {
            this.setData({ viewState: 'ready', logged: false, profile: null });
            if (done) done();
            return;
        }
        this.setData({ viewState: 'loading', logged: true });
        const self = this;
        api(app).profile().then(profile => {
            self.setData({
                profile: profile || {},
                avatarLetter: profile && profile.nickname ? profile.nickname.slice(0, 1) : '绿',
                viewState: 'ready',
                logged: true
            });
            // 同步拉取等级信息用于展示
            api(app).honor().then(h => {
                const lv = (h && h.donationLevel) || 0;
                self.setData({ levelLabel: LEVEL_LABELS[lv] || LEVEL_LABELS[0] });
            }).catch(() => {});
            if (done) done();
        }).catch(err => {
            self.setData({ viewState: 'error', logged: true });
            wx.showToast({ icon: 'none', title: (err && err.message) || '加载失败' });
            if (done) done();
        });
    },

    doLogin() {
        // 跳转到微信授权登录页，选头像/填昵称/手机号后完成登录
        wx.navigateTo({ url: '/pages/login/login' });
    },

    doLogout() {
        const self = this;
        wx.showModal({
            title: '退出登录',
            content: '退出后将无法查看荣誉档案、积分与订单，确定退出吗？',
            confirmColor: '#2e7d5b',
            success(res) {
                if (!res.confirm) return;
                app.logout();
                self.setData({
                    logged: false,
                    profile: null,
                    levelLabel: '',
                    avatarLetter: '绿',
                    viewState: 'ready',
                    showClaims: false,
                    showChat: false
                });
                wx.showToast({ icon: 'success', title: '已退出登录' });
            }
        });
    },

    handleEntry(e) {
        const idx = e.currentTarget.dataset.index;
        const item = this.data.entries[idx];
        if (!item) return;
        if (item.action === 'navigate') {
            wx.navigateTo({
                url: item.path,
                fail: () => wx.showToast({ icon: 'none', title: '页面暂未开放' })
            });
        } else if (item.action === 'claims') {
            this.openClaims();
        } else if (item.action === 'chat') {
            this.openChat();
        }
    },

    openClaims() {
        this.setData({ showClaims: true, claimsState: 'loading', claims: [] });
        const self = this;
        api(app).myClaims().then(res => {
            self.setData({ claims: res || [], claimsState: 'ready' });
        }).catch(() => self.setData({ claimsState: 'error' }));
    },
    closeClaims() { this.setData({ showClaims: false }); },
    retryClaims() { this.openClaims(); },

    openChat() {
        const anchor = 'chat-' + this.data.chatSeq;
        this.setData({
            showChat: true,
            chatAnchor: anchor,
            chatSeq: this.data.chatSeq + 1,
            messages: [{ role: 'ai', anchor: anchor, content: '你好，我是绿益公益小助手，想了解捐赠、积分、申领或证书，都可以问我。', transferHuman: false }]
        });
    },
    closeChat() { this.setData({ showChat: false }); },
    onChatInput(e) { this.setData({ chatInput: e.detail.value }); },
    sendChat() {
        const q = (this.data.chatInput || '').trim();
        if (!q || this.data.chatSending) return;
        const self = this;
        let seq = this.data.chatSeq;
        let anchor = 'chat-' + seq;
        let messages = this.data.messages.concat([{ role: 'me', anchor: anchor, content: q }]);
        seq = seq + 1;
        this.setData({ messages: messages, chatAnchor: anchor, chatSeq: seq, chatInput: '', chatSending: true });

        api(app).aiChat(q).then(res => {
            const aAnchor = 'chat-' + self.data.chatSeq;
            self.setData({
                messages: self.data.messages.concat([{
                    role: 'ai',
                    anchor: aAnchor,
                    content: (res && res.answer) || '暂未获取到回答。',
                    transferHuman: !!(res && res.transferHuman)
                }]),
                chatAnchor: aAnchor,
                chatSeq: self.data.chatSeq + 1,
                chatSending: false
            });
        }).catch(err => {
            const eAnchor = 'chat-' + self.data.chatSeq;
            self.setData({
                messages: self.data.messages.concat([{
                    role: 'ai',
                    anchor: eAnchor,
                    content: (err && err.message) || '服务暂时不可用，请稍后再试。',
                    transferHuman: true
                }]),
                chatAnchor: eAnchor,
                chatSeq: self.data.chatSeq + 1,
                chatSending: false
            });
        });
    },

    goVerify(e) {
        wx.navigateTo({ url: '/pages/cert-verify/cert-verify' });
    }
});
