# 绿益公益 小程序 — 全栈工程

依据 `绿益公益-需求分析文档.md` 与 `绿益公益-产品需求文档-PRD.md`（V1 全量范围）实现的原生微信小程序 + SpringBoot + MySQL 全栈工程。

> 当前版本：可运行的工程化实现。支付、AI、证书验真等外部依赖通过**适配层 + 模拟实现**打通，留出真实接入点；数据库示例数值均为 PRD 标注的**草案值**，可在后台配置表调整。上线前需完成 PRD §10 的关键 OQ（支付商户、证书资质、积分合规）。

---

## 一、工程结构

```
2026-11-19-08-30-49/
├── 绿益公益-需求分析文档.md          # 需求分析（草案）
├── 绿益公益-产品需求文档-PRD.md       # PRD（草案）
├── miniprogram/                      # 原生微信小程序（C 端）
│   ├── app.js / app.json / app.wxss  # 全局配置、登录态、样式
│   ├── pages/                        # 16 个页面（首页/捐赠/商城/申领/活动/我的/荣誉/积分/证书验真等）
│   ├── utils/                        # request / auth / api 三层封装
│   └── project.config.json
└── server/                           # SpringBoot 后台
    ├── pom.xml / mvnw*               # Maven Wrapper（无需本地安装 Maven）
    └── src/main/
        ├── java/com/greenyi/charity/
        │   ├── common/               # 统一返回/异常/分页
        │   ├── config/ security/     # MyBatis-Plus、JWT 鉴权、RBAC
        │   ├── entity/ mapper/       # 21 实体 + 21 Mapper
        │   ├── dto/ service/         # 11 个业务服务
        │   ├── controller/           # 9 个 REST 控制器
        │   └── integration/          # 支付/AI/证书/OSS 适配层（含 Mock）
        └── resources/
            ├── application.yml
            └── schema.sql            # 全量建表 + 示例数据
```

## 二、启动后台

### 1. 准备数据库
在 MySQL 创建库并导入 Schema：
```sql
CREATE DATABASE IF NOT EXISTS greenyi_charity DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
```
```bash
mysql -h <你的地址> -u root -p greenyi_charity < server/src/main/resources/schema.sql
```

### 2. 配置连接（凭据不落代码，用环境变量）
```bash
export DB_URL="jdbc:mysql://192.168.18.130:3306/greenyi_charity?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
export DB_USERNAME="root"
export DB_PASSWORD="<你的密码>"
export JWT_SECRET="<换成一个生产环境随机密钥>"
```
Windows PowerShell：
```powershell
$env:DB_URL="jdbc:mysql://192.168.18.130:3306/greenyi_charity?..."
$env:DB_USERNAME="root"
$env:DB_PASSWORD="<你的密码>"
```

### 3. 编译并运行
```bash
cd server
./mvnw spring-boot:run          # 或打包后运行
# 打包：./mvnw package -DskipTests
# 运行：java -jar target/charity-miniprogram-server-1.0.0.jar
```
后台接口根路径：`http://<后台地址>:8080/api`

> 本机无 Maven/JDK 也能构建：`mvnw` 会自动下载 Maven；需 JDK 8+（本机 JDK 位于 `C:\Program Files (x86)\Java\jdk1.8.0_152`，构建时设置 `JAVA_HOME` 指向它）。

## 三、启动小程序

1. 用微信开发者工具「导入项目」，目录选择 `miniprogram/`，AppID 可用测试号。
2. 编译预览。C 端登录走 `wx.login` → 后端 `/auth/login` 自动建号。

### 真机预览加载失败？（最常见）

小程序真机上的 `localhost` 指的是**手机自己**，不是你的电脑。所以模拟器能显示、真机却「加载失败」。按以下步骤配置：

1. **让手机和电脑连同一个 WiFi**（或电脑开热点给手机）。
2. **查电脑局域网 IP**：CMD 执行 `ipconfig`，找到 WLAN 的 IPv4 地址（如 `192.168.x.x`）。
3. **让后端监听局域网**：启动 SpringBoot 时加参数 `--server.address=0.0.0.0`（默认已监听所有网卡），并确保防火墙放行 8080 端口。
4. **改小程序后端地址**，二选一：
   - 改代码：`miniprogram/app.js` 顶部的 `DEFAULT_BASE_URL` 改为 `'http://电脑IP:8080/api'`，如 `'http://192.168.1.10:8080/api'`；
   - 不改代码：真机调试控制台执行 `wx.setStorageSync('greenyi_api_base', 'http://电脑IP:8080/api')`，重新编译即可（优先级高于代码里的常量）。
5. **勾选「不校验合法域名」**：开发者工具 → 详情 → 本地设置 → 勾选「不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书」，否则微信会拦截 http 局域网请求。
6. 手机扫码预览，即可正常加载内容。

> 调试完如需还原，执行 `wx.removeStorageSync('greenyi_api_base')`，并把 `app.js` 的 `DEFAULT_BASE_URL` 改回 `localhost`。

### 首页点功能按钮提示「页面暂未开放」？

