<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, errorMessage } from '../services/api'
import { dateTime, money } from '../utils/format'

const route = useRoute()
const router = useRouter()
const page = ref(0)
const size = 20
const query = ref(String(route.query.query || ''))
const lowStock = ref(route.query.lowStock === 'true')
const result = ref({ items: [], totalElements: 0, totalPages: 0 })
const loading = ref(false)
const error = ref('')
const categories = ref([])
const editorOpen = ref(false)
const editing = ref(null)
const saving = ref(false)
const editorError = ref('')
const form = ref({})

/*
 * 首版商品管理复用库存列表：新增创建一个默认 SKU，编辑作用于当前 SKU，
 * 删除则软删除整个商品。这样不增加路由，也不维护第二套商品列表状态。
 */

const availableTotal = computed(() => result.value.items.reduce((sum, item) => sum + item.availableQuantity, 0))
const editorTitle = computed(() => editing.value ? '修改商品' : '新增商品')

function emptyForm(item = {}) {
  return {
    name: item.productName || '',
    categoryId: item.categoryId || categories.value[0]?.id || '',
    imageUrl: item.image || '',
    tagline: item.tagline || '',
    description: item.description || '',
    status: item.productStatus || 'active',
    skuCode: item.skuCode || '',
    skuLabel: item.skuLabel || '标准款',
    price: item.price ?? '',
    originalPrice: item.originalPrice ?? '',
    availableQuantity: item.availableQuantity ?? 0,
  }
}

async function load() {
  loading.value = true
  error.value = ''
  const params = new URLSearchParams({ page: page.value, size, lowStock: lowStock.value })
  if (query.value.trim()) params.set('query', query.value.trim())
  try {
    result.value = await api.get(`/api/merchant/inventory?${params}`)
  } catch (cause) {
    error.value = errorMessage(cause, '无法加载库存')
  } finally {
    loading.value = false
  }
}

function apply() {
  page.value = 0
  router.replace({
    query: {
      ...(query.value.trim() ? { query: query.value.trim() } : {}),
      ...(lowStock.value ? { lowStock: 'true' } : {}),
    },
  })
  load()
}

function openEditor(item) {
  editing.value = item || null
  form.value = emptyForm(item)
  editorError.value = ''
  editorOpen.value = true
}

