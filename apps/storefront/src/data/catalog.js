export const categories = [
  { id: 'phones', name: '手机通讯', icon: '01', description: '影像与移动性能' },
  { id: 'computers', name: '电脑办公', icon: '02', description: '轻薄本与桌面装备' },
  { id: 'audio', name: '影音娱乐', icon: '03', description: '沉浸聆听与创作' },
  { id: 'smart-home', name: '智能家居', icon: '04', description: '让空间主动响应' },
  { id: 'accessories', name: '数码配件', icon: '05', description: '精简但关键的装备' },
]

const product = (details) => ({
  rating: 4.8,
  reviewCount: 1200,
  soldCount: 5800,
  gallery: [details.image],
  ...details,
})

export const products = [
  product({
    id: 101, slug: 'aether-x1-pro', name: 'Aether X1 Pro', brand: 'AETHER', categoryId: 'phones',
    tagline: '把专业影像，装进口袋', description: '1 英寸主摄、全天候自适应屏幕与双向卫星通信，为移动创作者打造的旗舰手机。',
    price: 4299, originalPrice: 4799, reviewCount: 2841, soldCount: 12600, badge: '本周热卖', accent: '#dfe8ff',
    image: 'https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=1100&q=88',
    features: ['1 英寸影像传感器', '2K 自适应高刷屏', '100W 闪充'], isFeatured: true, isNew: true, isDeal: true,
    skus: [
      { id: 'aether-x1-256-black', label: '曜石黑 / 256GB', price: 4299, stock: 18, options: { color: '曜石黑', storage: '256GB' } },
      { id: 'aether-x1-512-silver', label: '月岩银 / 512GB', price: 4899, stock: 9, options: { color: '月岩银', storage: '512GB' } },
    ],
  }),
  product({
    id: 102, slug: 'nomadbook-air-14', name: 'NomadBook Air 14', brand: 'NOMAD', categoryId: 'computers',
    tagline: '轻装上阵，灵感不掉线', description: '全金属机身仅重 1.08kg，配备高色域 OLED 屏与长续航架构，适合移动办公。',
    price: 5699, originalPrice: 6299, badge: '新品', accent: '#e7e2d8',
    image: 'https://images.unsplash.com/photo-1496181133206-80ce9b88a853?auto=format&fit=crop&w=1100&q=88',
    features: ['2.8K OLED 屏', '18 小时续航', '1.08kg 机身'], isFeatured: true, isNew: true, isDeal: false,
    skus: [
      { id: 'nomad-air-16-512', label: '雾银 / 16GB + 512GB', price: 5699, stock: 12, options: { color: '雾银', memory: '16GB', storage: '512GB' } },
      { id: 'nomad-air-32-1t', label: '深空灰 / 32GB + 1TB', price: 7299, stock: 6, options: { color: '深空灰', memory: '32GB', storage: '1TB' } },
    ],
  }),
  product({
    id: 103, slug: 'pulse-studio-max', name: 'Pulse Studio Max', brand: 'PULSE', categoryId: 'audio',
    tagline: '安静下来，听见更多', description: '自适应空间音频与宽频主动降噪，让通勤、工作与长途旅行都保持专注。',
    price: 1599, originalPrice: 1899, rating: 4.9, reviewCount: 3860, soldCount: 18700, badge: '限时直降', accent: '#fee2cf',
    image: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=1100&q=88',
    features: ['-48dB 主动降噪', '60 小时续航', '无损空间音频'], isFeatured: true, isNew: false, isDeal: true,
    skus: [
      { id: 'pulse-max-black', label: '曜石黑', price: 1599, stock: 32, options: { color: '曜石黑' } },
      { id: 'pulse-max-sand', label: '沙砾金', price: 1699, stock: 14, options: { color: '沙砾金' } },
    ],
  }),
  product({
    id: 104, slug: 'orbit-watch-s', name: 'Orbit Watch S', brand: 'ORBIT', categoryId: 'smart-home',
    tagline: '健康节奏，抬腕可见', description: '全天候健康趋势监测、专业运动模式与独立通信，兼顾轻量设计和户外可靠性。',
    price: 1299, originalPrice: 1499, reviewCount: 914, soldCount: 4600, badge: '以旧换新', accent: '#dce9e4',
    image: 'https://images.unsplash.com/photo-1546868871-7041f2a55e12?auto=format&fit=crop&w=1100&q=88',
    features: ['双频 GPS', '14 天续航', '全天健康趋势'], isFeatured: false, isNew: true, isDeal: true,
    skus: [
      { id: 'orbit-s-42-black', label: '42mm / 黑色硅胶', price: 1299, stock: 20, options: { size: '42mm', strap: '黑色硅胶' } },
      { id: 'orbit-s-46-steel', label: '46mm / 钛灰钢带', price: 1699, stock: 8, options: { size: '46mm', strap: '钛灰钢带' } },
    ],
  }),
  product({
    id: 105, slug: 'viewedge-32', name: 'ViewEdge 32 设计显示器', brand: 'VIEWEDGE', categoryId: 'computers',
    tagline: '每一种颜色，都有依据', description: '32 英寸 4K 专业显示器，出厂逐台校色，并提供完整的桌面连接与供电能力。',
    price: 3299, originalPrice: 3699, reviewCount: 643, soldCount: 2300, badge: '设计师推荐', accent: '#dce6ec',
    image: 'https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?auto=format&fit=crop&w=1100&q=88',
    features: ['4K IPS Black', '98% P3 色域', '90W USB-C'], isFeatured: false, isNew: false, isDeal: true,
    skus: [
      { id: 'viewedge-32-stand', label: '标准升降支架', price: 3299, stock: 10, options: { stand: '标准升降支架' } },
      { id: 'viewedge-32-arm', label: '悬浮显示器臂', price: 3599, stock: 5, options: { stand: '悬浮显示器臂' } },
    ],
  }),
  product({
    id: 106, slug: 'arcpods-pro', name: 'ArcPods Pro 2', brand: 'ARC', categoryId: 'audio',
    tagline: '小体积，也有大声场', description: '半开放声学架构与智能降噪融合，提供舒适佩戴和清晰通话体验。',
    price: 699, originalPrice: 799, reviewCount: 5240, soldCount: 32100, badge: '人气单品', accent: '#edf0f4',
    image: 'https://images.unsplash.com/photo-1590658268037-6bf12165a8df?auto=format&fit=crop&w=1100&q=88',
    features: ['智能动态降噪', '36 小时续航', 'IP55 防护'], isFeatured: false, isNew: true, isDeal: false,
    skus: [
      { id: 'arcpods-pro-white', label: '陶瓷白', price: 699, stock: 48, options: { color: '陶瓷白' } },
      { id: 'arcpods-pro-blue', label: '雾海蓝', price: 699, stock: 0, options: { color: '雾海蓝' } },
    ],
  }),
  product({
    id: 107, slug: 'keyframe-75', name: 'Keyframe 75 键盘', brand: 'KEYFRAME', categoryId: 'accessories',
    tagline: '把每次输入，调到顺手', description: '75% 配列、热插拔轴座与三模连接，用克制的桌面体积提供完整的输入体验。',
    price: 499, originalPrice: 599, reviewCount: 1870, soldCount: 9100, badge: '桌搭精选', accent: '#ece4db',
    image: 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=1100&q=88',
    features: ['全键热插拔', '三模连接', 'Gasket 结构'], isFeatured: false, isNew: false, isDeal: true,
    skus: [
      { id: 'keyframe-75-brown', label: '云杉绿 / 轻触轴', price: 499, stock: 16, options: { color: '云杉绿', switch: '轻触轴' } },
      { id: 'keyframe-75-linear', label: '岩灰 / 线性轴', price: 529, stock: 11, options: { color: '岩灰', switch: '线性轴' } },
    ],
  }),
  product({
    id: 108, slug: 'homehub-mini', name: 'HomeHub Mini', brand: 'NESTA', categoryId: 'smart-home',
    tagline: '一句话，让家开始行动', description: '融合家庭中枢、智能音箱和环境感知，支持本地自动化与多协议设备接入。',
    price: 399, originalPrice: 499, reviewCount: 2240, soldCount: 14800, badge: '智能入门', accent: '#e4e7dc',
    image: 'https://images.unsplash.com/photo-1589492477829-5e65395b66cc?auto=format&fit=crop&w=1100&q=88',
    features: ['Matter 多协议', '本地场景自动化', '360° 远场语音'], isFeatured: false, isNew: true, isDeal: false,
    skus: [
      { id: 'homehub-mini-white', label: '云朵白', price: 399, stock: 35, options: { color: '云朵白' } },
      { id: 'homehub-mini-green', label: '苔原绿', price: 399, stock: 22, options: { color: '苔原绿' } },
    ],
  }),
]
