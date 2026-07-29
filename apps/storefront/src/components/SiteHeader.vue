<script setup>
import { RouterLink } from 'vue-router'

defineProps({
  modelValue: { type: String, default: '' },
  itemCount: { type: Number, default: 0 },
  user: { type: Object, default: null },
})

const emit = defineEmits(['update:modelValue', 'search', 'open-cart', 'open-agent'])
</script>

<template>
  <header class="site-header">
    <div class="service-bar">
      <div class="page-width service-bar__inner">
        <p><span class="live-dot"></span> 今日 23:00 前下单，核心城市次日送达</p>
        <nav aria-label="服务导航">
          <button type="button" @click="emit('open-agent')">在线客服</button>
          <RouterLink to="/help">帮助中心</RouterLink>
        </nav>
      </div>
    </div>

    <div class="commerce-header">
      <div class="page-width commerce-header__inner">
        <RouterLink class="brand" to="/" aria-label="SUPER MALL 首页">
          <span class="brand__mark" aria-hidden="true">
            <img src="/brand/super-mall-logo.png" alt="" width="42" height="42">
          </span>
          <span class="brand__copy"><b>SUPER</b><small>MALL / SELECT</small></span>
        </RouterLink>

        <form class="search-box" role="search" @submit.prevent="emit('search', modelValue)">
          <span class="search-box__icon" aria-hidden="true">⌕</span>
          <input
            :value="modelValue"
            type="search"
            placeholder="搜索手机、电脑、耳机或品牌"
            aria-label="搜索商品"
            @input="emit('update:modelValue', $event.target.value)"
          />
          <button class="search-box__submit" type="submit">SEARCH</button>
        </form>

        <div class="header-actions">
          <RouterLink v-if="!user" class="header-action header-action--account" to="/login" aria-label="登录账户">
            <span aria-hidden="true">◎</span><span>登录</span>
          </RouterLink>
          <RouterLink v-else class="header-action header-action--account header-action--user" to="/account" aria-label="进入用户中心">
            <span aria-hidden="true">●</span><span>{{ user.displayName }}</span>
          </RouterLink>
          <button class="header-action header-action--cart" type="button" aria-label="打开购物车" @click="emit('open-cart')">
            <span aria-hidden="true">▱</span><span>购物车</span><b>{{ itemCount }}</b>
          </button>
        </div>
      </div>
    </div>

    <div class="category-nav">
      <div class="page-width category-nav__inner">
        <RouterLink class="category-nav__all" to="/search"><span>☷</span> 全部商品</RouterLink>
        <nav aria-label="商城主导航">
          <RouterLink to="/search?collection=deals">限时优惠</RouterLink>
          <RouterLink to="/search?collection=new">新品首发</RouterLink>
          <RouterLink to="/search?sort=popular">热卖推荐</RouterLink>
        </nav>
        <p>商城商品 · 满 ¥99 免运费</p>
      </div>
    </div>
  </header>
</template>
