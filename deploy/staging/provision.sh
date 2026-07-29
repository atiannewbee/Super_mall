#!/usr/bin/env bash
set -euo pipefail

if [[ ${EUID} -ne 0 ]]; then
  echo "必须使用 root 执行预发布环境初始化。" >&2
  exit 1
fi

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
APP_USER=super-mall
APP_GROUP=super-mall
ENV_DIR=/etc/super-mall
ENV_FILE=${ENV_DIR}/staging.env
CREDENTIAL_FILE=/root/super-mall-staging-credentials.txt

# 服务器的宝塔基线会过滤 mysql 包，且预装的 MariaDB 客户端与 MySQL 客户端冲突。
# 模拟事务已确认只替换客户端，不会移除现有数据库服务。
dnf install -y --disableexcludes=all --allowerasing \
  java-17-konajdk-headless \
  mysql-server
command -v curl >/dev/null 2>&1 || dnf install -y curl

if ! id "${APP_USER}" >/dev/null 2>&1; then
  useradd --system --home-dir /var/lib/super-mall --create-home --shell /sbin/nologin "${APP_USER}"
fi

install -d -o "${APP_USER}" -g "${APP_GROUP}" -m 0750 /var/lib/super-mall
install -d -o root -g root -m 0755 /opt/super-mall/releases
install -d -o root -g nginx -m 0750 /var/www/super-mall/releases
install -d -o root -g "${APP_GROUP}" -m 0750 "${ENV_DIR}"
install -d -o root -g root -m 0755 /etc/nginx/conf.d

install -o root -g root -m 0644 \
  "${SCRIPT_DIR}/mysql-super-mall-staging.cnf" \
  /etc/my.cnf.d/super-mall-staging.cnf

systemctl enable --now mysqld

if [[ ! -s "${ENV_FILE}" ]]; then
  DB_PASSWORD=$(openssl rand -hex 24)
  JWT_SECRET=$(openssl rand -hex 48)
  MERCHANT_JWT_SECRET=$(openssl rand -hex 48)
  MERCHANT_PASSWORD="Stg-A9!$(openssl rand -hex 16)"
  MERCHANT_EMAIL=merchant-owner@350233.xyz

  mysql --protocol=socket -uroot <<SQL
CREATE DATABASE IF NOT EXISTS super_mall_staging
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'super_mall_staging'@'127.0.0.1'
  IDENTIFIED BY '${DB_PASSWORD}';
ALTER USER 'super_mall_staging'@'127.0.0.1'
  IDENTIFIED BY '${DB_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES
  ON super_mall_staging.* TO 'super_mall_staging'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL

  TMP_ENV=$(mktemp "${ENV_DIR}/staging.env.XXXXXX")
  cat >"${TMP_ENV}" <<EOF
DB_URL=jdbc:mysql://127.0.0.1:3306/super_mall_staging?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf-8
DB_USERNAME=super_mall_staging
DB_PASSWORD=${DB_PASSWORD}
JWT_SECRET=${JWT_SECRET}
JWT_ISSUER=super-mall-staging-backend
JWT_AUDIENCE=super-mall-staging-api
JWT_ACCESS_TOKEN_TTL=PT2H
MERCHANT_JWT_SECRET=${MERCHANT_JWT_SECRET}
MERCHANT_JWT_ISSUER=super-mall-staging-merchant-backend
MERCHANT_JWT_AUDIENCE=super-mall-staging-merchant-api
MERCHANT_JWT_ACCESS_TOKEN_TTL=PT2H
MERCHANT_BOOTSTRAP_EMAIL=${MERCHANT_EMAIL}
MERCHANT_BOOTSTRAP_PASSWORD=${MERCHANT_PASSWORD}
MERCHANT_BOOTSTRAP_NAME=预发布商家主管
PAYMENT_SANDBOX_ENABLED=true
ALIPAY_ENABLED=false
CORS_ALLOWED_ORIGINS=https://mall-staging.350233.xyz,https://merchant-staging.350233.xyz
ORDER_PENDING_PAYMENT_TTL=PT30M
ORDER_EXPIRATION_SCAN_INTERVAL=PT1M
SERVER_ADDRESS=127.0.0.1
SERVER_PORT=18080
SERVER_FORWARD_HEADERS_STRATEGY=framework
SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=1
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5
JPA_SHOW_SQL=false
EOF
  install -o root -g "${APP_GROUP}" -m 0640 "${TMP_ENV}" "${ENV_FILE}"
  rm -f "${TMP_ENV}"

  TMP_CREDENTIALS=$(mktemp /root/super-mall-staging-credentials.XXXXXX)
  cat >"${TMP_CREDENTIALS}" <<EOF
Super Mall 预发布商家账号
地址：https://merchant-staging.350233.xyz
账号：${MERCHANT_EMAIL}
初始密码：${MERCHANT_PASSWORD}

首次登录后必须立即修改密码，然后删除本文件。
EOF
  install -o root -g root -m 0600 "${TMP_CREDENTIALS}" "${CREDENTIAL_FILE}"
  rm -f "${TMP_CREDENTIALS}"
fi

# MySQL 8 默认的 caching_sha2_password 在无 TLS 的本机连接上需要取回服务端公钥。
# 数据库只监听 127.0.0.1，因此允许本机 JDBC 驱动取回公钥不会扩大公网攻击面。
if grep -q '^DB_URL=.*allowPublicKeyRetrieval=false' "${ENV_FILE}"; then
  TMP_ENV=$(mktemp "${ENV_DIR}/staging.env.XXXXXX")
  sed 's/allowPublicKeyRetrieval=false/allowPublicKeyRetrieval=true/' "${ENV_FILE}" >"${TMP_ENV}"
  install -o root -g "${APP_GROUP}" -m 0640 "${TMP_ENV}" "${ENV_FILE}"
  rm -f "${TMP_ENV}"
fi

install -o root -g root -m 0644 \
  "${SCRIPT_DIR}/super-mall-staging.service" \
  /etc/systemd/system/super-mall-staging.service
install -o root -g root -m 0644 \
  "${SCRIPT_DIR}/nginx-super-mall-staging.conf" \
  /etc/nginx/conf.d/super-mall-staging.conf

if ! grep -Eq '^[[:space:]]*include[[:space:]]+/etc/nginx/conf\.d/\*\.conf;' /etc/nginx/nginx.conf; then
  cp -a /etc/nginx/nginx.conf "/etc/nginx/nginx.conf.bak-$(date +%Y%m%d-%H%M%S)"
  sed -i '$i\    include /etc/nginx/conf.d/*.conf;' /etc/nginx/nginx.conf
fi

restorecon -RF /var/www/super-mall /opt/super-mall /etc/super-mall 2>/dev/null || true
systemctl daemon-reload
systemctl enable super-mall-staging.service
nginx -t
systemctl reload nginx

echo "预发布基础设施初始化完成。密钥未输出到终端。"
echo "下一步：配置 Cloudflare ingress 和 DNS，然后执行 deploy-release.sh。"
