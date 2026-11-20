-- ============================================================
-- 捐赠邮寄地址功能升级脚本（可在现有正式库重复执行，幂等）
-- 目标库：greenyi_charity
-- 作用：
--   1. 新建 donation_address（管理端「捐赠设置」维护、小程序快递邮寄时展示）
--   2. donation 表补 delivery_address_id 列（记录用户选择的邮寄地址）
--   3. 插入两条示例地址（仅当不存在同名收件人时插入，可重复执行）
-- 说明：脚本用 UTF-8 编码执行；示例地址请在管理端改成真实收件信息。
-- ============================================================

-- ---------- 1. 邮寄地址表 ----------
CREATE TABLE IF NOT EXISTS donation_address (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  receiver    VARCHAR(64)  NOT NULL COMMENT '收件人',
  phone       VARCHAR(32)  NOT NULL COMMENT '联系电话',
  province    VARCHAR(32)  DEFAULT NULL,
  city        VARCHAR(32)  DEFAULT NULL,
  district    VARCHAR(32)  DEFAULT NULL,
  detail      VARCHAR(256) NOT NULL COMMENT '详细地址',
  postal_code VARCHAR(16)  DEFAULT NULL COMMENT '邮政编码',
  remark      VARCHAR(256) DEFAULT NULL COMMENT '说明，如仅接收衣物类',
  enabled     TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  sort_no     INT          NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
  created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_enabled_sort (enabled, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='捐赠物资邮寄地址';

-- ---------- 2. donation 表补字段（MySQL 不支持 ADD COLUMN IF NOT EXISTS，用预处理判断） ----------
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'donation'
    AND COLUMN_NAME = 'delivery_address_id'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE donation ADD COLUMN delivery_address_id BIGINT DEFAULT NULL COMMENT ''用户选择的邮寄地址ID''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 3. 示例地址（幂等：同名收件人已存在则跳过） ----------
INSERT INTO donation_address
  (receiver, phone, province, city, district, detail, postal_code, remark, enabled, sort_no)
SELECT '绿益公益接收站', '0571-88886666', '浙江省', '杭州市', '西湖区',
       '文三路 100 号公益大厦 1 楼捐赠接收处', '310012',
       '工作日 9:00-17:00 接收，寄出后请填写捐赠单号', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM donation_address WHERE receiver = '绿益公益接收站');

INSERT INTO donation_address
  (receiver, phone, province, city, district, detail, postal_code, remark, enabled, sort_no)
SELECT '绿益公益仓储中心', '0571-88887777', '浙江省', '杭州市', '余杭区',
       '仓前街道公益物流园 B 区 3 号库', '311100',
       '仅接收衣物被褥、图书文具，大件请先联系', 1, 2
WHERE NOT EXISTS (SELECT 1 FROM donation_address WHERE receiver = '绿益公益仓储中心');

-- ---------- 4. 验收查询 ----------
SELECT id, receiver, phone, detail, enabled, sort_no FROM donation_address ORDER BY sort_no, id;
SHOW COLUMNS FROM donation LIKE 'delivery_address_id';
