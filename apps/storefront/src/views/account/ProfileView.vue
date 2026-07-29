<script setup>
import { onUnmounted, reactive, ref, watch } from 'vue'
import AppToast from '../../components/AppToast.vue'
import { useCommerce } from '../../composables/useCommerce'
import { localDateInputValue, validateProfile } from '../../utils/profileValidation'

const { profile, saveProfile } = useCommerce()
const draft = reactive({ ...profile.value })
const errors = reactive({ nickname: '', phone: '', birthday: '' })
const saving = ref(false)
const toastVisible = ref(false)
const toastMessage = ref('')
const today = localDateInputValue()
let toastTimer

onUnmounted(() => window.clearTimeout(toastTimer))
watch(profile, (value) => Object.assign(draft, value), { deep: true })

function showToast(message) {
  toastMessage.value = message
  toastVisible.value = true
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toastVisible.value = false }, 2200)
}

async function save() {
  if (saving.value) return
  Object.assign(errors, validateProfile(draft, today))
  if (Object.values(errors).some(Boolean)) return

  saving.value = true
  try {
    await saveProfile({
      ...draft,
      nickname: draft.nickname.trim(),
      phone: draft.phone?.trim() || '',
    })
    showToast('个人资料已保存')
  } catch (error) {
    showToast(error.message || '个人资料保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="account-view">
    <header class="account-page-heading">
      <div><p class="eyebrow">PROFILE & SECURITY</p><h1>个人资料</h1><p>维护用于订单通知和账户展示的基本信息。</p></div>
    </header>

    <form class="profile-form" novalidate @submit.prevent="save">
      <section class="account-panel">
        <div class="profile-avatar">
          <span>{{ draft.nickname?.slice(0, 1) || 'S' }}</span>
          <div><b>账户头像</b><small>正式版本支持上传 JPG 或 PNG 图片</small></div>
          <button type="button" disabled>更换头像</button>
        </div>

        <div class="form-grid">
          <label class="form-field" :class="{ 'has-error': errors.nickname }">
            <span>昵称</span>
            <input v-model="draft.nickname" type="text" autocomplete="nickname" maxlength="50" placeholder="请输入昵称" :aria-invalid="Boolean(errors.nickname)" />
            <small>{{ errors.nickname }}</small>
          </label>
          <label>
            <span>性别</span>
            <select v-model="draft.gender"><option>保密</option><option>男</option><option>女</option></select>
          </label>
          <label class="form-field" :class="{ 'has-error': errors.phone }">
            <span>手机号码（选填）</span>
            <input v-model="draft.phone" type="tel" inputmode="numeric" autocomplete="tel" maxlength="11" placeholder="11 位手机号" :aria-invalid="Boolean(errors.phone)" />
            <small>{{ errors.phone }}</small>
          </label>
          <label>
            <span>电子邮箱（登录账号）</span>
            <input v-model="draft.email" type="email" autocomplete="email" readonly aria-readonly="true" />
            <small class="field-hint">登录账号暂不支持在此修改</small>
          </label>
          <label class="form-field" :class="{ 'has-error': errors.birthday }">
            <span>生日（选填）</span>
            <input v-model="draft.birthday" type="date" :max="today" :aria-invalid="Boolean(errors.birthday)" />
            <small>{{ errors.birthday }}</small>
          </label>
        </div>
      </section>

      <section class="account-panel security-panel">
        <p class="eyebrow">ACCOUNT SECURITY</p>
        <h2>账号安全</h2>
        <div><span>密码</span><p><b>已设置登录密码</b><small>建议定期更新，并避免与其他网站共用</small></p><button type="button" disabled>密码修改后续开放</button></div>
        <div><span>手机验证</span><p><b>{{ draft.phone || '未绑定' }}</b><small>用于账户登录和订单状态通知</small></p><button type="button" disabled>更换绑定</button></div>
      </section>

      <button class="button button--primary" type="submit" :disabled="saving">{{ saving ? '正在保存…' : '保存个人资料' }}</button>
    </form>
    <AppToast :visible="toastVisible" :message="toastMessage" />
  </div>
</template>
