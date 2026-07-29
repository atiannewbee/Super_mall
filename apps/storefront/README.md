# SUPER MALL 前端

基于 Vue 3 与 Vite 的消费者端商城，已接入 `Spring_boot_Demo1` 后端和 MySQL 数据库。覆盖注册登录、商品目录、SKU 库存、购物车、收藏、地址、下单、支付状态、订单查询、资料与售后主流程。

## 本地联调

先在 `D:\Files\Spring_boot_Demo1` 启动后端，确认 `http://localhost:8080/actuator/health` 返回 `UP`，再启动前端：

```powershell
cd D:\Files\super_mall_frontend
npm install
npm run dev
```

访问 `http://localhost:5173`。开发服务器会把 `/api` 与 `/actuator` 代理到 `http://localhost:8080`，浏览器无需额外处理跨域。

前端默认使用 `VITE_PAYMENT_MODE=mock`：结算页可以选择模拟支付宝或模拟微信，付款页需要手动确认，且不会产生真实扣款。后端 `.env.properties` 同时需要设置：

```properties
PAYMENT_SANDBOX_ENABLED=true
```

## 测试与构建

```powershell
npm run test:run
npm run build
```

## 数据与安全边界

- JWT 会话保存在 `super-mall-session`，只包含访问令牌、过期时间和公开用户资料，不保存密码。
- 未登录购物车暂存于 localStorage；登录后自动合并到服务端购物车。
- 商品价格、库存、运费、优惠、退款金额和订单状态全部以后端为准。
- 开发支付通过后端受控模拟接口完成；每次成功仍会创建支付流水、更新订单状态并完成库存转换，但不会产生真实资金交易。
- 商品目录在后端不可用时保留静态降级展示；账户与交易功能不会使用模拟数据冒充真实结果。
- Agent 客服仍为 UI 预览，后续由 Spring Boot 调用独立 Python Agent 服务。

## 生产环境

推荐由 Nginx 将前端静态文件与 `/api` 反向代理到同一域名。若 API 使用独立域名，复制 `.env.example` 为 `.env.production.local`，设置：

```properties
VITE_API_BASE_URL=https://api.example.com
VITE_PAYMENT_MODE=alipay
```

切换真实支付宝时，后端必须同时设置 `PAYMENT_SANDBOX_ENABLED=false` 并完成支付宝参数配置。不要在前端环境变量中存放数据库密码、JWT 密钥或支付密钥。独立域名部署时，还需把前端域名加入后端 `CORS_ALLOWED_ORIGINS`。

## 主要路由

- `/`、`/search`、`/category/:slug`、`/product/:slug`：商品发现与详情
- `/login`：注册与密码登录
- `/cart`、`/checkout`、`/checkout/result`：购物车、结算与支付状态
- `/account/orders`、`/account/orders/:orderNo`：订单查询与详情
- `/account/addresses`、`/account/favorites`、`/account/after-sales`、`/account/profile`：账户服务
- `/help`：帮助中心

商品图片目前使用远程演示资源，正式运营前应迁移到自有对象存储或 CDN。
