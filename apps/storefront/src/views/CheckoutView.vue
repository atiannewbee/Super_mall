<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import CommerceShell from '../components/CommerceShell.vue'
import AppToast from '../components/AppToast.vue'
import { useCart } from '../composables/useCart'
import { useCommerce } from '../composables/useCommerce'
import { errorMessage } from '../services/api'

const router = useRouter()
const { items, subtotal, refreshCart } = useCart()
const { addresses, defaultAddress, createOrder } = useCommerce()
const mockPaymentEnabled = import.meta.env.VITE_PAYMENT_MODE !== 'alipay'
const selectedAddressId = ref(defaultAddress.value?.id || '')
const payment = ref('alipay')
const invoice = ref(false)
const note = ref('')
const agreed = ref(true)
const submitting = ref(false)
const toastVisible = ref(false)
const toastMessage = ref('')
let toastTimer

const selectedAddress = computed(() => addresses.value.find((address) => address.id === selectedAddressId.value))
const deliveryFee = computed(() => subtotal.value >= 99 ? 0 : 10)
const discount = computed(() => 0)
const payable = computed(() => Math.max(0, subtotal.value + deliveryFee.value - discount.value))
onUnmounted(() => window.clearTimeout(toastTimer))
watch([addresses, defaultAddress], ([currentAddresses, currentDefault]) => {
  if (currentAddresses.some((address) => address.id === selectedAddressId.value)) return
  selectedAddressId.value = currentDefault?.id || currentAddresses[0]?.id || ''
}, { immediate: true })

function money(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function showToast(message) {
  toastMessage.value = message
  toastVisible.value = true
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toastVisible.value = false }, 2300)
}

