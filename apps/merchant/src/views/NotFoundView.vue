<template>
  <main class="not-found">
    <img src="/brand/merchant-mark.svg" alt="" />
    <span>ERROR / 404</span>
    <h1>这里没有运营任务</h1>
    <p>页面可能已移动，回到看板继续工作。</p>
    <RouterLink :to="{ name: 'dashboard' }">返回运营看板 →</RouterLink>
  </main>
</template>
