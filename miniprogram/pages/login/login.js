const app = getApp();
const { api } = require('../../utils/api.js');

Page({
    data: {
        avatarUrl: '',
        nickname: '',
        phone: '',
        phoneReadonly: false,
        logging: false,
        resolvingPhone: false,
        phoneTip: '',
        checking: true,
        returning: false,
        loginBtnText: '微信一键登录'
    },

    onLoad() {
        const self = this;
        // 先从本地带出一次，避免等待网络时出现空白表单
        const local = app.globalData.userInfo || wx.getStorageSync('userInfo');
        if (local) {
            this.setData({
                avatarUrl: local.avatarUrl || '',
                nickname: local.nickname || '',
                phone: local.phone || ''
            });
        }
        // 静默登录：用持久化的 clientId 从后端回读已有资料，老用户无需重复填手机号/昵称
        app.ensureLogin({}).then(data => {
            const p = data || {};
            const hasPhone = !!p.phone;
            // 后端为新用户生成的占位昵称不作为正式昵称预填，避免误用随机名称
            const autoNick = /^绿益用户/.test(p.nickname || '');
            const nickname = p.nickname && !autoNick ? p.nickname : (hasPhone ? p.nickname : '');
            self.setData({
                checking: false,
                avatarUrl: p.avatarUrl || self.data.avatarUrl,
                nickname: nickname || self.data.nickname,
                phone: p.phone || self.data.phone,
                returning: hasPhone,
                loginBtnText: hasPhone ? '直接进入' : '微信一键登录',
                phoneTip: hasPhone ? '已自动匹配上次的手机号，可直接登录' : ''
            });
        }).catch(() => {
            // 静默登录失败不阻断：用户仍可手动填写后走正常登录流程
            self.setData({ checking: false });
        });
    },

    // 微信头像选择：button open-type="chooseAvatar"
    onChooseAvatar(e) {
        const url = e.detail.avatarUrl;
        if (url) this.setData({ avatarUrl: url });
    },

    // 微信昵称输入框：type="nickname"，失焦时回填
    onNicknameInput(e) {
        this.setData({ nickname: e.detail.value });
    },

    onPhoneInput(e) {
        this.setData({ phone: e.detail.value, phoneTip: '' });
    },

    /**
     * 微信手机号快速验证：button open-type="getPhoneNumber"
     * 仅「非个人主体 + 已认证」小程序可用；未配置或失败时返回 code，交由后端换取，
     * 若后端也无法获取（个人主体/演示模式），引导用户手动填写。
     */
    onGetPhoneNumber(e) {
        const detail = e.detail || {};
        if (!detail.code) {
            this.setData({ phoneTip: '未授权或当前微信版本不支持，请手动填写手机号' });
            return;
        }
        const self = this;
        this.setData({ resolvingPhone: true, phoneTip: '' });
        api(app).resolvePhone(detail.code).then(res => {
            self.setData({ resolvingPhone: false });
            if (res && res.ok && res.phone) {
                self.setData({ phone: res.phone, phoneReadonly: true, phoneTip: '已获取微信绑定手机号' });
            } else {
                self.setData({
                    phoneReadonly: false,
                    phoneTip: '当前小程序未开通手机号快速验证（需非个人主体并完成微信认证），请手动填写'
                });
            }
        }).catch(() => {
            self.setData({
                resolvingPhone: false,
                phoneReadonly: false,
                phoneTip: '自动获取失败，请手动填写手机号'
            });
        });
    },

    // 允许手动修改（先授权失败后用户自己填）
    enableManualPhone() {
        this.setData({ phoneReadonly: false });
    },

    validPhone(p) {
        return /^1\d{10}$/.test(p || '');
    },

    doLogin() {
        if (this.data.logging || this.data.checking) return;
        const nickname = (this.data.nickname || '').trim();
        const phone = (this.data.phone || '').trim();
        if (!nickname) {
            wx.showToast({ icon: 'none', title: '请填写姓名或昵称' });
            return;
        }
        if (!this.validPhone(phone)) {
            wx.showToast({ icon: 'none', title: '请填写正确的 11 位手机号' });
            return;
        }

        const self = this;
        const profile = { nickname: nickname, avatarUrl: this.data.avatarUrl, phone: phone };
        this.setData({ logging: true });
        wx.showLoading({ title: '登录中…' });

        // 静默登录已产生 token：补全资料；否则走 wx.login 登录
        let task;
        if (app.globalData.token) {
            task = api(app).updateProfile(profile).then(() => {
                const merged = Object.assign({}, app.globalData.userInfo || {}, profile);
                app.globalData.userInfo = merged;
                wx.setStorageSync('userInfo', merged);
                return app.globalData.token;
            });
        } else {
            task = app.ensureLogin(profile);
        }

        task.then(() => {
            // 头像为 chooseAvatar 临时路径会过期：登录后上传到服务器并写回资料
            const avatar = self.data.avatarUrl;
            const isTemp = avatar && (avatar.indexOf('http://tmp') === 0 || avatar.indexOf('wxfile://') === 0 || avatar.indexOf('http://store') === 0);
            if (!isTemp) return Promise.resolve();
            return api(app).uploadImage(avatar).then(url => api(app).updateProfile({ avatarUrl: url })).then(() => {
                const merged = Object.assign({}, app.globalData.userInfo || {}, { avatarUrl: url });
                app.globalData.userInfo = merged;
                wx.setStorageSync('userInfo', merged);
                self.setData({ avatarUrl: url });
            }).catch(() => {}); // 头像上传失败不影响登录主流程
        }).then(() => {
            wx.hideLoading();
            self.setData({ logging: false });
            wx.showToast({ title: self.data.returning ? '欢迎回来' : '登录成功', icon: 'success' });
            setTimeout(() => {
                const pages = getCurrentPages();
                if (pages.length > 1) wx.navigateBack();
                else wx.switchTab({ url: '/pages/mine/mine' });
            }, 600);
        }).catch(err => {
            wx.hideLoading();
            self.setData({ logging: false });
            wx.showToast({ icon: 'none', title: (err && err.message) || '登录失败' });
        });
    }
});
