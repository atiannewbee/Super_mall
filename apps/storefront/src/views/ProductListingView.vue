<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommerceShell from '../components/CommerceShell.vue'
import ProductCard from '../components/ProductCard.vue'
import AppToast from '../components/AppToast.vue'
import { categories, products } from '../data/catalog'
import { useCart } from '../composables/useCart'
import { useCommerce } from '../composables/useCommerce'

const route = useRoute()
const router = useRouter()
const { addItem } = useCart()
const { favoriteIds, toggleFavorite } = useCommerce()
const brand = ref('all')
const price = ref('all')
const inStockOnly = ref(false)
const sort = ref(String(route.query.sort || 'recommended'))
const loading = ref(true)
const toastVisible = ref(false)
const toastMessage = ref('')
let loadTimer
let toastTimer

const categoryAliases = {
  phones: '手机 通讯 安卓 旗舰机 影像',
  computers: '电脑 笔记本 显示器 办公 生产力',
  audio: '影音 耳机 音箱 蓝牙 降噪 音频',
  'smart-home': '智能家居 手表 穿戴 中枢 健康',
  accessories: '数码配件 键盘 充电器 桌搭 外设',
}

const category = computed(() => categories.find((item) => item.id === route.params.slug))
const keyword = computed(() => String(route.query.q || '').trim())
const collection = computed(() => String(route.query.collection || ''))

const baseProducts = computed(() => products.filter((item) => {
  if (category.value && item.categoryId !== category.value.id) return false
  if (collection.value === 'deals' && !item.isDeal) return false
  if (collection.value === 'new' && !item.isNew) return false
  if (!keyword.value) return true
  const categoryName = categories.find((entry) => entry.id === item.categoryId)?.name || ''
  const text = [item.name, item.brand, item.tagline, item.description, categoryName, categoryAliases[item.categoryId], ...item.features].join(' ').toLocaleLowerCase('zh-CN')
  return text.includes(keyword.value.toLocaleLowerCase('zh-CN'))
}))

const brands = computed(() => [...new Set(baseProducts.value.map((item) => item.brand))].sort())
const visibleProducts = computed(() => {
  const result = baseProducts.value.filter((item) => {
    if (brand.value !== 'all' && item.brand !== brand.value) return false
    if (inStockOnly.value && !item.skus.some((sku) => sku.stock > 0)) return false
    if (price.value === 'under-1000' && item.price >= 1000) return false
    if (price.value === '1000-3000' && (item.price < 1000 || item.price > 3000)) return false
    if (price.value === 'over-3000' && item.price <= 3000) return false
    return true
  })
  return [...result].sort((a, b) => {
    if (sort.value === 'price-asc') return a.price - b.price
    if (sort.value === 'price-desc') return b.price - a.price
    if (sort.value === 'popular') return b.soldCount - a.soldCount
    if (sort.value === 'rating') return b.rating - a.rating
    return Number(b.isFeatured) - Number(a.isFeatured)
  })
})

const pageTitle = computed(() => {
  if (keyword.value) return `“${keyword.value}” 的搜索结果`
  if (category.value) return category.value.name
  if (collection.value === 'deals') return '限时优惠'
  if (collection.value === 'new') return '新品首发'
  return '全部商品'
})

const pageIntro = computed(() => category.value?.description || (keyword.value ? '根据名称、品牌、分类和商品特性为你匹配。' : '从精选数码装备中筛选适合你的产品。'))

watch(() => route.fullPath, () => {
  loading.value = true
  brand.value = 'all'
  sort.value = String(route.query.sort || 'recommended')
  window.clearTimeout(loadTimer)
  loadTimer = window.setTimeout(() => { loading.value = false }, 260)
}, { immediate: true })

onUnmounted(() => {
  window.clearTimeout(loadTimer)
  window.clearTimeout(toastTimer)
})