async function submitOrder() {
  if (submitting.value) return
  if (!selectedAddress.value) return showToast('请先选择收货地址')
  if (!agreed.value) return showToast('请确认订单与支付说明')
  if (!items.value.length) return
  submitting.value = true
  try {
    const order = await createOrder({
      address: selectedAddress.value,
      paymentMethod: payment.value,
      buyerNote: note.value,
      invoiceRequired: invoice.value,
    })
    await refreshCart()
    await router.replace({ name: 'checkout-result', query: { orderNo: order.orderNo, status: 'pay', payment: payment.value } })
  } catch (error) {
    showToast(errorMessage(error, '订单创建失败，请稍后重试'))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <CommerceShell>
    <main class="commerce-page checkout-page">
      <div class="page-width checkout-width">
        <nav class="breadcrumbs" aria-label="面包屑"><RouterLink to="/cart">购物车</RouterLink><span>/</span><span>确认订单</span></nav>
        <header class="commerce-heading commerce-heading--checkout"><div><p class="eyebrow">SECURE CHECKOUT</p><h1>确认订单</h1></div><ol><li class="is-done">购物车</li><li class="is-active">确认订单</li><li>完成支付</li></ol></header>

        <div v-if="items.length" class="checkout-layout">
          <div class="checkout-sections">
            <section class="checkout-card"><header><span>01</span><div><h2>收货地址</h2><p>商品将配送至以下地址</p></div><RouterLink to="/account/addresses">管理地址</RouterLink></header><div class="address-options"><label v-for="address in addresses" :key="address.id" :class="{ 'is-selected': selectedAddressId === address.id }"><input v-model="selectedAddressId" type="radio" :value="address.id" /><span><b>{{ address.name }} · {{ address.phone }}</b><small>{{ address.province }} {{ address.city }} {{ address.district }} {{ address.detail }}</small><i v-if="address.isDefault">默认地址</i></span></label><RouterLink class="address-option-add" to="/account/addresses">＋ 新增收货地址</RouterLink></div></section>

            <section class="checkout-card"><header><span>02</span><div><h2>配送方式</h2><p>当前订单由标准配送服务承运</p></div></header><div class="delivery-options"><label class="is-selected"><input type="radio" checked /><span><b>标准配送</b><small>实际时效以订单物流为准 · {{ subtotal >= 99 ? '免费' : '¥10' }}</small></span><strong>{{ subtotal >= 99 ? '免运费' : '¥10' }}</strong></label></div></section>

            <section class="checkout-card"><header><span>03</span><div><h2>支付方式</h2><p>{{ mockPaymentEnabled ? '当前为模拟支付环境，不会发生真实扣款' : '当前开放支付宝支付，支付结果以渠道回调为准' }}</p></div></header><div class="payment-options"><label :class="{ 'is-selected': payment === 'alipay' }"><input v-model="payment" type="radio" value="alipay" /><span class="payment-icon payment-icon--alipay">支</span><b>支付宝 <small>{{ mockPaymentEnabled ? '模拟支付' : '电脑网站支付' }}</small></b></label><label :class="{ 'is-selected': payment === 'wechat', 'is-disabled': !mockPaymentEnabled }"><input v-model="payment" type="radio" value="wechat" :disabled="!mockPaymentEnabled" /><span class="payment-icon payment-icon--wechat">微</span><b>微信支付 <small>{{ mockPaymentEnabled ? '模拟支付' : '即将开放' }}</small></b></label><label class="is-disabled"><input type="radio" value="card" disabled /><span class="payment-icon">卡</span><b>银联支付 <small>即将开放</small></b></label></div></section>

            <section class="checkout-card"><header><span>04</span><div><h2>商品与备注</h2><p>共 {{ items.length }} 种商品</p></div><RouterLink to="/cart">返回修改</RouterLink></header><div class="checkout-items"><article v-for="item in items" :key="item.skuId"><img :src="item.image" :alt="item.name" /><div><h3>{{ item.name }}</h3><p>{{ item.skuLabel }}</p><small>数量 × {{ item.quantity }}</small></div><strong>¥{{ money(item.price * item.quantity) }}</strong></article></div><label class="checkout-note"><span>订单备注</span><input v-model="note" type="text" maxlength="100" placeholder="选填，请与客服协商一致" /></label><label class="invoice-toggle"><input v-model="invoice" type="checkbox" />需要电子发票 <small>{{ invoice ? '将在订单完成后开具个人电子发票' : '可在订单详情中补开发票' }}</small></label></section>
          </div>

          <aside class="order-summary order-summary--checkout"><p class="eyebrow">PAYMENT SUMMARY</p><h2>付款明细</h2><dl><div><dt>商品金额</dt><dd>¥{{ money(subtotal) }}</dd></div><div><dt>配送费</dt><dd>{{ deliveryFee ? `¥${money(deliveryFee)}` : '免运费' }}</dd></div><div v-if="discount"><dt>优惠</dt><dd class="summary-discount">−¥{{ money(discount) }}</dd></div></dl><p class="order-summary__total"><span>应付合计<small>共 {{ items.reduce((sum, item) => sum + item.quantity, 0) }} 件</small></span><strong><i>¥</i>{{ money(payable) }}</strong></p><label class="checkout-agreement"><input v-model="agreed" type="checkbox" />我已核对商品、地址与配送信息，并了解支付状态以后端结果为准。</label><button class="button button--primary button--wide" type="button" :disabled="submitting" @click="submitOrder">{{ submitting ? '正在创建订单…' : `提交订单并去支付 · ¥${money(payable)}` }}</button><p class="checkout-secure-note">⌾ 商品金额、运费、库存与优惠均由服务端在下单时重新校验。</p></aside>
        </div>

        <div v-else class="commerce-empty commerce-empty--page"><span>▱</span><h1>没有可结算的商品</h1><p>购物车为空，或者刚刚已经完成了订单提交。</p><RouterLink class="button button--dark" to="/search">继续购物</RouterLink></div>
      </div>
    </main>
    <AppToast :visible="toastVisible" :message="toastMessage" />
  </CommerceShell>
</template>
