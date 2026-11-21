/**
 * 云托管诊断页：逐个测试候选服务名，找出能取到数据的那个。
 */
const app = getApp();

function callTest(service, env) {
    return new Promise(resolve => {
        if (!wx.cloud || !wx.cloud.callContainer) {
            resolve({ ok: false, detail: '当前基础库不支持 wx.cloud.callContainer，请升级微信/开发者工具' });
            return;
        }
        const start = Date.now();
        wx.cloud.callContainer({
            config: { env: env },
            path: '/api/content/list?current=1&size=5',
            method: 'GET',
            header: { 'content-type': 'application/json', 'X-WX-SERVICE': service },
            data: {},
            success(res) {
                const ms = Date.now() - start;
                let detail = 'status=' + res.statusCode + ' (' + ms + 'ms) ';
                const body = res.data;
                if (res.statusCode === 200 && body && body.code === 0) {
                    const n = (body.data && body.data.records) ? body.data.records.length : 0;
                    resolve({ ok: true, detail: detail + '取到 ' + n + ' 条内容' });
                } else {
                    detail += JSON.stringify(body).slice(0, 160);
                    resolve({ ok: false, detail: detail });
                }
            },
            fail(err) {
                resolve({ ok: false, detail: '调用失败：' + ((err && err.errMsg) || JSON.stringify(err)).slice(0, 160) });
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
                env: cfg.env || '(空)',
                service: cfg.service || '(空)',
                fileBase: cfg.fileBase || '(空)'
            },
            tests: [],
            conclusion: '检测中…'
        });

        if (!app.globalData.useCloud) {
            this.setData({ conclusion: '云模式未开启。请在开发者工具 Console 执行 wx.clearStorageSync() 后重新编译，或在 app.js 确认 useCloud 默认值为 true。' });
            return;
        }

        const env = cfg.env;
        // 候选服务名：当前配置的 + 日志里出现的 greenyi + 公网域名前缀 springboot
        const candidates = [];
        const push = (s, label) => { if (s && candidates.indexOf(s) < 0) candidates.push(s); };
        push(cfg.service, '当前');
        push('greenyi', '日志实例名');
        push('springboot', '公网域名前缀');

        const tests = candidates.map(s => ({ name: s, ok: false, detail: '检测中…' }));
        this.setData({ tests: tests });

        const jobs = candidates.map(s => callTest(s, env).then(r => Object.assign({ name: s }, r)));
        Promise.all(jobs).then(results => {
            this.setData({ tests: results });
            const okServices = results.filter(r => r.ok).map(r => r.name);
            if (okServices.length === 0) {
                this.setData({ conclusion: '所有服务名都取不到数据。常见原因：① 云开发环境 ID 不对；② 服务未部署成功或已停止；③ 小程序未开通云开发/云托管；④ 真机调试基础库版本过低。请把上面结果截图发给技术支持。' });
            } else if (okServices.indexOf(cfg.service) < 0) {
                this.setData({ conclusion: '找到可用服务名：' + okServices.join('、') + '。当前配置的服务名 ' + cfg.service + ' 是错的。请把 app.js 里 CLOUD_DEFAULTS.service 改成 ' + okServices[0] + '，并清除缓存后重新编译。' });
            } else {
                this.setData({ conclusion: '服务名 ' + cfg.service + ' 调用成功、能取到数据。若首页仍无数据，请检查首页代码或数据库里的活动/内容记录是否为空。' });
            }
        });
    }
});
