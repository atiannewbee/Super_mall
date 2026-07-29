<script setup>
import { computed, nextTick, onUnmounted, reactive, ref, watch } from 'vue'
import AppToast from '../../components/AppToast.vue'
import { useCommerce } from '../../composables/useCommerce'
import { getCityOptions, getDistrictOptions, getProvinceOptions, normalizeRegion } from '../../data/regions'

const { addresses, saveAddress, removeAddress, setDefaultAddress } = useCommerce()
const formOpen = ref(false)
const editingId = ref('')
const saving = ref(false)
const firstField = ref(null)
const errors = reactive({ name: '', phone: '', detail: '', postalCode: '' })
const draft = reactive(defaultDraft())
const toastVisible = ref(false)
const toastMessage = ref('')
let toastTimer

const provinceOptions = computed(() => getProvinceOptions(draft.province))
const cityOptions = computed(() => getCityOptions(draft.province, draft.city))
const districtOptions = computed(() => getDistrictOptions(draft.province, draft.city, draft.district))

watch(() => draft.province, () => {
  Object.assign(draft, normalizeRegion(draft))
})

watch(() => draft.city, () => {
  const districts = getDistrictOptions(draft.province, draft.city)
  if (districts.length && !districts.includes(draft.district)) draft.district = districts[0]
})

onUnmounted(() => window.clearTimeout(toastTimer))

function defaultDraft() {
  return {
    name: '', phone: '', province: '广东省', city: '深圳市', district: '南山区',
    detail: '', postalCode: '', tag: '家', isDefault: false,
  }
}

function showToast(message) {
  toastMessage.value = message
  toastVisible.value = true
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toastVisible.value = false }, 2200)
}

function clearErrors() {
  Object.keys(errors).forEach((key) => { errors[key] = '' })
}

function resetDraft() {
  Object.assign(draft, defaultDraft())
  editingId.value = ''
  clearErrors()
}

async function focusFirstField() {
  await nextTick()
  firstField.value?.focus()
}

function openAdd() {
  resetDraft()
  formOpen.value = true
  focusFirstField()
}

function openEdit(address) {
  resetDraft()
  editingId.value = address.id
  Object.assign(draft, {
    ...address,
    phone: address.phone || '',
    postalCode: address.postalCode || '',
  }, normalizeRegion(address))
  formOpen.value = true
  focusFirstField()
}

function closeForm() {
  if (!saving.value) formOpen.value = false
}

