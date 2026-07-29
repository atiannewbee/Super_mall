<script setup>
import { computed, nextTick, ref, watch } from 'vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  product: { type: Object, default: null },
})
const emit = defineEmits(['close', 'add'])
const closeButton = ref(null)
const selectedSkuId = ref('')
const quantity = ref(1)
let previousFocus

const selectedSku = computed(() => props.product?.skus.find((sku) => sku.id === selectedSkuId.value))

watch(() => [props.open, props.product], async ([open, product]) => {
  if (open && product) {
    previousFocus = document.activeElement
    selectedSkuId.value = product.skus.find((sku) => sku.stock > 0)?.id || product.skus[0]?.id || ''
    quantity.value = 1
    await nextTick()
    closeButton.value?.focus()
  } else if (!open) {
    previousFocus?.focus?.()
  }
})

function addToCart() {
  if (!selectedSku.value || selectedSku.value.stock <= 0) return
  emit('add', { product: props.product, sku: selectedSku.value, quantity: quantity.value })
}

function money(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open && product" class="overlay" @click.self="emit('close')" @keydown.esc="emit('close')">
      <section class="product-dialog" role="dialog" aria-modal="true" :aria-labelledby="`product-title-${product.id}`">
        <button ref="closeButton" class="overlay-close" type="button" aria-label="关闭商品详情" @click="emit('close')">×</button>
        <div class="product-dialog__media" :style="{ background: product.accent }">
          <span class="product-dialog__counter">PRODUCT / {{ String(product.id).padStart(3, '0') }}</span>
          <img :src="product.gallery[0]" :alt="product.name" />
          <p><span>SUPER SELECT</span><b>{{ product.badge }}</b></p>
        </div>
        <div class="product-dialog__details">
          <p class="eyebrow">{{ product.brand }} / OFFICIAL</p>
          <h2 :id="`product-title-${product.id}`">{{ product.name }}</h2>
          <p class="product-dialog__tagline">{{ product.tagline }}</p>
          <div class="product-dialog__rating"><b>★ {{ product.rating }}</b><span>{{ product.reviewCount.toLocaleString('zh-CN') }} 条评价</span><span>已售 {{ product.soldCount.toLocaleString('zh-CN') }}+</span></div>
          <p class="product-dialog__description">{{ product.description }}</p>
          <ul class="feature-list">
            <li v-for="feature in product.features" :key="feature">{{ feature }}</li>
          </ul>

          <fieldset class="sku-picker">
            <legend>选择规格</legend>
            <button
              v-for="sku in product.skus"
              :key="sku.id"
              type="button"
              :class="{ 'is-selected': selectedSkuId === sku.id }"
              :disabled="sku.stock === 0"
              @click="selectedSkuId = sku.id"
            >
              <span>{{ sku.label }}</span><small>{{ sku.stock === 0 ? '暂时无货' : `仅剩 ${sku.stock} 件` }}</small>
            </button>
          </fieldset>

          <div class="buy-row">
            <div class="quantity-control" aria-label="购买数量">
              <button type="button" aria-label="减少数量" @click="quantity = Math.max(1, quantity - 1)">−</button>
              <span>{{ quantity }}</span>
              <button type="button" aria-label="增加数量" @click="quantity = Math.min(selectedSku?.stock || 1, quantity + 1)">＋</button>
            </div>
            <p><small>到手价</small><strong><i>¥</i>{{ money(selectedSku?.price || product.price) }}</strong></p>
            <button class="button button--primary" type="button" :disabled="!selectedSku || selectedSku.stock === 0" @click="addToCart">加入购物车</button>
          </div>
          <p class="purchase-note"><span>✓ 官方正品</span><span>✓ 7 天无理由退货</span><span>✓ 满 ¥99 免运费</span></p>
        </div>
      </section>
    </div>
  </Teleport>
</template>
