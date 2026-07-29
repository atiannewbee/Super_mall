<script setup>
import ProductCard from './ProductCard.vue'

defineProps({
  sectionId: { type: String, required: true },
  eyebrow: { type: String, required: true },
  title: { type: String, required: true },
  note: { type: String, default: '' },
  products: { type: Array, required: true },
})
const emit = defineEmits(['open-product', 'quick-add'])
</script>

<template>
  <section :id="sectionId" class="product-section page-width">
    <div class="section-heading">
      <div><p class="eyebrow">{{ eyebrow }}</p><h2>{{ title }}</h2></div>
      <p>{{ note }}</p>
    </div>
    <div class="product-grid">
      <ProductCard
        v-for="product in products"
        :key="product.id"
        :product="product"
        @open="emit('open-product', $event)"
        @quick-add="emit('quick-add', $event)"
      />
    </div>
  </section>
</template>
