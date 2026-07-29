import { createRouter, createWebHistory } from 'vue-router'
import StorefrontView from '../views/StorefrontView.vue'
import LoginView from '../views/LoginView.vue'
import ProductListingView from '../views/ProductListingView.vue'
import ProductDetailView from '../views/ProductDetailView.vue'
import CartView from '../views/CartView.vue'
import CheckoutView from '../views/CheckoutView.vue'
import OrderResultView from '../views/OrderResultView.vue'
import HelpView from '../views/HelpView.vue'
import NotFoundView from '../views/NotFoundView.vue'
import AccountLayout from '../views/account/AccountLayout.vue'
import AccountDashboardView from '../views/account/AccountDashboardView.vue'
import OrderListView from '../views/account/OrderListView.vue'
import OrderDetailView from '../views/account/OrderDetailView.vue'
import AddressView from '../views/account/AddressView.vue'
import FavoritesView from '../views/account/FavoritesView.vue'
import AfterSalesView from '../views/account/AfterSalesView.vue'
import AfterSaleApplyView from '../views/account/AfterSaleApplyView.vue'
import ProfileView from '../views/account/ProfileView.vue'
import { hasValidSession } from '../composables/useAuth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'storefront', component: StorefrontView },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/search', name: 'search', component: ProductListingView },
    { path: '/category/:slug', name: 'category', component: ProductListingView },
    { path: '/product/:slug', name: 'product-detail', component: ProductDetailView },
    { path: '/cart', name: 'cart', component: CartView },
    { path: '/checkout', name: 'checkout', component: CheckoutView, meta: { requiresAuth: true } },
    { path: '/checkout/result', name: 'checkout-result', component: OrderResultView, meta: { requiresAuth: true } },
    { path: '/help', name: 'help', component: HelpView },
    {
      path: '/account',
      component: AccountLayout,
      meta: { requiresAuth: true },
      children: [
        { path: '', name: 'account', component: AccountDashboardView },
        { path: 'orders', name: 'orders', component: OrderListView },
        { path: 'orders/:orderNo', name: 'order-detail', component: OrderDetailView },
        { path: 'addresses', name: 'addresses', component: AddressView },
        { path: 'favorites', name: 'favorites', component: FavoritesView },
        { path: 'after-sales', name: 'after-sales', component: AfterSalesView },
        { path: 'after-sales/apply/:orderNo', name: 'after-sale-apply', component: AfterSaleApplyView },
        { path: 'profile', name: 'profile', component: ProfileView },
      ],
    },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFoundView },
  ],
  scrollBehavior() {
    return { top: 0 }
  },
})

router.beforeEach((to) => {
  if (!to.matched.some((record) => record.meta.requiresAuth)) return true
  if (hasValidSession()) return true
  return { path: '/login', query: { redirect: to.fullPath } }
})

export default router
