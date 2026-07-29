import { describe, expect, it } from 'vitest'
import { createCartStore } from './useCart'

function memoryStorage(seed = {}) {
  const state = new Map(Object.entries(seed))
  return {
    getItem: (key) => state.get(key) ?? null,
    setItem: (key, value) => state.set(key, value),
    removeItem: (key) => state.delete(key),
  }
}

const product = {
  id: 1,
  name: 'Aether X1 手机',
  image: '/phone.jpg',
}

const sku = {
  id: 'phone-x1-256-black',
  label: '曜石黑 / 256GB',
  price: 4299,
  stock: 3,
}

describe('createCartStore', () => {
  it('merges the same SKU and respects available stock', async () => {
    const cart = createCartStore(memoryStorage())

    await cart.addItem(product, sku, 2)
    await cart.addItem(product, sku, 2)

    expect(cart.items.value).toHaveLength(1)
    expect(cart.items.value[0].quantity).toBe(3)
  })

  it('updates quantities, removes items, and calculates totals', async () => {
    const cart = createCartStore(memoryStorage())

    await cart.addItem(product, sku, 1)
    await cart.updateQuantity(sku.id, 2)

    expect(cart.subtotal.value).toBe(8598)
    expect(cart.deliveryFee.value).toBe(0)
    expect(cart.total.value).toBe(8598)

    await cart.removeItem(sku.id)
    expect(cart.items.value).toEqual([])
  })

  it('hydrates previously saved cart state', () => {
    const saved = [{ productId: 1, skuId: sku.id, name: product.name, image: product.image, skuLabel: sku.label, price: sku.price, stock: sku.stock, quantity: 2 }]
    const storage = memoryStorage({ 'super-mall-cart': JSON.stringify(saved) })

    const cart = createCartStore(storage)

    expect(cart.items.value).toEqual(saved)
    expect(cart.itemCount.value).toBe(2)
  })
})
