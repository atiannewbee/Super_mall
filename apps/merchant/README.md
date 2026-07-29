# SUPER MALL 商家运营中心

独立于消费者商城的 Vue 3 商家前端，默认运行在 `http://localhost:5174`。

## 本地运行

```powershell
npm install
npm run dev
```

后端需运行在 `http://localhost:8080`，Vite 会代理 `/api` 请求。

商家账号由后端环境变量 `MERCHANT_BOOTSTRAP_*` 首次初始化，不提供公开注册入口。

首次登录会要求修改初始密码。商家端使用独立 JWT、独立本地会话键和独立路由守卫，消费者商城的登录状态不会在这里生效。

## 当前功能

- 今日待办、待拣货、已发货和低库存总览
- 按履约状态、订单号或收件人筛选订单
- 开始拣货、填写承运商与运单号并确认发货
- 查看仓库、库存、预占量和低库存 SKU
- 查看订单履约时间线与操作审计记录

生产构建：

```powershell
npm run test:run
npm run build
```

如商家端与 API 使用不同域名，请复制 `.env.example` 为 `.env` 并设置 `VITE_API_BASE_URL`，同时将商家端域名加入后端 `CORS_ALLOWED_ORIGINS`。
