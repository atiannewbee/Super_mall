<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import CommerceShell from '../components/CommerceShell.vue'
import { useCommerce } from '../composables/useCommerce'
import { apiUrl, ApiError, errorMessage } from '../services/api'

const route = useRoute()
const { getOrder, loadOrder, startOrderPayment, loadPayment, markOrderPaid } = useCommerce()
const mockPaymentEnabled = import.meta.env.VITE_PAYMENT_MODE !== 'alipay'
const status = ref(String(route.query.status || 'pending'))
const message = ref('')
const order = computed(() => getOrder(String(route.query.orderNo || '')))
const paymentNo = ref(String(route.query.paymentNo || ''))
const paymentMethod = ref(String(route.query.payment || 'alipay') === 'wechat' ? 'wechat' : 'alipay')
const paying = ref(false)
const paymentLabel = computed(() => paymentMethod.value === 'wechat' ? '微信支付' : '支付宝')
const canPay = computed(() => order.value?.status === 'pending-payment' && order.value?.paymentStatus !== 'paid')
let pollTimer
let pollAttempts = 0

onMounted(async () => {
  const orderNo = String(route.query.orderNo || '')
  if (!orderNo) return
  try {
    if (!order.value) await loadOrder(orderNo)
    if (!route.query.payment && order.value?.paymentMethod === '微信支付') {
      paymentMethod.value = 'wechat'
    }
    if (order.value?.paymentStatus === 'paid') {
      status.value = 'success'
      return
    }
    if (status.value === 'pay') {
      if (mockPaymentEnabled) {
        message.value = '请选择一种模拟支付方式并确认。整个过程不会发生真实扣款。'
        return
      }
      const launch = await startOrderPayment(orderNo, String(route.query.payment || 'alipay'))
      paymentNo.value = launch.paymentNo
      status.value = 'pending'
      message.value = '正在安全跳转到支付宝，请稍候…'
      window.location.assign(apiUrl(launch.launchUrl))
      return
    }
    if (status.value === 'return') {
      status.value = 'pending'
      message.value = '正在向支付宝确认支付结果…'
      await refreshPaymentStatus()
    }
  } catch (error) {
    if (error instanceof ApiError && error.code === 'PAYMENT_PROVIDER_NOT_CONFIGURED') {
      status.value = 'pending'
      message.value = '订单已经创建，但支付宝沙箱尚未配置完成。'
    } else {
      status.value = 'failure'
      message.value = error.message || '支付状态确认失败，请在订单详情中重试。'
    }
  }
})

onUnmounted(() => window.clearTimeout(pollTimer))

async function confirmMockPayment() {
  if (!canPay.value || paying.value) return
  paying.value = true
  status.value = 'pending'
  message.value = `正在通过模拟${paymentLabel.value}确认订单…`
  try {
    await markOrderPaid(order.value.orderNo, paymentMethod.value)
    status.value = 'success'
    message.value = `模拟${paymentLabel.value}已完成，订单已进入备货流程。`
  } catch (error) {
    status.value = 'pay'
    if (error instanceof ApiError && error.code === 'PAYMENT_PROVIDER_NOT_CONFIGURED') {
      message.value = '后端尚未开启模拟支付，请设置 PAYMENT_SANDBOX_ENABLED=true 后重启后端。'
    } else {
      message.value = errorMessage(error, '模拟支付失败，请稍后重试。')
    }
  } finally {
    paying.value = false
  }
}

async function refreshPaymentStatus() {
  if (!paymentNo.value) {
    status.value = order.value?.paymentStatus === 'paid' ? 'success' : 'pending'
    return
  }
  try {
    const payment = await loadPayment(paymentNo.value)
    await loadOrder(String(route.query.orderNo || ''))
    if (payment.status === 'success') {
      status.value = 'success'
      message.value = ''
      return
    }
    if (['closed', 'failed'].includes(payment.status)) {
      status.value = 'failure'
      message.value = '本次支付没有完成，你可以返回订单详情重新支付。'
      return
    }
    status.value = 'pending'
    message.value = '支付结果仍在确认中，请不要重复付款。'
  } catch (error) {
    status.value = 'pending'
    message.value = error.message || '暂时没有取得最新支付状态，系统会继续确认。'
  }
  pollAttempts += 1
  if (pollAttempts < 15) {
    pollTimer = window.setTimeout(refreshPaymentStatus, 2000)
  }
}

function money(value) { return Number(value || 0).toLocaleString('zh-CN') }
</script>

<template>
  <CommerceShell>
    <main class="commerce-page result-page">
      <div class="page-width result-card" :class="`result-card--${status}`">
        <div class="result-symbol"><span>{{ status === 'success' ? '✓' : status === 'pay' ? '¥' : status === 'pending' ? '…' : '!' }}</span><i></i><i></i></div>
        <p class="eyebrow">ORDER CONFIRMATION</p>
        <h1>{{ status === 'success' ? '支付成功' : status === 'pay' ? '选择支付方式' : status === 'pending' ? '正在确认支付' : '支付未完成' }}</h1>
        <p>{{ message || (status === 'success' ? '订单已经进入仓库处理流程，我们会通过订单状态及时同步配送进度。' : status === 'pending' || status === 'pay' ? '订单已经创建，支付完成前库存将按订单规则暂时锁定。' : '本次没有完成扣款，你可以返回订单详情重新支付。') }}</p>
        <dl v-if="order"><div><dt>订单编号</dt><dd>{{ order.orderNo }}</dd></div><div><dt>支付方式</dt><dd>{{ canPay ? `${paymentLabel}（模拟）` : order.paymentMethod }}</dd></div><div><dt>应付金额</dt><dd>¥{{ money(order.total) }}</dd></div><div><dt>配送地址</dt><dd>{{ order.address.city }} {{ order.address.district }} {{ order.address.detail }}</dd></div></dl>
        <section v-if="mockPaymentEnabled && canPay && status !== 'pending'" class="mock-payment-panel" aria-labelledby="mock-payment-title">
          <header><div><p class="eyebrow">DEMO PAYMENT</p><h2 id="mock-payment-title">选择模拟渠道</h2></div><span>不会真实扣款</span></header>
          <div class="payment-options payment-options--result">
            <label :class="{ 'is-selected': paymentMethod === 'alipay' }"><input v-model="paymentMethod" type="radio" value="alipay" /><span class="payment-icon payment-icon--alipay">支</span><b>支付宝 <small>模拟支付</small></b></label>
            <label :class="{ 'is-selected': paymentMethod === 'wechat' }"><input v-model="paymentMethod" type="radio" value="wechat" /><span class="payment-icon payment-icon--wechat">微</span><b>微信支付 <small>模拟支付</small></b></label>
          </div>
          <button class="button button--primary button--wide" type="button" :disabled="paying" @click="confirmMockPayment">{{ paying ? '正在确认…' : `确认模拟支付 · ¥${money(order.total)}` }}</button>
          <small>确认后，后端会创建模拟支付流水，并将订单更新为“已支付 / 备货中”。</small>
        </section>
        <div class="result-actions"><RouterLink v-if="order" class="button button--primary" :to="`/account/orders/${order.orderNo}`">查看订单详情</RouterLink><RouterLink class="button button--dark" to="/search">继续购物</RouterLink></div>
        <small>{{ mockPaymentEnabled ? '当前为功能测试环境；模拟成功不代表真实资金到账。' : '支付状态只以后端响应及支付渠道回调为准。' }}</small>
      </div>
    </main>
  </CommerceShell>
</template>
