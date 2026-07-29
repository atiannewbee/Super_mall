<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import SiteHeader from '../components/SiteHeader.vue'
import HeroSection from '../components/HeroSection.vue'
import CategoryRail from '../components/CategoryRail.vue'
import ProductSection from '../components/ProductSection.vue'
import ProductCard from '../components/ProductCard.vue'
import ProductDialog from '../components/ProductDialog.vue'
import CartDrawer from '../components/CartDrawer.vue'
import AgentPanel from '../components/AgentPanel.vue'
import AppToast from '../components/AppToast.vue'
import { useCatalog } from '../composables/useCatalog'
import { useCart } from '../composables/useCart'
import { useAuth } from '../composables/useAuth'

const { categories, query, activeCategory, filteredProducts, featuredProducts, dealProducts, newProducts, setCategory, resetFilters } = useCatalog()
const { items, itemCount, subtotal, deliveryFee, total, shippingGap, addItem, updateQuantity, removeItem } = useCart()
const { user } = useAuth()
const router = useRouter()

const selectedProduct = ref(null)
const cartOpen = ref(false)
const agentOpen = ref(false)
const toastVisible = ref(false)
const toastMessage = ref('')
let toastTimer

const resultTitle = computed(() => {
  if (query.value) return `“${query.value}” 的搜索结果`
  if (activeCategory.value !== 'all') return categories.find((category) => category.id === activeCategory.value)?.name || '商品精选'
  return '为你推荐'
})

watch([selectedProduct, cartOpen, agentOpen], () => {
  document.body.classList.toggle('has-overlay', Boolean(selectedProduct.value || cartOpen.value || agentOpen.value))
})

onUnmounted(() => {
  document.body.classList.remove('has-overlay')
  window.clearTimeout(toastTimer)
})

function showToast(message) {
  toastMessage.value = message
  toastVisible.value = true
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toastVisible.value = false }, 2400)
}

function openProduct(product) {
  selectedProduct.value = product
}

async function quickAdd(product) {
  const sku = product.skus.find((item) => item.stock > 0)
  if (!sku) return showToast('该商品暂时无货')
  try { await addItem(product, sku, 1); showToast(`${product.name} 已加入购物车`) }
  catch (error) { showToast(error.message || '加入购物车失败') }
}

async function addFromDialog({ product, sku, quantity }) {
  let added = false
  try { added = await addItem(product, sku, quantity) }
  catch (error) { return showToast(error.message || '加入购物车失败') }
  if (!added) return showToast('所选规格暂时无货')
  selectedProduct.value = null
  showToast(`${product.name} 已加入购物车`)
}

function chooseCategory(categoryId) {
  setCategory(categoryId)
  window.setTimeout(() => document.querySelector('#catalog')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 0)
}

function checkoutPreview() {
  cartOpen.value = false
  router.push('/checkout')
}

function submitSearch(value) {
  const search = String(value || '').trim()
  router.push({ path: '/search', query: search ? { q: search } : {} })
}

</script>

