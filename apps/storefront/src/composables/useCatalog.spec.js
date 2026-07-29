import { describe, expect, it } from 'vitest'
import { useCatalog } from './useCatalog'

describe('useCatalog', () => {
  it('matches category aliases used by shoppers', () => {
    const catalog = useCatalog()

    catalog.query.value = '耳机'

    expect(catalog.filteredProducts.value.map((product) => product.name)).toEqual([
      'Pulse Studio Max',
      'ArcPods Pro 2',
    ])
  })

  it('combines category and keyword filters', () => {
    const catalog = useCatalog()

    catalog.setCategory('computers')
    catalog.query.value = '显示器'

    expect(catalog.filteredProducts.value.map((product) => product.name)).toEqual([
      'ViewEdge 32 设计显示器',
    ])
  })
})
