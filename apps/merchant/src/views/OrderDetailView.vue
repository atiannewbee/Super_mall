<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import StatusBadge from '../components/StatusBadge.vue'
import { api, errorMessage } from '../services/api'
import { dateTime, money, statusLabel } from '../utils/format'

const route = useRoute()
const order = ref(null)
const loading = ref(true)
const acting = ref(false)
const error = ref('')
const success = ref('')
const showShipForm = ref(false)
const shipment = ref({ carrierCode: 'SF', carrierName: '顺丰速运', trackingNo: '' })

const canPick = computed(() => order.value?.paymentStatus === 'paid'
  && order.value?.fulfillmentStatus === 'unfulfilled')
const canShip = computed(() => order.value?.fulfillmentStatus === 'picking')

async function load() {
  loading.value = true
  error.value = ''
  try {
    order.value = await api.get(`/api/merchant/orders/${route.params.orderNo}`)
  } catch (cause) {
    error.value = errorMessage(cause, '无法加载订单详情')
  } finally {
    loading.value = false
  }
}

async function startPicking() {
  acting.value = true
  error.value = ''
  try {
    order.value = await api.post(`/api/merchant/orders/${order.value.orderNo}/picking`)
    success.value = '已进入拣货流程'
  } catch (cause) {
    error.value = errorMessage(cause, '无法开始拣货')
  } finally {
    acting.value = false
  }
}

async function ship() {
  if (!shipment.value.trackingNo.trim()) {
    error.value = '请输入物流单号'
    return
  }
  acting.value = true
  error.value = ''
  try {
    order.value = await api.post(`/api/merchant/orders/${order.value.orderNo}/ship`, shipment.value)
    success.value = '发货成功，消费者端已经可以查看物流信息'
    showShipForm.value = false
  } catch (cause) {
    error.value = errorMessage(cause, '发货失败')
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="order-detail-page page-enter">
    <div class="detail-breadcrumb">
      <RouterLink :to="{ name: 'orders' }">订单履约</RouterLink>
      <span>/</span>
      <strong>{{ route.params.orderNo }}</strong>
    </div>

    <div v-if="error" class="inline-error"><span>{{ error }}</span><button @click="load">重试</button></div>
    <div v-if="success" class="inline-success"><span>{{ success }}</span><button @click="success = ''">×</button></div>

    <template v-if="order">
      <section class="detail-hero">
        <div>
          <span class="section-index">ORDER / {{ order.id }}</span>
          <h2>{{ order.orderNo }}</h2>
          <p>创建于 {{ dateTime(order.createdAt) }}</p>
        </div>
        <div class="detail-statuses">
          <StatusBadge :value="order.paymentStatus" />
          <StatusBadge :value="order.fulfillmentStatus" />
        </div>
        <div class="detail-actions">
          <button v-if="canPick" class="primary-action" :disabled="acting" @click="startPicking">
            {{ acting ? '处理中…' : '确认开始拣货' }} <b>→</b>
          </button>
          <button v-if="canShip" class="primary-action" @click="showShipForm = !showShipForm">
            填写物流并发货 <b>→</b>
          </button>
          <span v-if="order.fulfillmentStatus === 'shipped'" class="done-stamp">已完成出库</span>
        </div>
      </section>

      <section v-if="showShipForm" class="ship-panel">
        <div class="panel-head">
          <div><span class="section-index">SHIPMENT</span><h2>录入发货信息</h2></div>
          <button class="close-button" @click="showShipForm = false">×</button>
        </div>
        <form class="ship-form" @submit.prevent="ship">
          <label class="field">
            <span>物流公司代码</span>
            <input v-model="shipment.carrierCode" maxlength="30" placeholder="例如 SF" />
          </label>
          <label class="field">
            <span>物流公司名称</span>
            <input v-model="shipment.carrierName" maxlength="80" placeholder="例如 顺丰速运" />
          </label>
          <label class="field wide">
            <span>物流单号</span>
            <input v-model="shipment.trackingNo" maxlength="100" placeholder="扫描或输入物流单号" />
          </label>
          <button class="primary-action" :disabled="acting">
            {{ acting ? '正在发货…' : '确认发货' }} <b>→</b>
          </button>
        </form>
      </section>

      <div class="detail-grid">
        <section class="panel detail-products">
          <div class="panel-head">
            <div><span class="section-index">01 / ITEMS</span><h2>商品清单</h2></div>
            <strong>{{ order.itemCount }} 件</strong>
          </div>
          <article v-for="item in order.items" :key="item.id" class="detail-product">
            <img :src="item.image" :alt="item.productName" />
            <div>
              <strong>{{ item.productName }}</strong>
              <span>{{ item.skuLabel }}</span>
              <small>SKU / {{ item.skuCode }}</small>
            </div>
            <div class="product-quantity">× {{ item.quantity }}</div>
            <div class="product-price">{{ money(item.lineAmount) }}</div>
          </article>
          <dl class="order-totals">
            <div><dt>商品小计</dt><dd>{{ money(order.subtotal) }}</dd></div>
            <div><dt>运费</dt><dd>{{ money(order.deliveryFee) }}</dd></div>
            <div><dt>优惠</dt><dd>− {{ money(order.discount) }}</dd></div>
            <div class="grand-total"><dt>实付金额</dt><dd>{{ money(order.paidAmount || order.total) }}</dd></div>
          </dl>
        </section>

        <aside class="detail-side">
          <section class="panel recipient-card">
            <span class="section-index">02 / RECIPIENT</span>
            <h2>收货信息</h2>
            <strong>{{ order.recipient.name }} · {{ order.recipient.phone }}</strong>
            <p>{{ order.recipient.province }} {{ order.recipient.city }} {{ order.recipient.district }}<br />
              {{ order.recipient.detail }}</p>
            <div v-if="order.buyerNote" class="buyer-note"><span>买家留言</span>{{ order.buyerNote }}</div>
          </section>

          <section v-if="order.shipment" class="panel shipment-card">
            <span class="section-index">03 / SHIPMENT</span>
            <h2>{{ order.shipment.carrierName }}</h2>
            <button
              class="tracking-number"
              title="点击复制"
              @click="navigator.clipboard?.writeText(order.shipment.trackingNo)"
            >{{ order.shipment.trackingNo }}</button>
            <p>{{ order.shipment.warehouseName }} · {{ dateTime(order.shipment.shippedAt) }}</p>
          </section>
        </aside>
      </div>

      <section class="panel timeline-panel">
        <div class="panel-head">
          <div><span class="section-index">04 / AUDIT TRAIL</span><h2>订单轨迹</h2></div>
          <span>共 {{ order.timeline.length }} 条记录</span>
        </div>
        <ol class="timeline">
          <li v-for="event in [...order.timeline].reverse()" :key="`${event.createdAt}-${event.type}`">
            <i></i>
            <time>{{ dateTime(event.createdAt) }}</time>
            <div>
              <strong>{{ statusLabel(event.toStatus) }}</strong>
              <span>{{ event.note }}</span>
            </div>
            <small>{{ event.operatorType === 'merchant' ? '商家操作' : '系统 / 用户' }}</small>
          </li>
        </ol>
      </section>
    </template>

    <div v-else-if="loading" class="page-loading">正在读取订单记录…</div>
  </div>
</template>
