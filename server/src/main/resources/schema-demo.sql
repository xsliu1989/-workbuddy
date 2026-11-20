-- ============================================================
-- 绿益公益 - H2 内存库演示 Schema（免 MySQL 启动）
-- 表结构与 schema.sql 完全一致，仅调整为 H2 兼容语法。
-- 用于本地快速体验所有业务闭环，重启后数据重置。
-- ============================================================

DROP TABLE IF EXISTS sys_config;
CREATE TABLE sys_config (
  config_key   VARCHAR(64)  NOT NULL,
  config_value VARCHAR(256) NOT NULL,
  description  VARCHAR(256),
  updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (config_key)
);

DROP TABLE IF EXISTS app_user;
CREATE TABLE app_user (
  id BIGINT NOT NULL AUTO_INCREMENT,
  openid VARCHAR(64) NOT NULL,
  unionid VARCHAR(64),
  nickname VARCHAR(64),
  avatar_url VARCHAR(512),
  phone VARCHAR(20),
  guardian_auth TINYINT NOT NULL DEFAULT 0,
  is_minor TINYINT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE (openid)
);

DROP TABLE IF EXISTS admin_user;
CREATE TABLE admin_user (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  password VARCHAR(128) NOT NULL,
  real_name VARCHAR(64),
  role_code VARCHAR(32) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  last_login_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE (username)
);

