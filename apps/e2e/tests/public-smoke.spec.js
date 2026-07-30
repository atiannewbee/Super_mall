import { expect, test } from '@playwright/test'
import { collectBrowserErrors, expectHealthy } from '../support/browser.js'
import { readRuntimeConfig } from '../support/runtime.js'

const runtime = readRuntimeConfig()

test('消费者公开页面、商品目录与健康接口可访问', async ({ page, request }) => {
  const browserErrors = collectBrowserErrors(page)
  await expectHealthy(request, runtime.storefrontUrl)

  const homeResponse = await page.goto(runtime.storefrontUrl)
  expect(homeResponse?.ok()).toBeTruthy()
  await expect(page.getByRole('link', { name: /SUPER MALL 首页/ }).first()).toBeVisible()

  const catalogResponse = await request.get(`${runtime.storefrontUrl}/api/products?size=1`)
  expect(catalogResponse.ok()).toBeTruthy()
  const catalog = await catalogResponse.json()
  const product = catalog.items?.[0] || catalog.content?.[0]
  expect(product?.slug).toBeTruthy()

  await page.goto(`${runtime.storefrontUrl}/search?q=${encodeURIComponent(product.name)}`)
  await expect(page.getByRole('heading', { name: new RegExp(product.name) })).toBeVisible()
  await expect(page.getByText(product.name).first()).toBeVisible()

  await page.goto(`${runtime.storefrontUrl}/product/${product.slug}`)
  await expect(page.getByRole('heading', { name: product.name })).toBeVisible()
  await expect(page.getByRole('button', { name: /加入购物车/ })).toBeVisible()
  browserErrors.assertNone()
})

test('商家登录页与健康接口可访问', async ({ page, request }) => {
  const browserErrors = collectBrowserErrors(page)
  await expectHealthy(request, runtime.merchantUrl)

  const response = await page.goto(`${runtime.merchantUrl}/login`)
  expect(response?.ok()).toBeTruthy()
  await expect(page.getByRole('heading', { name: '登录运营中心' })).toBeVisible()
  await expect(page.getByLabel('商家邮箱')).toBeVisible()
  await expect(page.getByLabel('密码')).toBeVisible()
  browserErrors.assertNone()
})
