<script setup>
import { computed, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CommerceShell from '../components/CommerceShell.vue'
import ProductCard from '../components/ProductCard.vue'
import AppToast from '../components/AppToast.vue'
import { products } from '../data/catalog'
import { useCart } from '../composables/useCart'
import { useCommerce } from '../composables/useCommerce'

const router = useRouter()
const { items, itemCount, subtotal, deliveryFee, shippingGap, addItem, updateQuantity, removeItem, clearCart } = useCart()
const { favoriteIds, toggleFavorite } = useCommerce()
const coupon = ref('')
const discount = ref(0)
const toastVisible = ref(false)
const toastMessage = ref('')
let toastTimer

const payable = computed(() => Math.max(0, subtotal.value + deliveryFee.value - discount.value))
const recommendations = computed(() => products.filter((product) => !items.value.some((item) => item.productId === product.id)).slice(0, 4))

onUnmounted(() => window.clearTimeout(toastTimer))

function money(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function showToast(message) {
  toastMessage.value = message
  toastVisible.value = true
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toastVisible.value = false }, 2200)
}

function applyCoupon() {
  discount.value = 0
  showToast('优惠码功能尚未开放，订单金额将由后端统一计算')
}

async function quickAdd(product) {
  const sku = product.skus.find((item) => item.stock > 0)
  if (!sku) return
  try {
    await addItem(product, sku)
    showToast(`${product.name} 已加入购物车`)
  } catch (error) {
    showToast(error.message || '加入购物车失败')
  }
}
</script>

<template>
  <CommerceShell>
    <main class="commerce-page cart-page">
      <div class="page-width">
        <nav class="breadcrumbs" aria-label="面包屑"><RouterLink to="/">首页</RouterLink><span>/</span><span>购物车</span></nav>
        <header class="commerce-heading"><div><p class="eyebrow">YOUR SELECTION</p><h1>购物车</h1></div><p>{{ itemCount }} 件商品，满 ¥99 享免运费</p></header>

        <template v-if="items.length">
          <div class="cart-page-layout">
            <section class="cart-page-items" aria-label="购物车商品">
              <div class="cart-table-heading"><span>商品信息</span><span>单价</span><span>数量</span><span>小计</span></div>
              <article v-for="item in items" :key="item.skuId" class="cart-page-item">
                <RouterLink class="cart-page-item__product" :to="`/product/${products.find(product => product.id === item.productId)?.slug}`"><img :src="item.image" :alt="item.name" /><div><p>{{ products.find(product => product.id === item.productId)?.brand }}</p><h2>{{ item.name }}</h2><small>{{ item.skuLabel }}</small><span>现货 · 支持 7 天无理由退货</span></div></RouterLink>
                <strong>¥{{ money(item.price) }}</strong>
                <div class="quantity-control"><button type="button" aria-label="减少数量" @click="updateQuantity(item.skuId, item.quantity - 1)">−</button><span>{{ item.quantity }}</span><button type="button" aria-label="增加数量" :disabled="item.quantity >= item.stock" @click="updateQuantity(item.skuId, item.quantity + 1)">＋</button></div>
                <div class="cart-page-item__subtotal"><strong>¥{{ money(item.price * item.quantity) }}</strong><button type="button" @click="removeItem(item.skuId)">移除</button></div>
              </article>
              <div class="cart-page-actions"><RouterLink to="/search">← 继续购物</RouterLink><button type="button" @click="clearCart">清空购物车</button></div>
            </section>

            <aside class="order-summary">
              <p class="eyebrow">ORDER SUMMARY</p><h2>订单摘要</h2>
              <div class="shipping-progress shipping-progress--page"><p v-if="shippingGap">再选 <b>¥{{ money(shippingGap) }}</b> 即可免运费</p><p v-else><b>已享免运费</b></p><span><i :style="{ width: `${Math.min(100, subtotal / 0.99)}%` }"></i></span></div>
              <dl><div><dt>商品小计</dt><dd>¥{{ money(subtotal) }}</dd></div><div><dt>配送费</dt><dd>{{ deliveryFee ? `¥${money(deliveryFee)}` : '免运费' }}</dd></div><div v-if="discount"><dt>优惠</dt><dd class="summary-discount">−¥{{ money(discount) }}</dd></div></dl>
              <div class="coupon-field"><label for="coupon">优惠码</label><div><input id="coupon" v-model="coupon" type="text" placeholder="优惠功能后续开放" /><button type="button" @click="applyCoupon">应用</button></div></div>
              <p class="order-summary__total"><span>应付合计<small>已含税费</small></span><strong><i>¥</i>{{ money(payable) }}</strong></p>
              <button class="button button--primary button--wide" type="button" @click="router.push('/checkout')">去结算 <span>→</span></button>
              <small class="order-summary__secure">⌾ 安全结算 · 支付信息不会保存在前端</small>
            </aside>
          </div>
        </template>

        <div v-else class="commerce-empty commerce-empty--page"><span>▱</span><h1>购物车还是空的</h1><p>浏览商品并选择规格，喜欢的装备会出现在这里。</p><RouterLink class="button button--dark" to="/search">去选购商品</RouterLink></div>

        <section v-if="recommendations.length" class="related-products"><div class="section-heading"><div><p class="eyebrow">COMPLETE YOUR SETUP</p><h2>顺手带上这些</h2></div><p>根据当前购物车为你推荐</p></div><div class="product-grid"><ProductCard v-for="product in recommendations" :key="product.id" :product="product" :wished="favoriteIds.includes(product.id)" @open="router.push(`/product/${product.slug}`)" @quick-add="quickAdd" @toggle-wish="toggleFavorite(product.id)" /></div></section>
      </div>
    </main>
    <AppToast :visible="toastVisible" :message="toastMessage" />
  </CommerceShell>
</template>
