<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMerchantAuth } from '../composables/useMerchantAuth'
import { errorMessage } from '../services/api'

const route = useRoute()
const router = useRouter()
const auth = useMerchantAuth()
const email = ref('')
const password = ref('')
const showPassword = ref(false)
const error = ref('')

const expired = computed(() => route.query.reason === 'expired')
const passwordChanged = computed(() => route.query.reason === 'password-changed')

async function submit() {
  error.value = ''
  if (!email.value.trim() || !password.value) {
    error.value = '请输入商家邮箱和密码'
    return
  }
  try {
    await auth.login(email.value, password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (cause) {
    error.value = errorMessage(cause, '登录失败，请检查账号信息')
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-story">
      <div class="login-brand">
        <img src="/brand/merchant-mark.svg" alt="" />
        <span>SUPER MALL</span>
      </div>
      <div class="story-copy">
        <p class="eyebrow">MERCHANT OPERATIONS / 2026</p>
        <h1>把每一笔订单，<br />送到该去的地方。</h1>
        <p>从支付完成，到拣货出库，再到物流签收。一个清晰、克制、可追踪的运营工作台。</p>
      </div>
      <div class="story-metrics">
        <div><strong>01</strong><span>独立商家身份</span></div>
        <div><strong>02</strong><span>全链路履约审计</span></div>
        <div><strong>03</strong><span>实时库存视图</span></div>
      </div>
      <div class="story-orbit orbit-one"></div>
      <div class="story-orbit orbit-two"></div>
    </section>

    <section class="login-panel">
      <form class="login-card" @submit.prevent="submit">
        <div class="login-card-head">
          <span class="step-mark">ACCESS / 01</span>
          <h2>登录运营中心</h2>
          <p>这里与消费者账号完全独立。</p>
        </div>

        <div v-if="expired" class="form-message neutral">登录已失效，请重新验证身份。</div>
        <div v-if="passwordChanged" class="form-message success">密码已更新，请使用新密码登录。</div>
        <div v-if="error" class="form-message error" role="alert">{{ error }}</div>

        <label class="field">
          <span>商家邮箱</span>
          <input
            v-model="email"
            type="email"
            autocomplete="username"
            placeholder="owner@your-store.com"
            maxlength="255"
          />
        </label>

        <label class="field">
          <span>密码</span>
          <div class="password-field">
            <input
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              autocomplete="current-password"
              placeholder="输入商家账号密码"
              maxlength="72"
            />
            <button type="button" @click="showPassword = !showPassword">
              {{ showPassword ? '隐藏' : '显示' }}
            </button>
          </div>
        </label>

        <button class="primary-action login-submit" :disabled="auth.loading.value">
          <span>{{ auth.loading.value ? '正在验证…' : '进入运营中心' }}</span>
          <b>→</b>
        </button>

        <p class="login-help">账号由平台负责人分配。如无法登录，请联系系统管理员解锁。</p>
      </form>
    </section>
  </main>
</template>
