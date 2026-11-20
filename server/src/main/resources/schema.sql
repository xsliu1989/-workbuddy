-- ============================================================
-- 绿益公益 小程序 - MySQL 数据库 Schema (V1 全量)
-- 依据：绿益公益-产品需求文档-PRD.md §8.2 数据定义
-- 说明：示例数值（等级阈值/积分比例等）为草案，集中在 sys_config 表，可后台配置
-- 字符集：utf8mb4 / utf8mb4_unicode_ci
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------- 1. 基础配置 ----------
DROP TABLE IF EXISTS sys_config;
CREATE TABLE sys_config (
  config_key   VARCHAR(64)  NOT NULL COMMENT '配置键',
  config_value VARCHAR(256) NOT NULL COMMENT '配置值',
  description  VARCHAR(256) DEFAULT NULL COMMENT '说明',
  updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置(证书阈值/积分比例/限购等草案值)';

-- ---------- 2. 用户与权限 ----------
DROP TABLE IF EXISTS app_user;
CREATE TABLE app_user (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  openid        VARCHAR(64)  NOT NULL COMMENT '微信openid',
  unionid       VARCHAR(64)  DEFAULT NULL,
  nickname      VARCHAR(64)  DEFAULT NULL,
  avatar_url    VARCHAR(512) DEFAULT NULL,
  phone         VARCHAR(20)  DEFAULT NULL,
  guardian_auth TINYINT      NOT NULL DEFAULT 0 COMMENT '监护人授权标记:0未授权/1已授权',
  is_minor      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否未成年人',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常/0禁用',
  created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_openid (openid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='C端用户';

DROP TABLE IF EXISTS admin_user;
CREATE TABLE admin_user (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  username     VARCHAR(64) NOT NULL,
  password     VARCHAR(128) NOT NULL COMMENT 'BCrypt',
  real_name    VARCHAR(64) DEFAULT NULL,
  role_code    VARCHAR(32) NOT NULL COMMENT 'EDITOR/ASSESSOR/OPERATOR/ADMIN',
  status       TINYINT     NOT NULL DEFAULT 1,
  last_login_at DATETIME   DEFAULT NULL,
  created_at   DATETIME    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台账号';

DROP TABLE IF EXISTS user_address;
CREATE TABLE user_address (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  user_id     BIGINT      NOT NULL,
  receiver    VARCHAR(64) NOT NULL,
  phone       VARCHAR(20) NOT NULL,
  province    VARCHAR(32) DEFAULT NULL,
  city        VARCHAR(32) DEFAULT NULL,
  district    VARCHAR(32) DEFAULT NULL,
  detail      VARCHAR(256) NOT NULL,
  is_default  TINYINT     NOT NULL DEFAULT 0,
  created_at  DATETIME    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货地址';

-- ---------- 3. 物资捐赠域 ----------
DROP TABLE IF EXISTS donation;
CREATE TABLE donation (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  donation_no   VARCHAR(32)  NOT NULL COMMENT '捐赠单号',
  user_id       BIGINT       NOT NULL,
  category      VARCHAR(32)  NOT NULL COMMENT '物种类别',
  quantity      INT          NOT NULL DEFAULT 1,
  description   VARCHAR(512) DEFAULT NULL,
  photos        TEXT         DEFAULT NULL COMMENT '照片URL,JSON数组',
  delivery_type VARCHAR(16)  NOT NULL DEFAULT 'OFFLINE' COMMENT 'OFFLINE线下/PICKUP上门/MAIL邮寄',
  delivery_address_id BIGINT DEFAULT NULL COMMENT '用户选择的邮寄地址ID(donation_address.id)',
  status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING_RECEIVE' COMMENT 'PENDING_RECEIVE/RECEIVED/ASSESSED/BOOKED/REVIEWING/CLOSED',
  created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_donation_no (donation_no),
  KEY idx_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物资捐赠单';

-- 捐赠物资邮寄接收地址：管理端维护，小程序选择「快递邮寄」时展示
DROP TABLE IF EXISTS donation_address;
CREATE TABLE donation_address (
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

DROP TABLE IF EXISTS assessment;
CREATE TABLE assessment (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  donation_id  BIGINT      NOT NULL,
  assessor_id  BIGINT      NOT NULL COMMENT '评估员admin id',
  value_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '公益估值(评估值)',
  note         VARCHAR(512) DEFAULT NULL,
  status       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/REPLACED/REVIEWING',
  is_review    TINYINT     NOT NULL DEFAULT 0 COMMENT '是否复核结果',
  review_of    BIGINT      DEFAULT NULL COMMENT '复核的原评估id',
  created_at   DATETIME    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_donation (donation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物资评估记录(保留历史)';

DROP TABLE IF EXISTS disposal_ledger;
CREATE TABLE disposal_ledger (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  donation_id BIGINT      NOT NULL,
  method      VARCHAR(32) NOT NULL COMMENT '入库/转赠/回收等去向',
  note        VARCHAR(256) DEFAULT NULL,
  operator_id BIGINT      DEFAULT NULL,
  created_at  DATETIME    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_donation (donation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物资处置台账';

-- ---------- 4. 商品与订单域 ----------
DROP TABLE IF EXISTS product;
CREATE TABLE product (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  name        VARCHAR(128) NOT NULL,
  kind        VARCHAR(16) NOT NULL DEFAULT 'SALE' COMMENT 'SALE公益商品/CLAIM免费申领品',
  price       DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '售价(CLAIM为0)',
  weight_gram INT         NOT NULL DEFAULT 0 COMMENT '重量(克)用于运费试算',
  stock       INT         NOT NULL DEFAULT 0,
  cover_url   VARCHAR(512) DEFAULT NULL,
  images      TEXT        DEFAULT NULL,
  description TEXT        DEFAULT NULL COMMENT '商品详情描述，支持HTML(文字+图片)',
  after_sale_desc TEXT    DEFAULT NULL COMMENT '售后描述',
  status      TINYINT     NOT NULL DEFAULT 1 COMMENT '1上架/0下架',
  created_at  DATETIME    DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_kind_status (kind, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品/申领品';

DROP TABLE IF EXISTS cart;
CREATE TABLE cart (
  id         BIGINT   NOT NULL AUTO_INCREMENT,
  user_id    BIGINT   NOT NULL,
  product_id BIGINT   NOT NULL,
  quantity   INT      NOT NULL DEFAULT 1,
  selected   TINYINT  NOT NULL DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_product (user_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车';

DROP TABLE IF EXISTS orders;
CREATE TABLE orders (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  order_no      VARCHAR(32)  NOT NULL,
  user_id       BIGINT       NOT NULL,
  address_id    BIGINT       DEFAULT NULL,
  goods_amount  DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '货款',
  freight_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '运费',
  points_used   INT          NOT NULL DEFAULT 0 COMMENT '使用积分',
  discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '积分抵扣金额',
  pay_amount    DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '实付',
  pay_status    VARCHAR(16)  NOT NULL DEFAULT 'UNPAID' COMMENT 'UNPAID/PAID/REFUNDED',
  status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING_PAY' COMMENT 'PENDING_PAY/PAID/SHIPPED/COMPLETED/REFUNDED/CLOSED',
  transaction_id VARCHAR(64) DEFAULT NULL COMMENT '微信支付交易号',
  logistics_no  VARCHAR(64)  DEFAULT NULL,
  created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  paid_at       DATETIME     DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品订单';

DROP TABLE IF EXISTS order_item;
CREATE TABLE order_item (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  order_id   BIGINT       NOT NULL,
  product_id BIGINT       NOT NULL,
  name       VARCHAR(128) NOT NULL,
  price      DECIMAL(10,2) NOT NULL,
  quantity   INT          NOT NULL,
  PRIMARY KEY (id),
  KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细';

DROP TABLE IF EXISTS claim_order;
CREATE TABLE claim_order (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  claim_no      VARCHAR(32)  NOT NULL,
  user_id       BIGINT       NOT NULL,
  product_id    BIGINT       NOT NULL,
  address_id    BIGINT       NOT NULL,
  region        VARCHAR(64)  DEFAULT NULL COMMENT '试算地区',
  weight_gram   INT          NOT NULL DEFAULT 0,
  freight_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  pay_status    VARCHAR(16)  NOT NULL DEFAULT 'UNPAID',
  status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING_PAY' COMMENT 'PENDING_PAY/PAID/SHIPPED/COMPLETED/CLOSED',
  transaction_id VARCHAR(64) DEFAULT NULL,
  logistics_no  VARCHAR(64)  DEFAULT NULL,
  created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  paid_at       DATETIME     DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_claim_no (claim_no),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='免费申领单(0元商品+运费)';

DROP TABLE IF EXISTS freight_template;
CREATE TABLE freight_template (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  region      VARCHAR(64) NOT NULL COMMENT '地区,如 华东/全国',
  base_fee    DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '首重费',
  per_kg_fee  DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '续重每公斤',
  status      TINYINT     NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  UNIQUE KEY uk_region (region)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运费模板';

-- ---------- 5. 积分域 ----------
DROP TABLE IF EXISTS point_account;
CREATE TABLE point_account (
  id           BIGINT  NOT NULL AUTO_INCREMENT,
  user_id      BIGINT  NOT NULL,
  balance      INT     NOT NULL DEFAULT 0 COMMENT '可用积分余额',
  total_earned INT     NOT NULL DEFAULT 0,
  total_used   INT     NOT NULL DEFAULT 0,
  updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分账户';

DROP TABLE IF EXISTS point_ledger;
CREATE TABLE point_ledger (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  user_id     BIGINT      NOT NULL,
  change_type VARCHAR(16) NOT NULL COMMENT 'EARN评估获得/USE抵扣核销/REFUND退款回退/EXPIRE过期',
  amount      INT         NOT NULL COMMENT '正数为增加,负数为减少',
  balance_after INT       NOT NULL,
  source      VARCHAR(32) DEFAULT NULL COMMENT '来源:ASSESSMENT/ORDER',
  ref_no      VARCHAR(64) DEFAULT NULL COMMENT '关联单号',
  remark      VARCHAR(256) DEFAULT NULL,
  created_at  DATETIME    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水';

-- ---------- 6. 证书与荣誉域 ----------
DROP TABLE IF EXISTS honor_profile;
CREATE TABLE honor_profile (
  id                  BIGINT  NOT NULL AUTO_INCREMENT,
  user_id             BIGINT  NOT NULL,
  total_assess_value  DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '累计评估值',
  volunteer_hours     INT     NOT NULL DEFAULT 0 COMMENT '志愿时长(分钟)',
  donation_level      INT     NOT NULL DEFAULT 0 COMMENT '捐赠等级 0未达标/1/2/3',
  updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一荣誉档案';

DROP TABLE IF EXISTS certificate;
CREATE TABLE certificate (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  cert_no      VARCHAR(64) NOT NULL COMMENT '唯一证书编号',
  verify_code  VARCHAR(32) NOT NULL COMMENT '验真码',
  user_id      BIGINT      NOT NULL,
  cert_type    VARCHAR(24) NOT NULL COMMENT 'DONATION_LEVEL捐赠等级/ACTIVITY_VOLUNTEER活动志愿者',
  category     VARCHAR(16) NOT NULL COMMENT 'HONOR荣誉激励类/OFFICIAL官方资质类',
  title        VARCHAR(128) NOT NULL,
  level        INT         DEFAULT NULL COMMENT '等级(捐赠证书)',
  activity_id  BIGINT      DEFAULT NULL,
  holder_name  VARCHAR(64) DEFAULT NULL,
  issued_at    DATETIME    DEFAULT CURRENT_TIMESTAMP,
  status       TINYINT     NOT NULL DEFAULT 1 COMMENT '1有效/0作废',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cert_no (cert_no),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='证书(捐赠等级+官方资质)';

-- ---------- 7. 活动与志愿者域 ----------
DROP TABLE IF EXISTS activity;
CREATE TABLE activity (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  title       VARCHAR(128) NOT NULL,
  cover_url   VARCHAR(512) DEFAULT NULL,
  description TEXT        DEFAULT NULL,
  location    VARCHAR(256) DEFAULT NULL,
  start_time  DATETIME    DEFAULT NULL,
  end_time    DATETIME    DEFAULT NULL,
  quota       INT         NOT NULL DEFAULT 0 COMMENT '名额',
  signed_count INT        NOT NULL DEFAULT 0 COMMENT '已报名数',
  status      VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED' COMMENT 'PUBLISHED/ONGOING/FINISHED/CANCELLED',
  created_at  DATETIME    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公益活动';

DROP TABLE IF EXISTS volunteer_signup;
CREATE TABLE volunteer_signup (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  activity_id  BIGINT      NOT NULL,
  user_id      BIGINT      NOT NULL COMMENT '报名操作人（登录账号）',
  signup_name  VARCHAR(64) DEFAULT NULL COMMENT '报名人姓名（可替他人报名）',
  phone        VARCHAR(20) DEFAULT NULL COMMENT '报名人手机号',
  guardian_auth TINYINT    NOT NULL DEFAULT 0 COMMENT '未成年人监护人授权',
  status       VARCHAR(16) NOT NULL DEFAULT 'SIGNED_UP' COMMENT 'SIGNED_UP/CHECKED_IN/ABSENT',
  checkin_at   DATETIME    DEFAULT NULL,
  hours_minutes INT        NOT NULL DEFAULT 0 COMMENT '时长(分钟)',
  cert_id      BIGINT      DEFAULT NULL COMMENT '发放的证书id',
  created_at   DATETIME    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_activity_phone (activity_id, phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='志愿者报名与签到';

-- ---------- 8. 内容与宣传域 ----------
DROP TABLE IF EXISTS content;
CREATE TABLE content (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  title       VARCHAR(256) NOT NULL,
  kind        VARCHAR(16) NOT NULL DEFAULT 'ARTICLE' COMMENT 'ARTICLE图文/VIDEO短视频',
  cover_url   VARCHAR(512) DEFAULT NULL,
  body        TEXT        DEFAULT NULL,
  video_url   VARCHAR(512) DEFAULT NULL,
  status      VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED' COMMENT 'DRAFT/REVIEWING/PUBLISHED/OFFLINE',
  share_count INT         NOT NULL DEFAULT 0,
  published_at DATETIME   DEFAULT NULL,
  created_at  DATETIME    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_status_kind (status, kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宣传内容';

-- ---------- 9. AI 审计域 ----------
DROP TABLE IF EXISTS ai_audit_log;
CREATE TABLE ai_audit_log (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  user_id     BIGINT      DEFAULT NULL,
  feature     VARCHAR(32) NOT NULL COMMENT 'RECOMMEND/CHAT/COPY/POSTER/VALUATION',
  input_text  TEXT        DEFAULT NULL,
  output_text TEXT        DEFAULT NULL,
  confidence  DECIMAL(5,4) DEFAULT NULL,
  decision    VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ADOPTED/MODIFIED/REJECTED/FALLBACK',
  operator_id BIGINT      DEFAULT NULL COMMENT '人工处理人',
  created_at  DATETIME    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_feature (feature)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI输出人审与审计日志';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 示例配置数据（均为草案值，可后台修改）
-- ============================================================
INSERT INTO sys_config (config_key, config_value, description) VALUES
  ('cert.level1.threshold', '100',    '捐赠等级1累计评估值阈值(元) - 草案'),
  ('cert.level2.threshold', '500',    '捐赠等级2累计评估值阈值(元) - 草案'),
  ('cert.level3.threshold', '1000',   '捐赠等级3累计评估值阈值(元) - 草案'),
  ('point.per.yuan',        '10',     '每1元评估值发放积分 - 草案(象征性回馈)'),
  ('point.exchange.rate',   '100',    '多少积分抵1元(100=100积分1元,即1000积分10元) - 已确认'),
  ('point.min.use',         '100',    '最低使用积分门槛 - 草案'),
  ('point.max.discount.ratio','0.3',  '单笔订单积分抵扣上限比例 - 草案'),
  ('claim.per.user.limit',  '3',      '每人限领件数 - 草案'),
  ('ai.chat.confidence.threshold','0.75','AI客服低置信转人工阈值 - 草案');

-- 示例邮寄地址（管理端可增删改；正式地址请在管理端「捐赠设置」中维护）
INSERT INTO donation_address (receiver, phone, province, city, district, detail, postal_code, remark, enabled, sort_no) VALUES
  ('绿益公益接收站', '0571-88886666', '浙江省', '杭州市', '西湖区', '文三路 100 号公益大厦 1 楼捐赠接收处', '310012', '工作日 9:00-17:00 接收，寄出后请填写捐赠单号', 1, 1),
  ('绿益公益仓储中心', '0571-88887777', '浙江省', '杭州市', '余杭区', '仓前街道公益物流园 B 区 3 号库', '311100', '仅接收衣物被褥、图书文具，大件请先联系', 1, 2);

-- 示例管理员（密码均为 BCrypt 加密的 admin123 / assess123，仅开发演示用）
INSERT INTO admin_user (username, password, real_name, role_code) VALUES
  ('admin',    '$2a$10$7EqJtq98hPqEX7fNZaFWoOHiYcD1N5fD2ePqM1oGk.OnQ7WjJ0QxG', '系统管理员', 'ADMIN'),
  ('assessor', '$2a$10$7EqJtq98hPqEX7fNZaFWoOHiYcD1N5fD2ePqM1oGk.OnQ7WjJ0QxG', '评估员', 'ASSESSOR');

-- 示例运费模板
INSERT INTO freight_template (region, base_fee, per_kg_fee) VALUES
  ('华东', 8.00, 2.00), ('华北', 10.00, 2.50), ('华南', 10.00, 2.50),
  ('西南', 12.00, 3.00), ('西北', 15.00, 3.50), ('全国', 12.00, 3.00);

-- 示例商品
-- 示例商品（图片为样例图，位于 uploads/samples/，对外通过 /api/uploads/samples/** 访问）
INSERT INTO product (name, kind, price, weight_gram, stock, cover_url, images, description) VALUES
  ('环保帆布袋', 'SALE', 29.90, 200, 100, '/uploads/samples/product-canvas-bag.png', '/uploads/samples/product-canvas-bag.png', '<p>可循环使用，公益义卖。</p><p><img src="/uploads/samples/product-canvas-bag.png" style="max-width:100%"></p>'),
  ('公益文化衫', 'SALE', 59.00, 300, 50, '/uploads/samples/product-tshirt.png', '/uploads/samples/product-tshirt.png', '<p>绿益公益限定款，舒适纯棉。</p><p><img src="/uploads/samples/product-tshirt.png" style="max-width:100%"></p>'),
  ('暖冬棉衣(申领)', 'CLAIM', 0.00, 800, 30, '/uploads/samples/product-winter-coat.png', '/uploads/samples/product-winter-coat.png', '<p>免费提供给有需要的人，仅付运费。</p><p><img src="/uploads/samples/product-winter-coat.png" style="max-width:100%"></p>'),
  ('学习用品包(申领)', 'CLAIM', 0.00, 500, 20, '/uploads/samples/product-stationery.png', '/uploads/samples/product-stationery.png', '<p>免费申领，仅付运费。含笔记本、铅笔、橡皮与笔袋。</p><p><img src="/uploads/samples/product-stationery.png" style="max-width:100%"></p>');

-- 示例活动
INSERT INTO activity (title, location, description, quota, start_time, end_time, status) VALUES
  ('社区旧衣回收志愿行', '青岛市南區社区服务中心', '旧衣分类整理与登记志愿服务', 20, '2026-12-01 09:00:00', '2026-12-01 16:00:00', 'PUBLISHED');

-- 示例内容
INSERT INTO content (title, kind, body, status, published_at) VALUES
  ('一件旧衣的温暖旅程', 'ARTICLE', '从衣柜到山区孩子手中，一件旧衣要经过多少道工序？', 'PUBLISHED', '2026-11-01 10:00:00'),
  ('生态置换集市回顾', 'ARTICLE', '旧物循环，让闲置焕发新生。', 'PUBLISHED', '2026-10-15 10:00:00');
