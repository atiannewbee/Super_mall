import { createRouter, createWebHistory } from 'vue-router'
import { hasValidMerchantSession } from '../composables/useMerchantAuth'
import LoginView from '../views/LoginView.vue'
import MerchantLayout from '../views/MerchantLayout.vue'
import DashboardView from '../views/DashboardView.vue'
import OrdersView from '../views/OrdersView.vue'
import OrderDetailView from '../views/OrderDetailView.vue'
import InventoryView from '../views/InventoryView.vue'
import NotFoundView from '../views/NotFoundView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { guestOnly: true } },
    {
      path: '/',
      component: MerchantLayout,
      meta: { requiresAuth: true },
      children: [
        { path: '', name: 'dashboard', component: DashboardView, meta: { title: '运营看板' } },
        { path: 'orders', name: 'orders', component: OrdersView, meta: { title: '订单履约' } },
        {
          path: 'orders/:orderNo',
          name: 'order-detail',
          component: OrderDetailView,
          meta: { title: '订单详情' },
        },
      { path: 'inventory', name: 'inventory', component: InventoryView, meta: { title: '商品与库存' } },
      ],
    },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFoundView },
  ],
  scrollBehavior() {
    return { top: 0 }
  },
})

router.beforeEach((to) => {
  const authenticated = hasValidMerchantSession()
  if (to.meta.requiresAuth && !authenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guestOnly && authenticated) return { name: 'dashboard' }
  return true
})

export default router
