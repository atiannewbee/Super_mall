# Handoff — Super Mall

## 1. 当前定位

Super Mall 是一个可在本地完整运行的全栈电商 MVP，不再是早期的用户 CRUD 教学 Demo。

当前阶段包括：

- Vue 3 消费者商城；
- Vue 3 独立商家运营端；
- Spring Boot 4.1.0 + Java 17 后端；
- MySQL + Flyway 版本化数据库；
- 消费者与商家双 JWT 身份体系；
- 商品、SKU、库存、购物车、订单、模拟支付、支付宝适配、物流与售后；
- GitHub CI 和 `main` 分支保护。

下一阶段才包括公网部署、支付宝真实联调、微信支付、物流承运商回调、Python AI 客服、运营 Agent 和数据中台。

## 2. 仓库结构

```text
Super_mall/
├─ apps/
│  ├─ storefront/       Vue 3 消费者商城，开发端口 5173
│  └─ merchant/         Vue 3 商家运营端，开发端口 5174
├─ src/
│  ├─ main/java/        Spring Boot 业务代码
│  ├─ main/resources/   配置与 Flyway 迁移
│  └─ test/java/        后端集成测试
├─ docs/                API、数据库、支付、架构和安全文档
├─ .github/workflows/   全栈 CI
├─ pom.xml
└─ mvnw.cmd
```

## 3. 已完成能力

### 消费者端

- 邮箱或手机号注册、登录；
- 首页、分类、搜索、商品详情和 SKU 选择；
- 收藏、购物车、地址和个人资料；
- 创建订单、订单列表、订单详情和取消订单；
- 模拟支付宝、模拟微信支付；
- 支付结果查询、物流时间线和确认收货；
- 退款、退货退款、换货申请及退货物流填写；
- AI 客服交互面板原型。

AI 客服面板当前使用本地规则回复，并明确提示未连接真实订单和 AI 模型，不能将其视为已经接入外部 Agent。

### 商家端

- 独立商家登录、首次登录强制改密；
- 经营看板、订单查询和订单详情；
- 开始拣货、填写物流并确认发货；
- 库存查询与低库存筛选；
- 商品新增、修改和软删除；
- 消费者与商家令牌隔离、商户数据隔离和操作日志。

当前默认只运营一家商户。数据模型保留 `OWNER`、`OPERATOR`、`WAREHOUSE` 三种权限，但首版可以只使用主管账号，不要求另设仓库人员。

### 后端与数据库

- Controller → Service → Repository 分层；
- DTO、Bean Validation、统一业务异常和稳定错误 JSON；
- BCrypt 密码哈希、JWT 认证、账号状态实时校验；
- 服务端计价、订单幂等、库存原子锁定与释放；
- 支付单、支付通知幂等、支付宝 RSA2 验签和主动查询；
- 订单、履约、售后状态机；
- Flyway V1～V5 管理业务结构和演示目录数据；
- Hibernate 使用 `ddl-auto=validate`，不自动改表。

## 4. 支付边界

本地前端默认使用受控模拟支付：

- `VITE_PAYMENT_MODE=mock`
- `PAYMENT_SANDBOX_ENABLED=true`

模拟支付不会产生真实扣款，只用于验证下单、支付成功、库存变化和商家履约链路。

支付宝 provider adapter 已实现，但真实联调仍需要：

1. 可被支付宝访问的 HTTPS 异步通知地址；
2. 支付宝沙箱或正式 AppID；
3. 应用私钥、支付宝公钥及可选 SellerID；
4. `ALIPAY_ENABLED=true`；
5. `PAYMENT_SANDBOX_ENABLED=false`。

生产环境不得开放模拟支付接口。微信支付尚未接入。

## 5. 数据库约定

- 默认开发库：`super_mall`；
- 自动测试库：`super_mall_test`；
- 本地机密配置：`.env.properties`，该文件禁止提交；
- 已执行的 V1～V5 迁移不可原地修改；
- 后续结构变更必须新增 V6、V7 等迁移；
- 生产环境使用低权限数据库账号，不使用 root；
- 上线前必须验证数据库备份可以恢复。

数据库详情见 [docs/database/README.md](docs/database/README.md)。

## 6. 本地启动

先复制 `.env.properties.example` 为 `.env.properties`，填写 MySQL 密码、消费者 JWT 密钥和商家 JWT 密钥。两个 JWT 密钥必须不同且均至少 32 字节。

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd spring-boot:run
```

后端健康检查：

```text
GET http://localhost:8080/actuator/health
```

消费者端：

```powershell
Set-Location apps/storefront
npm ci
npm run dev
```

商家端：

```powershell
Set-Location apps/merchant
npm ci
npm run dev
```

## 7. 测试与验收

```powershell
.\mvnw.cmd clean test

Set-Location apps/storefront
npm ci
npm run test:run
npm run build

Set-Location ../merchant
npm ci
npm run test:run
npm run build
```

GitHub Actions 会在 PR 和推送到 `main` 时执行同等的后端、消费者端和商家端验证。`main` 已配置为必须通过 PR、严格状态检查和讨论解决后才能合并。

## 8. 配置与安全注意事项

- 不提交 `.env.properties`、数据库密码、JWT 密钥或支付私钥；
- 消费者 JWT 与商家 JWT 不得共用密钥、签发方或受众；
- 商家初始账号由环境变量创建，首次登录后必须修改密码；
- 完成首次建号后，从服务器环境删除初始商家密码；
- 金额、库存、订单状态和支付结果只信任服务端；
- 支付成功只以后端验签通知或主动查询为准；
- 商品删除使用软删除，不能破坏历史订单快照；
- Java 后端新增代码遵循中文注释规范。

## 9. 当前阶段与后续阶段

当前阶段的交付标准是：在本地 MySQL 环境中完成消费者下单、模拟支付、商家拣货发货、消费者确认收货或申请售后的完整链路，并通过三个应用的自动测试和生产构建。

后续建议按以下顺序推进：

1. 增加贯穿消费者与商家端的浏览器端到端测试；
2. 准备预发布环境、Nginx、HTTPS、生产配置、日志和数据库备份；
3. 在预发布环境联调支付宝沙箱公网回调；
4. 安全评审后切换支付宝正式环境；
5. 接入物流回调和微信支付；
6. 通过受保护的内部接口连接 Python AI 客服、运营 Agent 和数据中台。

## 10. 参考文档

- [README](README.md)
- [API 文档](docs/api/README.md)
- [数据库文档](docs/database/README.md)
- [支付宝沙箱接入](docs/payments/alipay-sandbox.md)
- [后端架构 ADR](docs/adr/0002-modular-backend-and-security.md)
- [商家端架构 ADR](docs/adr/0003-separate-merchant-portal-and-identity.md)
- [后端中文注释规范](docs/development/backend-commenting-guidelines.md)
- [安全审查报告](docs/security/review-2026-07-22.md)
