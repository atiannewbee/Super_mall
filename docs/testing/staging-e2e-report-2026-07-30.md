# Super Mall 预发布 E2E 验收报告

- 验收日期：2026-07-30
- 预发布提交：`8da186ad0757a5754f564f10bf68ea8eec7863e3`
- 合并记录：PR #9
- 消费者端：<https://mall-staging.350233.xyz>
- 商家端：<https://merchant-staging.350233.xyz>

## 验收结论

预发布版本已上线，用户端、商家端、后端健康检查和原有个人站点均正常。
本地构建测试、GitHub CI、依赖安全审计、本地驱动与 GitHub 远程真实浏览器
全链路测试全部通过。

## 自动化结果

| 范围 | 结果 |
| --- | --- |
| 后端单元与集成测试 | 26/26 通过 |
| 消费者端组件测试 | 22/22 通过 |
| 商家端组件测试 | 4/4 通过 |
| Playwright 预发布 E2E | 7/7 通过 |
| GitHub 远程预发布 E2E | 7/7 通过 |
| GitHub `main` CI | Backend、Storefront、Merchant 全部通过 |
| npm 官方漏洞库审计 | 三个 JavaScript 工程均为 0 漏洞 |

真实浏览器已覆盖以下业务路径：

1. 用户注册。
2. 新增地址并验证省、市、区级联。
3. 搜索商品、加入购物车、提交订单。
4. 使用模拟支付宝完成支付。
5. 商家拣货、出库并填写物流单号。
6. 用户查看物流、确认收货。
7. 用户发起售后。
8. 商家新增、修改、搜索并软删除测试商品。
9. 用户端和商家端公开页面、商品接口及健康接口检查。
10. 页面运行期间的 JavaScript 异常和浏览器控制台错误检查。

## 首轮发现与修复

1. Cloudflare Browser Insights 脚本和上报地址被 CSP 拦截。
   已在 Nginx 中只放行 Cloudflare 官方脚本与上报域名。
2. 商家端依赖 Google Fonts，预发布 CSP 会阻止加载。
   已移除外部字体请求，改为本地系统字体栈。
3. 商家新增 SKU 的 HTML `pattern` 在现代浏览器 `v` 模式下无效。
   已修正表达式并增加组件回归测试。
4. 带商家密码的 E2E 若保存 trace，可能记录登录请求体。
   已关闭两条凭据场景的 Playwright trace，仅保留失败截图和录像。
5. 前端测试工具存在高危传递依赖。
   已锁定修复后的 `glob` 和 `minimatch`，官方漏洞库复查为 0。

## 数据与凭据边界

- 本地后端集成测试使用 `super_mall_test`。
- 预发布浏览器验收使用 `super_mall_staging`。
- E2E 使用独立低权限商家账号，仅有 `OPERATOR`、`WAREHOUSE`，没有 `OWNER`。
- 凭据只保存在服务器 root 专用文件中，权限为 `0600`，未写入仓库、报告或测试产物。
- 站主明确授权后，凭据已加密写入 GitHub `staging` Environment Secrets。
- GitHub 仅保存 `STAGING_MERCHANT_EMAIL`、`STAGING_MERCHANT_PASSWORD` 两个密钥；
  工作流日志中二者均显示为 `***`。

## 服务器复核

- 当前版本软链接指向提交 `8da186a`。
- `super-mall-staging`、Nginx、Cloudflare Tunnel、logrotate timer 均为 active。
- 部署后 15 分钟内应用日志没有 warning 及以上记录。
- 用户端、商家端、原有个人站点公网响应均为 HTTP 200。
- 根分区使用率 29%，约剩余 36 GB。
- systemd journal 占用约 72.7 MB，`/var/log` 总计约 142 MB。
- 发布目录仅保留当前版本和两个回滚版本，共 3 个版本。

## GitHub 远程复验

- 工作流运行：<https://github.com/atiannewbee/Super_mall/actions/runs/30513911079>
- 运行提交：`cb19b941bbb153a0c8dfcc27ab7ed8df5ef4900b`
- 结果：`Cross-portal acceptance` 成功，7 项测试全部执行。
- Playwright 证据包：`staging-e2e-30513911079`
- 证据包大小：6,685,915 bytes，共 27 个文件。
- 证据包 SHA-256：`88c12f08b96fb311e2fa86d553244ff9ba4e86c2ad910ef5d7c78940c16a8dca`
- 保留截止日期：2026-08-13。
- 下载证据包后按专用邮箱和密码原值扫描，两个命中数均为 0。
