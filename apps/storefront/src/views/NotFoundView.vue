<script setup>
import CommerceShell from '../components/CommerceShell.vue'
</script>

<template>
  <CommerceShell><main class="commerce-page not-found-page"><div><span>404</span><p class="eyebrow">PAGE NOT FOUND</p><h1>这条路暂时<br />没有商品。</h1><p>页面可能已被移动，返回商城继续浏览吧。</p><div><RouterLink class="button button--primary" to="/">返回首页</RouterLink><RouterLink class="button button--dark" to="/search">浏览商品</RouterLink></div></div></main></CommerceShell>
</template>
