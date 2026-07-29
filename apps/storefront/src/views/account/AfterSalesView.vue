<script setup>
import { computed } from 'vue'
import { useCommerce } from '../../composables/useCommerce'

const { afterSales, orders } = useCommerce()
const eligibleOrders = computed(() => orders.value.filter((order) => ['processing', 'shipped', 'completed', 'after-sale'].includes(order.status)))
const statusLabels = { requested: '已提交', reviewing: '审核中', approved: '等待寄回', returning: '退货中', refunding: '退款中', completed: '已完成', rejected: '未通过', cancelled: '已取消' }
function money(value) { return Number(value || 0).toLocaleString('zh-CN') }
</script>

<template>
  <div class="account-view">
    <header class="account-page-heading"><div><p class="eyebrow">RETURNS & REFUNDS</p><h1>退款 / 售后</h1><p>申请退货退款并跟踪每一步处理进度。</p></div></header>
    <section v-if="eligibleOrders.length" class="after-sale-entry"><div><span>↺</span><p><b>需要申请售后？</b><small>请选择最近订单中的商品发起申请</small></p></div><RouterLink class="button button--dark" :to="`/account/after-sales/apply/${eligibleOrders[0].orderNo}`">发起售后</RouterLink></section>
    <div v-if="afterSales.length" class="after-sale-list"><article v-for="request in afterSales" :key="request.id"><header><div><b>服务单 {{ request.id }}</b><small>{{ request.createdAt }} · 订单 {{ request.orderNo }}</small></div><span :class="`after-sale-status after-sale-status--${request.status}`">{{ statusLabels[request.status] || request.status }}</span></header><div class="after-sale-body"><img :src="request.item.image" :alt="request.item.name" /><div><h2>{{ request.item.name }}</h2><p>{{ request.type }} · {{ request.reason }}</p><small>{{ request.item.skuLabel }}</small></div><strong>¥{{ money(request.amount) }}</strong></div><ol><li v-for="(step, index) in request.progress" :key="step" :class="{ 'is-current': index === request.progress.length - 1 }"><span>{{ index + 1 }}</span>{{ step }}</li></ol></article></div>
    <div v-else class="commerce-empty"><span>↺</span><h2>暂无售后记录</h2><p>符合条件的订单可以在订单详情中发起退货退款。</p><RouterLink class="button button--dark" to="/account/orders">查看我的订单</RouterLink></div>
  </div>
</template>
