<script setup>
import { nextTick, ref, watch } from 'vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  items: { type: Array, required: true },
  itemCount: { type: Number, default: 0 },
  subtotal: { type: Number, default: 0 },
  deliveryFee: { type: Number, default: 0 },
  total: { type: Number, default: 0 },
  shippingGap: { type: Number, default: 0 },
})
const emit = defineEmits(['close', 'update-quantity', 'remove', 'checkout'])
const closeButton = ref(null)
let previousFocus

watch(() => props.open, async (open) => {
  if (open) {
    previousFocus = document.activeElement
    await nextTick()
    closeButton.value?.focus()
  } else {
    previousFocus?.focus?.()
  }
})

function money(value) {
  return Number(value).toLocaleString('zh-CN')
}
</script>

<template>
  <Teleport to="body">
    <div
      class="drawer-layer"
      :class="{ 'is-open': open }"
      :aria-hidden="!open"
      :inert="!open"
      @click.self="emit('close')"
      @keydown.esc="emit('close')"
    >
      <aside class="cart-drawer" role="dialog" :aria-modal="open ? 'true' : undefined" aria-labelledby="cart-title">
        <header class="drawer-header">
          <div><p class="eyebrow">YOUR CART</p><h2 id="cart-title">购物车 <span>{{ itemCount }}</span></h2></div>
          <button ref="closeButton" type="button" aria-label="关闭购物车" @click="emit('close')">×</button>
        </header>

        <div v-if="items.length" class="shipping-progress">
          <p v-if="shippingGap">再选 <b>¥{{ money(shippingGap) }}</b> 即可免运费</p>
          <p v-else><b>已享免运费</b>，预计明日发出</p>
          <span><i :style="{ width: `${Math.min(100, subtotal / 3.99)}%` }"></i></span>
        </div>

        <div v-if="items.length" class="cart-items">
          <article v-for="item in items" :key="item.skuId" class="cart-item">
            <img :src="item.image" :alt="item.name" width="105" height="118" decoding="async" />
            <div class="cart-item__details">
              <h3>{{ item.name }}</h3><p>{{ item.skuLabel }}</p><strong>¥{{ money(item.price) }}</strong>
              <div class="cart-item__actions">
                <div class="quantity-control quantity-control--small">
                  <button type="button" aria-label="减少数量" @click="emit('update-quantity', item.skuId, item.quantity - 1)">−</button>
                  <span>{{ item.quantity }}</span>
                  <button type="button" aria-label="增加数量" :disabled="item.quantity >= item.stock" @click="emit('update-quantity', item.skuId, item.quantity + 1)">＋</button>
                </div>
                <button type="button" @click="emit('remove', item.skuId)">移除</button>
              </div>
            </div>
          </article>
        </div>

        <div v-else class="empty-cart">
          <span aria-hidden="true">▱</span>
          <h3>购物车还是空的</h3>
          <p>从本周精选中挑一件，让新装备先占个位置。</p>
          <button class="button button--dark" type="button" @click="emit('close')">继续逛逛</button>
        </div>

        <footer v-if="items.length" class="cart-summary">
          <dl><div><dt>商品小计</dt><dd>¥{{ money(subtotal) }}</dd></div><div><dt>配送费</dt><dd>{{ deliveryFee ? `¥${money(deliveryFee)}` : '免运费' }}</dd></div></dl>
          <p><span>应付合计 <small>含税</small></span><strong><i>¥</i>{{ money(total) }}</strong></p>
          <button class="button button--primary button--wide" type="button" @click="emit('checkout')">去结算 <span>→</span></button>
          <small>当前支持支付宝与微信模拟支付，不会发生真实扣款</small>
        </footer>
      </aside>
    </div>
  </Teleport>
</template>
