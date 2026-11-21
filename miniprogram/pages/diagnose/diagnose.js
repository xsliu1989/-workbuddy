/**
 * 云托管诊断页：分别测试 callContainer（内部通道，免域名）与公网域名（兜底通道）能否取到数据。
 * 甲方真机扫码能否看到数据，取决于 callContainer 这一项是否为「成功」。
 */
const app = getApp();

function pickResult(res) {
    const body = res.data;
    if (res.statusCode === 200 && body && body.code === 0) {
        const n = (body.data && body.data.records) ? body.data.records.length : 0;
        return { ok: true, detail: 'status=200 取到 ' + n + ' 条' };
    }
    return { ok: false, detail: 'status=' + res.statusCode + ' ' + (typeof body === 'string' ? body : JSON.stringify(body)).slice(0, 140) };
}

// 测试 callContainer：path 带 /api，header 带 X-WX-SERVICE
function containerTest(cfg, path) {
    return new Promise(resolve => {
        if (!wx.cloud || !wx.cloud.callContainer) { resolve({ ok: false, detail: '基础库不支持 callContainer' }); return; }
        if (!cfg.env || !cfg.service) { resolve({ ok: false, detail: '环境ID/服务名未配置' }); return; }
        const start = Date.now();
        try {
            wx.cloud.callContainer({
                config: { env: cfg.env },
                path: '/api' + path,
                method: 'GET',
                data: {},
                header: { 'content-type': 'application/json', 'X-WX-SERVICE': cfg.service },
                success(res) {
                    const ms = Date.now() - start;
                    const r = pickResult(res);
                    resolve({ ok: r.ok, detail: r.detail + ' (' + ms + 'ms)' });
                },
                fail(err) {
                    resolve({ ok: false, detail: ((err && err.errMsg) || JSON.stringify(err)).slice(0, 180) });
                }
            });
        } catch (e) {
            resolve({ ok: false, detail: '调用异常：' + (e && e.message) });
        }
    });
}

// 测试公网域名兜底
function httpTest(base, path) {
    return new Promise(resolve => {
        if (!base) { resolve({ ok: false, detail: '公网域名未配置' }); return; }
        const start = Date.now();
        wx.request({
            url: String(base).replace(/\/$/, '') + '/api' + path,
            method: 'GET',
            header: { 'content-type': 'application/json' },
            success(res) {
                const ms = Date.now() - start;
                const r = pickResult(res);
                resolve({ ok: r.ok, detail: r.detail + ' (' + ms + 'ms)' });
            },
            fail(err) {
                resolve({ ok: false, detail: '连接失败：' + ((err && err.errMsg) || JSON.stringify(err)).slice(0, 180) });
            }
        });
    });
}

Page({
    data: {
        cfg: {},
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
            containerTests: [],
            httpTests: [],
            conclusion: '检测中…'
        });

        if (!app.globalData.useCloud) {
            this.setData({ conclusion: '云模式未开启。请在开发者工具 Console 执行 wx.clearStorageSync() 后重新编译。' });
            return;
        }

        const path = 'content/list?current=1&size=3';

        // ① 关键：callContainer 内部通道
        containerTest(cfg, path).then(r => {
            this.setData({ containerTests: [{ name: 'callContainer · 内容列表', ok: r.ok, detail: r.detail }] });
        });

        // ② 兜底：公网域名通道
        httpTest(cfg.publicBase, path).then(r => {
            this.setData({ httpTests: [{ name: '公网域名 · 内容列表', ok: r.ok, detail: r.detail }] });
        });

        Promise.all([containerTest(cfg, path), httpTest(cfg.publicBase, path)]).then(([cc, http]) => {
            let conclusion;
            if (cc.ok) {
                conclusion = '✅ 内部通道（callContainer）正常。甲方真机扫码即可看到数据，无需配置域名。若首页仍空白，把本页截图发我。';
            } else if (http.ok) {
                conclusion = '⚠️ 内部通道失败，但公网域名可用。甲方真机需先配好 request 合法域名才能看到数据；开发者工具可先勾「不校验合法域名」验证。把本页截图发我。';
            } else {
                conclusion = '❌ 两条通道都失败。请把本页截图发我，我按报错定位（常见：云开发环境与云托管服务未关联、服务已停止、域名未配合法域名）。';
            }
            this.setData({ conclusion: conclusion });
        });
    }
});
