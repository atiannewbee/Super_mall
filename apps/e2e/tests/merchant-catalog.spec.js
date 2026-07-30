import { expect, test } from '@playwright/test'
import {
  collectBrowserErrors,
  expectApiOk,
  waitForApi,
} from '../support/browser.js'
import {
  createRunData,
  readRuntimeConfig,
  requireMerchantCredentials,
} from '../support/runtime.js'

const runtime = readRuntimeConfig()

// Trace 会记录网络请求体；该流程包含商家密码，因此只保留失败截图和录像。
test.use({ trace: 'off' })

test('商家可以新增、修改、搜索并软删除测试商品', async ({ page }) => {
  const merchantCredentials = requireMerchantCredentials(runtime)
  const run = createRunData()
  const browserErrors = collectBrowserErrors(page)

  await page.goto(`${runtime.merchantUrl}/login`)
  await page.getByLabel('商家邮箱').fill(merchantCredentials.email)
  await page.locator('input[autocomplete="current-password"]').fill(merchantCredentials.password)
  const loginResponse = waitForApi(page, 'POST', '/api/merchant/auth/login')
  await page.getByRole('button', { name: /进入运营中心/ }).click()
  await expectApiOk(loginResponse)
  await page.waitForURL(`${runtime.merchantUrl}/`)

  await page.goto(`${runtime.merchantUrl}/inventory`)
  await expect(page.getByRole('heading', { name: '商品与库存', level: 2 })).toBeVisible()
  await page.getByRole('button', { name: /新增商品/ }).click()

  const editor = page.locator('.product-modal')
  await editor.getByLabel('商品名称').fill(run.productName)
  await editor.getByLabel('封面图片 URL').fill(`${runtime.storefrontUrl}/brand/super-mall-logo.png`)
  await editor.getByLabel('一句话简介').fill('E2E 自动验收商品')
  await editor.getByLabel('商品说明').fill(`由 Playwright 创建，运行标识 ${run.suffix}`)
  await editor.getByLabel('SKU 编码').fill(run.skuCode)
  await editor.getByLabel('规格名称').fill('自动验收款')
  await editor.getByLabel('售价').fill('199.00')
  await editor.getByLabel('原价（可选）').fill('299.00')
  await editor.getByLabel('可售库存').fill('8')

  const createResponse = waitForApi(page, 'POST', '/api/merchant/products')
  await editor.getByRole('button', { name: /保存商品/ }).click()
  await expectApiOk(createResponse)

  const search = page.getByPlaceholder('商品名 / SKU 编码 / 规格')
  await search.fill(run.skuCode)
  await page.getByRole('button', { name: '搜索' }).click()
  const createdRow = page.locator('tbody tr').filter({ hasText: run.skuCode })
  await expect(createdRow).toContainText(run.productName)
  await expect(createdRow).toContainText('8')

  await createdRow.getByRole('button', { name: '编辑' }).click()
  const updatedName = `${run.productName}-已更新`
  await editor.getByLabel('商品名称').fill(updatedName)
  await editor.getByLabel('可售库存').fill('12')
  const updateResponse = waitForApi(
    page,
    'PUT',
    /^\/api\/merchant\/products\/\d+\/skus\/\d+$/,
  )
  await editor.getByRole('button', { name: /保存商品/ }).click()
  await expectApiOk(updateResponse)
  await expect(page.locator('tbody tr').filter({ hasText: run.skuCode })).toContainText(updatedName)
  await expect(page.locator('tbody tr').filter({ hasText: run.skuCode })).toContainText('12')

  const updatedRow = page.locator('tbody tr').filter({ hasText: run.skuCode })
  page.once('dialog', (dialog) => dialog.accept())
  const deleteResponse = waitForApi(
    page,
    'DELETE',
    /^\/api\/merchant\/products\/\d+$/,
  )
  await updatedRow.getByRole('button', { name: '删除' }).click()
  await expectApiOk(deleteResponse)
  await expect(page.locator('tbody tr').filter({ hasText: run.skuCode })).toHaveCount(0)

  browserErrors.assertNone()
})
