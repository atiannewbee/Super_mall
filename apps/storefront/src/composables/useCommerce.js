import { computed, ref } from 'vue'
import { products } from '../data/catalog'
import { api } from '../services/api'
import { normalizeProduct } from './useCatalog'
import { useAuth } from './useAuth'

const genderFromApi = { MALE: '男', FEMALE: '女', UNSPECIFIED: '保密' }
const genderToApi = { 男: 'MALE', 女: 'FEMALE', 保密: 'UNSPECIFIED' }
const paymentLabels = {
  alipay: '支付宝', ALIPAY: '支付宝',
  wechat: '微信支付', 'wechat-pay': '微信支付', WECHAT_PAY: '微信支付',
  card: '银联支付', 'union-pay': '银联支付', UNION_PAY: '银联支付',
}
const paymentChannels = { alipay: 'ALIPAY', wechat: 'WECHAT_PAY', card: 'UNION_PAY', 'wechat-pay': 'WECHAT_PAY', 'union-pay': 'UNION_PAY' }
const afterSaleTypes = { REFUND_ONLY: '仅退款', RETURN_REFUND: '退货退款', EXCHANGE: '换货', 'refund-only': '仅退款', 'return-refund': '退货退款', exchange: '换货' }
const afterSaleTypeCodes = { 仅退款: 'REFUND_ONLY', 退货退款: 'RETURN_REFUND', 换货: 'EXCHANGE' }

function pageItems(response) {
  return response?.items || response?.content || (Array.isArray(response) ? response : [])
}

function formatDate(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value).replace('T', ' ').slice(0, 16)
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(date).replaceAll('/', '-').replace(' 24:', ' 00:')
}

function normalizeProfile(value = {}) {
  return {
    id: value.id,
    nickname: value.name || '',
    phone: value.phone || '',
    email: value.email || '',
    avatarUrl: value.avatarUrl || '',
    birthday: value.birthday || '',
    gender: genderFromApi[value.gender] || value.gender || '保密',
    createdAt: value.createdAt,
  }
}

function normalizeOrderItem(item) {
  return {
    ...item,
    orderItemId: item.id,
    id: item.id,
    slug: item.productSlug,
    name: item.productName,
    skuId: item.skuCode,
    databaseSkuId: item.skuId,
    price: Number(item.unitPrice),
    lineAmount: Number(item.lineAmount),
  }
}

function normalizeOrder(order) {
  if (!order) return null
  return {
    ...order,
    subtotal: Number(order.subtotal),
    deliveryFee: Number(order.deliveryFee),
    discount: Number(order.discount),
    total: Number(order.total),
    paidAmount: Number(order.paidAmount),
    paymentMethod: paymentLabels[order.paymentMethod] || order.paymentMethod || '待选择',
    returnStatus: order.afterSaleStatus,
    items: (order.items || []).map(normalizeOrderItem),
    carrier: order.shipment?.carrierName || '',
    trackingNo: order.shipment?.trackingNo || '',
    createdAt: formatDate(order.createdAt),
    timeline: (order.timeline || []).map((event) => ({
      label: event.note || event.toStatus || '订单状态已更新',
      time: formatDate(event.createdAt),
      done: true,
    })),
  }
}

function normalizeAfterSale(request) {
  const item = request.items?.[0] || {}
  return {
    ...request,
    id: request.afterSaleNo,
    type: afterSaleTypes[request.type] || request.type,
    reason: request.reasonDescription,
    amount: Number(request.requestedAmount),
    createdAt: formatDate(request.createdAt),
    item: {
      id: item.id,
      orderItemId: item.orderItemId,
      name: item.productName,
      image: item.image,
      skuLabel: item.skuLabel,
      quantity: item.quantity,
    },
    progress: (request.events || []).map((event) => event.description || event.toStatus).filter(Boolean),
  }
}

