# 支付宝沙箱接入

当前实现使用支付宝电脑网站支付 `alipay.trade.page.pay`。应用生成待支付记录后，浏览器访问同源 `launchUrl`，后端通过支付宝官方 Java SDK 生成自动提交表单。支付成功只由验签异步通知或服务端主动查询确认，浏览器返回参数不能直接修改订单。

## 1. 在支付宝开放平台准备参数

1. 进入支付宝开放平台沙箱应用。
2. 使用支付宝密钥工具生成 RSA2 应用公私钥。
3. 将应用公钥上传到沙箱应用，复制平台显示的“支付宝公钥”。不要误填应用公钥。
4. 记录沙箱 AppID；如需加强商户校验，同时记录 SellerID。
5. 准备一个公网 HTTPS 地址转发到后端：

```text
POST https://你的API域名/api/payments/alipay/notify
```

本机 `localhost` 不能作为异步通知地址。前端返回地址可以暂时使用 `http://localhost:5173/checkout/result`，因为它由用户浏览器访问。

## 2. 填写本地配置

复制 `.env.properties.example` 为已被 Git 忽略的 `.env.properties`，填写：

```properties
ALIPAY_ENABLED=true
ALIPAY_GATEWAY_URL=https://openapi-sandbox.dl.alipaydev.com/gateway.do
ALIPAY_APP_ID=你的沙箱AppID
ALIPAY_PRIVATE_KEY=应用私钥的单行Base64内容
ALIPAY_PUBLIC_KEY=支付宝公钥的单行Base64内容
ALIPAY_SELLER_ID=可选SellerID
ALIPAY_NOTIFY_URL=https://你的API域名/api/payments/alipay/notify
ALIPAY_RETURN_URL=http://localhost:5173/checkout/result
```

私钥、公钥只保留 Base64 内容，不需要 `BEGIN/END` 行。配置类也兼容粘贴包含头尾的 PEM 文本，但 `.properties` 文件更适合使用单行内容。

## 3. 启动与联调

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd spring-boot:run
```

随后启动 Vue 前端，在结算页选择支付宝并提交订单。正常顺序是：

```text
创建订单 → 创建 PENDING 支付单 → 跳转支付宝沙箱
→ 支付宝异步通知 → RSA2 验签与金额校验
→ 支付单 SUCCESS → 订单 PROCESSING → 锁定库存转为已售
```

若异步通知暂未到达，支付宝返回页会通过 `GET /api/payments/{paymentNo}` 主动查询，最多轮询约 30 秒。

## 4. 上线切换

上线前将网关改为：

```properties
ALIPAY_GATEWAY_URL=https://openapi.alipay.com/gateway.do
```

同时替换为正式应用的 AppID、应用私钥、支付宝公钥、正式 HTTPS 通知地址和前端返回地址。不要把任何真实密钥提交到 Git、日志、截图或聊天记录中。

正式接入与产品开通以支付宝开放平台当前说明为准：

- https://open.alipay.com/module/webApp
- https://open.alipay.com/paymentServicer/paymentProvider.htm