<template>
  <div id="top" class="storefront">
    <SiteHeader v-model="query" :item-count="itemCount" :user="user" @search="submitSearch" @open-cart="cartOpen = true" @open-agent="agentOpen = true" />

    <main>
      <HeroSection
        v-if="featuredProducts.length"
        :featured="featuredProducts[0]"
        @open-product="openProduct"
        @open-agent="agentOpen = true"
      />

      <section id="service" class="trust-strip page-width" aria-label="商城服务承诺">
        <article><span>01</span><div><b>官方正品</b><small>品牌授权渠道</small></div></article>
        <article><span>02</span><div><b>极速配送</b><small>核心城市次日达</small></div></article>
        <article><span>03</span><div><b>无忧售后</b><small>7 天无理由退货</small></div></article>
        <article><span>04</span><div><b>Agent 顾问</b><small>选购问题随时问</small></div></article>
      </section>

      <CategoryRail :categories="categories" :active-category="activeCategory" @select="chooseCategory" />

      <ProductSection
        section-id="deals"
        eyebrow="LIMITED OFFER"
        title="本周限时优惠"
        note="精选爆款，价格将在活动结束后恢复"
        :products="dealProducts.slice(0, 4)"
        @open-product="openProduct"
        @quick-add="quickAdd"
      />

      <section class="editorial-banner page-width">
        <div class="editorial-banner__copy">
          <p class="eyebrow eyebrow--light">DESK / RESET</p>
          <h2>少一点杂乱，<br>多一点<i>专注。</i></h2>
          <p>从专业显示器到趁手键盘，重新组织你的创作桌面。</p>
          <RouterLink to="/category/computers">探索桌面装备 <span>→</span></RouterLink>
        </div>
        <div class="editorial-banner__visual" aria-hidden="true">
          <span class="desk-screen"></span><span class="desk-keyboard"></span><span class="desk-orb"></span>
          <p>CURATED SETUP<br><b>NO. 024</b></p>
        </div>
      </section>

      <ProductSection
        section-id="new"
        eyebrow="JUST LANDED"
        title="本月新品首发"
        note="从第一天开始，体验新一代产品"
        :products="newProducts.slice(0, 4)"
        @open-product="openProduct"
        @quick-add="quickAdd"
      />

      <section id="catalog" class="catalog-section page-width">
        <div class="section-heading catalog-heading">
          <div><p class="eyebrow">SUPER RECOMMENDS</p><h2>{{ resultTitle }}</h2></div>
          <p>共 {{ filteredProducts.length }} 件商品</p>
        </div>
        <div class="filter-pills" role="group" aria-label="商品分类筛选">
          <button type="button" :class="{ 'is-active': activeCategory === 'all' }" @click="setCategory('all')">全部</button>
          <button v-for="category in categories" :key="category.id" type="button" :class="{ 'is-active': activeCategory === category.id }" @click="setCategory(category.id)">{{ category.name }}</button>
        </div>
        <div v-if="filteredProducts.length" class="product-grid">
          <ProductCard v-for="product in filteredProducts" :key="product.id" :product="product" @open="openProduct" @quick-add="quickAdd" />
        </div>
        <div v-else class="empty-results">
          <span>⌕</span><h3>没有找到匹配商品</h3><p>换个关键词，或清除分类后再试一次。</p>
          <button class="button button--dark" type="button" @click="resetFilters">清除筛选</button>
        </div>
      </section>

      <section class="agent-cta page-width">
        <div><p class="eyebrow eyebrow--light">SUPER AGENT / BETA</p><h2>选购不是做题，<br>说出你的需求就好。</h2></div>
        <div><p>预算、用途、性能、续航——智能顾问会用更自然的方式帮你比较当前商品。</p><button class="button button--light" type="button" @click="agentOpen = true">开始对话 <span>✦</span></button></div>
        <span class="agent-cta__glyph" aria-hidden="true">S</span>
      </section>
    </main>

    <footer id="footer" class="site-footer">
      <div class="page-width site-footer__top">
        <div class="footer-brand"><span class="brand__mark" aria-hidden="true"><img src="/brand/super-mall-logo.png" alt="" width="42" height="42"></span><h2>让好产品，<br>更容易被选中。</h2></div>
        <div class="footer-links"><div><b>购物指南</b><a href="#catalog">商品分类</a><a href="#deals">优惠活动</a><a href="#">会员权益</a></div><div><b>订单服务</b><a href="#">配送说明</a><a href="#">退换政策</a><a href="#">维修支持</a></div><div><b>关于我们</b><a href="#">品牌故事</a><a href="#">联系我们</a><button type="button" @click="agentOpen = true">Agent 客服</button></div></div>
      </div>
      <div class="page-width site-footer__bottom"><p>© 2026 SUPER MALL.</p><p>PRIVACY · TERMS · ACCESSIBILITY</p></div>
    </footer>

    <button class="floating-agent" type="button" aria-label="打开 Agent 客服" @click="agentOpen = true"><span>✦</span><b>问问<br>SUPER</b></button>
    <ProductDialog :open="Boolean(selectedProduct)" :product="selectedProduct" @close="selectedProduct = null" @add="addFromDialog" />
    <CartDrawer :open="cartOpen" :items="items" :item-count="itemCount" :subtotal="subtotal" :delivery-fee="deliveryFee" :total="total" :shipping-gap="shippingGap" @close="cartOpen = false" @update-quantity="updateQuantity" @remove="removeItem" @checkout="checkoutPreview" />
    <AgentPanel :open="agentOpen" @close="agentOpen = false" />
    <AppToast :visible="toastVisible" :message="toastMessage" />
  </div>
</template>
