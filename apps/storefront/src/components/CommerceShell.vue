<script setup>
import { onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SiteHeader from './SiteHeader.vue'
import CartDrawer from './CartDrawer.vue'
import AgentPanel from './AgentPanel.vue'
import { useAuth } from '../composables/useAuth'
import { useCart } from '../composables/useCart'

const route = useRoute()
const router = useRouter()
const { user } = useAuth()
const { items, itemCount, subtotal, deliveryFee, total, shippingGap, updateQuantity, removeItem } = useCart()
const searchTerm = ref(String(route.query.q || ''))
const cartOpen = ref(false)
const agentOpen = ref(false)

watch(() => route.query.q, (value) => { searchTerm.value = String(value || '') })
watch([cartOpen, agentOpen], () => {
  document.body.classList.toggle('has-overlay', cartOpen.value || agentOpen.value)
})

onUnmounted(() => document.body.classList.remove('has-overlay'))

function search(value) {
  const q = String(value || '').trim()
  router.push({ path: '/search', query: q ? { q } : {} })
}

function checkout() {
  cartOpen.value = false
  router.push('/checkout')
}
</script>

<template>
  <div class="commerce-shell">
    <SiteHeader
      v-model="searchTerm"
      :item-count="itemCount"
      :user="user"
      @search="search"
      @open-cart="cartOpen = true"
      @open-agent="agentOpen = true"
    />

    <slot />

    <footer class="commerce-footer">
      <div class="page-width commerce-footer__inner">
        <RouterLink class="brand" to="/"><span class="brand__mark" aria-hidden="true"><img src="/brand/super-mall-logo.png" alt="" width="42" height="42"></span><span class="brand__copy"><b>SUPER</b><small>MALL / SELECT</small></span></RouterLink>
        <nav aria-label="页脚导航">
          <RouterLink to="/search">全部商品</RouterLink>
          <RouterLink to="/account/orders">我的订单</RouterLink>
          <RouterLink to="/help">帮助中心</RouterLink>
        </nav>
        <p>© 2026 SUPER MALL · FRONTEND DEMO</p>
      </div>
    </footer>

    <button class="floating-agent" type="button" aria-label="打开 Agent 客服演示" @click="agentOpen = true"><span>✦</span><b>客服<br>预览</b></button>
    <CartDrawer
      :open="cartOpen"
      :items="items"
      :item-count="itemCount"
      :subtotal="subtotal"
      :delivery-fee="deliveryFee"
      :total="total"
      :shipping-gap="shippingGap"
      @close="cartOpen = false"
      @update-quantity="updateQuantity"
      @remove="removeItem"
      @checkout="checkout"
    />
    <AgentPanel :open="agentOpen" @close="agentOpen = false" />
  </div>
</template>
