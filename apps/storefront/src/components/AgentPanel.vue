<script setup>
import { nextTick, ref, watch } from 'vue'

const props = defineProps({ open: { type: Boolean, default: false } })
const emit = defineEmits(['close'])
const input = ref('')
const inputField = ref(null)
const messageList = ref(null)
const messages = ref([
  { role: 'agent', text: '你好，我是 SUPER 选购助手。告诉我预算和使用场景，我可以从当前商品中帮你缩小范围。' },
])

const prompts = ['预算 5000 元买手机', '通勤降噪耳机', '多久可以送到？', '查询订单']

watch(() => props.open, async (open) => {
  if (open) {
    await nextTick()
    inputField.value?.focus()
  }
})

function answer(text) {
  if (/订单|物流/.test(text)) return '订单查询需要登录后调用真实订单接口。当前是前端原型，我不会编造订单状态；后续会安全接入订单号与身份校验。'
  if (/退|售后/.test(text)) return '未拆封商品支持 7 天无理由退货。数码产品激活后会按品类售后规则处理，正式规则将来自售后知识库。'
  if (/耳机|降噪|通勤/.test(text)) return '通勤优先推荐 Pulse Studio Max：-48dB 主动降噪和 60 小时续航。如果更看重轻便，可以看看 ArcPods Pro 2。'
  if (/手机|5000|影像/.test(text)) return '预算 5000 元以内，Aether X1 Pro 的 256GB 版本最合适：影像能力强，当前 ¥4,299，还有预算搭配耳机或充电配件。'
  if (/送|到货|配送/.test(text)) return '核心城市现货商品通常次日送达，具体时效需要在结算页根据收货地址和库存仓确认。'
  return '我可以继续从预算、便携性、性能、续航或使用场景帮你比较。你最在意哪一点？'
}

async function send(text = input.value) {
  const clean = text.trim()
  if (!clean) return
  messages.value.push({ role: 'user', text: clean })
  messages.value.push({ role: 'agent', text: answer(clean) })
  input.value = ''
  await nextTick()
  messageList.value?.scrollTo({ top: messageList.value.scrollHeight, behavior: 'smooth' })
}
</script>

<template>
  <Teleport to="body">
    <aside v-if="open" class="agent-panel" role="dialog" aria-modal="true" aria-labelledby="agent-title" @keydown.esc="emit('close')">
      <header class="agent-panel__header">
        <span class="agent-avatar">✦</span>
        <div><h2 id="agent-title">SUPER Agent</h2><p><i></i> 选购助手在线</p></div>
        <button type="button" aria-label="关闭客服" @click="emit('close')">×</button>
      </header>
      <div ref="messageList" class="agent-messages" aria-live="polite">
        <p class="prototype-note">前端功能预览 · 暂未连接真实订单和 AI 模型</p>
        <div v-for="(message, index) in messages" :key="index" :class="['agent-message', `agent-message--${message.role}`]">
          <span v-if="message.role === 'agent'">✦</span><p>{{ message.text }}</p>
        </div>
      </div>
      <div class="agent-prompts">
        <button v-for="prompt in prompts" :key="prompt" type="button" @click="send(prompt)">{{ prompt }}</button>
      </div>
      <form class="agent-input" @submit.prevent="send()">
        <input ref="inputField" v-model="input" type="text" placeholder="输入你的问题…" aria-label="向选购助手提问" />
        <button type="submit" aria-label="发送消息">↑</button>
      </form>
    </aside>
  </Teleport>
</template>