DROP TABLE IF EXISTS user_address;
CREATE TABLE user_address (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  receiver VARCHAR(64) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  province VARCHAR(32),
  city VARCHAR(32),
  district VARCHAR(32),
  detail VARCHAR(256) NOT NULL,
  is_default TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

DROP TABLE IF EXISTS donation;
CREATE TABLE donation (
  id BIGINT NOT NULL AUTO_INCREMENT,
  donation_no VARCHAR(32) NOT NULL,
  user_id BIGINT NOT NULL,
  category VARCHAR(32) NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  description VARCHAR(512),
  photos TEXT,
  delivery_type VARCHAR(16) NOT NULL DEFAULT 'OFFLINE',
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING_RECEIVE',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE (donation_no)
);

DROP TABLE IF EXISTS assessment;
CREATE TABLE assessment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  donation_id BIGINT NOT NULL,
  assessor_id BIGINT NOT NULL,
  value_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  note VARCHAR(512),
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  is_review TINYINT NOT NULL DEFAULT 0,
  review_of BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

DROP TABLE IF EXISTS disposal_ledger;
CREATE TABLE disposal_ledger (
  id BIGINT NOT NULL AUTO_INCREMENT,
  donation_id BIGINT NOT NULL,
  method VARCHAR(32) NOT NULL,
  note VARCHAR(256),
  operator_id BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

DROP TABLE IF EXISTS product;
CREATE TABLE product (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  kind VARCHAR(16) NOT NULL DEFAULT 'SALE',
  price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  weight_gram INT NOT NULL DEFAULT 0,
  stock INT NOT NULL DEFAULT 0,
  cover_url VARCHAR(512),
  images TEXT,
  description TEXT,
  after_sale_desc TEXT,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

DROP TABLE IF EXISTS cart;
CREATE TABLE cart (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  selected TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE (user_id, product_id)
);

DROP TABLE IF EXISTS orders;
CREATE TABLE orders (
  id BIGINT NOT NULL AUTO_INCREMENT,
  order_no VARCHAR(32) NOT NULL,
  user_id BIGINT NOT NULL,
  address_id BIGINT,
  goods_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  freight_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  points_used INT NOT NULL DEFAULT 0,
  discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  pay_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  pay_status VARCHAR(16) NOT NULL DEFAULT 'UNPAID',
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING_PAY',
  transaction_id VARCHAR(64),
  logistics_no VARCHAR(64),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  paid_at DATETIME,
  PRIMARY KEY (id),
  UNIQUE (order_no)
);

DROP TABLE IF EXISTS order_item;
CREATE TABLE order_item (
  id BIGINT NOT NULL AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  quantity INT NOT NULL,
  PRIMARY KEY (id)
);

DROP TABLE IF EXISTS claim_order;
CREATE TABLE claim_order (
  id BIGINT NOT NULL AUTO_INCREMENT,
  claim_no VARCHAR(32) NOT NULL,
  user_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  address_id BIGINT NOT NULL,
  region VARCHAR(64),
  weight_gram INT NOT NULL DEFAULT 0,
  freight_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  pay_status VARCHAR(16) NOT NULL DEFAULT 'UNPAID',
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING_PAY',
  transaction_id VARCHAR(64),
  logistics_no VARCHAR(64),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  paid_at DATETIME,
  PRIMARY KEY (id),
  UNIQUE (claim_no)
);

DROP TABLE IF EXISTS freight_template;
CREATE TABLE freight_template (
  id BIGINT NOT NULL AUTO_INCREMENT,
  region VARCHAR(64) NOT NULL,
  base_fee DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  per_kg_fee DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  status TINYINT NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  UNIQUE (region)
);

DROP TABLE IF EXISTS point_account;
CREATE TABLE point_account (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  balance INT NOT NULL DEFAULT 0,
  total_earned INT NOT NULL DEFAULT 0,
  total_used INT NOT NULL DEFAULT 0,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE (user_id)
);

DROP TABLE IF EXISTS point_ledger;
CREATE TABLE point_ledger (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  change_type VARCHAR(16) NOT NULL,
  amount INT NOT NULL,
  balance_after INT NOT NULL,
  source VARCHAR(32),
  ref_no VARCHAR(64),
  remark VARCHAR(256),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

DROP TABLE IF EXISTS honor_profile;
CREATE TABLE honor_profile (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  total_assess_value DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  volunteer_hours INT NOT NULL DEFAULT 0,
  donation_level INT NOT NULL DEFAULT 0,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE (user_id)
);

DROP TABLE IF EXISTS certificate;
CREATE TABLE certificate (
  id BIGINT NOT NULL AUTO_INCREMENT,
  cert_no VARCHAR(64) NOT NULL,
  verify_code VARCHAR(32) NOT NULL,
  user_id BIGINT NOT NULL,
  cert_type VARCHAR(24) NOT NULL,
  category VARCHAR(16) NOT NULL,
  title VARCHAR(128) NOT NULL,
  level INT,
  activity_id BIGINT,
  holder_name VARCHAR(64),
  issued_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  status TINYINT NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  UNIQUE (cert_no)
);

DROP TABLE IF EXISTS activity;
CREATE TABLE activity (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(128) NOT NULL,
  cover_url VARCHAR(512),
  description TEXT,
  location VARCHAR(256),
  start_time DATETIME,
  end_time DATETIME,
  quota INT NOT NULL DEFAULT 0,
  signed_count INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

DROP TABLE IF EXISTS volunteer_signup;
CREATE TABLE volunteer_signup (
  id BIGINT NOT NULL AUTO_INCREMENT,
  activity_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  signup_name VARCHAR(64),
  phone VARCHAR(20),
  guardian_auth TINYINT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'SIGNED_UP',
  checkin_at DATETIME,
  hours_minutes INT NOT NULL DEFAULT 0,
  cert_id BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE (activity_id, phone)
);

DROP TABLE IF EXISTS content;
CREATE TABLE content (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(256) NOT NULL,
  kind VARCHAR(16) NOT NULL DEFAULT 'ARTICLE',
  cover_url VARCHAR(512),
  body TEXT,
  video_url VARCHAR(512),
  status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
  share_count INT NOT NULL DEFAULT 0,
  published_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

DROP TABLE IF EXISTS ai_audit_log;
CREATE TABLE ai_audit_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT,
  feature VARCHAR(32) NOT NULL,
  input_text TEXT,
  output_text TEXT,
  confidence DECIMAL(5,4),
  decision VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  operator_id BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

-- ============================================================
-- 示例数据（与 schema.sql 一致，均为草案值）
-- ============================================================
INSERT INTO sys_config (config_key, config_value, description) VALUES
  ('cert.level1.threshold', '100', '捐赠等级1累计评估值阈值(元) - 草案'),
  ('cert.level2.threshold', '500', '捐赠等级2累计评估值阈值(元) - 草案'),
  ('cert.level3.threshold', '1000', '捐赠等级3累计评估值阈值(元) - 草案'),
  ('point.per.yuan', '10', '每1元评估值发放积分 - 草案'),
  ('point.exchange.rate', '100', '多少积分抵1元 - 已确认'),
  ('point.min.use', '100', '最低使用积分门槛 - 草案'),
  ('point.max.discount.ratio', '0.3', '单笔订单积分抵扣上限比例 - 草案'),
  ('claim.per.user.limit', '3', '每人限领件数 - 草案'),
  ('ai.chat.confidence.threshold', '0.75', 'AI客服低置信转人工阈值 - 草案');

INSERT INTO admin_user (username, password, real_name, role_code) VALUES
  ('admin', '$2a$10$7EqJtq98hPqEX7fNZaFWoOHiYcD1N5fD2ePqM1oGk.OnQ7WjJ0QxG', '系统管理员', 'ADMIN'),
  ('assessor', '$2a$10$7EqJtq98hPqEX7fNZaFWoOHiYcD1N5fD2ePqM1oGk.OnQ7WjJ0QxG', '评估员', 'ASSESSOR');

INSERT INTO freight_template (region, base_fee, per_kg_fee) VALUES
  ('华东', 8.00, 2.00), ('华北', 10.00, 2.50), ('华南', 10.00, 2.50),
  ('西南', 12.00, 3.00), ('西北', 15.00, 3.50), ('全国', 12.00, 3.00);

INSERT INTO product (name, kind, price, weight_gram, stock, description) VALUES
  ('环保帆布袋', 'SALE', 29.90, 200, 100, '可循环使用，公益义卖'),
  ('公益文化衫', 'SALE', 59.00, 300, 50, '绿益公益限定款'),
  ('暖冬棉衣(申领)', 'CLAIM', 0.00, 800, 30, '免费提供给有需要的人，仅付运费'),
  ('学习用品包(申领)', 'CLAIM', 0.00, 500, 20, '免费申领，仅付运费');

INSERT INTO activity (title, location, description, quota, start_time, end_time, status) VALUES
  ('社区旧衣回收志愿行', '青岛市南区社区服务中心', '旧衣分类整理与登记志愿服务', 20, '2026-12-01 09:00:00', '2026-12-01 16:00:00', 'PUBLISHED');

INSERT INTO content (title, kind, body, status, published_at) VALUES
  ('一件旧衣的温暖旅程', 'ARTICLE', '从衣柜到山区孩子手中，一件旧衣要经过多少道工序？', 'PUBLISHED', '2026-11-01 10:00:00'),
  ('生态置换集市回顾', 'ARTICLE', '旧物循环，让闲置焕发新生。', 'PUBLISHED', '2026-10-15 10:00:00');
