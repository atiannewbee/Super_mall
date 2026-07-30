const DEFAULT_STOREFRONT_URL = 'https://mall-staging.350233.xyz'
const DEFAULT_MERCHANT_URL = 'https://merchant-staging.350233.xyz'

function normalizeBaseUrl(value, label) {
  let url
  try {
    url = new URL(value)
  } catch {
    throw new Error(`${label} 必须是完整 URL`)
  }

  const local = ['localhost', '127.0.0.1', '::1'].includes(url.hostname)
  if (url.protocol !== 'https:' && !(local && url.protocol === 'http:')) {
    throw new Error(`${label} 必须使用 HTTPS；只有本机调试允许 HTTP`)
  }

  return url.toString().replace(/\/$/, '')
}

export function readRuntimeConfig(env = process.env) {
  return {
    storefrontUrl: normalizeBaseUrl(
      env.E2E_STOREFRONT_URL || DEFAULT_STOREFRONT_URL,
      'E2E_STOREFRONT_URL',
    ),
    merchantUrl: normalizeBaseUrl(
      env.E2E_MERCHANT_URL || DEFAULT_MERCHANT_URL,
      'E2E_MERCHANT_URL',
    ),
    merchantEmail: String(env.E2E_MERCHANT_EMAIL || '').trim(),
    merchantPassword: String(env.E2E_MERCHANT_PASSWORD || ''),
  }
}

export function requireMerchantCredentials(config = readRuntimeConfig()) {
  if (!config.merchantEmail || !config.merchantPassword) {
    throw new Error('完整验收需要设置 E2E_MERCHANT_EMAIL 和 E2E_MERCHANT_PASSWORD')
  }
  return {
    email: config.merchantEmail,
    password: config.merchantPassword,
  }
}

export function createRunData(now = Date.now()) {
  const suffix = `${now}-${Math.random().toString(36).slice(2, 8)}`
  return {
    suffix,
    customerName: `E2E用户-${suffix}`,
    customerEmail: `e2e-${suffix}@example.com`,
    customerPassword: `E2E-${suffix}-Aa1!`,
    customerPhone: `199${String(now).slice(-8)}`,
    productName: `E2E商品-${suffix}`,
    skuCode: `E2E-SKU-${suffix}`.replace(/[^A-Za-z0-9._-]/g, '-'),
    trackingNo: `E2E${String(now)}${Math.floor(Math.random() * 1000)}`,
  }
}