async function save() {
  saving.value = true
  editorError.value = ''
  const payload = {
    ...form.value,
    categoryId: Number(form.value.categoryId),
    price: Number(form.value.price),
    originalPrice: form.value.originalPrice === '' ? null : Number(form.value.originalPrice),
    availableQuantity: Number(form.value.availableQuantity),
  }
  try {
    if (editing.value) {
      await api.put(`/api/merchant/products/${editing.value.productId}/skus/${editing.value.skuId}`, payload)
    } else {
      await api.post('/api/merchant/products', payload)
    }
    editorOpen.value = false
    await load()
  } catch (cause) {
    editorError.value = errorMessage(cause, '商品保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(item) {
  if (!window.confirm(`确定删除“${item.productName}”及其全部 SKU 吗？历史订单不会受影响。`)) return
  try {
    await api.delete(`/api/merchant/products/${item.productId}`)
    await load()
  } catch (cause) {
    error.value = errorMessage(cause, '商品删除失败')
  }
}

onMounted(async () => {
  await Promise.all([
    load(),
    api.get('/api/categories', { auth: false }).then((items) => { categories.value = items }),
  ])
})
</script>

<template>
  <div class="inventory-page page-enter">
    <div class="section-heading split-heading">
      <div>
        <span class="section-index">01 / STOCK</span>
        <h2>商品与库存</h2>
        <p>在同一张 SKU 库存表中新增、修改和下架商品。</p>
      </div>
      <div class="inventory-heading-tools">
        <div class="inventory-summary">
          <div><strong>{{ result.totalElements }}</strong><span>SKU</span></div>
          <div><strong>{{ availableTotal }}</strong><span>本页可售</span></div>
        </div>
        <button class="primary-action" type="button" @click="openEditor()">新增商品 <b>＋</b></button>
      </div>
    </div>

    <section class="filter-bar">
      <form class="search-box inventory-search" @submit.prevent="apply">
        <span aria-hidden="true">⌕</span>
        <input v-model="query" placeholder="商品名 / SKU 编码 / 规格" maxlength="100" />
        <button>搜索</button>
      </form>
      <label class="toggle-filter">
        <input v-model="lowStock" type="checkbox" @change="apply" />
        <i></i>
        <span>仅看低库存</span>
      </label>
    </section>

    <div v-if="error" class="inline-error"><span>{{ error }}</span><button @click="load">重试</button></div>

    <section class="panel inventory-panel" :class="{ loading }">
      <div class="table-wrap">
        <table class="inventory-table">
          <thead>
            <tr>
              <th>商品 / SKU</th>
              <th>售价</th>
              <th>可售</th>
              <th>订单锁定</th>
              <th>累计售出</th>
              <th>商品状态</th>
              <th>库存状态</th>
              <th>更新时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in result.items" :key="item.skuId">
              <td>
                <div class="inventory-product">
                  <img :src="item.image" :alt="item.productName" />
                  <div>
                    <strong>{{ item.productName }}</strong>
                    <span>{{ item.skuLabel }}</span>
                    <small>{{ item.skuCode }}</small>
                  </div>
                </div>
              </td>
              <td>{{ money(item.price) }}</td>
              <td><strong class="stock-number">{{ item.availableQuantity }}</strong></td>
              <td>{{ item.lockedQuantity }}</td>
              <td>{{ item.soldQuantity }}</td>
              <td><span class="product-state" :data-status="item.productStatus">{{ item.productStatus === 'active' ? '已上架' : item.productStatus === 'draft' ? '草稿' : '已下架' }}</span></td>
              <td>
                <span class="stock-state" :data-low="item.lowStock">
                  {{ item.lowStock ? '需要补货' : '库存正常' }}
                </span>
              </td>
              <td>{{ dateTime(item.updatedAt) }}</td>
              <td>
                <div class="row-actions">
                  <button type="button" @click="openEditor(item)">编辑</button>
                  <button type="button" class="danger" @click="remove(item)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="!loading && !result.items.length">
              <td colspan="9" class="empty-cell">没有匹配的商品，点击“新增商品”创建第一件商品。</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <div v-if="result.totalPages > 1" class="pagination">
      <button :disabled="page === 0" @click="page--; load()">← 上一页</button>
      <span>{{ page + 1 }} / {{ result.totalPages }}</span>
      <button :disabled="page + 1 >= result.totalPages" @click="page++; load()">下一页 →</button>
    </div>

    <Teleport to="body">
      <div v-if="editorOpen" class="modal-layer" @click.self="editorOpen = false">
        <form class="product-modal" @submit.prevent="save">
          <div class="panel-head">
            <div><span class="section-index">PRODUCT / EDITOR</span><h2>{{ editorTitle }}</h2></div>
            <button type="button" class="close-button" aria-label="关闭商品表单" @click="editorOpen = false">×</button>
          </div>
          <div class="product-form">
            <div v-if="editorError" class="form-message error field--wide">{{ editorError }}</div>
            <label class="field"><span>商品名称</span><input v-model.trim="form.name" required maxlength="160" /></label>
            <label class="field"><span>分类</span><select v-model="form.categoryId" required><option v-for="item in categories" :key="item.id" :value="item.id">{{ item.name }}</option></select></label>
            <label class="field field--wide"><span>封面图片 URL</span><input v-model.trim="form.imageUrl" type="url" required maxlength="500" /></label>
            <label class="field field--wide"><span>一句话简介</span><input v-model.trim="form.tagline" maxlength="255" /></label>
            <label class="field field--wide"><span>商品说明</span><textarea v-model.trim="form.description" rows="3" maxlength="5000"></textarea></label>
            <label class="field"><span>商品状态</span><select v-model="form.status"><option value="active">立即上架</option><option value="draft">保存草稿</option><option value="inactive">暂时下架</option></select></label>
            <label class="field"><span>SKU 编码</span><input v-model.trim="form.skuCode" required pattern="[A-Za-z0-9][A-Za-z0-9._\-]*" maxlength="80" /></label>
            <label class="field"><span>规格名称</span><input v-model.trim="form.skuLabel" required maxlength="255" /></label>
            <label class="field"><span>售价</span><input v-model="form.price" type="number" required min="0" step="0.01" /></label>
            <label class="field"><span>原价（可选）</span><input v-model="form.originalPrice" type="number" min="0" step="0.01" /></label>
            <label class="field"><span>可售库存</span><input v-model="form.availableQuantity" type="number" required min="0" max="100000000" step="1" /></label>
            <button class="primary-action field--wide" :disabled="saving">{{ saving ? '正在保存…' : '保存商品' }} <b>→</b></button>
          </div>
        </form>
      </div>
    </Teleport>
  </div>
</template>
