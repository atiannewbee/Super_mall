<script setup>
import { reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useAuth } from '../composables/useAuth'
import { useCart } from '../composables/useCart'
import { useCommerce } from '../composables/useCommerce'
import { errorMessage } from '../services/api'

const router = useRouter()
const route = useRoute()
const { login, register } = useAuth()
const { syncGuestCart } = useCart()
const { refreshAll } = useCommerce()

const mode = ref('login')
const name = ref('')
const account = ref('')
const phone = ref('')
const password = ref('')
const agreed = ref(true)
const showPassword = ref(false)
const submitting = ref(false)
const notice = ref('')
const noticeTone = ref('info')
const errors = reactive({ name: '', account: '', phone: '', password: '', agreed: '' })

function clearErrors() {
  Object.keys(errors).forEach((key) => { errors[key] = '' })
  notice.value = ''
  noticeTone.value = 'info'
}

function switchMode(nextMode) {
  mode.value = nextMode
  clearErrors()
}

function isAccountValid(value) {
  return /^1[3-9]\d{9}$/.test(value) || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
}

function validate() {
  clearErrors()
  let valid = true
  if (mode.value === 'register' && !name.value.trim()) {
    errors.name = '请输入昵称'
    valid = false
  }
  if (mode.value === 'login') {
    if (!isAccountValid(account.value.trim())) {
      errors.account = '请输入有效的手机号或邮箱'
      valid = false
    }
  } else {
    const emailValid = !account.value.trim() || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(account.value.trim())
    const phoneValid = !phone.value.trim() || /^1[3-9]\d{9}$/.test(phone.value.trim())
    if (!emailValid) { errors.account = '请输入有效的邮箱'; valid = false }
    if (!phoneValid) { errors.phone = '请输入有效的 11 位手机号'; valid = false }
    if (!account.value.trim() && !phone.value.trim()) { errors.account = '邮箱或手机号至少填写一项'; valid = false }
  }
  if (password.value.length < 8) {
    errors.password = '密码至少需要 8 个字符'
    valid = false
  }
  if (!agreed.value) {
    errors.agreed = '请先同意服务协议与隐私政策'
    valid = false
  }
  return valid
}

async function submit() {
  if (!validate()) return
  submitting.value = true
  try {
    if (mode.value === 'login') await login(account.value, password.value)
    else await register({ name: name.value, email: account.value, phone: phone.value, password: password.value })
    await Promise.allSettled([syncGuestCart(), refreshAll()])
    noticeTone.value = 'success'
    notice.value = mode.value === 'login' ? '登录成功，正在返回商城…' : '账户创建成功，正在进入商城…'
    const destination = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') ? route.query.redirect : '/'
    await router.push(destination)
  } catch (error) {
    noticeTone.value = 'error'
    notice.value = errorMessage(error, mode.value === 'login' ? '登录失败，请检查账户和密码' : '注册失败，请检查填写内容')
  } finally {
    submitting.value = false
  }
}

function showUnavailable(message) {
  noticeTone.value = 'info'
  notice.value = message
}
</script>

