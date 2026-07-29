# SUPER MALL 数据库使用说明

## 本地配置

项目默认连接本机 `super_mall` 数据库。复制示例配置：

```powershell
Copy-Item .env.properties.example .env.properties
```

然后只在 `.env.properties` 中填写本地数据库密码。该文件已经加入 `.gitignore`，不要提交。

也可以使用系统环境变量覆盖：

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JPA_SHOW_SQL`

生产环境必须创建权限受限的应用账号，不使用 MySQL root；数据库应由部署流程预先创建，生产连接串不依赖 `createDatabaseIfNotExist=true`。

## 迁移

迁移文件位于：

```text
src/main/resources/db/migration/
├── V1__create_super_mall_schema.sql
├── V2__seed_storefront_catalog.sql
├── V3__add_order_idempotency.sql
├── V4__add_alipay_payment_tracking.sql
└── V5__add_merchant_operations.sql
```

启动应用或运行测试时，Flyway 会自动校验并执行尚未应用的迁移。Hibernate 配置为 `ddl-auto=validate`，只校验 Entity，不再自行建表或改表。

已经被任一环境应用的迁移文件不得原地修改。结构变更必须新增：

```text
V6__describe_the_change.sql
```

禁止在生产环境使用 `flyway clean`，项目已设置 `spring.flyway.clean-disabled=true`。

## 验证

```powershell
.\mvnw.cmd test
```

`DatabaseSchemaIntegrationTests` 会核对：

- 26 张业务表与 Flyway 历史表存在，其中 `payment_notifications` 保存去标识化通知摘要并负责回调幂等。
- 5 个分类、8 个品牌、8 个商品、16 个 SKU 和 16 条库存记录存在。
- 每个 SKU 都有库存行，前端缺货 SKU 的库存为 0。
- 订单金额使用 `DECIMAL(12,2)`。
- 商品 slug、SKU 编码、订单号和支付单号唯一索引存在。
- 支付通知的渠道与通知 ID 组合唯一，重复回调不会重复扣减库存。
- 非法用户身份和负库存会被数据库约束拒绝。

## 上线前检查

1. 备份数据库并验证备份可恢复。
2. 使用独立低权限账号运行迁移，确认具备本次 DDL 所需权限。
3. 在预发布数据库执行测试与迁移。
4. 检查 Flyway checksum 和目标版本。
5. 迁移生产库后执行只读健康检查，不直接手工修改 `flyway_schema_history`。
