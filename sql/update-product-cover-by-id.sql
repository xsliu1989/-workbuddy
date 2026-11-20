-- =============================================================
-- 绿益公益 - 按商品 id 精确补齐封面/图片（避开商品名不匹配问题）
--
-- 用途：如果你的库里商品名称与标准名不一致（带编号/后缀/改过名），
--       上一条按 name 更新的脚本可能没匹配到，导致 cover_url 为空、
--       小程序商品列表封面不显示（但详情描述里的图能显示）。
--
-- 用法两步：
--   第 1 步：执行下面的 SELECT，记下 4 个商品的实际 id
--   第 2 步：把下面 UPDATE 里的 id 改成你记下的实际值，再执行
-- =============================================================

-- 第 1 步：查看商品 id 与当前封面状态
SELECT id, name, cover_url,
       CASE WHEN cover_url IS NULL OR cover_url = '' THEN '★封面为空' ELSE '有封面' END AS cover_state
FROM product
ORDER BY id;

-- 第 2 步：按实际 id 更新（下面 id=1..4 请按第 1 步结果替换）
-- id=1 环保帆布袋
UPDATE product
SET cover_url  = '/uploads/samples/product-canvas-bag.png',
    images     = '/uploads/samples/product-canvas-bag.png',
    description = '<p>可循环使用，公益义卖。</p><p><img src="/uploads/samples/product-canvas-bag.png" style="max-width:100%"></p>'
WHERE id = 1;

-- id=2 公益文化衫
UPDATE product
SET cover_url  = '/uploads/samples/product-tshirt.png',
    images     = '/uploads/samples/product-tshirt.png',
    description = '<p>绿益公益限定款，舒适纯棉。</p><p><img src="/uploads/samples/product-tshirt.png" style="max-width:100%"></p>'
WHERE id = 2;

-- id=3 暖冬棉衣(申领)
UPDATE product
SET cover_url  = '/uploads/samples/product-winter-coat.png',
    images     = '/uploads/samples/product-winter-coat.png',
    description = '<p>免费提供给有需要的人，仅付运费。</p><p><img src="/uploads/samples/product-winter-coat.png" style="max-width:100%"></p>'
WHERE id = 3;

-- id=4 学习用品包(申领)
UPDATE product
SET cover_url  = '/uploads/samples/product-stationery.png',
    images     = '/uploads/samples/product-stationery.png',
    description = '<p>免费申领，仅付运费。含笔记本、铅笔、橡皮与笔袋。</p><p><img src="/uploads/samples/product-stationery.png" style="max-width:100%"></p>'
WHERE id = 4;

-- 验证
SELECT id, name, cover_url FROM product ORDER BY id;