<template>
  <main class="login-page">
    <section class="login-story" aria-labelledby="login-story-title">
      <RouterLink class="login-brand" to="/" aria-label="返回 SUPER MALL 首页">
        <span class="brand__mark" aria-hidden="true"><img src="/brand/super-mall-logo.png" alt="" width="42" height="42"></span><span><b>SUPER</b><small>MALL / MEMBER</small></span>
      </RouterLink>
      <div class="login-story__copy"><p class="eyebrow eyebrow--light">SUPER+ MEMBERSHIP</p><h1 id="login-story-title">好装备，<br>从<i>懂你</i>开始。</h1><p>登录后同步购物车、订单和售后进度，也让选购 Agent 更了解你的设备偏好。</p></div>
      <div class="member-card" aria-hidden="true"><span class="member-card__chip"></span><p>SUPER+<small>DIGITAL MEMBER PASS</small></p><strong>0248 · 0716 · 3502</strong><i>MEMBER SINCE / 2026</i></div>
      <div class="login-benefits"><article><span>01</span><p><b>购物车同步</b><small>跨设备继续选购</small></p></article><article><span>02</span><p><b>订单追踪</b><small>配送与售后一目了然</small></p></article><article><span>03</span><p><b>专属 Agent</b><small>记住偏好，不记住密码</small></p></article></div>
      <span class="login-story__index">SECURE ACCESS / 01</span>
    </section>

    <section class="login-form-panel" aria-labelledby="login-title">
      <header class="login-form-panel__top"><RouterLink to="/">← 返回商城</RouterLink><p>{{ mode === 'login' ? '还没有账户？' : '已有账户？' }} <button type="button" @click="switchMode(mode === 'login' ? 'register' : 'login')">{{ mode === 'login' ? '立即注册' : '立即登录' }}</button></p></header>
      <div class="login-form-wrap">
        <div class="login-heading"><p class="eyebrow">ACCOUNT ACCESS</p><h2 id="login-title">{{ mode === 'login' ? '欢迎回来' : '创建账户' }}</h2><p>{{ mode === 'login' ? '登录你的 SUPER MALL 账户' : '注册后即可同步订单与购物车' }}</p></div>
        <div class="login-tabs" role="tablist" aria-label="账户操作"><button type="button" role="tab" :aria-selected="mode === 'login'" :class="{ 'is-active': mode === 'login' }" @click="switchMode('login')">密码登录</button><button type="button" role="tab" :aria-selected="mode === 'register'" :class="{ 'is-active': mode === 'register' }" @click="switchMode('register')">注册账户</button></div>

        <form class="login-form" novalidate @submit.prevent="submit">
          <label v-if="mode === 'register'" class="form-field" :class="{ 'has-error': errors.name }"><span>昵称</span><input v-model="name" type="text" autocomplete="name" maxlength="50" placeholder="怎么称呼你" /><small>{{ errors.name }}</small></label>
          <label class="form-field" :class="{ 'has-error': errors.account }"><span>{{ mode === 'login' ? '手机号或邮箱' : '邮箱（与手机号至少填一项）' }}</span><input v-model="account" :type="mode === 'login' ? 'text' : 'email'" autocomplete="username" placeholder="name@example.com" /><small>{{ errors.account }}</small></label>
          <label v-if="mode === 'register'" class="form-field" :class="{ 'has-error': errors.phone }"><span>手机号（选填）</span><span class="phone-input"><i>+86</i><input v-model="phone" type="tel" inputmode="numeric" autocomplete="tel" maxlength="11" placeholder="请输入手机号" /></span><small>{{ errors.phone }}</small></label>
          <label class="form-field" :class="{ 'has-error': errors.password }"><span>密码</span><span class="password-input"><input v-model="password" :type="showPassword ? 'text' : 'password'" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" placeholder="至少 8 个字符" /><button type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword">{{ showPassword ? '隐藏' : '显示' }}</button></span><small>{{ errors.password }}</small></label>

          <div class="login-options"><label><input v-model="agreed" type="checkbox" /> <span>我已阅读并同意<a href="#">服务协议</a>和<a href="#">隐私政策</a></span></label><button v-if="mode === 'login'" type="button" @click="showUnavailable('找回密码需要邮件或短信服务，后续接入。')">忘记密码？</button></div>
          <small v-if="errors.agreed" class="agreement-error">{{ errors.agreed }}</small>
          <button class="login-submit" type="submit" :disabled="submitting"><span v-if="submitting" class="login-spinner" aria-hidden="true"></span>{{ submitting ? '正在提交…' : mode === 'login' ? '登录账户' : '注册并登录' }}<i v-if="!submitting">→</i></button>
          <Transition name="notice"><p v-if="notice" :class="['login-notice', `login-notice--${noticeTone}`]" role="status"><span>i</span>{{ notice }}</p></Transition>
        </form>

        <div class="login-security-note"><span>⌾</span><p><b>由后端安全认证保护</b><small>密码只用于本次加密传输，服务端使用 BCrypt 保存摘要。</small></p></div>
      </div>
      <footer class="login-footer"><span>© 2026 SUPER MALL</span><span>SECURED BY DESIGN</span></footer>
    </section>
  </main>
</template>
