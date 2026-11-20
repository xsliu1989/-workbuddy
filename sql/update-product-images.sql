-- =============================================================
-- 绿益公益 - 为「已存在的商品记录」补上样例图片
--
-- 背景：样例图片是在 schema.sql 的 INSERT 种子语句里补的，
--       只对「新建库」生效；你的正式 MySQL 库里商品记录已经存在，
--       所以管理端看到的还是没有图片的旧数据。执行本脚本即可修复。
--
-- 图片文件位置：server/uploads/samples/*.png
--   请确保这些文件在后端「启动目录」下也存在（IDEA 通常以 server 模块为工作目录，
--   即 F:\codex-lvyi\server\uploads\samples\）。若部署在其它目录，需一并复制过去。
--   对外访问路径：/api/uploads/samples/xxx.png
--
-- 使用方式：在 MySQL 客户端（Navicat / IDEA Database / mysql 命令行）
--           选择绿益公益的数据库后，整体执行本文件。
-- =============================================================

-- 0. 执行前可先看一眼现有商品（确认下面 WHERE 里的名称与库中一致）
SELECT id, name, kind, cover_url, images FROM product ORDER BY id;

-- 1. 环保帆布袋
UPDATE product
SET cover_url  = '/uploads/samples/product-canvas-bag.png',
    images     = '/uploads/samples/product-canvas-bag.png',
    description = '<p>可循环使用，公益义卖。</p><p><img src="/uploads/samples/product-canvas-bag.png" style="max-width:100%"></p>'
WHERE name = '环保帆布袋';

-- 2. 公益文化衫
UPDATE product
SET cover_url  = '/uploads/samples/product-tshirt.png',
    images     = '/uploads/samples/product-tshirt.png',
    description = '<p>绿益公益限定款，舒适纯棉。</p><p><img src="/uploads/samples/product-tshirt.png" style="max-width:100%"></p>'
WHERE name = '公益文化衫';

-- 3. 暖冬棉衣(申领)
UPDATE product
SET cover_url  = '/uploads/samples/product-winter-coat.png',
    images     = '/uploads/samples/product-winter-coat.png',
    description = '<p>免费提供给有需要的人，仅付运费。</p><p><img src="/uploads/samples/product-winter-coat.png" style="max-width:100%"></p>'
WHERE name = '暖冬棉衣(申领)';

-- 4. 学习用品包(申领)
UPDATE product
SET cover_url  = '/uploads/samples/product-stationery.png',
    images     = '/uploads/samples/product-stationery.png',
    description = '<p>免费申领，仅付运费。含笔记本、铅笔、橡皮与笔袋。</p><p><img src="/uploads/samples/product-stationery.png" style="max-width:100%"></p>'
WHERE name = '学习用品包(申领)';

-- 5. 验证：四条记录的 cover_url 都应非空
SELECT id, name, cover_url, images, description FROM product ORDER BY id;

-- =============================================================
-- 备注：
-- 1. 如果你的库中商品名称带了不同后缀（例如「环保帆布袋 2」），
--    可把 WHERE 改成  WHERE name LIKE '%帆布袋%'  再执行对应那一条。
-- 2. 如果列 cover_url / images / description 不存在（旧版表结构），
--    先执行以下 DDL 补列（已执行过请忽略）：
-- ALTER TABLE product ADD COLUMN cover_url VARCHAR(500) DEFAULT NULL COMMENT '封面图片';
-- ALTER TABLE product ADD COLUMN images TEXT COMMENT '商品图片列表，逗号分隔';
-- ALTER TABLE product ADD COLUMN description TEXT COMMENT '商品详情（支持 HTML 图文）';
-- 3. 执行后刷新管理后台商品页即可看到缩略图；小程序端重新进入商品详情可见轮播图。
-- =============================================================
