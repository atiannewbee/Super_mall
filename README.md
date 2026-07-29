# Super Mall

这是一个包含消费者商城、商家运营端和 Spring Boot 后端的电商单体仓库。当前实现覆盖账号、商品目录、地址、收藏、购物车、订单、库存、支付宝沙箱支付、受控模拟支付、物流查询、售后，以及商家的待办、拣货、发货和商品库存管理。

## 项目结构

```text
Super_mall/
├─ apps/
│  ├─ storefront/       Vue 3 消费者商城（默认端口 5173）
│  └─ merchant/         Vue 3 商家运营端（默认端口 5174）
├─ src/                 Spring Boot 后端
├─ docs/                架构、数据库、支付与实施文档
├─ pom.xml
└─ mvnw.cmd
```

## 本地启动

1. 确认 MySQL 已启动，并让 Maven 使用 JDK 17。
2. 复制 `.env.properties.example` 为 `.env.properties`，填写数据库密码以及消费者端、商家端各自的随机 JWT 密钥。两个密钥必须不同且均至少 32 字节。
3. 当前前端默认使用模拟支付。本地 `.env.properties` 设置 `PAYMENT_SANDBOX_ENABLED=true` 后，可以在页面选择模拟支付宝或模拟微信；该流程不会产生真实扣款。
4. 需要联调支付宝时，将 `PAYMENT_SANDBOX_ENABLED` 改为 `false`，填写沙箱 AppID、应用私钥、支付宝公钥、异步通知 URL 与前端返回 URL，并设置 `ALIPAY_ENABLED=true`。异步通知 URL 必须能被支付宝公网访问。
5. 启动应用：

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd spring-boot:run
```

首次启动时 Flyway 会自动创建 `super_mall` 数据库结构并写入与 Vue 前端一致的演示商品。健康检查地址为 `GET /actuator/health`。

后端健康后：

- 在 `apps/storefront` 执行 `npm ci` 和 `npm run dev`，消费者商城位于 `http://localhost:5173`。
- 在 `apps/merchant` 执行 `npm ci` 和 `npm run dev`，商家运营中心位于 `http://localhost:5174`。

两个 Vite 项目都会将 `/api` 代理到本服务。生产环境建议为商家端使用独立域名，并通过 `VITE_API_BASE_URL` 与后端 `CORS_ALLOWED_ORIGINS` 配置 API 地址。

## 商家账号与权限

- 商家端没有公开注册入口，首次启动通过 `MERCHANT_BOOTSTRAP_EMAIL`、`MERCHANT_BOOTSTRAP_PASSWORD` 和 `MERCHANT_BOOTSTRAP_NAME` 创建主管账号。
- 初始账号首次登录后必须修改密码；改密会立即撤销旧令牌。
- 权限分为 `OWNER`、`OPERATOR` 和 `WAREHOUSE`。开始拣货、确认发货仅允许主管或仓库角色执行。
- 消费者令牌与商家令牌使用不同密钥、签发方和受众，不能跨端调用。
- 生产环境完成首次建号和改密后，应移除 `MERCHANT_BOOTSTRAP_PASSWORD`，并将密钥交给服务器密钥管理机制。

## 测试与打包

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd package

Set-Location apps/storefront
npm ci
npm run test:run
npm run build

Set-Location ../merchant
npm ci
npm run test:run
npm run build
```

后端测试固定使用独立的 `super_mall_test` MySQL 数据库，包含数据库约束、安全边界、目录查询以及注册到售后的完整交易流程，不会读写日常开发使用的 `super_mall` 数据库。测试产生的用户、订单和库存变化均在事务结束时回滚。

## 代码 Review

后端 Java 源码使用中文包级、类级和关键业务注释，重点说明模块边界、事务、租户隔离、
库存并发、支付验签与状态机。新增代码请遵循
[后端中文注释规范](docs/development/backend-commenting-guidelines.md)。

V1～V5 已执行的 Flyway 文件保持不可变，版本目的和 Review 提示记录在
[迁移 Review 说明](docs/database/migration-review-notes.md)；后续数据库变更必须新增迁移版本。

## 关键行为

- 密码使用 BCrypt 自适应哈希，接口只返回 DTO，不返回持久化实体或密码字段。
- JWT 为无状态短期访问令牌；账号锁定或删除后，已有令牌会立即失效。
- 商品价格、运费、优惠和退款金额全部由服务器计算，客户端金额不会被信任。
- 下单通过数据库条件更新原子锁定库存；取消、超时会释放库存；支付后转为已售库存。
- 支付创建、渠道跳转、异步通知与订单状态相互隔离；只有验签通知或主动查询确认成功后才更新订单。
- 支付宝通知同时校验 RSA2 签名、AppID、可选 SellerID、支付单号、交易号和服务端订单金额，并按通知 ID 幂等处理。
- 取消或超时订单前会先查询并关闭尚未完成的支付宝交易，未确认关闭时不会释放库存。
- `Idempotency-Key` 可防止重复提交订单，建议前端每次结算生成一个 UUID。
- 未支付订单默认 30 分钟自动关闭。
- 模拟支付接口在应用默认配置中关闭；本地示例配置会明确设置 `PAYMENT_SANDBOX_ENABLED=true`。生产环境和真实支付环境必须设置为 `false`。
- 商家只能访问自己商户名下的订单、库存和履约记录；拣货、发货均有状态机校验和审计日志。

支付宝配置步骤见 [支付宝沙箱接入](docs/payments/alipay-sandbox.md)，完整接口见 [API 文档](docs/api/README.md)，数据库设计见 [数据库文档](docs/database/README.md)，后端架构见 [ADR-0002](docs/adr/0002-modular-backend-and-security.md)，商家端架构见 [ADR-0003](docs/adr/0003-separate-merchant-portal-and-identity.md)，安全审查见 [安全审查报告](docs/security/review-2026-07-22.md)。
