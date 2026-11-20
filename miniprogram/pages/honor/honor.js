const app = getApp();
const { api } = require('../../utils/api.js');

const LEVEL_LABELS = ['暂未达标', '爱心志愿者', '公益之星', '公益大使'];
const LEVEL_MARKS = ['○', '一', '二', '三'];

Page({
    data: {
        viewState: 'loading',
        info: null,
        levelLabel: '',
        levelMark: '○',
        progressText: '',
        honorCerts: [],
        officialCerts: []
    },

    onLoad() { this.loadData(); },
    onPullDownRefresh() { this.loadData(() => wx.stopPullDownRefresh()); },
    onShareAppMessage() { return { title: '我的绿益公益荣誉档案', path: '/pages/honor/honor' }; },

    loadData(done) {
        this.setData({ viewState: 'loading' });
        const self = this;
        app.ensureLogin().then(() => api(app).honor()).then(res => {
            const data = res || {};
            const lv = data.donationLevel || 0;
            const total = Number(data.totalAssessValue || 0);
            const next = Number(data.levelNextThreshold || 0);
            const diff = next - total;
            self.setData({
                info: data,
                levelLabel: LEVEL_LABELS[lv] || LEVEL_LABELS[0],
                levelMark: LEVEL_MARKS[lv] || LEVEL_MARKS[0],
                progressText: lv >= 3 ? '已达最高荣誉等级' : (diff > 0 ? ('距下一等级还差 ' + diff + ' 评估值') : '继续参与公益，积累荣誉'),
                honorCerts: data.honorCerts || [],
                officialCerts: data.officialCerts || [],
                viewState: 'ready'
            });
            if (done) done();
        }).catch(err => {
            self.setData({ viewState: 'error' });
            wx.showToast({ icon: 'none', title: (err && err.message) || '加载失败' });
            if (done) done();
        });
    },

    goVerify(e) {
        const no = e.currentTarget.dataset.no || '';
        wx.navigateTo({ url: '/pages/cert-verify/cert-verify?certNo=' + no });
    }
});
