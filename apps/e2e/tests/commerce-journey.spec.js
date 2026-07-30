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

test('消费者下单支付后由商家履约，消费者确认收货并申请售后', async ({ browser, request }) => {
  const merchantCredentials = requireMerchantCredentials(runtime)
  const run = createRunData()

  const catalogResponse = await request.get(`${runtime.storefrontUrl}/api/products?size=100&sort=recommended`)
  expect(catalogResponse.ok()).toBeTruthy()
  const catalog = await catalogResponse.json()
  const product = (catalog.items || catalog.content || [])
    .find((item) => item.skus?.some((sku) => sku.stock > 0))
  expect(product, '预发布目录需要至少一个可售 SKU').toBeTruthy()

  const customerContext = await browser.newContext()
  const customerPage = await customerContext.newPage()
  const customerErrors = collectBrowserErrors(customerPage)

  await test.step('注册消费者并新增级联地址', async () => {
    await customerPage.goto(`${runtime.storefrontUrl}/login`)
    await customerPage.getByRole('tab', { name: '注册账户' }).click()
    await customerPage.getByLabel('昵称').fill(run.customerName)
    await customerPage.getByLabel(/邮箱（与手机号至少填一项）/).fill(run.customerEmail)
    await customerPage.getByLabel(/手机号（选填）/).fill(run.customerPhone)
    await customerPage.locator('input[autocomplete="new-password"]').fill(run.customerPassword)

    const registerResponse = waitForApi(customerPage, 'POST', '/api/auth/register')
    await customerPage.getByRole('button', { name: '注册并登录' }).click()
    await expectApiOk(registerResponse)
    await customerPage.waitForURL(`${runtime.storefrontUrl}/`)
    await expect(customerPage.getByRole('link', { name: '进入用户中心' })).toContainText(run.customerName)

    await customerPage.goto(`${runtime.storefrontUrl}/account/addresses`)
    await customerPage.getByRole('button', { name: /新增地址/ }).first().click()
    const dialog = customerPage.getByRole('dialog', { name: '新增地址' })
    await dialog.getByLabel('收货人').fill(run.customerName)
    await dialog.getByLabel('手机号').fill(run.customerPhone)
    await dialog.getByLabel('省份').selectOption('北京市')
    await expect(dialog.getByLabel('城市')).toHaveValue('北京市')
    await expect(dialog.getByLabel('区县')).toContainText('东城区')
    await dialog.getByLabel('区县').selectOption('东城区')
    await dialog.getByLabel('详细地址').fill(`东华门街道 E2E 测试楼 ${run.suffix}`)
    await dialog.getByLabel('设为默认收货地址').check()

    const addressResponse = waitForApi(customerPage, 'POST', '/api/me/addresses')
    await dialog.getByRole('button', { name: '保存地址' }).click()
    await expectApiOk(addressResponse)
    await expect(customerPage.getByText(`北京市 北京市 东城区`)).toBeVisible()
  })

  let order
  await test.step('搜索、加购、下单并完成模拟支付宝支付', async () => {
    await customerPage.goto(runtime.storefrontUrl)
    await customerPage.getByRole('searchbox', { name: '搜索商品' }).fill(product.name)
    await customerPage.getByRole('button', { name: 'SEARCH' }).click()
    await expect.poll(() => new URL(customerPage.url()).pathname).toBe('/search')
    await expect.poll(() => new URL(customerPage.url()).searchParams.get('q')).toBe(product.name)

    const productCard = customerPage.locator('.listing-results .product-card').filter({ hasText: product.name })
    await expect(productCard).toBeVisible()
    await productCard.click()
    await expect(customerPage).toHaveURL(`${runtime.storefrontUrl}/product/${product.slug}`)

    const cartResponse = waitForApi(customerPage, 'POST', '/api/cart/items')
    await customerPage.getByRole('button', { name: '加入购物车', exact: true }).click()
    await expectApiOk(cartResponse)
    await customerPage.goto(`${runtime.storefrontUrl}/cart`)
    await expect(customerPage.getByRole('heading', { name: product.name })).toBeVisible()
    await customerPage.getByRole('button', { name: /去结算/ }).click()
    await expect(customerPage.getByRole('heading', { name: '确认订单' })).toBeVisible()
    await customerPage.getByLabel(/我已核对商品、地址与配送信息/).check()

    const orderResponsePromise = waitForApi(customerPage, 'POST', '/api/orders')
    await customerPage.getByRole('button', { name: /提交订单并去支付/ }).click()
    const orderResponse = await expectApiOk(orderResponsePromise)
    order = await orderResponse.json()
    expect(order.orderNo).toBeTruthy()

    await customerPage.waitForURL(new RegExp('/checkout/result\\?'))
    await expect(customerPage.getByRole('heading', { name: '选择模拟渠道' })).toBeVisible()
    await customerPage.getByLabel(/支付宝.*模拟支付/).check()
    const paymentResponse = waitForApi(
      customerPage,
      'POST',
      `/api/orders/${order.orderNo}/payments/sandbox`,
    )
    await customerPage.getByRole('button', { name: /确认模拟支付/ }).click()
    await expectApiOk(paymentResponse)
    await expect(customerPage.getByRole('heading', { name: '支付成功' })).toBeVisible()
  })

  const merchantContext = await browser.newContext()
  const merchantPage = await merchantContext.newPage()
  const merchantErrors = collectBrowserErrors(merchantPage)

  await test.step('商家检索订单、拣货并填写物流发货', async () => {
    await merchantPage.goto(`${runtime.merchantUrl}/login`)
    await merchantPage.getByLabel('商家邮箱').fill(merchantCredentials.email)
    await merchantPage.locator('input[autocomplete="current-password"]').fill(merchantCredentials.password)
    const loginResponse = waitForApi(merchantPage, 'POST', '/api/merchant/auth/login')
    await merchantPage.getByRole('button', { name: /进入运营中心/ }).click()
    await expectApiOk(loginResponse)
    await merchantPage.waitForURL(`${runtime.merchantUrl}/`)

    await merchantPage.goto(`${runtime.merchantUrl}/orders/${order.orderNo}`)
    await expect(merchantPage.getByRole('heading', { name: order.orderNo })).toBeVisible()

    const pickingResponse = waitForApi(
      merchantPage,
      'POST',
      `/api/merchant/orders/${order.orderNo}/picking`,
    )
    await merchantPage.getByRole('button', { name: /确认开始拣货/ }).click()
    await expectApiOk(pickingResponse)
    await expect(merchantPage.getByText('已进入拣货流程')).toBeVisible()

    await merchantPage.getByRole('button', { name: /填写物流并发货/ }).click()
    await merchantPage.getByLabel('物流公司代码').fill('SF')
    await merchantPage.getByLabel('物流公司名称').fill('顺丰速运')
    await merchantPage.getByLabel('物流单号').fill(run.trackingNo)
    const shipResponse = waitForApi(
      merchantPage,
      'POST',
      `/api/merchant/orders/${order.orderNo}/ship`,
    )
    await merchantPage.getByRole('button', { name: '确认发货' }).click()
    await expectApiOk(shipResponse)
    await expect(merchantPage.getByText('发货成功，消费者端已经可以查看物流信息')).toBeVisible()
    await expect(merchantPage.getByText(run.trackingNo)).toBeVisible()
  })

  await test.step('消费者确认收货并提交售后', async () => {
    await customerPage.goto(`${runtime.storefrontUrl}/account/orders/${order.orderNo}`)
    await expect(customerPage.getByText(run.trackingNo)).toBeVisible()
    const receiptResponse = waitForApi(
      customerPage,
      'POST',
      `/api/orders/${order.orderNo}/confirm-receipt`,
    )
    await customerPage.getByRole('button', { name: '确认收货' }).click()
    await expectApiOk(receiptResponse)
    await expect(customerPage.getByText('已确认收货，感谢你的购买')).toBeVisible()

    await customerPage.getByRole('link', { name: '申请售后' }).click()
    await expect(customerPage.getByRole('heading', { name: '申请售后' })).toBeVisible()
    await customerPage.getByLabel('问题说明').fill(`E2E 自动验收售后 ${run.suffix}`)
    await customerPage.getByLabel('我确认申请信息真实有效').check()
    const afterSaleResponse = waitForApi(customerPage, 'POST', '/api/after-sales')
    await customerPage.getByRole('button', { name: '提交售后申请' }).click()
    await expectApiOk(afterSaleResponse)
    await customerPage.waitForURL(`${runtime.storefrontUrl}/account/after-sales`)
    await expect(customerPage.getByText(`订单 ${order.orderNo}`)).toBeVisible()
  })

  customerErrors.assertNone()
  merchantErrors.assertNone()
  await customerContext.close()
  await merchantContext.close()
})
