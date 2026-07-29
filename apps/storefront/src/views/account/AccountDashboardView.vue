<script setup>
import { computed } from 'vue'
import OrderStatusBadge from '../../components/OrderStatusBadge.vue'
import { useAuth } from '../../composables/useAuth'
import { useCommerce } from '../../composables/useCommerce'

const { user } = useAuth()
const { orders, favorites, addresses, afterSales, activeOrders } = useCommerce()
const recentOrders = computed(() => orders.value.slice(0, 3))

function money(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}
</script>

<template>
  <div class="account-view">
    <header class="account-hero"><div><p class="eyebrow">MEMBER CENTER</p><h1>你好，{{ user?.displayName }}</h1><p>订单、收藏与售后进度都在这里。</p></div><div class="account-hero__card"><span>SUPER+</span><b>MEMBER / 0248</b><small>自 2026 年加入</small></div></header>

    <section class="account-stats" aria-label="账户数据概览"><RouterLink to="/account/orders"><span>01</span><strong>{{ activeOrders.length }}</strong><p>进行中的订单</p></RouterLink><RouterLink to="/account/favorites"><span>02</span><strong>{{ favorites.length }}</strong><p>收藏商品</p></RouterLink><RouterLink to="/account/addresses"><span>03</span><strong>{{ addresses.length }}</strong><p>收货地址</p></RouterLink><RouterLink to="/account/after-sales"><span>04</span><strong>{{ afterSales.length }}</strong><p>售后记录</p></RouterLink></section>

    <section class="account-section"><div class="account-section__heading"><div><p class="eyebrow">RECENT ORDERS</p><h2>最近订单</h2></div><RouterLink to="/account/orders">查看全部 →</RouterLink></div><div class="dashboard-orders"><article v-for="order in recentOrders" :key="order.orderNo"><header><div><small>{{ order.createdAt }}</small><b>{{ order.orderNo }}</b></div><OrderStatusBadge :status="order.status" /></header><div class="dashboard-order-products"><img v-for="item in order.items.slice(0, 3)" :key="item.skuId" :src="item.image" :alt="item.name" /><span v-if="order.items.length > 3">+{{ order.items.length - 3 }}</span></div><footer><p>共 {{ order.items.reduce((sum, item) => sum + item.quantity, 0) }} 件商品 <b>¥{{ money(order.total) }}</b></p><RouterLink :to="`/account/orders/${order.orderNo}`">订单详情</RouterLink></footer></article></div></section>

    <section class="account-shortcuts"><RouterLink to="/search"><span>⌕</span><div><b>继续选购</b><small>浏览全部商品</small></div><i>→</i></RouterLink><RouterLink to="/account/after-sales"><span>↺</span><div><b>售后服务</b><small>退货退款与进度</small></div><i>→</i></RouterLink><RouterLink to="/help"><span>?</span><div><b>帮助中心</b><small>配送、支付与保修</small></div><i>→</i></RouterLink></section>
  </div>
</template>
