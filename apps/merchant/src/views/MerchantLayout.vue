<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMerchantAuth } from '../composables/useMerchantAuth'
import { api, errorMessage } from '../services/api'

const route = useRoute()
const router = useRouter()
const auth = useMerchantAuth()
const mobileOpen = ref(false)
const showPasswordForm = ref(false)
const changingPassword = ref(false)
const passwordError = ref('')
const passwords = ref({ currentPassword: '', newPassword: '', confirmPassword: '' })

const pageTitle = computed(() => route.meta.title || '商家运营中心')
const initials = computed(() => (auth.user.value?.name || '商').slice(0, 1))

const nav = [
  { to: { name: 'dashboard' }, label: '运营看板', code: '01', icon: 'grid' },
  { to: { name: 'orders' }, label: '订单履约', code: '02', icon: 'box' },
  { to: { name: 'inventory' }, label: '商品与库存', code: '03', icon: 'stack' },
]

function logout() {
  auth.logout()
  router.replace({ name: 'login' })
}

async function changePassword() {
  passwordError.value = ''
  if (passwords.value.newPassword !== passwords.value.confirmPassword) {
    passwordError.value = '两次输入的新密码不一致'
    return
  }
  changingPassword.value = true
  try {
    await api.post('/api/merchant/me/password', {
      currentPassword: passwords.value.currentPassword,
      newPassword: passwords.value.newPassword,
    })
    auth.logout()
    await router.replace({ name: 'login', query: { reason: 'password-changed' } })
  } catch (cause) {
    passwordError.value = errorMessage(cause, '密码修改失败')
  } finally {
    changingPassword.value = false
  }
}
</script>

<template>
  <div class="merchant-shell">
    <button
      v-if="mobileOpen"
      class="nav-scrim"
      aria-label="关闭导航"
      @click="mobileOpen = false"
    ></button>
    <aside class="side-nav" :class="{ open: mobileOpen }">
      <div class="brand-lockup">
        <img src="/brand/merchant-mark.svg" alt="" />
        <div>
          <strong>SUPER MALL</strong>
          <span>MERCHANT OPS</span>
        </div>
      </div>

      <div class="nav-context">
        <span>当前店铺</span>
        <strong>{{ auth.user.value?.merchantName || 'SUPER MALL 自营' }}</strong>
        <small># {{ auth.user.value?.merchantCode || 'SUPER_MALL' }}</small>
      </div>

      <nav aria-label="商家运营导航">
        <RouterLink
          v-for="item in nav"
          :key="item.code"
          :to="item.to"
          @click="mobileOpen = false"
        >
          <span class="nav-code">{{ item.code }}</span>
          <span class="nav-glyph" :data-icon="item.icon" aria-hidden="true"></span>
          <strong>{{ item.label }}</strong>
          <span class="nav-arrow">↗</span>
        </RouterLink>
      </nav>

      <div class="side-foot">
        <div class="system-state">
          <i></i>
          <span>运营服务正常</span>
          <b>LIVE</b>
        </div>
        <button class="logout-button" @click="logout">退出商家账号</button>
      </div>
    </aside>

    <main class="workspace">
      <header class="topbar">
        <div class="topbar-left">
          <button class="mobile-menu" aria-label="打开导航" @click="mobileOpen = true">☰</button>
          <div>
            <span class="eyebrow">SUPER MALL / OPERATIONS</span>
            <h1>{{ pageTitle }}</h1>
          </div>
        </div>
        <div class="account-chip">
          <div class="account-avatar">{{ initials }}</div>
          <div>
            <strong>{{ auth.user.value?.displayName }}</strong>
            <span>商家账号 · 全部运营权限</span>
          </div>
        </div>
      </header>

      <div v-if="auth.user.value?.forcePasswordChange" class="security-notice">
        <strong>安全提醒</strong>
        <span>这是初始化账号，请在正式上线前设置仅你知晓的密码。</span>
        <button @click="showPasswordForm = true">立即修改 →</button>
      </div>

      <section class="page-stage">
        <RouterView />
      </section>
    </main>

    <div v-if="showPasswordForm" class="modal-layer" @click.self="showPasswordForm = false">
      <form class="password-modal" @submit.prevent="changePassword">
        <div class="panel-head">
          <div><span class="section-index">SECURITY / PASSWORD</span><h2>修改商家密码</h2></div>
          <button type="button" class="close-button" @click="showPasswordForm = false">×</button>
        </div>
        <div class="password-modal-body">
          <p>修改成功后，当前令牌会立即失效，需要使用新密码重新登录。</p>
          <div v-if="passwordError" class="form-message error">{{ passwordError }}</div>
          <label class="field">
            <span>当前密码</span>
            <input v-model="passwords.currentPassword" type="password" autocomplete="current-password" maxlength="72" />
          </label>
          <label class="field">
            <span>新密码</span>
            <input v-model="passwords.newPassword" type="password" autocomplete="new-password" maxlength="72" />
            <small>至少 12 位，并同时包含字母和数字。</small>
          </label>
          <label class="field">
            <span>再次输入新密码</span>
            <input v-model="passwords.confirmPassword" type="password" autocomplete="new-password" maxlength="72" />
          </label>
          <button class="primary-action" :disabled="changingPassword">
            {{ changingPassword ? '正在更新…' : '确认修改密码' }} <b>→</b>
          </button>
        </div>
      </form>
    </div>
  </div>
</template>
