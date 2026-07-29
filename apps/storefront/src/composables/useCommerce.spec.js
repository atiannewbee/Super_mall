import { describe, expect, it, vi } from 'vitest'
import { createCommerceStore } from './useCommerce'

const address = { id: 1, name: '小超', phone: '13900000000', province: '广东省', city: '深圳市', district: '南山区', detail: '科技路 8 号', postalCode: '', tag: '家', isDefault: true }
const orderItem = { id: 11, productId: 101, skuId: 1001, productSlug: 'aether-x1-pro', productName: 'Aether X1 Pro', skuCode: 'aether-x1-256-black', skuLabel: '256GB', image: '/phone.jpg', unitPrice: 4299, quantity: 1, lineAmount: 4299, afterSaleQuantity: 0 }

function orderResponse(overrides = {}) {
  return {
    id: 9, orderNo: 'SM202607220001', status: 'pending-payment', paymentStatus: 'unpaid', fulfillmentStatus: 'unfulfilled', afterSaleStatus: 'none', itemCount: 1,
    subtotal: 4299, deliveryFee: 0, discount: 0, total: 4299, paidAmount: 0, currency: 'CNY', paymentMethod: 'WECHAT_PAY', buyerNote: '', address,
    items: [orderItem], shipment: null, timeline: [{ type: 'order', fromStatus: null, toStatus: 'pending-payment', note: '订单已创建', operatorType: 'user', createdAt: '2026-07-22T10:00:00' }], createdAt: '2026-07-22T10:00:00',
    ...overrides,
  }
}

function afterSaleResponse() {
  return {
    id: 3, afterSaleNo: 'AS20260722001', orderNo: 'SM202607220001', type: 'return-refund', status: 'reviewing', reasonCode: 'OTHER', reasonDescription: '质量问题', requestedAmount: 4299, refundedAmount: 0,
    customerNote: '', adminNote: '', returnCarrier: null, returnTrackingNo: null,
    items: [{ id: 5, orderItemId: 11, productName: 'Aether X1 Pro', skuLabel: '256GB', image: '/phone.jpg', quantity: 1, requestedAmount: 4299 }],
    events: [{ fromStatus: 'requested', toStatus: 'reviewing', description: '等待平台审核', operatorType: 'system', createdAt: '2026-07-22T10:05:00' }], createdAt: '2026-07-22T10:05:00',
  }
}

describe('createCommerceStore', () => {
  it('hydrates account data from the five protected endpoints', async () => {
    const client = { get: vi.fn((path) => Promise.resolve({
      '/api/me/profile': { id: 7, name: '小超', email: 'user@example.com', phone: '', gender: 'UNSPECIFIED' },
      '/api/me/addresses': [address],
      '/api/me/favorites?size=100': { items: [] },
      '/api/orders?size=50': { items: [orderResponse()] },
      '/api/after-sales?size=50': { items: [] },
    }[path])), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() }
    const store = createCommerceStore(client)

    await store.refreshAll()

    expect(store.profile.value).toMatchObject({ nickname: '小超', gender: '保密' })
    expect(store.defaultAddress.value.id).toBe(1)
    expect(store.orders.value[0]).toMatchObject({ status: 'pending-payment', paymentMethod: '微信支付' })
    expect(store.orders.value[0].items[0]).toMatchObject({ skuId: 'aether-x1-256-black', price: 4299 })
  })

  it('creates an order and starts an Alipay payment without trusting a client amount', async () => {
    const pending = orderResponse({ paymentMethod: 'alipay' })
    const launch = { paymentNo: 'PAY202607230001', status: 'pending', action: 'redirect', launchUrl: '/api/payments/alipay/PAY202607230001/launch' }
    const client = {
      post: vi.fn((path) => Promise.resolve(path === '/api/orders' ? pending : launch)),
      get: vi.fn().mockResolvedValue({ paymentNo: launch.paymentNo, status: 'pending', amount: 4299 }),
    }
    const store = createCommerceStore(client)

    const order = await store.createOrder({ address, paymentMethod: 'alipay', buyerNote: '工作日送达' })
    expect(order.total).toBe(4299)
    expect(client.post.mock.calls[0][1]).toEqual({ addressId: 1, buyerNote: '工作日送达', paymentChannel: 'ALIPAY', invoiceRequired: false })

    expect(await store.startOrderPayment(order.orderNo, 'alipay')).toEqual(launch)
    expect(client.post).toHaveBeenLastCalledWith(`/api/orders/${order.orderNo}/payments`, { channel: 'ALIPAY' })
    expect(await store.loadPayment(launch.paymentNo)).toMatchObject({ status: 'pending', amount: 4299 })
  })

  it('completes a mock WeChat payment and refreshes the paid order', async () => {
    const paid = orderResponse({
      status: 'processing',
      paymentStatus: 'paid',
      paidAmount: 4299,
      paymentMethod: 'WECHAT_PAY',
    })
    const client = {
      post: vi.fn().mockResolvedValue({
        paymentNo: 'PAY202607230002',
        orderNo: paid.orderNo,
        channel: 'WECHAT_PAY',
        status: 'success',
        amount: 4299,
      }),
      get: vi.fn().mockResolvedValue(paid),
    }
    const store = createCommerceStore(client)

    const order = await store.markOrderPaid(paid.orderNo, 'wechat')

    expect(client.post).toHaveBeenCalledWith(
      `/api/orders/${paid.orderNo}/payments/sandbox`,
      { channel: 'WECHAT_PAY' },
    )
    expect(client.get).toHaveBeenCalledWith(`/api/orders/${paid.orderNo}`)
    expect(order).toMatchObject({
      status: 'processing',
      paymentStatus: 'paid',
      paymentMethod: '微信支付',
      paidAmount: 4299,
    })
    expect(store.getOrder(paid.orderNo)).toEqual(order)
  })

  it('creates and normalizes a traceable after-sale request', async () => {
    const client = { post: vi.fn().mockResolvedValue(afterSaleResponse()) }
    const store = createCommerceStore(client)

    const request = await store.createAfterSale({ orderNo: 'SM202607220001', item: { orderItemId: 11 }, type: '退货退款', reason: '质量问题', quantity: 1 })

    expect(client.post).toHaveBeenCalledWith('/api/after-sales', expect.objectContaining({ type: 'RETURN_REFUND', items: [{ orderItemId: 11, quantity: 1 }] }))
    expect(request).toMatchObject({ id: 'AS20260722001', type: '退货退款', status: 'reviewing', amount: 4299 })
    expect(request.progress).toEqual(['等待平台审核'])
  })
})