export function createCommerceStore(client = api) {
  const addresses = ref([])
  const orders = ref([])
  const afterSales = ref([])
  const favoriteIds = ref([])
  const favoriteProducts = ref([])
  const profile = ref(normalizeProfile())
  const loading = ref(false)
  const error = ref('')

  const favorites = computed(() => favoriteProducts.value.length
    ? favoriteProducts.value
    : favoriteIds.value.map((id) => products.find((product) => product.id === id)).filter(Boolean))
  const defaultAddress = computed(() => addresses.value.find((address) => address.isDefault) || addresses.value[0] || null)
  const activeOrders = computed(() => orders.value.filter((order) => !['completed', 'cancelled'].includes(order.status)))

  function upsertOrder(order) {
    const normalized = normalizeOrder(order)
    const index = orders.value.findIndex((item) => item.orderNo === normalized.orderNo)
    if (index >= 0) orders.value.splice(index, 1, normalized)
    else orders.value.unshift(normalized)
    return normalized
  }

  async function refreshAll() {
    loading.value = true
    error.value = ''
    try {
      const [profileData, addressData, favoriteData, orderData, afterSaleData] = await Promise.all([
        client.get('/api/me/profile'),
        client.get('/api/me/addresses'),
        client.get('/api/me/favorites?size=100'),
        client.get('/api/orders?size=50'),
        client.get('/api/after-sales?size=50'),
      ])
      profile.value = normalizeProfile(profileData)
      addresses.value = addressData || []
      favoriteProducts.value = pageItems(favoriteData).map(normalizeProduct)
      favoriteIds.value = favoriteProducts.value.map((product) => product.id)
      orders.value = pageItems(orderData).map(normalizeOrder)
      afterSales.value = pageItems(afterSaleData).map(normalizeAfterSale)
    } catch (cause) {
      error.value = cause.message || '账户数据加载失败'
      throw cause
    } finally {
      loading.value = false
    }
  }

  async function toggleFavorite(productId) {
    const id = Number(productId)
    const removing = favoriteIds.value.includes(id)
    favoriteIds.value = removing ? favoriteIds.value.filter((item) => item !== id) : [...favoriteIds.value, id]
    try {
      if (removing) {
        await client.delete(`/api/me/favorites/${id}`)
        favoriteProducts.value = favoriteProducts.value.filter((product) => product.id !== id)
      } else {
        await client.post(`/api/me/favorites/${id}`)
        const product = products.find((item) => item.id === id)
        if (product) favoriteProducts.value.push(product)
      }
    } catch (cause) {
      favoriteIds.value = removing ? [...favoriteIds.value, id] : favoriteIds.value.filter((item) => item !== id)
      throw cause
    }
  }

  function addressPayload(input) {
    return {
      name: input.name,
      phone: input.phone,
      province: input.province,
      city: input.city,
      district: input.district,
      detail: input.detail,
      postalCode: input.postalCode || '',
      tag: input.tag || '',
      isDefault: Boolean(input.isDefault),
    }
  }

  async function saveAddress(input) {
    const saved = input.id
      ? await client.put(`/api/me/addresses/${input.id}`, addressPayload(input))
      : await client.post('/api/me/addresses', addressPayload(input))
    addresses.value = await client.get('/api/me/addresses')
    return saved
  }

  async function removeAddress(id) {
    await client.delete(`/api/me/addresses/${id}`)
    addresses.value = await client.get('/api/me/addresses')
  }

  async function setDefaultAddress(id) {
    await client.patch(`/api/me/addresses/${id}/default`, null)
    addresses.value = await client.get('/api/me/addresses')
  }

  function getOrder(orderNo) {
    return orders.value.find((order) => order.orderNo === orderNo)
  }

  async function loadOrder(orderNo) {
    return upsertOrder(await client.get(`/api/orders/${orderNo}`))
  }

  async function createOrder({ address, addressId, paymentMethod = 'alipay', buyerNote = '', invoiceRequired = false }) {
    const selectedAddressId = Number(addressId || address?.id)
    const order = await client.post('/api/orders', {
      addressId: selectedAddressId,
      buyerNote,
      paymentChannel: paymentChannels[paymentMethod] || 'ALIPAY',
      invoiceRequired,
    }, { headers: { 'Idempotency-Key': globalThis.crypto?.randomUUID?.() || `web-${Date.now()}` } })
    return upsertOrder(order)
  }

  async function markOrderPaid(orderNo, paymentMethod = 'alipay') {
    await client.post(`/api/orders/${orderNo}/payments/sandbox`, { channel: paymentChannels[paymentMethod] || 'ALIPAY' })
    return loadOrder(orderNo)
  }

  async function startOrderPayment(orderNo, paymentMethod = 'alipay') {
    return client.post(`/api/orders/${orderNo}/payments`, {
      channel: paymentChannels[paymentMethod] || 'ALIPAY',
    })
  }

  async function loadPayment(paymentNo) {
    return client.get(`/api/payments/${paymentNo}`)
  }

  async function cancelOrder(orderNo) {
    upsertOrder(await client.post(`/api/orders/${orderNo}/cancel`))
    return true
  }

  async function confirmReceipt(orderNo) {
    upsertOrder(await client.post(`/api/orders/${orderNo}/confirm-receipt`))
    return true
  }

  async function createAfterSale(input) {
    const request = await client.post('/api/after-sales', {
      orderNo: input.orderNo,
      type: afterSaleTypeCodes[input.type] || input.type,
      reasonCode: input.reasonCode || 'OTHER',
      reasonDescription: input.reason || input.reasonDescription,
      customerNote: input.note || input.customerNote || '',
      items: [{ orderItemId: Number(input.orderItemId || input.item?.orderItemId || input.item?.id), quantity: Number(input.quantity || 1) }],
    })
    const normalized = normalizeAfterSale(request)
    afterSales.value.unshift(normalized)
    const order = getOrder(input.orderNo)
    if (order) order.returnStatus = 'requested'
    return normalized
  }

  async function saveProfile(next) {
    const saved = await client.patch('/api/me/profile', {
      name: next.nickname,
      phone: next.phone || '',
      avatarUrl: next.avatarUrl || '',
      birthday: next.birthday || null,
      gender: genderToApi[next.gender] || 'UNSPECIFIED',
    })
    profile.value = normalizeProfile(saved)
    useAuth().replaceUser(saved)
    return profile.value
  }

  function reset() {
    addresses.value = []
    orders.value = []
    afterSales.value = []
    favoriteIds.value = []
    favoriteProducts.value = []
    profile.value = normalizeProfile()
  }

  return {
    addresses, orders, afterSales, favoriteIds, profile, favorites, defaultAddress, activeOrders, loading, error,
    refreshAll, toggleFavorite, saveAddress, removeAddress, setDefaultAddress, getOrder, loadOrder,
    createOrder, startOrderPayment, loadPayment, markOrderPaid, cancelOrder, confirmReceipt,
    createAfterSale, saveProfile, reset,
  }
}

let browserStore

export function useCommerce() {
  if (!browserStore) browserStore = createCommerceStore()
  return browserStore
}
