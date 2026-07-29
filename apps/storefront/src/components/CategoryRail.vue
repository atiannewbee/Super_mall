<script setup>
defineProps({
  categories: { type: Array, required: true },
  activeCategory: { type: String, default: 'all' },
})
const emit = defineEmits(['select'])
</script>

<template>
  <section class="category-rail page-width" aria-labelledby="category-title">
    <div class="section-heading section-heading--compact">
      <div><p class="eyebrow">SHOP BY CATEGORY</p><h2 id="category-title">按场景，找到合适装备</h2></div>
      <p>精选 5 个核心数码品类</p>
    </div>
    <div class="category-rail__grid">
      <button
        v-for="category in categories"
        :key="category.id"
        type="button"
        :class="['category-tile', { 'is-active': activeCategory === category.id }]"
        @click="emit('select', category.id)"
      >
        <span class="category-tile__index">{{ category.icon }}</span>
        <span class="category-tile__icon" aria-hidden="true">
          <i></i><i></i><i></i>
        </span>
        <strong>{{ category.name }}</strong>
        <small>{{ category.description }}</small>
        <span class="category-tile__arrow">↗</span>
      </button>
    </div>
  </section>
</template>
