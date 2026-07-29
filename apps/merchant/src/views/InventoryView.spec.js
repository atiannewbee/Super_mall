import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import InventoryView from './InventoryView.vue'
import { api } from '../services/api'

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ replace: vi.fn() }),
}))

vi.mock('../services/api', () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
  errorMessage: (_, fallback) => fallback,
}))

describe('InventoryView', () => {
  beforeEach(() => {
    api.get.mockImplementation((path) => Promise.resolve(path === '/api/categories'
      ? [{ id: 1, name: '手机通讯' }]
      : { items: [], totalElements: 0, totalPages: 0 }))
    api.post.mockResolvedValue(null)
  })

  it('creates a product with one default SKU', async () => {
    const wrapper = mount(InventoryView, { global: { stubs: { Teleport: true } } })
    await flushPromises()
    expect(api.get).toHaveBeenCalledWith('/api/categories', { auth: false })
    await wrapper.get('button.primary-action').trigger('click')

    const values = {
      'input[required][maxlength="160"]': '测试商品',
      'input[type="url"]': 'https://images.example.com/test.jpg',
      'input[pattern]': 'test-sku-1',
      'input[required][maxlength="255"]': '标准款',
      'input[type="number"][step="0.01"]': '99.00',
      'input[type="number"][step="1"]': '6',
    }
    for (const [selector, value] of Object.entries(values)) {
      await wrapper.get(selector).setValue(value)
    }
    await wrapper.get('.product-modal').trigger('submit')
    await flushPromises()

    expect(api.post).toHaveBeenCalledWith('/api/merchant/products', expect.objectContaining({
      name: '测试商品',
      skuCode: 'test-sku-1',
      availableQuantity: 6,
    }))
  })
})
