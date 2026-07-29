import { computed, ref } from 'vue'
import { api } from '../services/api'
import { hasValidSession } from './useAuth'

const STORAGE_KEY = 'super-mall-cart'

function safeLoad(storage) {
  if (!storage) return []
  try {
    const parsed = JSON.parse(storage.getItem(STORAGE_KEY) || '[]')
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function normalizeCart(response) {
  return (response?.items || []).map((item) => ({
    itemId: item.id,
    productId: item.productId,
    productSlug: item.productSlug,
    skuId: item.skuCode,
    databaseSkuId: item.skuId,
    name: item.productName,
    image: item.image,
    skuLabel: item.skuLabel,
    price: Number(item.price),
    stock: item.stock,
    quantity: item.quantity,
    selected: item.selected,
  }))
}

export function createCartStore(storage, client = api) {
  const items = ref(safeLoad(storage))
  const loading = ref(false)
  const error = ref('')
  const authenticated = () => hasValidSession(storage)
  const itemCount = computed(() => items.value.reduce((total, item) => total + item.quantity, 0))
  const subtotal = computed(() => items.value.reduce((total, item) => total + item.price * item.quantity, 0))
  const deliveryFee = computed(() => items.value.length === 0 || subtotal.value >= 99 ? 0 : 10)
  const total = computed(() => subtotal.value + deliveryFee.value)
  const shippingGap = computed(() => Math.max(0, 99 - subtotal.value))

  function persist() {
    storage?.setItem(STORAGE_KEY, JSON.stringify(items.value))
  }

  function setFromResponse(response) {
    items.value = normalizeCart(response)
    return items.value
  }

  async function refreshCart() {
    if (!authenticated()) return items.value
    loading.value = true
    try {
      return setFromResponse(await client.get('/api/cart'))
    } catch (cause) {
      error.value = cause.message || '购物车加载失败'
      throw cause
    } finally {
      loading.value = false
    }
  }

  async function addItem(product, sku, quantity = 1) {
    if (!sku || sku.stock <= 0) return false
    const requested = Math.max(1, Number(quantity) || 1)
    error.value = ''
    if (authenticated()) {
      try {
        setFromResponse(await client.post('/api/cart/items', { skuCode: sku.skuCode || sku.id, quantity: requested }))
        return true
      } catch (cause) {
        error.value = cause.message || '加入购物车失败'
        throw cause
      }
    }
    const existing = items.value.find((entry) => entry.skuId === sku.id)
    if (existing) existing.quantity = Math.min(existing.stock, existing.quantity + requested)
    else items.value.push({ productId: product.id, productSlug: product.slug, skuId: sku.id, name: product.name, image: product.image, skuLabel: sku.label, price: sku.price ?? product.price, stock: sku.stock, quantity: Math.min(sku.stock, requested), selected: true })
    persist()
    return true
  }

  async function updateQuantity(skuId, quantity) {
    const item = items.value.find((entry) => entry.skuId === skuId)
    if (!item) return
    const next = Number(quantity) || 0
    if (next <= 0) return removeItem(skuId)
    if (authenticated()) {
      setFromResponse(await client.patch(`/api/cart/items/${item.itemId}`, { quantity: next, selected: item.selected !== false }))
      return
    }
    item.quantity = Math.min(item.stock, next)
    persist()
  }

  async function removeItem(skuId) {
    const item = items.value.find((entry) => entry.skuId === skuId)
    if (authenticated() && item?.itemId) await client.delete(`/api/cart/items/${item.itemId}`)
    items.value = items.value.filter((entry) => entry.skuId !== skuId)
    persist()
  }

  async function clearCart() {
    if (authenticated()) await client.delete('/api/cart/items')
    items.value = []
    persist()
  }

  async function syncGuestCart() {
    if (!authenticated()) return
    const guestItems = safeLoad(storage).filter((item) => !item.itemId)
    for (const item of guestItems) {
      await client.post('/api/cart/items', { skuCode: item.skuId, quantity: item.quantity })
    }
    storage?.removeItem(STORAGE_KEY)
    await refreshCart()
  }

  function resetCart() {
    items.value = safeLoad(storage).filter((item) => !item.itemId)
  }

  return { items, itemCount, subtotal, deliveryFee, total, shippingGap, loading, error, addItem, updateQuantity, removeItem, clearCart, refreshCart, syncGuestCart, resetCart }
}

let browserStore

export function useCart() {
  if (!browserStore) browserStore = createCartStore(typeof window === 'undefined' ? undefined : window.localStorage)
  return browserStore
}
