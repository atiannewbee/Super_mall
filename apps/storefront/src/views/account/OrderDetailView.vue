<script setup>
import { computed, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import OrderStatusBadge from '../../components/OrderStatusBadge.vue'
import AppToast from '../../components/AppToast.vue'
import { products } from '../../data/catalog'
import { useCommerce } from '../../composables/useCommerce'

const route = useRoute()
const { getOrder, cancelOrder, confirmReceipt } = useCommerce()
const order = computed(() => getOrder(String(route.params.orderNo)))
const toastVisible = ref(false)
const toastMessage = ref('')
let toastTimer

onUnmounted(() => window.clearTimeout(toastTimer))

function money(value) { return Number(value || 0).toLocaleString('zh-CN') }
function showToast(message) { toastMessage.value = message; toastVisible.value = true; window.clearTimeout(toastTimer); toastTimer = window.setTimeout(() => { toastVisible.value = false }, 2200) }
async function cancel() { try { await cancelOrder(order.value.orderNo); showToast('订单已取消') } catch (error) { showToast(error.message || '取消订单失败') } }
async function confirm() { try { await confirmReceipt(order.value.orderNo); showToast('已确认收货，感谢你的购买') } catch (error) { showToast(error.message || '确认收货失败') } }
</script>

<template>
  <div class="account-view">
    <template v-if="order">
      <nav class="account-breadcrumb"><RouterLink to="/account/orders">我的订单</RouterLink><span>/</span><span>{{ order.orderNo }}</span></nav>
      <header class="order-detail-hero"><div><p class="eyebrow">ORDER / {{ order.orderNo }}</p><h1><OrderStatusBadge :status="order.status" /> {{ order.status === 'shipped' ? '商品正在向你靠近' : order.status === 'pending-payment' ? '等待完成支付' : order.status === 'processing' ? '仓库正在备货' : order.status === 'completed' ? '订单已完成' : '订单已关闭' }}</h1><p>{{ order.status === 'shipped' ? `${order.carrier} · ${order.trackingNo}` : `下单时间 ${order.createdAt}` }}</p></div><div class="order-detail-actions"><RouterLink v-if="order.status === 'pending-payment'" class="button button--primary" :to="`/checkout/result?orderNo=${order.orderNo}&status=pay`">立即付款</RouterLink><button v-if="order.status === 'shipped'" class="button button--primary" type="button" @click="confirm">确认收货</button><button v-if="order.status === 'pending-payment'" class="button button--dark" type="button" @click="cancel">取消订单</button><RouterLink v-if="['shipped', 'completed'].includes(order.status)" class="button button--dark" :to="`/account/after-sales/apply/${order.orderNo}`">申请售后</RouterLink></div></header>

      <section class="order-progress"><div v-for="(item, index) in order.timeline" :key="`${item.label}-${index}`" :class="{ 'is-done': item.done }"><span>{{ item.done ? '✓' : index + 1 }}</span><p><b>{{ item.label }}</b><small>{{ item.time }}</small></p></div></section>

      <div class="order-detail-grid"><section class="account-panel order-detail-products"><header><div><p class="eyebrow">ITEMS</p><h2>商品清单</h2></div><span>共 {{ order.items.reduce((sum, item) => sum + item.quantity, 0) }} 件</span></header><article v-for="item in order.items" :key="item.skuId"><RouterLink :to="`/product/${products.find(product => product.id === item.productId)?.slug}`"><img :src="item.image" :alt="item.name" /></RouterLink><div><h3>{{ item.name }}</h3><p>{{ item.skuLabel }}</p><small>数量 × {{ item.quantity }}</small></div><strong>¥{{ money(item.price * item.quantity) }}</strong></article><dl><div><dt>商品小计</dt><dd>¥{{ money(order.subtotal) }}</dd></div><div><dt>配送费</dt><dd>{{ order.deliveryFee ? `¥${money(order.deliveryFee)}` : '免运费' }}</dd></div><div v-if="order.discount"><dt>优惠</dt><dd>−¥{{ money(order.discount) }}</dd></div><div><dt>实付金额</dt><dd>¥{{ money(order.total) }}</dd></div></dl></section>

        <aside><section class="account-panel order-info-card"><p class="eyebrow">DELIVERY</p><h2>收货信息</h2><dl><div><dt>收货人</dt><dd>{{ order.address.name }} · {{ order.address.phone }}</dd></div><div><dt>地址</dt><dd>{{ order.address.province }} {{ order.address.city }} {{ order.address.district }} {{ order.address.detail }}</dd></div><div><dt>配送方式</dt><dd>{{ order.carrier || '标准配送' }}</dd></div></dl></section><section class="account-panel order-info-card"><p class="eyebrow">PAYMENT</p><h2>支付信息</h2><dl><div><dt>支付状态</dt><dd>{{ order.paymentStatus === 'paid' ? '已支付' : '未支付' }}</dd></div><div><dt>支付方式</dt><dd>{{ order.paymentMethod }}</dd></div><div><dt>发票</dt><dd>个人电子发票</dd></div></dl></section><RouterLink class="order-help-card" to="/help"><span>?</span><p><b>订单遇到问题？</b><small>查看帮助中心或联系客服</small></p><i>→</i></RouterLink></aside>
      </div>
    </template>
    <div v-else class="commerce-empty commerce-empty--page"><span>404</span><h1>没有找到该订单</h1><p>请检查订单编号，或返回订单列表重新选择。</p><RouterLink class="button button--dark" to="/account/orders">返回我的订单</RouterLink></div>
    <AppToast :visible="toastVisible" :message="toastMessage" />
  </div>
</template>
