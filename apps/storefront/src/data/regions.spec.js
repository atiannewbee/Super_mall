import { describe, expect, it } from 'vitest'
import { getCityOptions, getDistrictOptions, getProvinceOptions, normalizeRegion } from './regions'

describe('service regions', () => {
  it('provides the complete province-level list', () => {
    expect(getProvinceOptions()).toHaveLength(34)
    expect(getProvinceOptions()).toEqual(expect.arrayContaining(['广东省', '四川省', '北京市', '香港特别行政区']))
  })

  it('only returns cities belonging to the selected province', () => {
    expect(getCityOptions('广东省')).toContain('深圳市')
    expect(getCityOptions('广东省')).not.toContain('北京市')
    expect(getCityOptions('北京市')).toEqual(['北京市'])
  })

  it('only returns districts belonging to the selected city', () => {
    expect(getDistrictOptions('广东省', '深圳市')).toContain('南山区')
    expect(getDistrictOptions('广东省', '深圳市')).not.toContain('天河区')
    expect(getDistrictOptions('浙江省', '杭州市')).toContain('西湖区')
    expect(getDistrictOptions('北京市', '北京市')).toContain('海淀区')
  })

  it('resets invalid child selections when the province changes', () => {
    expect(normalizeRegion({ province: '北京市', city: '深圳市', district: '南山区' })).toEqual({
      province: '北京市',
      city: '北京市',
      district: '东城区',
    })
  })

  it('preserves an existing address that is not in the data source', () => {
    expect(getProvinceOptions('测试省')[0]).toBe('测试省')
    expect(normalizeRegion({ province: '测试省', city: '测试市', district: '测试区' })).toEqual({
      province: '测试省',
      city: '测试市',
      district: '测试区',
    })
  })

  it('normalizes supported provinces using their real hierarchy', () => {
    expect(normalizeRegion({ province: '四川省', city: '成都市', district: '武侯区' })).toEqual({
      province: '四川省',
      city: '成都市',
      district: '武侯区',
    })
    expect(normalizeRegion({ province: '台湾省' })).toEqual({
      province: '台湾省',
      city: '台湾省',
      district: '其他区县',
    })
  })
})