async function save() {
  clearErrors()
  if (!draft.name.trim()) errors.name = '请输入收货人姓名'
  if (!/^1[3-9]\d{9}$/.test(draft.phone)) errors.phone = '请输入有效的 11 位手机号'
  if (draft.detail.trim().length < 5) errors.detail = '请填写详细到门牌号的地址'
  if (draft.postalCode && !/^\d{6}$/.test(draft.postalCode)) errors.postalCode = '邮政编码应为 6 位数字'
  if (Object.values(errors).some(Boolean)) return

  saving.value = true
  try {
    const wasEditing = Boolean(editingId.value)
    await saveAddress({ ...draft, id: editingId.value || undefined })
    formOpen.value = false
    showToast(wasEditing ? '地址已更新' : '地址已添加')
  } catch (error) {
    showToast(error.message || '地址保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(id) {
  if (addresses.value.length <= 1) return showToast('至少保留一个收货地址')
  try { await removeAddress(id); showToast('地址已删除') }
  catch (error) { showToast(error.message || '地址删除失败') }
}

async function makeDefault(id) {
  try { await setDefaultAddress(id); showToast('已设为默认地址') }
  catch (error) { showToast(error.message || '设置默认地址失败') }
}
</script>

<template>
  <div class="account-view">
    <header class="account-page-heading">
      <div><p class="eyebrow">ADDRESS BOOK</p><h1>收货地址</h1><p>管理结算时可使用的配送地址。</p></div>
      <button class="button button--primary" type="button" @click="openAdd">＋ 新增地址</button>
    </header>

    <div v-if="addresses.length" class="address-grid">
      <article v-for="address in addresses" :key="address.id" :class="{ 'is-default': address.isDefault }">
        <header><span>{{ address.tag || '地址' }}</span><i v-if="address.isDefault">默认地址</i></header>
        <h2>{{ address.name }} <small>{{ address.phone }}</small></h2>
        <p>{{ address.province }} {{ address.city }} {{ address.district }}<br />{{ address.detail }}</p>
        <footer>
          <button v-if="!address.isDefault" type="button" @click="makeDefault(address.id)">设为默认</button><span></span>
          <button type="button" @click="openEdit(address)">编辑</button>
          <button type="button" @click="remove(address.id)">删除</button>
        </footer>
      </article>
    </div>
    <div v-else class="commerce-empty">
      <span>＋</span><h2>还没有收货地址</h2><p>添加地址后即可继续结算。</p>
      <button class="button button--dark" type="button" @click="openAdd">新增地址</button>
    </div>

    <Teleport to="body">
      <div v-if="formOpen" class="form-overlay" @click.self="closeForm" @keydown.esc="closeForm">
        <section class="address-form-dialog" role="dialog" aria-modal="true" aria-labelledby="address-form-title">
          <header>
            <div><p class="eyebrow">DELIVERY ADDRESS</p><h2 id="address-form-title">{{ editingId ? '编辑地址' : '新增地址' }}</h2></div>
            <button type="button" aria-label="关闭" :disabled="saving" @click="closeForm">×</button>
          </header>
          <form novalidate @submit.prevent="save">
            <div class="form-grid">
              <label class="form-field" :class="{ 'has-error': errors.name }"><span>收货人</span><input ref="firstField" v-model.trim="draft.name" type="text" autocomplete="name" maxlength="50" placeholder="请输入姓名" /><small>{{ errors.name }}</small></label>
              <label class="form-field" :class="{ 'has-error': errors.phone }"><span>手机号</span><input v-model.trim="draft.phone" type="tel" inputmode="numeric" autocomplete="tel" maxlength="11" placeholder="11 位手机号" /><small>{{ errors.phone }}</small></label>
            </div>

            <div class="region-fields">
              <p>配送地区 <small>省、市、区会自动联动</small></p>
              <div class="form-grid form-grid--three">
                <label><span>省份</span><select v-model="draft.province"><option v-for="province in provinceOptions" :key="province" :value="province">{{ province }}</option></select></label>
                <label><span>城市</span><select v-model="draft.city" :disabled="!cityOptions.length"><option v-for="city in cityOptions" :key="city" :value="city">{{ city }}</option></select></label>
                <label><span>区县</span><select v-model="draft.district" :disabled="!districtOptions.length"><option v-for="district in districtOptions" :key="district" :value="district">{{ district }}</option></select></label>
              </div>
            </div>

            <label class="form-field" :class="{ 'has-error': errors.detail }"><span>详细地址</span><input v-model.trim="draft.detail" type="text" autocomplete="street-address" maxlength="255" placeholder="街道、楼栋、门牌号" /><small>{{ errors.detail }}</small></label>
            <div class="form-grid">
              <label class="form-field" :class="{ 'has-error': errors.postalCode }"><span>邮政编码（选填）</span><input v-model.trim="draft.postalCode" type="text" inputmode="numeric" autocomplete="postal-code" maxlength="6" placeholder="6 位邮政编码" /><small>{{ errors.postalCode }}</small></label>
              <label><span>地址标签</span><select v-model="draft.tag"><option>家</option><option>公司</option><option>学校</option><option>其他</option></select></label>
            </div>
            <label class="form-checkbox"><input v-model="draft.isDefault" type="checkbox" />设为默认收货地址</label>
            <button class="button button--primary button--wide" type="submit" :disabled="saving">{{ saving ? '正在保存…' : '保存地址' }}</button>
          </form>
        </section>
      </div>
    </Teleport>
    <AppToast :visible="toastVisible" :message="toastMessage" />
  </div>
</template>