已修复：首页 8 个入口中，物资捐赠/公益商城/志愿活动属于 tabBar 页面，必须用 `wx.switchTab` 跳转（原代码统一用了 `navigateTo` 导致失败）。若仍出现，检查 `app.json` 的 tabBar 列表与 `pages/index/index.js` 的 `tabPages` 是否一致。

## 四、业务闭环（已实现）

| 闭环 | 关键链路 | 对应 FR |
|------|----------|---------|
| 物资捐赠 | 发起→签收→评估计价→累计评估值→发积分→达阈值发等级证书 | FR-001~007 |
| 商品购买 | 购物车→结算试算（积分抵扣，运费不抵）→下单→支付回调幂等→核销积分→发货/退款回退 | FR-008~013 |
| 免费申领 | 选品→地址→按地区/重量试算运费→限购校验→付运费→扣库存→发货 | FR-014~016 |
| 活动志愿 | 发布→报名（先到先得/满员即止）→签到记时长→结束发官方资质证书→时长并入档案 | FR-017~021 |
| 统一荣誉 | 捐赠评估值 + 志愿时长 + 活动参与聚合，两类证书分类展示 | FR-020~021 |
| 内容宣传 | 图文/短视频/分享计数、智能推荐 | FR-022, FR-025 |
| AI 增强 | 客服（低置信转人工）、估值参考/文案草稿（人工确认不自动生效）、全量审计日志 | FR-026~030 |
| 后台管理 | 内容/商品/活动维护、评估工作台、订单/申领管理、规则配置、数据看板、RBAC | FR-023 |

## 五、关键实现说明

- **合规红线**：积分仅来自物资评估值（象征性比例，草案 1 元=10 积分），兑换率 1000 积分=10 元，抵扣设上限+门槛、运费不可抵扣，退款回退——均集中在 `PointService` 与 `sys_config`。
- **两类证书**：捐赠等级证书（HONOR 荣誉激励类）与活动志愿者证书（OFFICIAL 官方资质类）均由平台签发但分开标注背书口径；编号唯一、可验真。
- **外部依赖可替换**：`integration/` 下的 `PaymentGateway` / `AiService` / `CertificateVerifier` 均有 Mock 实现，配置 `greenyi.integration.*-mock: false` 后补充真实 SDK 即可切换。
- **支付联调**：Mock 模式下小程序无需真实 `wx.requestPayment`，`createOrder`/`claimSubmit` 返回模拟支付参数，前端调用 `confirmPay`/`claimConfirmPay` 完成模拟支付。真实商户开通后替换 `PaymentGateway` 并在小程序端接入 `wx.requestPayment`。

## 六、验证结果

- 后端：`mvnw clean package` 编译通过；H2 内存库上下文冒烟测试通过（1 test, 0 failures）；可执行 jar 构建成功。
- 小程序：16 页面 × 4 文件齐全；全部 JSON 严格校验通过；全部 JS `node --check` 通过；WXML 无禁用语法；app.json 页面/tabBar/lazyCodeLoading 校验通过；33 个 API 调用全部有定义。

## 七、上线前必须关闭的高优先级问题（来自 PRD）
- OQ-01 微信支付商户开通
- OQ-09a 官方承认资质的授权依据、证书编号/验真规则
- OQ-16 积分回馈比例与合规口径（法务/财务）

## 八、微信手机号 / 退出登录 / 首页活动 / 公众号关联（本次新增）

### 1. 微信登录能否直接带出手机号？
- **wx.login 本身拿不到手机号**：它只返回 `code`，后端换取的是 `openid`/`unionid`，不含手机号。
- 正确做法是微信官方的「手机号快速验证组件」（`<button open-type="getPhoneNumber">`）：
  - 仅对**非个人主体且完成微信认证**的小程序开放，且**按次计费**（政府/非营利组织主体可免费）。个人主体小程序无法使用。
  - 已在登录页接入：点「微信一键填入」→ 用户同意 → 前端把 `code` 传给后端 `/auth/phone` → 后端用 `app-id/app-secret` 调微信 `getuserphonenumber` 换取手机号并回填；未配置凭证 / 个人主体 / 调用失败时**自动降级为手动填写**，流程不中断。
- 因此登录页现在要求**填写手机号**（11 位校验），姓名/昵称沿用微信 `type="nickname"` 授权填写。手机号随登录写入 `app_user.phone`（已有字段，无需改表），活动报名页会自动带出供确认。

### 2. 退出登录
- 「我的」页面底部新增「退出登录」按钮，确认后清空本地 token/userInfo（后端 JWT 无状态，前端清除即生效），页面回到未登录态。

### 3. 首页丰富公益活动
- 首页新增「公益活动」板块：展示报名中/进行中的活动（时间、地点、名额、状态），点击进入活动详情。
- 同时把首页「公益动态」与「公众号精选」拆分；修复了「更多」进入文章页无 id 报错（无 id 时改为展示内容列表）。

