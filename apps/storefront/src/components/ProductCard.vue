<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  product: { type: Object, required: true },
  wished: { type: Boolean, default: undefined },
})
const emit = defineEmits(['open', 'quick-add', 'toggle-wish'])
const localWished = ref(false)
const isWished = computed(() => props.wished === undefined ? localWished.value : props.wished)

function toggleWish() {
  if (props.wished === undefined) localWished.value = !localWished.value
  emit('toggle-wish', props.product)
}

function money(value) {
  return Number(value).toLocaleString('zh-CN')
}
</script>

<template>
  <article class="product-card" role="link" tabindex="0" @click="emit('open', product)" @keydown.enter="emit('open', product)">
    <div class="product-card__media" :style="{ '--product-accent': product.accent }">
      <span v-if="product.badge" class="product-badge">{{ product.badge }}</span>
      <button
        class="wish-button"
        type="button"
        :aria-label="isWished ? '取消收藏' : '收藏商品'"
        :aria-pressed="isWished"
        @click.stop="toggleWish"
      >{{ isWished ? '♥' : '♡' }}</button>
      <img :src="product.image" :alt="product.name" loading="lazy" />
      <button class="quick-view" type="button" @click.stop="emit('open', product)">快速查看</button>
    </div>
    <div class="product-card__body">
      <p class="product-brand">{{ product.brand }}</p>
      <h3>{{ product.name }}</h3>
      <p class="product-tagline">{{ product.tagline }}</p>
      <div class="rating-line"><span>★ {{ product.rating }}</span><small>{{ product.reviewCount.toLocaleString('zh-CN') }} 条评价</small></div>
      <div class="product-card__footer">
        <p><strong><small>¥</small>{{ money(product.price) }}</strong><del>¥{{ money(product.originalPrice) }}</del></p>
        <button type="button" aria-label="加入购物车" @click.stop="emit('quick-add', product)">＋</button>
      </div>
    </div>
  </article>
</template>
