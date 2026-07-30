# Super Mall 预发布部署

## 目标与边界

预发布环境用于在正式营业前验证完整电商链路和后续 Agent 接口，当前地址：

- 消费者端：`https://mall-staging.350233.xyz`
- 商家端：`https://merchant-staging.350233.xyz`
- 后端：仅监听 `127.0.0.1:18080`，由两个前端域名的同源 `/api` 代理访问
- MySQL：仅监听 `127.0.0.1:3306`
- 支付：只启用受控模拟支付，支付宝和微信真实扣款均关闭

现有个人站点、管理后台和 Cloudflare Tunnel 保持独立。Nginx 的预发布入口只监听本机 `8081`，公网 HTTPS 由 Cloudflare Tunnel 提供。

## 服务器目录

```text
/srv/super-mall/                         Git 仓库
/etc/super-mall/staging.env              服务环境变量，root:super-mall 0640
/root/super-mall-staging-credentials.txt 首次商家登录凭据，root 0600
/opt/super-mall/releases/<sha>/           不可变后端 release
/opt/super-mall/current                   当前后端软链接
/var/www/super-mall/releases/<sha>/       不可变前端 release
/var/www/super-mall/current               当前前端软链接
```

不要把 `staging.env`、初始商家密码、数据库密码或 JWT 密钥提交到仓库。

## 首次初始化

```bash
cd /srv/super-mall
sudo ./deploy/staging/provision.sh
```

脚本会：

1. 安装 Java 17、MySQL 8 和 curl；
2. 将 MySQL 绑定到本机并应用低内存配置；
3. 创建 `super_mall_staging` 数据库和最小权限应用账号；
4. 生成相互独立的消费者、商家 JWT 密钥；
5. 写入 root-only 初始商家凭据文件；
6. 安装 systemd 与 Nginx 配置；
7. 设置日志、core dump 和 Nginx/MySQL 轮转上限；
8. 关闭预发布不需要的 MySQL binlog；
9. 保持真实支付关闭。

服务器基线预装了 MariaDB 命令行客户端但没有 MariaDB 服务，同时在 DNF 中过滤
MySQL 包。初始化脚本会临时绕过该过滤，并只用 MySQL 8 客户端替换冲突的
MariaDB 客户端；安装模拟事务必须确认没有移除其他业务服务后才能执行。

Cloudflare 的 `/etc/cloudflared/config.yml` 需要在最终 404 规则之前加入
`deploy/staging/cloudflared-ingress.snippet.yml`，然后执行：

```bash
cloudflared tunnel route dns 345891ae-f7ad-40f0-bf88-c3c0a0d30c8e mall-staging.350233.xyz
cloudflared tunnel route dns 345891ae-f7ad-40f0-bf88-c3c0a0d30c8e merchant-staging.350233.xyz
cloudflared --config /etc/cloudflared/config.yml tunnel ingress validate
systemctl restart cloudflared
```

## 构建发布包

Windows 本地执行以下脚本。脚本要求工作区干净，会运行三个应用的测试、
构建并输出 SHA256：

```powershell
.\deploy\staging\build-release.ps1
```

生成的统一发布包结构：

```text
release/
├─ backend/super-mall.jar
├─ storefront/
└─ merchant/
```

上传压缩包后执行：

```bash
sudo /srv/super-mall/deploy/staging/deploy-release.sh \
  /tmp/super-mall-<sha>.tar.gz \
  <sha>
```

发布脚本使用不可变 release 和原子软链接。后端健康检查失败时会自动恢复上一版。
发布成功后只保留当前版本和最近两个回滚版本。

## 验证

```bash
systemctl is-active mysqld super-mall-staging nginx cloudflared
curl -fsS http://127.0.0.1:18080/actuator/health
curl -fsS -H 'Host: mall-staging.350233.xyz' http://127.0.0.1:8081/actuator/health
curl -fsS https://mall-staging.350233.xyz/actuator/health
curl -fsS https://merchant-staging.350233.xyz/actuator/health
```

还需要在浏览器验证：

1. 消费者注册和登录；
2. 搜索、商品详情、购物车和地址级联；
3. 下单与模拟支付；
4. 商家登录、拣货与发货；
5. 消费者确认收货或申请售后。

## 商家初始密码

初始凭据不会输出到 CI、聊天或普通日志。通过受控 SSH 会话读取：

```bash
sudo less /root/super-mall-staging-credentials.txt
```

首次登录必须改密；确认新密码可用后删除该文件：

```bash
shred -u /root/super-mall-staging-credentials.txt
```

## 手动回滚

列出已验证 release：

```bash
find /opt/super-mall/releases -mindepth 1 -maxdepth 1 -type d -printf '%f\n'
find /var/www/super-mall/releases -mindepth 1 -maxdepth 1 -type d -printf '%f\n'
```

将两个 `current` 原子切换到同一提交并重启：

```bash
sha=<verified-sha>
ln -s "/opt/super-mall/releases/${sha}" /opt/super-mall/current.next
mv -Tf /opt/super-mall/current.next /opt/super-mall/current
ln -s "/var/www/super-mall/releases/${sha}" /var/www/super-mall/current.next
mv -Tf /var/www/super-mall/current.next /var/www/super-mall/current
systemctl restart super-mall-staging
curl -fsS http://127.0.0.1:18080/actuator/health
```

数据库迁移只能向前新增 Flyway 版本。若新版本包含不可逆迁移，回滚应用前必须单独评估数据库兼容性。

## 日志

```bash
journalctl -u super-mall-staging -n 150 --no-pager
journalctl -u mysqld -n 100 --no-pager
tail -100 /var/log/nginx/error.log
journalctl --disk-usage
df -h /
```

磁盘保护策略：

- systemd journal 总量不超过 `512M`，最多保留 14 天，并始终为磁盘保留 `5G`；
- Nginx 每小时检查轮转，单个日志达到 `100M` 即轮转，最多保留 10 份；
- MySQL 错误日志达到 `50M` 即轮转，最多保留 7 份；
- core dump 总量不超过 `256M`；
- 预发布关闭 MySQL binlog；正式生产环境必须改用独立的备份与 binlog 策略；
- 后端只保留当前 release 和最近两个可回滚 release。
