<script setup>
import { computed, onUnmounted, ref } from 'vue'
import OrderStatusBadge from '../../components/OrderStatusBadge.vue'
import AppToast from '../../components/AppToast.vue'
import { products } from '../../data/catalog'
import { useCart } from '../../composables/useCart'
import { useCommerce } from '../../composables/useCommerce'

const { orders, cancelOrder } = useCommerce()
const { addItem } = useCart()
const activeStatus = ref('all')
const query = ref('')
const toastVisible = ref(false)
const toastMessage = ref('')
let toastTimer

const tabs = [
  { value: 'all', label: '全部' }, { value: 'pending-payment', label: '待付款' }, { value: 'processing', label: '待发货' },
  { value: 'shipped', label: '待收货' }, { value: 'completed', label: '已完成' }, { value: 'after-sale', label: '退款 / 售后' },
]

const filteredOrders = computed(() => orders.value.filter((order) => {
  if (activeStatus.value !== 'all' && order.status !== activeStatus.value) return false
  const text = `${order.orderNo} ${order.items.map((item) => item.name).join(' ')}`.toLocaleLowerCase('zh-CN')
  return text.includes(query.value.trim().toLocaleLowerCase('zh-CN'))
}))

onUnmounted(() => window.clearTimeout(toastTimer))

function money(value) { return Number(value || 0).toLocaleString('zh-CN') }
function showToast(message) { toastMessage.value = message; toastVisible.value = true; window.clearTimeout(toastTimer); toastTimer = window.setTimeout(() => { toastVisible.value = false }, 2200) }
async function cancel(orderNo) { try { await cancelOrder(orderNo); showToast('订单已取消') } catch (error) { showToast(error.message || '取消订单失败') } }
async function buyAgain(order) {
  try {
    for (const item of order.items) {
    const product = products.find((entry) => entry.id === item.productId)
    const sku = product?.skus.find((entry) => entry.id === item.skuId) || product?.skus.find((entry) => entry.stock > 0)
      if (product && sku) await addItem(product, sku, item.quantity)
    }
    showToast('商品已重新加入购物车')
  } catch (error) { showToast(error.message || '重新加入购物车失败') }
}
</script>

<template>
  <div class="account-view">
    <header class="account-page-heading"><div><p class="eyebrow">ORDER HISTORY</p><h1>我的订单</h1><p>查看支付、发货、物流和售后状态。</p></div><label class="account-search"><span>⌕</span><input v-model="query" type="search" placeholder="搜索订单号或商品名称" /></label></header>
    <div class="order-tabs" role="tablist"><button v-for="tab in tabs" :key="tab.value" type="button" role="tab" :aria-selected="activeStatus === tab.value" :class="{ 'is-active': activeStatus === tab.value }" @click="activeStatus = tab.value">{{ tab.label }}<small>{{ tab.value === 'all' ? orders.length : orders.filter(order => order.status === tab.value).length }}</small></button></div>

    <div v-if="filteredOrders.length" class="order-list"><article v-for="order in filteredOrders" :key="order.orderNo" class="order-card"><header><div><b>{{ order.createdAt }}</b><span>订单号 {{ order.orderNo }}</span></div><OrderStatusBadge :status="order.status" /></header><div class="order-card__body"><div class="order-card__items"><RouterLink v-for="item in order.items" :key="item.skuId" :to="`/product/${item.slug}`"><img :src="item.image" :alt="item.name" /><div><h2>{{ item.name }}</h2><p>{{ item.skuLabel }}</p><small>¥{{ money(item.price) }} × {{ item.quantity }}</small></div></RouterLink></div><div class="order-card__amount"><small>实付金额</small><strong>¥{{ money(order.total) }}</strong><span>{{ order.paymentMethod }}</span></div><div class="order-card__actions"><RouterLink class="button button--dark" :to="`/account/orders/${order.orderNo}`">订单详情</RouterLink><RouterLink v-if="order.status === 'pending-payment'" class="button button--primary" :to="`/checkout/result?orderNo=${order.orderNo}&status=pay`">立即付款</RouterLink><button v-if="order.status === 'pending-payment'" type="button" @click="cancel(order.orderNo)">取消订单</button><button v-if="order.status === 'completed'" type="button" @click="buyAgain(order)">再次购买</button></div></div></article></div>
    <div v-else class="commerce-empty"><span>⌕</span><h2>没有找到相关订单</h2><p>换个关键词，或查看其他订单状态。</p><button class="button button--dark" type="button" @click="activeStatus = 'all'; query = ''">查看全部订单</button></div>
    <AppToast :visible="toastVisible" :message="toastMessage" />
  </div>
</template>
