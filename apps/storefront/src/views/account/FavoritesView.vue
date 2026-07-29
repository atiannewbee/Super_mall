<script setup>
import { onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ProductCard from '../../components/ProductCard.vue'
import AppToast from '../../components/AppToast.vue'
import { useCart } from '../../composables/useCart'
import { useCommerce } from '../../composables/useCommerce'

const router = useRouter()
const { addItem } = useCart()
const { favorites, favoriteIds, toggleFavorite } = useCommerce()
const toastVisible = ref(false)
const toastMessage = ref('')
let toastTimer
onUnmounted(() => window.clearTimeout(toastTimer))
function showToast(message) { toastMessage.value = message; toastVisible.value = true; window.clearTimeout(toastTimer); toastTimer = window.setTimeout(() => { toastVisible.value = false }, 2200) }
async function quickAdd(product) { const sku = product.skus.find((item) => item.stock > 0); if (!sku) return showToast('该商品暂时无货'); try { await addItem(product, sku); showToast(`${product.name} 已加入购物车`) } catch (error) { showToast(error.message || '加入购物车失败') } }
</script>

<template>
  <div class="account-view">
    <header class="account-page-heading"><div><p class="eyebrow">WISHLIST</p><h1>我的收藏</h1><p>保存感兴趣的商品，方便稍后比较和购买。</p></div><span class="account-page-count">{{ favorites.length }}<small>ITEMS</small></span></header>
    <div v-if="favorites.length" class="product-grid product-grid--account"><ProductCard v-for="product in favorites" :key="product.id" :product="product" :wished="favoriteIds.includes(product.id)" @open="router.push(`/product/${product.slug}`)" @quick-add="quickAdd" @toggle-wish="toggleFavorite(product.id)" /></div>
    <div v-else class="commerce-empty"><span>♡</span><h2>还没有收藏商品</h2><p>在商品卡片或详情页点击收藏，方便以后快速找到。</p><RouterLink class="button button--dark" to="/search">浏览全部商品</RouterLink></div>
    <AppToast :visible="toastVisible" :message="toastMessage" />
  </div>
</template>
