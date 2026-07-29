<script setup>
import { useRouter } from 'vue-router'
import { useAuth } from '../composables/useAuth'
import { useCart } from '../composables/useCart'
import { useCommerce } from '../composables/useCommerce'

const router = useRouter()
const { user, logout } = useAuth()
const { resetCart } = useCart()
const { reset } = useCommerce()

const links = [
  { to: '/account', label: '账户概览', icon: '01' },
  { to: '/account/orders', label: '我的订单', icon: '02' },
  { to: '/account/addresses', label: '收货地址', icon: '03' },
  { to: '/account/favorites', label: '我的收藏', icon: '04' },
  { to: '/account/after-sales', label: '退款 / 售后', icon: '05' },
  { to: '/account/profile', label: '个人资料', icon: '06' },
]

function signOut() {
  logout()
  resetCart()
  reset()
  router.push('/')
}
</script>

<template>
  <aside class="account-nav">
    <div class="account-nav__identity">
      <span>{{ user?.displayName?.slice(0, 1).toUpperCase() || 'S' }}</span>
      <div><b>{{ user?.displayName || 'SUPER 会员' }}</b><small>普通会员 · 已登录</small></div>
    </div>
    <nav aria-label="用户中心导航">
      <RouterLink v-for="link in links" :key="link.to" :to="link.to" exact-active-class="is-active">
        <span>{{ link.icon }}</span>{{ link.label }}<i>→</i>
      </RouterLink>
    </nav>
    <button class="account-nav__logout" type="button" @click="signOut">退出当前账户</button>
  </aside>
</template>
