<script setup>
import { onMounted, ref } from 'vue'
import { api, errorMessage } from '../services/api'
import { dateTime, money } from '../utils/format'
import StatusBadge from '../components/StatusBadge.vue'

const metrics = ref(null)
const orders = ref([])
const loading = ref(true)
const error = ref('')

const cards = [
  { key: 'unfulfilledOrders', label: '待拣货', note: '已付款，等待仓库接单', tone: 'lime' },
  { key: 'pickingOrders', label: '拣货中', note: '正在仓内处理', tone: 'amber' },
  { key: 'shippedToday', label: '今日发货', note: '今日完成出库', tone: 'blue' },
  { key: 'lowStockSkus', label: '低库存 SKU', note: '可售库存 ≤ 10', tone: 'red' },
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [dashboard, recent] = await Promise.all([
      api.get('/api/merchant/dashboard'),
      api.get('/api/merchant/orders?size=5'),
    ])
    metrics.value = dashboard
    orders.value = recent.items
  } catch (cause) {
    error.value = errorMessage(cause, '无法加载运营数据')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="dashboard-page page-enter">
    <div class="section-heading split-heading">
      <div>
        <span class="section-index">01 / PULSE</span>
        <h2>今天需要你关注的事</h2>
        <p>数字来自当前店铺的实时订单与库存。</p>
      </div>
      <div class="revenue-chip">
        <span>今日已支付金额</span>
        <strong>{{ money(metrics?.todayPaidAmount) }}</strong>
      </div>
    </div>

    <div v-if="error" class="inline-error">
      <span>{{ error }}</span>
      <button @click="load">重新加载</button>
    </div>

    <div class="metric-grid" :class="{ loading }">
      <article v-for="(card, index) in cards" :key="card.key" class="metric-card" :data-tone="card.tone">
        <span class="metric-sequence">0{{ index + 1 }}</span>
        <div class="metric-value">{{ loading ? '—' : metrics?.[card.key] ?? 0 }}</div>
        <h3>{{ card.label }}</h3>
        <p>{{ card.note }}</p>
        <RouterLink
          :to="card.key === 'lowStockSkus'
            ? { name: 'inventory', query: { lowStock: 'true' } }
            : { name: 'orders', query: { fulfillmentStatus: card.key === 'pickingOrders' ? 'picking' : undefined } }"
        >查看详情 ↗</RouterLink>
      </article>
    </div>

    <div class="dashboard-lower">
      <section class="panel recent-orders">
        <div class="panel-head">
          <div>
            <span class="section-index">02 / RECENT</span>
            <h2>最新订单</h2>
          </div>
          <RouterLink :to="{ name: 'orders' }">全部订单 →</RouterLink>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>订单</th>
                <th>收货人</th>
                <th>履约</th>
                <th>金额</th>
                <th>创建时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="order in orders" :key="order.orderNo">
                <td>
                  <RouterLink :to="{ name: 'order-detail', params: { orderNo: order.orderNo } }">
                    {{ order.orderNo }}
                  </RouterLink>
                </td>
                <td>{{ order.recipient.name }}<small>{{ order.recipient.city }}</small></td>
                <td><StatusBadge :value="order.fulfillmentStatus" /></td>
                <td>{{ money(order.total) }}</td>
                <td>{{ dateTime(order.createdAt) }}</td>
              </tr>
              <tr v-if="!loading && !orders.length">
                <td colspan="5" class="empty-cell">暂无订单，新的交易会出现在这里。</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <aside class="panel process-card">
        <span class="section-index">03 / FLOW</span>
        <h2>标准履约路径</h2>
        <ol>
          <li class="done"><i>1</i><div><strong>支付确认</strong><span>系统自动核验</span></div></li>
          <li><i>2</i><div><strong>开始拣货</strong><span>商家确认处理</span></div></li>
          <li><i>3</i><div><strong>填写物流</strong><span>生成发货记录</span></div></li>
          <li><i>4</i><div><strong>用户收货</strong><span>履约闭环完成</span></div></li>
        </ol>
        <p>每次状态变更都会记录操作者和时间。</p>
      </aside>
    </div>
  </div>
</template>
