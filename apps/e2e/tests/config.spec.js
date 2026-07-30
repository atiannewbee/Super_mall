import { expect, test } from '@playwright/test'
import { readRuntimeConfig, requireMerchantCredentials } from '../support/runtime.js'

test('预发布地址默认使用 HTTPS', () => {
  const config = readRuntimeConfig({})

  expect(config.storefrontUrl).toBe('https://mall-staging.350233.xyz')
  expect(config.merchantUrl).toBe('https://merchant-staging.350233.xyz')
})

test('只允许 HTTPS 或本机 HTTP 地址', () => {
  expect(() => readRuntimeConfig({
    E2E_STOREFRONT_URL: 'http://example.com',
    E2E_MERCHANT_URL: 'https://merchant.example.com',
  })).toThrow(/HTTPS/)

  expect(readRuntimeConfig({
    E2E_STOREFRONT_URL: 'http://127.0.0.1:5173',
    E2E_MERCHANT_URL: 'http://localhost:5174',
  }).storefrontUrl).toBe('http://127.0.0.1:5173')
})

test('完整验收缺少商家凭据时明确失败', () => {
  expect(() => requireMerchantCredentials(readRuntimeConfig({})))
    .toThrow(/E2E_MERCHANT_EMAIL.*E2E_MERCHANT_PASSWORD/)
})
