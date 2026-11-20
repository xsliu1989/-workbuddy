/**
 * 商品展示工具：统一计算商品封面，避免 cover_url 为空时列表/购物车空白。
 * 兜底顺序：coverUrl -> images 第一张 -> description 里第一个 <img src>。
 * 入参的数据已经过 request.js 的 normalizeImageUrls，图片地址均为绝对地址。
 */
function splitImages(images) {
    if (!images) return [];
    if (Array.isArray(images)) return images.filter(Boolean);
    return String(images).split(/[,，\n]/).map(function (s) { return s.trim(); }).filter(Boolean);
}

function coverOf(p) {
    if (!p) return '';
    if (p.coverUrl) return p.coverUrl;
    const imgs = splitImages(p.images);
    if (imgs.length) return imgs[0];
    // 详情富文本里的第一张图
    const desc = p.description || p.desc || '';
    const m = String(desc).match(/<img[^>]*\bsrc\s*=\s*["']([^"']+)["']/i);
    return m ? m[1] : '';
}

function imagesOf(p) {
    const imgs = splitImages(p && p.images);
    if (imgs.length) return imgs;
    const cover = coverOf(p);
    return cover ? [cover] : [];
}

module.exports = { coverOf: coverOf, imagesOf: imagesOf, splitImages: splitImages };
