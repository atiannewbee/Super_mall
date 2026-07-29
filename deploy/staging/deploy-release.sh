#!/usr/bin/env bash
set -euo pipefail

if [[ ${EUID} -ne 0 ]]; then
  echo "必须使用 root 发布预发布版本。" >&2
  exit 1
fi

if [[ $# -ne 2 ]]; then
  echo "用法：$0 <release.tar.gz> <git-commit-sha>" >&2
  exit 1
fi

ARCHIVE=$1
RELEASE_ID=$2
APP_RELEASE=/opt/super-mall/releases/${RELEASE_ID}
WEB_RELEASE=/var/www/super-mall/releases/${RELEASE_ID}
APP_CURRENT=/opt/super-mall/current
WEB_CURRENT=/var/www/super-mall/current
ENV_FILE=/etc/super-mall/staging.env
TMP_DIR=$(mktemp -d /tmp/super-mall-release.XXXXXX)

cleanup() {
  rm -rf "${TMP_DIR}"
}
trap cleanup EXIT

if [[ ! "${RELEASE_ID}" =~ ^[0-9a-f]{7,40}$ ]]; then
  echo "release id 必须是 Git commit SHA。" >&2
  exit 1
fi

tar -xzf "${ARCHIVE}" -C "${TMP_DIR}"
test -s "${TMP_DIR}/backend/super-mall.jar"
test -s "${TMP_DIR}/storefront/index.html"
test -s "${TMP_DIR}/merchant/index.html"

if [[ ! -d "${APP_RELEASE}" && ! -d "${WEB_RELEASE}" ]]; then
  install -d -o root -g super-mall -m 0750 "${APP_RELEASE}"
  install -o root -g super-mall -m 0640 \
    "${TMP_DIR}/backend/super-mall.jar" \
    "${APP_RELEASE}/super-mall.jar"

  install -d -o root -g nginx -m 0750 \
    "${WEB_RELEASE}/storefront" \
    "${WEB_RELEASE}/merchant"
  cp -a "${TMP_DIR}/storefront/." "${WEB_RELEASE}/storefront/"
  cp -a "${TMP_DIR}/merchant/." "${WEB_RELEASE}/merchant/"
  chown -R root:nginx "${WEB_RELEASE}"
  find "${WEB_RELEASE}" -type d -exec chmod 0750 {} +
  find "${WEB_RELEASE}" -type f -exec chmod 0640 {} +
  restorecon -RF "${WEB_RELEASE}" 2>/dev/null || true
elif [[ ! -s "${APP_RELEASE}/super-mall.jar" ||
        ! -s "${WEB_RELEASE}/storefront/index.html" ||
        ! -s "${WEB_RELEASE}/merchant/index.html" ]]; then
  echo "发现不完整的同名 release，拒绝覆盖。" >&2
  exit 1
fi

OLD_APP=$(readlink -f "${APP_CURRENT}" 2>/dev/null || true)
OLD_WEB=$(readlink -f "${WEB_CURRENT}" 2>/dev/null || true)

switch_link() {
  local target=$1
  local link=$2
  rm -f "${link}.next"
  ln -s "${target}" "${link}.next"
  mv -Tf "${link}.next" "${link}"
}

rollback() {
  if [[ -n "${OLD_APP}" && -n "${OLD_WEB}" ]]; then
    switch_link "${OLD_APP}" "${APP_CURRENT}"
    switch_link "${OLD_WEB}" "${WEB_CURRENT}"
    systemctl restart super-mall-staging.service || true
  else
    rm -f "${APP_CURRENT}" "${WEB_CURRENT}"
    systemctl stop super-mall-staging.service || true
  fi
}

switch_link "${APP_RELEASE}" "${APP_CURRENT}"
switch_link "${WEB_RELEASE}" "${WEB_CURRENT}"

if ! systemctl restart super-mall-staging.service ||
   ! curl --fail --silent --show-error \
      --retry 30 --retry-all-errors --retry-delay 2 \
      http://127.0.0.1:18080/actuator/health >/dev/null; then
  echo "新版本健康检查失败，正在回滚。" >&2
  rollback
  exit 1
fi

# 首次建号完成后，从长期环境文件移除明文初始密码，并重启清理进程环境。
if grep -q '^MERCHANT_BOOTSTRAP_PASSWORD=' "${ENV_FILE}"; then
  TMP_ENV=$(mktemp /etc/super-mall/staging.env.XXXXXX)
  grep -v '^MERCHANT_BOOTSTRAP_PASSWORD=' "${ENV_FILE}" >"${TMP_ENV}"
  install -o root -g super-mall -m 0640 "${TMP_ENV}" "${ENV_FILE}"
  rm -f "${TMP_ENV}"
  if ! systemctl restart super-mall-staging.service ||
     ! curl --fail --silent --show-error \
        --retry 30 --retry-all-errors --retry-delay 2 \
        http://127.0.0.1:18080/actuator/health >/dev/null; then
    echo "移除初始密码后的健康检查失败，正在回滚应用版本。" >&2
    rollback
    exit 1
  fi
fi

nginx -t
systemctl reload nginx
echo "Super Mall 预发布版本 ${RELEASE_ID} 已上线。"
