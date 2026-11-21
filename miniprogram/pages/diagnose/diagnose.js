/**
 * 云托管诊断页：依次测试三条数据通道能否取到数据。
 * ①云函数代理 apiProxy（首选，免域名）②callContainer ③公网域名兜底。
 * 甲方真机能否看到数据，取决于①是否为「成功」。
 */
const app = getApp();

const TEST_PATH = 'content/list?current=1&size=3';

function pickResult(res) {
    const body = res.data;
    if (res.statusCode === 200 && body && body.code === 0) {
        const n = (body.data && body.data.records) ? body.data.records.length : 0;
        return { ok: true, detail: 'status=200 取到 ' + n + ' 条' };
    }
    return { ok: false, detail: 'status=' + res.statusCode + ' ' + (typeof body === 'string' ? body : JSON.stringify(body)).slice(0, 140) };
}

function functionTest() {
    return new Promise(resolve => {
        if (!wx.cloud || !wx.cloud.callFunction) { resolve({ ok: false, detail: 'wx.cloud 不可用（基础库/未开通云开发）' }); return; }
        const start = Date.now();
        wx.cloud.callFunction({
            name: 'apiProxy',
            data: { path: '/' + TEST_PATH, method: 'GET', data: {}, headers: { 'content-type': 'application/json' } },
            success(res) {
                const ms = Date.now() - start;
                const r = res && res.result;
                if (!r || r.statusCode == null) { resolve({ ok: false, detail: '云函数无有效返回 (' + ms + 'ms)：' + JSON.stringify(res).slice(0, 120) }); return; }
                if (r.statusCode !== 200) { resolve({ ok: false, detail: 'status=' + r.statusCode + ' ' + (r.error || '') + ' (' + ms + 'ms)' }); return; }
                const p = pickResult({ statusCode: 200, data: r.body });
                resolve({ ok: p.ok, detail: p.detail + ' (' + ms + 'ms)' });
            },
            fail(err) {
                const msg = ((err && err.errMsg) || JSON.stringify(err)).slice(0, 180);
                resolve({ ok: false, detail: msg + (msg.indexOf('not found') >= 0 ? ' —— 请先在开发者工具上传 apiProxy 云函数' : '') });
            }
        });
    });
}

function containerTest(cfg) {
    return new Promise(resolve => {
        if (!wx.cloud || !wx.cloud.callContainer) { resolve({ ok: false, detail: '基础库不支持 callContainer' }); return; }
        if (!cfg.env || !cfg.service) { resolve({ ok: false, detail: '环境ID/服务名未配置' }); return; }
        const start = Date.now();
        try {
            wx.cloud.callContainer({
                config: { env: cfg.env },
                path: '/api/' + TEST_PATH,
                method: 'GET',
                data: {},
                header: { 'content-type': 'application/json', 'X-WX-SERVICE': cfg.service },
                success(res) {
                    const ms = Date.now() - start;
                    const r = pickResult(res);
                    resolve({ ok: r.ok, detail: r.detail + ' (' + ms + 'ms)' });
                },
                fail(err) { resolve({ ok: false, detail: ((err && err.errMsg) || JSON.stringify(err)).slice(0, 180) }); }
            });
        } catch (e) { resolve({ ok: false, detail: '调用异常：' + (e && e.message) }); }
    });
}

function httpTest(base) {
    return new Promise(resolve => {
        if (!base) { resolve({ ok: false, detail: '公网域名未配置' }); return; }
        const start = Date.now();
        wx.request({
            url: String(base).replace(/\/$/, '') + '/api/' + TEST_PATH,
            method: 'GET',
            header: { 'content-type': 'application/json' },
            success(res) {
                const ms = Date.now() - start;
                const r = pickResult(res);
                resolve({ ok: r.ok, detail: r.detail + ' (' + ms + 'ms)' });
            },
            fail(err) { resolve({ ok: false, detail: '连接失败：' + ((err && err.errMsg) || JSON.stringify(err)).slice(0, 180) }); }
        });
    });
}

Page({
    data: {
        cfg: {},
        fnTests: [],
        containerTests: [],
        httpTests: [],
        conclusion: '检测中…'
    },

    onLoad() { this.run(); },

    run() {
        const cfg = app.globalData.cloud || {};
        this.setData({
            cfg: {
                useCloud: app.globalData.useCloud ? '开启' : '关闭',
                env: cfg.env || '(空)',
                service: cfg.service || '(空)',
                publicBase: cfg.publicBase || '(空)'
            },
            fnTests: [], containerTests: [], httpTests: [], conclusion: '检测中…'
        });

        if (!app.globalData.useCloud) {
            this.setData({ conclusion: '云模式未开启。请在开发者工具 Console 执行 wx.clearStorageSync() 后重新编译。' });
            return;
        }

        Promise.all([functionTest(), containerTest(cfg), httpTest(cfg.publicBase)]).then(([fn, cc, http]) => {
            this.setData({
                fnTests: [{ name: '云函数代理 apiProxy', ok: fn.ok, detail: fn.detail }],
                containerTests: [{ name: 'callContainer', ok: cc.ok, detail: cc.detail }],
                httpTests: [{ name: '公网域名', ok: http.ok, detail: http.detail }]
            });
            let conclusion;
            if (fn.ok) {
                conclusion = '✅ 云函数代理正常。甲方真机扫码即可看到数据，无需配置域名。若首页仍空白，把本页截图发我。';
            } else if (cc.ok || http.ok) {
                conclusion = '⚠️ 云函数代理失败，但有备用通道可用。建议先把 apiProxy 云函数上传部署（首选方案）；把本页截图发我。';
            } else {
                conclusion = '❌ 三条通道都失败。最常见：apiProxy 云函数还没上传部署。请在开发者工具里右键 cloudfunctions/apiProxy → 上传并部署，再重新检测。';
            }
            this.setData({ conclusion: conclusion });
        });
    }
});
