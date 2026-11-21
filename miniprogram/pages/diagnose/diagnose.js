/**
 * 云托管诊断页：测试云端公网域名是否连通、能否取到数据。
 */
const app = getApp();

function httpTest(base, path) {
    return new Promise(resolve => {
        const start = Date.now();
        wx.request({
            url: String(base).replace(/\/$/, '') + path,
            method: 'GET',
            header: { 'content-type': 'application/json' },
            success(res) {
                const ms = Date.now() - start;
                let detail = 'status=' + res.statusCode + ' (' + ms + 'ms) ';
                const body = res.data;
                if (res.statusCode === 200 && body && body.code === 0) {
                    const n = (body.data && body.data.records) ? body.data.records.length : 0;
                    resolve({ ok: true, detail: detail + '取到 ' + n + ' 条内容' });
                } else {
                    detail += (typeof body === 'string' ? body : JSON.stringify(body)).slice(0, 160);
                    resolve({ ok: false, detail: detail });
                }
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
        tests: [],
        conclusion: '检测中…'
    },

    onLoad() { this.run(); },

    run() {
        const cfg = app.globalData.cloud || {};
        this.setData({
            cfg: {
                useCloud: app.globalData.useCloud ? '开启' : '关闭',
                publicBase: cfg.publicBase || '(空)',
                fileBase: cfg.fileBase || '(空)'
            },
            tests: [],
            conclusion: '检测中…'
        });

        if (!app.globalData.useCloud) {
            this.setData({ conclusion: '云模式未开启。请在开发者工具 Console 执行 wx.clearStorageSync() 后重新编译。' });
            return;
        }

        const base = cfg.publicBase;
        if (!base) {
            this.setData({ conclusion: '云端公网域名未配置。请检查 app.js 的 CLOUD_DEFAULTS.publicBase。' });
            return;
        }

        // 依次测试公开接口：内容列表、活动列表
        const cases = [
            { name: '内容列表 /content/list', path: '/api/content/list?current=1&size=5' },
            { name: '活动列表 /activity/list', path: '/api/activity/list?current=1&size=5' }
        ];
        const tests = cases.map(c => ({ name: c.name, ok: false, detail: '检测中…' }));
        this.setData({ tests: tests });

        const jobs = cases.map(c => httpTest(base, c.path).then(r => Object.assign({ name: c.name }, r)));
        Promise.all(jobs).then(results => {
            this.setData({ tests: results });
            const okCount = results.filter(r => r.ok).length;
            if (okCount === results.length) {
                this.setData({ conclusion: '云端公网域名连通正常，能取到数据。若首页仍空白，请回到首页看是否还有红色错误横幅，并把截图发来。' });
            } else if (okCount > 0) {
                this.setData({ conclusion: '部分接口通、部分不通。请把上面结果截图发来，我帮你查对应接口。' });
            } else {
                this.setData({ conclusion: '全部失败。常见原因：① 小程序后台未把公网域名加入 request 合法域名（真机强制要求）；② 云端服务未启动或已停止；③ 公网域名填错。开发者工具里可先勾选「不校验合法域名」验证。' });
            }
        });
    }
});
