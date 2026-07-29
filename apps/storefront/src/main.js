import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { loadCatalog } from './composables/useCatalog'
import { hasValidSession, useAuth } from './composables/useAuth'
import { useCart } from './composables/useCart'
import { useCommerce } from './composables/useCommerce'
import './styles/base.css'
import './styles/store.css'
import './styles/login.css'
import './styles/commerce.css'

async function bootstrap() {
  try {
    await loadCatalog()
  } catch (error) {
    console.warn('后端商品目录暂不可用，保留静态商品作为降级展示。', error)
  }

  if (hasValidSession()) {
    await Promise.allSettled([useCart().refreshCart(), useCommerce().refreshAll()])
  }

  createApp(App).use(router).mount('#app')
}

if (typeof window !== 'undefined') {
  window.addEventListener('super-mall:unauthorized', () => {
    useAuth().logout()
    useCart().resetCart()
    useCommerce().reset()
    if (router.currentRoute.value.name !== 'login') {
      router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    }
  })
}

bootstrap()
