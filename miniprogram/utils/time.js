/** 时间与文本展示工具：统一活动时间的格式化与纯文本转富文本 */

function formatTime(v) {
    if (!v) return '';
    if (typeof v === 'number') {
        const d = new Date(v);
        const p = n => n < 10 ? '0' + n : '' + n;
        return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' ' + p(d.getHours()) + ':' + p(d.getMinutes());
    }
    return String(v).replace('T', ' ').slice(0, 16);
}

/** 活动时间区间：同一天只显示一次日期，跨天显示完整起止 */
function rangeText(start, end) {
    const s = formatTime(start);
    const e = formatTime(end);
    if (!s && !e) return '待定';
    if (!e) return s + ' 起';
    if (!s) return '至 ' + e;
    if (s.slice(0, 10) === e.slice(0, 10)) return s + ' 至 ' + e.slice(11);
    return s + ' 至 ' + e;
}

/** 纯文本转 rich-text 可渲染的 HTML；已经是 HTML 的原样返回 */
function toRichText(desc) {
    if (!desc) return '';
    const s = String(desc);
    if (/<[a-z][\s\S]*>/i.test(s)) return s;
    return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\n/g, '<br/>');
}

module.exports = { formatTime: formatTime, rangeText: rangeText, toRichText: toRichText };
