Page({
    data: {
        url: '',
        error: false
    },

    onLoad(options) {
        let url = options.url || '';
        try { url = decodeURIComponent(url); } catch (e) { /* 保留原值 */ }
        if (!/^https?:\/\//.test(url)) {
            this.setData({ error: true });
            return;
        }
        this.setData({ url: url });
    },

    onLoadError() {
        this.setData({ error: true });
    },

    copyUrl() {
        if (!this.data.url) return;
        wx.setClipboardData({ data: this.data.url });
    }
});
