import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import CartDrawer from './CartDrawer.vue'

function mountDrawer(open = false) {
  return mount(CartDrawer, {
    attachTo: document.body,
    props: {
      open,
      items: [],
      itemCount: 0,
      subtotal: 0,
      deliveryFee: 0,
      total: 0,
      shippingGap: 99,
    },
  })
}

describe('CartDrawer', () => {
  it('keeps the drawer mounted and only switches its visible state', async () => {
    const wrapper = mountDrawer()
    const layer = document.body.querySelector('.drawer-layer')
    const drawer = document.body.querySelector('.cart-drawer')

    expect(layer).not.toBeNull()
    expect(layer.classList.contains('is-open')).toBe(false)
    expect(layer.getAttribute('aria-hidden')).toBe('true')

    await wrapper.setProps({ open: true })

    expect(document.body.querySelector('.cart-drawer')).toBe(drawer)
    expect(layer.classList.contains('is-open')).toBe(true)
    expect(layer.getAttribute('aria-hidden')).toBe('false')

    await wrapper.setProps({ open: false })

    expect(document.body.querySelector('.cart-drawer')).toBe(drawer)
    expect(layer.classList.contains('is-open')).toBe(false)
    expect(layer.getAttribute('aria-hidden')).toBe('true')

    wrapper.unmount()
  })
})
