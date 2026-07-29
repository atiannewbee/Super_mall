import { computed, ref } from 'vue'
import { categories, products } from '../data/catalog'
import { api } from '../services/api'

const loading = ref(false)
const loaded = ref(false)
const loadError = ref('')

export function normalizeProduct(product) {
  return {
    ...product,
    id: product.id,
    price: Number(product.price),
    originalPrice: product.originalPrice == null ? null : Number(product.originalPrice),
    rating: Number(product.rating || 0),
    gallery: product.gallery || [],
    features: product.features || [],
    skus: (product.skus || []).map((sku) => ({
      ...sku,
      id: sku.skuCode,
      databaseId: sku.id,
      price: Number(sku.price),
      originalPrice: sku.originalPrice == null ? null : Number(sku.originalPrice),
    })),
  }
}

export async function loadCatalog(client = api, { force = false } = {}) {
  if ((loaded.value && !force) || loading.value) return products
  loading.value = true
  loadError.value = ''
  try {
    const [categoryData, productPage] = await Promise.all([
      client.get('/api/categories'),
      client.get('/api/products?size=100&sort=recommended'),
    ])
    const nextCategories = (categoryData || []).map((category) => ({
      ...category,
      id: category.slug,
      databaseId: category.id,
    }))
    const nextProducts = (productPage?.items || productPage?.content || productPage || []).map(normalizeProduct)
    if (nextCategories.length) categories.splice(0, categories.length, ...nextCategories)
    products.splice(0, products.length, ...nextProducts)
    loaded.value = true
    return products
  } catch (error) {
    loadError.value = error.message || '商品目录加载失败'
    throw error
  } finally {
    loading.value = false
  }
}

export function useCatalog() {
  const query = ref('')
  const activeCategory = ref('all')
  const normalizedQuery = computed(() => query.value.trim().toLocaleLowerCase('zh-CN'))
  const categoryAliases = {
    phones: '手机 通讯 安卓 旗舰机 影像',
    computers: '电脑 笔记本 办公 生产力',
    audio: '影音 耳机 音箱 蓝牙 降噪 音频',
    'smart-home': '智能家居 手表 穿戴 中枢 健康',
    accessories: '数码配件 键盘 充电器 桌搭 外设',
  }

  const filteredProducts = computed(() => products.filter((item) => {
    const inCategory = activeCategory.value === 'all' || item.categoryId === activeCategory.value
    if (!inCategory) return false
    if (!normalizedQuery.value) return true
    const categoryName = categories.find((category) => category.id === item.categoryId)?.name || ''
    return [item.name, item.brand, item.tagline, item.description, categoryName, categoryAliases[item.categoryId], ...(item.features || [])]
      .join(' ')
      .toLocaleLowerCase('zh-CN')
      .includes(normalizedQuery.value)
  }))

  const featuredProducts = computed(() => products.filter((item) => item.isFeatured))
  const dealProducts = computed(() => products.filter((item) => item.isDeal))
  const newProducts = computed(() => products.filter((item) => item.isNew))

  function setCategory(categoryId) {
    activeCategory.value = categoryId
  }

  function resetFilters() {
    query.value = ''
    activeCategory.value = 'all'
  }

  return {
    products, categories, query, activeCategory, filteredProducts, featuredProducts, dealProducts, newProducts,
    loading, loaded, loadError, loadCatalog, setCategory, resetFilters,
  }
}
