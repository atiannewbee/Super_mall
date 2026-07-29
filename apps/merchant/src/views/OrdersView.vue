<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StatusBadge from '../components/StatusBadge.vue'
import { api, errorMessage } from '../services/api'
import { dateTime, money } from '../utils/format'

const route = useRoute()
const router = useRouter()
const page = ref(0)
const size = 12
const query = ref(String(route.query.query || ''))
const fulfillmentStatus = ref(String(route.query.fulfillmentStatus || 'all'))
const result = ref({ items: [], totalElements: 0, totalPages: 0 })
const loading = ref(false)
const error = ref('')
const acting = ref('')

const filters = [
  ['all', '全部'],
  ['unfulfilled', '待拣货'],
  ['picking', '拣货中'],
  ['shipped', '已发货'],
  ['delivered', '已签收'],
]

const canPrevious = computed(() => page.value > 0)
const canNext = computed(() => page.value + 1 < result.value.totalPages)

async function load() {
  loading.value = true
  error.value = ''
  const params = new URLSearchParams({ page: page.value, size })
  if (query.value.trim()) params.set('query', query.value.trim())
  if (fulfillmentStatus.value !== 'all') params.set('fulfillmentStatus', fulfillmentStatus.value)
  try {
    result.value = await api.get(`/api/merchant/orders?${params}`)
  } catch (cause) {
    error.value = errorMessage(cause, '无法加载订单')
  } finally {
    loading.value = false
  }
}

async function startPicking(order) {
  acting.value = order.orderNo
  error.value = ''
  try {
    await api.post(`/api/merchant/orders/${order.orderNo}/picking`)
    await load()
  } catch (cause) {
    error.value = errorMessage(cause, '无法开始拣货')
  } finally {
    acting.value = ''
  }
}

function applyFilters() {
  page.value = 0
  router.replace({
    query: {
      ...(query.value.trim() ? { query: query.value.trim() } : {}),
      ...(fulfillmentStatus.value !== 'all' ? { fulfillmentStatus: fulfillmentStatus.value } : {}),
    },
  })
  load()
}

watch(fulfillmentStatus, applyFilters)
onMounted(load)
</script>

<template>
  <div class="orders-page page-enter">
    <div class="section-heading split-heading">
      <div>
        <span class="section-index">01 / FULFILLMENT</span>
        <h2>订单履约队列</h2>
        <p>先确认付款，再开始拣货；发货后不可重复操作。</p>
      </div>
      <div class="count-block"><strong>{{ result.totalElements }}</strong><span>笔匹配订单</span></div>
    </div>

    <section class="filter-bar">
      <div class="segmented-filter" aria-label="履约状态">
        <button
          v-for="[value, label] in filters"
          :key="value"
          :class="{ active: fulfillmentStatus === value }"
          @click="fulfillmentStatus = value"
        >{{ label }}</button>
      </div>
      <form class="search-box" @submit.prevent="applyFilters">
        <span aria-hidden="true">⌕</span>
        <input v-model="query" placeholder="订单号 / 收货人 / 手机号" maxlength="100" />
        <button>搜索</button>
      </form>
    </section>

    <div v-if="error" class="inline-error"><span>{{ error }}</span><button @click="load">重试</button></div>

    <section class="panel order-list-panel" :class="{ loading }">
      <article v-for="order in result.items" :key="order.orderNo" class="order-row">
        <div class="order-row-main">
          <div class="order-number">
            <span>ORDER</span>
            <RouterLink :to="{ name: 'order-detail', params: { orderNo: order.orderNo } }">
              {{ order.orderNo }}
            </RouterLink>
            <small>{{ dateTime(order.createdAt) }}</small>
          </div>
          <div class="order-recipient">
            <strong>{{ order.recipient.name }}</strong>
            <span>{{ order.recipient.phone }}</span>
            <small>{{ order.recipient.province }} {{ order.recipient.city }}</small>
          </div>
          <div class="order-items-preview">
            <strong>{{ order.items[0]?.productName }}</strong>
            <span>{{ order.items[0]?.skuLabel }} × {{ order.items[0]?.quantity }}</span>
            <small v-if="order.items.length > 1">另有 {{ order.items.length - 1 }} 种商品</small>
          </div>
          <div class="order-status-stack">
            <StatusBadge :value="order.paymentStatus" />
            <StatusBadge :value="order.fulfillmentStatus" />
          </div>
          <div class="order-amount">
            <strong>{{ money(order.total) }}</strong>
            <span>{{ order.itemCount }} 件商品</span>
          </div>
        </div>
        <div class="order-row-actions">
          <button
            v-if="order.paymentStatus === 'paid' && order.fulfillmentStatus === 'unfulfilled'"
            class="compact-action"
            :disabled="acting === order.orderNo"
            @click="startPicking(order)"
          >{{ acting === order.orderNo ? '处理中…' : '开始拣货' }}</button>
          <RouterLink
            v-else-if="order.fulfillmentStatus === 'picking'"
            class="compact-action"
            :to="{ name: 'order-detail', params: { orderNo: order.orderNo } }"
          >去发货</RouterLink>
          <RouterLink
            class="text-action"
            :to="{ name: 'order-detail', params: { orderNo: order.orderNo } }"
          >查看详情 →</RouterLink>
        </div>
      </article>

      <div v-if="!loading && !result.items.length" class="empty-state">
        <div class="empty-symbol">□</div>
        <h3>没有匹配的订单</h3>
        <p>试试切换履约状态或清空搜索条件。</p>
      </div>
    </section>

    <div v-if="result.totalPages > 1" class="pagination">
      <button :disabled="!canPrevious" @click="page--; load()">← 上一页</button>
      <span>{{ page + 1 }} / {{ result.totalPages }}</span>
      <button :disabled="!canNext" @click="page++; load()">下一页 →</button>
    </div>
  </div>
</template>
