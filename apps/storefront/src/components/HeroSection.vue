<script setup>
defineProps({ featured: { type: Object, required: true } })
const emit = defineEmits(['open-product', 'open-agent'])
</script>

<template>
  <section class="hero page-width" aria-labelledby="hero-title">
    <article class="hero-main">
      <div class="hero-main__copy">
        <p class="eyebrow eyebrow--light">SUPER SELECT / 07.16</p>
        <h1 id="hero-title">下一代装备，<br><i>现在</i>就绪。</h1>
        <p>{{ featured.tagline }}。精选旗舰科技，本周会员再享专属换新礼遇。</p>
        <div class="hero-main__actions">
          <button class="button button--light" type="button" @click="emit('open-product', featured)">立即了解</button>
          <a href="#catalog">浏览全部 <span>↗</span></a>
        </div>
      </div>
      <div class="hero-main__visual" aria-hidden="true">
        <span class="hero-orbit hero-orbit--one"></span>
        <span class="hero-orbit hero-orbit--two"></span>
        <img :src="featured.image" :alt="featured.name" />
        <p><b>{{ featured.name }}</b><span>¥{{ featured.price.toLocaleString('zh-CN') }} 起</span></p>
      </div>
      <span class="hero-index">01—08</span>
    </article>

    <div class="hero-aside">
      <article class="campaign-card campaign-card--trade">
        <p class="eyebrow">TRADE IN</p>
        <h2>旧设备，<br>换新价值。</h2>
        <p>在线估价，最高补贴 ¥1,200</p>
        <a href="#deals">查看换新计划 <span>→</span></a>
        <div class="trade-shape" aria-hidden="true"><span></span><span></span></div>
      </article>
      <article class="campaign-card campaign-card--agent">
        <p class="eyebrow eyebrow--light">SUPER AGENT</p>
        <h2>不确定怎么选？</h2>
        <p>告诉智能顾问你的预算和使用场景，快速缩小选择。</p>
        <button type="button" @click="emit('open-agent')"><span class="agent-spark">✦</span> 问问选购助手</button>
      </article>
    </div>
  </section>
</template>
