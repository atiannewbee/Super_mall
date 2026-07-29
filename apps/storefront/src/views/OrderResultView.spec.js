import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import OrderResultView from './OrderResultView.vue'

const paymentMocks = vi.hoisted(() => ({
  route: {
    query: {
      orderNo: 'SM202607230001',
      status: 'pay',
      payment: 'wechat',
    },
  },
  order: {
    orderNo: 'SM202607230001',
    status: 'pending-payment',
    paymentStatus: 'unpaid',
    paymentMethod: '微信支付',
    total: 4299,
    address: { city: '深圳市', district: '南山区', detail: '科技路 8 号' },
  },
  loadOrder: vi.fn(),
  startOrderPayment: vi.fn(),
  loadPayment: vi.fn(),
  markOrderPaid: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => paymentMocks.route,
}))

vi.mock('../components/CommerceShell.vue', () => ({
  default: { template: '<div><slot /></div>' },
}))

vi.mock('../composables/useCommerce', () => ({
  useCommerce: () => ({
    getOrder: () => paymentMocks.order,
    loadOrder: paymentMocks.loadOrder,
    startOrderPayment: paymentMocks.startOrderPayment,
    loadPayment: paymentMocks.loadPayment,
    markOrderPaid: paymentMocks.markOrderPaid,
  }),
}))

describe('OrderResultView mock payment', () => {
  beforeEach(() => {
    paymentMocks.loadOrder.mockReset()
    paymentMocks.startOrderPayment.mockReset()
    paymentMocks.loadPayment.mockReset()
    paymentMocks.markOrderPaid.mockReset().mockResolvedValue({
      ...paymentMocks.order,
      status: 'processing',
      paymentStatus: 'paid',
    })
  })

  it('shows an explicit mock payment choice and confirms without launching Alipay', async () => {
    const wrapper = mount(OrderResultView, {
      global: {
        stubs: {
          CommerceShell: { template: '<div><slot /></div>' },
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('选择模拟渠道')
    expect(wrapper.text()).toContain('不会真实扣款')
    expect(wrapper.find('input[value="wechat"]').element.checked).toBe(true)
    expect(paymentMocks.startOrderPayment).not.toHaveBeenCalled()

    await wrapper.get('.mock-payment-panel button').trigger('click')
    await flushPromises()

    expect(paymentMocks.markOrderPaid).toHaveBeenCalledWith('SM202607230001', 'wechat')
    expect(wrapper.text()).toContain('支付成功')
    expect(wrapper.text()).toContain('订单已进入备货流程')
  })
})
