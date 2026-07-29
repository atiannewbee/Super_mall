# ADR-0003: 独立商家前端与商家身份边界

## Status

Accepted

## Context

SUPER MALL 当前是单商家自营商城。消费者支付后，订单会进入 `PROCESSING`，数据库已经具备 `UNFULFILLED → PICKING → SHIPPED → DELIVERED` 履约状态和物流表，但没有可以执行备货与发货的商家身份、受保护接口和后台页面。

项目由小团队维护，运行在单台资源有限的服务器上；订单、库存和物流需要保持本地事务一致。同时，未来会增加运营 Agent 和数据中台，但当前没有拆分分布式服务的必要。

## Decision

采用以下结构：

- 新建独立 Vue3 项目 `super_mall_merchant_frontend`，不把商家页面放入消费者商城。
- 保留当前 Spring Boot 模块化单体和同一个 MySQL 数据库。
- 新建 `merchants`、`merchant_users`、`merchant_user_roles`、`merchant_operation_logs` 和 `warehouses`。
- 消费者和商家使用不同 JWT 密钥、issuer、audience 和 SecurityFilterChain。
- 商家接口统一位于 `/api/merchant/**`，消费者令牌不能访问。
- 默认建立一个 `SUPER MALL 自营` 商家和默认仓库。
- 商品和订单显式保存 `merchant_id`；现有数据迁移到默认商家。
- 商家人工操作写入订单状态历史和追加式操作日志。
- 运营 Agent 后续使用独立服务身份调用受控应用接口，不复用商家员工令牌，也不直连数据库。

## Consequences

### Positive

- 消费者与商家登录、路由、资源和发布节奏完全隔离。
- 订单、库存、支付和发货继续使用单数据库事务，避免分布式一致性成本。
- 单商家阶段实现简单，同时保留未来按 `merchant_id` 扩展的边界。
- 商家操作具备明确人员归属和审计记录。

### Negative

- 需要维护第二个 Vue 项目和两套 JWT 配置。
- Spring Security 需要维护两条明确排序的过滤链。
- 商品和订单查询必须始终带商家条件，避免未来数据越权。

### Neutral

- 第一版角色固定为 `OWNER`、`OPERATOR`、`WAREHOUSE`，暂不建设动态权限管理界面。
- 第一版库存仍是每个 SKU 一份总库存；默认仓库用于发货归属，多仓库存后续再通过迁移扩展。

## Alternatives Considered

**在消费者 `users` 中加入商家角色**

- 放弃：认证、导航和权限边界容易混淆，后台能力会持续污染消费者应用。

**拆分独立商家后端服务**

- 放弃：当前只有一个商家和一台服务器，会引入不必要的部署、服务通信和分布式事务成本。

**接入外部 IAM**

- 暂缓：当前人员规模不需要额外基础设施；保留未来迁移空间。

## References

- `docs/adr/0002-modular-backend-and-security.md`
- `docs/plans/2026-07-16-super-mall-database-design.md`

