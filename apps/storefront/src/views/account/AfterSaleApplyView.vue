<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useCommerce } from '../../composables/useCommerce'

const route = useRoute()
const router = useRouter()
const { getOrder, createAfterSale } = useCommerce()
const order = computed(() => getOrder(String(route.params.orderNo)))
const selectedSkuId = ref(order.value?.items[0]?.skuId || '')
const type = ref('退货退款')
const reason = ref('商品与描述不符')
const note = ref('')
const agreed = ref(true)
const submitting = ref(false)
const error = ref('')
const selectedItem = computed(() => order.value?.items.find((item) => item.skuId === selectedSkuId.value))

async function submit() {
  if (!selectedItem.value || !agreed.value) return
  submitting.value = true
  error.value = ''
  try {
    await createAfterSale({ orderNo: order.value.orderNo, item: selectedItem.value, type: type.value, reason: reason.value, note: note.value, quantity: selectedItem.value.quantity })
    await router.replace('/account/after-sales')
  } catch (cause) { error.value = cause.message || '售后申请提交失败' }
  finally { submitting.value = false }
}
</script>

<template>
  <div class="account-view">
    <nav class="account-breadcrumb"><RouterLink to="/account/after-sales">退款 / 售后</RouterLink><span>/</span><span>发起申请</span></nav>
    <header class="account-page-heading"><div><p class="eyebrow">SERVICE REQUEST</p><h1>申请售后</h1><p v-if="order">订单 {{ order.orderNo }}</p></div></header>
    <form v-if="order" class="after-sale-form" @submit.prevent="submit"><section class="account-panel"><p class="after-sale-form__label">01 · 选择商品</p><label v-for="item in order.items" :key="item.skuId" class="after-sale-product" :class="{ 'is-selected': selectedSkuId === item.skuId }"><input v-model="selectedSkuId" type="radio" :value="item.skuId" /><img :src="item.image" :alt="item.name" /><span><b>{{ item.name }}</b><small>{{ item.skuLabel }} · 数量 {{ item.quantity }}</small></span><strong>¥{{ (item.price * item.quantity).toLocaleString('zh-CN') }}</strong></label></section><section class="account-panel"><p class="after-sale-form__label">02 · 申请信息</p><div class="form-grid"><label><span>售后类型</span><select v-model="type"><option>退货退款</option><option>仅退款</option><option>换货</option></select></label><label><span>申请原因</span><select v-model="reason"><option>商品与描述不符</option><option>质量问题</option><option>收到商品破损</option><option>不喜欢 / 不想要</option><option>价格保护</option></select></label></div><label class="form-field"><span>问题说明</span><textarea v-model="note" rows="5" maxlength="300" placeholder="请描述具体问题，后续可扩展上传凭证图片"></textarea><small>{{ note.length }}/300</small></label></section><p v-if="error" class="form-alert form-alert--error">{{ error }}</p><section class="after-sale-submit"><div><b>预计退款 ¥{{ (selectedItem?.price * selectedItem?.quantity || 0).toLocaleString('zh-CN') }}</b><label><input v-model="agreed" type="checkbox" />我确认申请信息真实有效</label></div><button class="button button--primary" type="submit" :disabled="submitting || !agreed">{{ submitting ? '正在提交…' : '提交售后申请' }}</button></section></form>
    <div v-else class="commerce-empty"><span>404</span><h2>没有找到可售后的订单</h2><p>请返回订单列表重新选择。</p><RouterLink class="button button--dark" to="/account/orders">返回订单列表</RouterLink></div>
  </div>
</template>