### 4. 小程序关联公众号、展示公众号文章
- **可以关联**。在微信公众平台完成「公众号关联小程序」后：
  - 小程序内可用 `web-view` 打开该公众号的文章链接（`https://mp.weixin.qq.com/s/...`），微信对**已关联公众号**的文章放行，无需配置业务域名；基础库支持时优先调用 `wx.openOfficialAccountArticle` 直接打开。
  - 未关联 / 打开失败时回退到自研 webview 页，提示复制链接查看，不会白屏。
  - `official-account` 关注组件只能在特定场景值下展示，不能任意场景强推，故未滥用。
- 运营方式：管理后台「内容管理」→「新增内容」→ 类型选「公众号文章」，填入标题、封面和文章链接（`kind=OA`，链接存 `video_url`），发布后即出现在首页「公众号精选」板块。

### 5. 本次改动文件
- 后端：`WeChatAuthService` / `DefaultWeChatAuthService`（resolvePhone + access_token 缓存）、`AuthService` / `AuthController`（登录存手机号 + `/auth/phone`）。
- 小程序：`app.js`（logout）、`utils/auth.js`、`utils/api.js`、`pages/login/*`、`pages/mine/*`、`pages/index/*`、`pages/article/*`、新增 `pages/webview/*`、`app.json`。
- 后台：`static/index.html`（内容管理新增/编辑，支持公众号文章类型）。
- 两端目录（工作区与 `F:/codex-lvyi`）已同步；JDK8 `mvnw clean compile` 通过；8102 demo 实例端到端 17 项校验全部通过。

## 九、图片改为「上传」模式（本次新增）

所有涉及图片的地方已从「填 URL」改为**本地上传**，文件落盘到服务器 `./uploads/` 目录，对外通过 `/api/uploads/**` 访问。

### 后端
- 新增 `POST /api/upload/image`（multipart，字段名 `file`），返回 `{url:"/uploads/日期/UUID.png"}`，仅允许 jpg/jpeg/png/gif/webp/bmp，单文件 ≤10MB。
- `FileStorageService` 负责落盘；`WebConfig` 把磁盘目录映射为 `/uploads/**` 并放行匿名访问。
- 目录可用 `greenyi.upload.dir` 或环境变量 `UPLOAD_DIR` 配置（默认 `./uploads`，相对应用启动目录）。**正式部署时该目录需随应用一起持久化/备份**。

### 管理后台
- 商品管理：封面、多张商品图改为上传控件（带缩略图预览、可删除）；详情描述新增「插入图片」按钮，上传后自动追加 `<img>` 标签。
- 内容管理：封面改为上传；列表新增封面缩略图列。
- 商品列表也新增封面缩略图列。

### 小程序
- 物资捐赠：照片改为「拍照/相册」选择并自动上传（最多 6 张），支持预览与删除；提交的 `photos` 为上传后的 URL 逗号串。
- 登录头像：`chooseAvatar` 得到的临时路径会在登录后自动上传并写回资料，避免临时路径过期导致头像丢失。
- `utils/request.js` 会把返回数据里的 `/uploads/...` 自动补全为绝对地址（含富文本 `<img src>`），所有展示页无需各自改；旧的完整 http 图片地址保持兼容。

### 验证
- F 盘 JDK8 `mvnw clean compile` 通过；8103 demo 实例端到端 11 项全通过（真实上传 PNG→静态访问→商品/内容保存 uploaded URL→游客回读；后台页含上传控件）；URL 归一化单测覆盖普通字段/富文本/外链/数组四种情况。

## 十、修复「退出后重新登录资料丢失」（本次新增）

- **原因**：`wx.login` 返回的 `code` 是一次性的。demo/mock 模式原来用 `code` 派生 `openid`，所以每次重新登录都被后端当成**新用户**，头像、昵称、手机号、积分、订单等自然全部「不见了」。
- **修复**：
  - 小程序端持久化一个 `clientId`（`greenyi_client_id`），**退出登录只清 token/userInfo，不清 clientId**；每次登录都上报。老用户首次升级时自动沿用旧的 `openid` 作为 `clientId`，身份不会断裂。
  - 后端 `AuthService.login` 接收 `clientId`；mock 模式以它为稳定 openid，真实微信模式仍以微信返回的 openid/unionid 为准（clientId 被忽略）。仅当请求没带 clientId 时才回退按 code 派生，保持向后兼容。
- **登录页优化**：进入登录页先静默登录，从后端回读已有资料并自动预填头像/昵称/手机号；老用户显示「欢迎回来 / 已自动带出上次的资料，无需重复填写」，按钮变为「直接进入」，不必再重复填手机号和昵称。新用户仍走完整填写流程（后端生成的占位昵称不会被当作正式昵称预填）。
- **验证**：F 盘 JDK8 编译通过；8104 demo 实例端到端 16 项全通过（同一 clientId 换 code 重登录仍是同一 userId 且资料保留、资料更新后回读保留、不同 clientId 为新用户、无 clientId 向后兼容）；小程序端单测 7 项通过（clientId 稳定、上报、退出保留、旧身份迁移、完整资料返回）。
