# Super Mall API

默认地址为 `http://localhost:8080`。除商品目录和认证接口外，请求都需要：

```http
Authorization: Bearer <accessToken>
Content-Type: application/json
```

分页从 `page=0` 开始。订单、支付、履约和售后状态在 JSON 中统一使用小写连字符形式，例如 `pending-payment`；创建请求中的受控枚举使用大写下划线形式。

## 接口清单

| 模块 | 方法与路径 | 说明 |
|---|---|---|
| 认证 | `POST /api/auth/register` | 邮箱或手机号注册并返回 JWT |
| 认证 | `POST /api/auth/login` | 邮箱或手机号登录 |
| 个人资料 | `GET/PUT /api/me/profile` | 查询或修改当前用户资料 |
| 分类 | `GET /api/categories` | 根分类列表，公开 |
| 品牌 | `GET /api/brands` | 品牌列表，公开 |
| 商品 | `GET /api/products` | 搜索、筛选、排序与分页，公开 |
| 商品 | `GET /api/products/{slug-or-id}` | 商品详情、SKU、库存和规格，公开 |
| 地址 | `GET/POST /api/me/addresses` | 地址列表与新增 |
| 地址 | `PUT/DELETE /api/me/addresses/{id}` | 修改或删除本人地址 |
| 地址 | `PATCH /api/me/addresses/{id}/default` | 设置默认地址 |
| 收藏 | `GET /api/me/favorites` | 分页查询收藏 |
| 收藏 | `POST/DELETE /api/me/favorites/{productId}` | 收藏或取消收藏 |
| 购物车 | `GET /api/cart` | 查询购物车和选中金额 |
| 购物车 | `POST /api/cart/items` | 按 `skuCode` 加购 |
| 购物车 | `PATCH /api/cart/items/{itemId}` | 修改数量或选中状态 |
| 购物车 | `DELETE /api/cart/items/{itemId}` | 删除一项 |
| 购物车 | `DELETE /api/cart/items` | 清空购物车 |
| 订单 | `POST /api/orders` | 结算已选购物车项；支持 `Idempotency-Key` |
| 订单 | `GET /api/orders` | 本人订单分页与状态筛选 |
| 订单 | `GET /api/orders/{orderNo}` | 本人订单、时间线与物流详情 |
| 订单 | `POST /api/orders/{orderNo}/cancel` | 取消待支付订单并释放库存 |
| 支付 | `POST /api/orders/{orderNo}/payments` | 创建支付宝支付单并返回同源跳转地址 |
| 支付 | `GET /api/payments/{paymentNo}` | 查询本人支付单并主动刷新支付宝状态 |
| 支付 | `GET /api/payments/alipay/{paymentNo}/launch` | 公开跳转页；输出支付宝 SDK 生成的表单 |
| 支付 | `POST /api/payments/alipay/notify` | 支付宝异步通知；公开但强制 RSA2 验签 |
| 支付 | `POST /api/orders/{orderNo}/payments/sandbox` | 仅开发/测试沙箱支付 |
| 收货 | `POST /api/orders/{orderNo}/confirm-receipt` | 确认已发货订单收货 |
| 售后 | `POST /api/after-sales` | 申请退款、退货退款或换货 |
| 售后 | `GET /api/after-sales` | 本人售后分页与状态筛选 |
| 售后 | `GET /api/after-sales/{afterSaleNo}` | 售后详情与事件时间线 |
| 售后 | `POST /api/after-sales/{afterSaleNo}/cancel` | 取消待审核售后 |
| 售后 | `PATCH /api/after-sales/{afterSaleNo}/return-shipment` | 填写本人退货物流 |

## 商家端接口

商家端使用独立 JWT，消费者令牌不能调用以下接口：

| 模块 | 方法与路径 | 说明 |
|---|---|---|
| 商家认证 | `POST /api/merchant/auth/login` | 商家员工登录 |
| 商家账号 | `GET /api/merchant/me` | 查询当前商家员工与权限 |
| 商家账号 | `POST /api/merchant/me/password` | 修改密码并使旧令牌失效 |
| 经营看板 | `GET /api/merchant/dashboard` | 查询订单与库存摘要 |
| 商家订单 | `GET /api/merchant/orders` | 按履约状态或关键字分页查询 |
| 商家订单 | `GET /api/merchant/orders/{orderNo}` | 查询本商户订单详情 |
| 商家履约 | `POST /api/merchant/orders/{orderNo}/picking` | 开始拣货 |
| 商家履约 | `POST /api/merchant/orders/{orderNo}/ship` | 填写物流并确认发货 |
| 库存中心 | `GET /api/merchant/inventory` | 查询库存或低库存商品 |
| 商品管理 | `POST /api/merchant/products` | 新增商品、默认 SKU 与库存 |
| 商品管理 | `PUT /api/merchant/products/{productId}/skus/{skuId}` | 修改商品、SKU 与可售库存 |
| 商品管理 | `DELETE /api/merchant/products/{productId}` | 软删除商品 |

## 商品查询参数

`GET /api/products` 支持：`q`、`category`、`brand`、`featured`、`new`、`deal`、`minPrice`、`maxPrice`、`page`、`size` 和 `sort`。`sort` 可选 `recommended`、`newest`、`price-asc`、`price-desc`、`sales`、`rating`。

## 核心请求示例

注册：

```json
{
  "name": "商城用户",
  "email": "user@example.com",
  "phone": "",
  "password": "SecurePass123"
}
```

加购：

```json
{
  "skuCode": "aether-x1-256-black",
  "quantity": 1
}
```

创建订单：

```http
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

```json
{
  "addressId": 1,
  "buyerNote": "工作日送达",
  "paymentChannel": "ALIPAY",
  "invoiceRequired": false
}
```

创建支付宝支付：

```http
POST /api/orders/SM.../payments
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{"channel": "ALIPAY"}
```

成功后返回 `action=redirect` 与 `launchUrl`。浏览器导航到 `launchUrl`，后端输出支付宝 SDK 生成的自动提交表单。支付宝返回前端页面只用于用户导航，前端必须再调用 `GET /api/payments/{paymentNo}`；订单是否支付成功只以后端验签通知或主动查询为准。

售后申请：

```json
{
  "orderNo": "SM...",
  "type": "REFUND_ONLY",
  "reasonCode": "NO_LONGER_NEEDED",
  "reasonDescription": "不再需要",
  "customerNote": "商品未拆封",
  "items": [
    {"orderItemId": 1, "quantity": 1}
  ]
}
```

## 错误格式

所有业务错误使用稳定 JSON 结构：

```json
{
  "code": "INSUFFICIENT_STOCK",
  "message": "商品库存不足",
  "details": {},
  "timestamp": "2026-07-22T08:00:00Z",
  "path": "/api/orders"
}
```

前端应根据 HTTP 状态和 `code` 处理，不要解析中文 `message`。

## 外部系统边界

支付宝电脑网站支付已通过独立 provider adapter 接入。微信支付、物流承运商回调、Python AI 客服、运营 Agent 和数据中台仍保留为外部系统边界；接入时应继续新增带签名校验的 provider adapter 或受服务身份保护的内部接口，不能开放消费者 JWT 直接修改支付、发货或审核状态。
