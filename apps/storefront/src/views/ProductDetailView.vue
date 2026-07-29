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
const selectedSkuId = ref('')
const quantity = ref(1)
const activeTab = ref('details')
const toastVisible = ref(false)
const toastMessage = ref('')
let toastTimer

const product = computed(() => products.find((item) => item.slug === route.params.slug))
const category = computed(() => categories.find((item) => item.id === product.value?.categoryId))
const selectedSku = computed(() => product.value?.skus.find((sku) => sku.id === selectedSkuId.value))
const related = computed(() => products.filter((item) => item.id !== product.value?.id && item.categoryId === product.value?.categoryId).slice(0, 4))
const specifications = computed(() => [
  { label: '品牌', value: product.value?.brand },
  { label: '商品编号', value: `SUPER-${product.value?.id}` },
  ...(product.value?.features || []).map((value, index) => ({ label: ['核心能力', '续航 / 性能', '特色功能'][index] || `特性 ${index + 1}`, value })),
  { label: '售后保障', value: '全国联保 · 7 天无理由退货' },
])

watch(product, (value) => {
  selectedSkuId.value = value?.skus.find((sku) => sku.stock > 0)?.id || value?.skus[0]?.id || ''
  quantity.value = 1
}, { immediate: true })

onUnmounted(() => window.clearTimeout(toastTimer))

function showToast(message) {
  toastMessage.value = message
  toastVisible.value = true
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toastVisible.value = false }, 2200)
}

async function addCurrent() {
  if (!product.value || !selectedSku.value || selectedSku.value.stock <= 0) return showToast('所选规格暂时无货')
  try { await addItem(product.value, selectedSku.value, quantity.value); showToast(`${product.value.name} 已加入购物车`); return true }
  catch (error) { showToast(error.message || '加入购物车失败'); return false }
}

async function buyNow() {
  if (await addCurrent()) router.push('/checkout')
}

async function quickAdd(value) {
  const sku = value.skus.find((entry) => entry.stock > 0)
  if (!sku) return showToast('该商品暂时无货')
  try { await addItem(value, sku); showToast(`${value.name} 已加入购物车`) }
  catch (error) { showToast(error.message || '加入购物车失败') }
}
</script>

