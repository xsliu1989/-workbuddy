const app = getApp();
const { api } = require('../../utils/api.js');

const EMPTY_FORM = {
    receiver: '',
    phone: '',
    province: '',
    city: '',
    district: '',
    region: [],
    detail: '',
    isDefault: false
};

Page({
    data: {
        viewState: 'loading',
        addresses: [],
        showForm: false,
        formMode: 'add',
        formId: null,
        form: EMPTY_FORM,
        saving: false
    },

    onLoad() { this.loadData(); },
    onPullDownRefresh() { this.loadData(() => wx.stopPullDownRefresh()); },
    onShareAppMessage() { return { title: '绿益公益 · 地址管理', path: '/pages/address/address' }; },

    loadData(done) {
        this.setData({ viewState: 'loading' });
        const self = this;
        app.ensureLogin().then(() => api(app).addresses()).then(res => {
            self.setData({ addresses: res || [], viewState: 'ready' });
            if (done) done();
        }).catch(err => {
            self.setData({ viewState: 'error' });
            wx.showToast({ icon: 'none', title: (err && err.message) || '加载失败' });
            if (done) done();
        });
    },

    openAdd() {
        this.setData({
            showForm: true,
            formMode: 'add',
            formId: null,
            form: Object.assign({}, EMPTY_FORM, { region: [] })
        });
    },

    openEdit(e) {
        const idx = e.currentTarget.dataset.index;
        const a = this.data.addresses[idx];
        if (!a) return;
        this.setData({
            showForm: true,
            formMode: 'edit',
            formId: a.id,
            form: {
                receiver: a.receiver || '',
                phone: a.phone || '',
                province: a.province || '',
                city: a.city || '',
                district: a.district || '',
                region: [a.province || '', a.city || '', a.district || ''],
                detail: a.detail || '',
                isDefault: a.isDefault === 1
            }
        });
    },

    closeForm() { this.setData({ showForm: false }); },
    stopPropagation() {},

    onField(e) {
        const key = e.currentTarget.dataset.key;
        const patch = {};
        patch['form.' + key] = e.detail.value;
        this.setData(patch);
    },

    /** 省市区三级联动下拉选择：回调值为 [省, 市, 区] */
    onRegionChange(e) {
        const v = e.detail.value || [];
        this.setData({
            'form.province': v[0] || '',
            'form.city': v[1] || '',
            'form.district': v[2] || '',
            'form.region': v
        });
    },

    onDefaultChange(e) {
        this.setData({ 'form.isDefault': e.detail.value });
    },

    save() {
        const f = this.data.form;
        if (!f.receiver || !f.phone || !f.detail) {
            wx.showToast({ icon: 'none', title: '请填写收货人、电话和详细地址' });
            return;
        }
        if (!f.province || !f.city || !f.district) {
            wx.showToast({ icon: 'none', title: '请选择省 / 市 / 区' });
            return;
        }
        const payload = {
            receiver: f.receiver,
            phone: f.phone,
            province: f.province,
            city: f.city,
            district: f.district,
            detail: f.detail,
            isDefault: f.isDefault ? 1 : 0
        };
        if (this.data.formMode === 'edit') payload.id = this.data.formId;

        this.setData({ saving: true });
        const self = this;
        api(app).saveAddress(payload).then(() => {
            wx.showToast({ title: '保存成功' });
            self.setData({ showForm: false, saving: false });
            self.loadData();
        }).catch(err => {
            self.setData({ saving: false });
            wx.showToast({ icon: 'none', title: (err && err.message) || '保存失败' });
        });
    },

    remove(e) {
        const id = e.currentTarget.dataset.id;
        const self = this;
        wx.showModal({
            title: '删除地址',
            content: '确定要删除该收货地址吗？',
            success(res) {
                if (!res.confirm) return;
                api(app).deleteAddress(id).then(() => {
                    wx.showToast({ title: '已删除' });
                    self.loadData();
                }).catch(err => {
                    wx.showToast({ icon: 'none', title: (err && err.message) || '删除失败' });
                });
            }
        });
    }
});