function showToast(message) {
  toastMessage.value = message
  toastVisible.value = true
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toastVisible.value = false }, 2200)
}

async function quickAdd(product) {
  const sku = product.skus.find((item) => item.stock > 0)
  if (!sku) return showToast('该商品暂时无货')
  try { await addItem(product, sku); showToast(`${product.name} 已加入购物车`) }
  catch (error) { showToast(error.message || '加入购物车失败') }
}

function clearFilters() {
  brand.value = 'all'
  price.value = 'all'
  inStockOnly.value = false
  sort.value = 'recommended'
}
</script>

<template>
  <CommerceShell>
    <main class="commerce-page listing-page">
      <div class="page-width">
        <nav class="breadcrumbs" aria-label="面包屑"><RouterLink to="/">首页</RouterLink><span>/</span><span>{{ pageTitle }}</span></nav>

        <header class="listing-hero">
          <div><p class="eyebrow">PRODUCT DISCOVERY</p><h1>{{ pageTitle }}</h1><p>{{ pageIntro }}</p></div>
          <span>{{ String(baseProducts.length).padStart(2, '0') }}<small>PRODUCTS</small></span>
        </header>

        <div class="listing-categories" aria-label="按分类浏览">
          <RouterLink to="/search" :class="{ 'is-active': !category }">全部</RouterLink>
          <RouterLink v-for="item in categories" :key="item.id" :to="`/category/${item.id}`" :class="{ 'is-active': category?.id === item.id }">{{ item.name }}</RouterLink>
        </div>

        <div class="listing-layout">
          <aside class="filter-panel" aria-label="筛选商品">
            <div class="filter-panel__heading"><b>筛选</b><button type="button" @click="clearFilters">重置</button></div>
            <fieldset><legend>品牌</legend><label><input v-model="brand" type="radio" value="all" /> 全部品牌</label><label v-for="item in brands" :key="item"><input v-model="brand" type="radio" :value="item" /> {{ item }}</label></fieldset>
            <fieldset><legend>价格区间</legend><label><input v-model="price" type="radio" value="all" /> 不限价格</label><label><input v-model="price" type="radio" value="under-1000" /> ¥1,000 以下</label><label><input v-model="price" type="radio" value="1000-3000" /> ¥1,000—3,000</label><label><input v-model="price" type="radio" value="over-3000" /> ¥3,000 以上</label></fieldset>
            <label class="filter-switch"><input v-model="inStockOnly" type="checkbox" /><span></span>仅看有货</label>
          </aside>

          <section class="listing-results" aria-live="polite">
            <div class="listing-toolbar"><p>找到 <b>{{ visibleProducts.length }}</b> 件商品</p><label>排序<select v-model="sort"><option value="recommended">综合推荐</option><option value="popular">销量优先</option><option value="rating">评分优先</option><option value="price-asc">价格从低到高</option><option value="price-desc">价格从高到低</option></select></label></div>

            <div v-if="loading" class="product-grid product-grid--listing" aria-label="正在加载商品">
              <div v-for="item in 6" :key="item" class="product-skeleton"><span></span><i></i><i></i><i></i></div>
            </div>
            <div v-else-if="visibleProducts.length" class="product-grid product-grid--listing">
              <ProductCard
                v-for="product in visibleProducts"
                :key="product.id"
                :product="product"
                :wished="favoriteIds.includes(product.id)"
                @open="router.push(`/product/${product.slug}`)"
                @quick-add="quickAdd"
                @toggle-wish="toggleFavorite(product.id)"
              />
            </div>
            <div v-else class="commerce-empty"><span>⌕</span><h2>没有匹配的商品</h2><p>试试放宽价格或品牌条件，也可以返回查看全部商品。</p><button class="button button--dark" type="button" @click="clearFilters">清除筛选</button></div>
          </section>
        </div>
      </div>
    </main>
    <AppToast :visible="toastVisible" :message="toastMessage" />
  </CommerceShell>
</template>