<template>
  <CommerceShell>
    <main class="commerce-page product-detail-page">
      <div v-if="product" class="page-width">
        <nav class="breadcrumbs" aria-label="面包屑"><RouterLink to="/">首页</RouterLink><span>/</span><RouterLink :to="`/category/${category?.id}`">{{ category?.name }}</RouterLink><span>/</span><span>{{ product.name }}</span></nav>

        <section class="product-detail-main">
          <div class="product-gallery" :style="{ '--detail-accent': product.accent }">
            <div class="product-gallery__index"><span>SUPER SELECT</span><b>NO. {{ String(product.id).padStart(3, '0') }}</b></div>
            <img :src="product.gallery[0]" :alt="product.name" />
            <div class="product-gallery__thumbs"><button class="is-active" type="button"><img :src="product.image" alt="正面展示" /></button><button type="button" aria-label="更多图片将在商品接口接入后提供">＋<small>MORE</small></button></div>
          </div>

          <div class="product-buybox">
            <p class="eyebrow">{{ product.brand }} / OFFICIAL</p>
            <div class="product-title-row"><div><h1>{{ product.name }}</h1><p>{{ product.tagline }}</p></div><button type="button" :aria-pressed="favoriteIds.includes(product.id)" @click="toggleFavorite(product.id)">{{ favoriteIds.includes(product.id) ? '♥' : '♡' }}<small>{{ favoriteIds.includes(product.id) ? '已收藏' : '收藏' }}</small></button></div>
            <div class="product-score"><b>★ {{ product.rating }}</b><span>{{ product.reviewCount.toLocaleString('zh-CN') }} 条评价</span><span>已售 {{ product.soldCount.toLocaleString('zh-CN') }}+</span></div>
            <div class="product-price"><small>会员到手价</small><strong><i>¥</i>{{ Number(selectedSku?.price || product.price).toLocaleString('zh-CN') }}</strong><del>¥{{ product.originalPrice.toLocaleString('zh-CN') }}</del><span>{{ product.badge }}</span></div>
            <p class="product-description">{{ product.description }}</p>

            <div class="detail-service-row"><div><span>01</span><p><b>官方正品</b><small>品牌授权渠道</small></p></div><div><span>02</span><p><b>极速配送</b><small>预计明日送达</small></p></div><div><span>03</span><p><b>无忧售后</b><small>7 天退货保障</small></p></div></div>

            <fieldset class="detail-skus"><legend>选择规格</legend><button v-for="sku in product.skus" :key="sku.id" type="button" :class="{ 'is-selected': selectedSkuId === sku.id }" :disabled="sku.stock === 0" @click="selectedSkuId = sku.id"><span>{{ sku.label }}</span><small>{{ sku.stock ? `库存 ${sku.stock} 件` : '暂时无货' }}</small></button></fieldset>

            <div class="detail-purchase-row"><div class="quantity-control"><button type="button" aria-label="减少数量" @click="quantity = Math.max(1, quantity - 1)">−</button><span>{{ quantity }}</span><button type="button" aria-label="增加数量" :disabled="quantity >= (selectedSku?.stock || 1)" @click="quantity = Math.min(selectedSku?.stock || 1, quantity + 1)">＋</button></div><button class="button button--dark" type="button" @click="addCurrent">加入购物车</button><button class="button button--primary" type="button" @click="buyNow">立即购买</button></div>
            <p class="detail-delivery">配送至 <b>广东省深圳市</b> · 现货 · 23:00 前付款预计明日送达</p>
          </div>
        </section>

        <section class="product-information">
          <div class="product-information__tabs" role="tablist"><button type="button" :class="{ 'is-active': activeTab === 'details' }" @click="activeTab = 'details'">商品详情</button><button type="button" :class="{ 'is-active': activeTab === 'specs' }" @click="activeTab = 'specs'">规格参数</button><button type="button" :class="{ 'is-active': activeTab === 'reviews' }" @click="activeTab = 'reviews'">用户评价 {{ product.reviewCount }}</button></div>
          <div v-if="activeTab === 'details'" class="detail-story"><div><p class="eyebrow">DESIGNED FOR REAL LIFE</p><h2>{{ product.tagline }}</h2><p>{{ product.description }} 从核心性能到日常体验，每一个选择都围绕真实使用场景展开。</p></div><ul><li v-for="(feature, index) in product.features" :key="feature"><span>0{{ index + 1 }}</span><b>{{ feature }}</b></li></ul></div>
          <dl v-else-if="activeTab === 'specs'" class="specification-table"><div v-for="item in specifications" :key="item.label"><dt>{{ item.label }}</dt><dd>{{ item.value }}</dd></div></dl>
          <div v-else class="review-overview"><div><strong>{{ product.rating }}</strong><span>★★★★★</span><p>基于 {{ product.reviewCount.toLocaleString('zh-CN') }} 条真实购买评价</p></div><article><b>“外观和质感比预期更好，配送也很快。”</b><p>规格：{{ product.skus[0].label }}</p><small>SUPER 用户 · 已购 12 天</small></article><article><b>“日常使用稳定，续航表现符合描述。”</b><p>规格：{{ product.skus.at(-1).label }}</p><small>匿名用户 · 已购 28 天</small></article></div>
        </section>

        <section v-if="related.length" class="related-products"><div class="section-heading"><div><p class="eyebrow">YOU MAY ALSO LIKE</p><h2>同类精选</h2></div><RouterLink :to="`/category/${product.categoryId}`">查看全部 →</RouterLink></div><div class="product-grid"><ProductCard v-for="item in related" :key="item.id" :product="item" :wished="favoriteIds.includes(item.id)" @open="router.push(`/product/${item.slug}`)" @quick-add="quickAdd" @toggle-wish="toggleFavorite(item.id)" /></div></section>
      </div>

      <div v-else class="page-width commerce-empty commerce-empty--page"><span>404</span><h1>没有找到这件商品</h1><p>它可能已经下架，或者商品链接发生了变化。</p><RouterLink class="button button--dark" to="/search">返回全部商品</RouterLink></div>
    </main>
    <AppToast :visible="toastVisible" :message="toastMessage" />
  </CommerceShell>
</template>
