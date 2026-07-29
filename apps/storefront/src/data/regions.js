import regionLevels from '@province-city-china/level/level.min.json'

const placeholderDistricts = new Set(['市辖区', '县'])
const nameOf = (item) => item.name ?? item.n
const childrenOf = (item) => item.children ?? item.d ?? []

function districtNames(items = []) {
  const names = items
    .map(nameOf)
    .filter((name) => name && !placeholderDistricts.has(name))
  return names.length ? names : ['其他区县']
}

function buildRegionTree(levels) {
  return Object.fromEntries(levels.map((province) => {
    const children = childrenOf(province)
    const nestedCities = children.filter((item) => childrenOf(item).length)
    const directDistricts = children.filter((item) => !childrenOf(item).length)
    const cities = Object.fromEntries(nestedCities.map((city) => [
      nameOf(city),
      districtNames(childrenOf(city)),
    ]))

    if (!nestedCities.length) {
      cities[nameOf(province)] = districtNames(directDistricts)
    } else if (directDistricts.length) {
      cities['省直辖县级行政区划'] = districtNames(directDistricts)
    }

    return [nameOf(province), cities]
  }))
}

export const serviceRegions = Object.freeze(buildRegionTree(regionLevels))

const first = (items) => items[0] || ''

export function getProvinceOptions(current = '') {
  const options = Object.keys(serviceRegions)
  return current && !options.includes(current) ? [current, ...options] : options
}

export function getCityOptions(province, current = '') {
  const cities = Object.keys(serviceRegions[province] || {})
  if (cities.length) return cities
  return current ? [current] : []
}

export function getDistrictOptions(province, city, current = '') {
  const districts = serviceRegions[province]?.[city] || []
  if (districts.length) return [...districts]
  return current ? [current] : []
}

export function normalizeRegion({ province = '', city = '', district = '' } = {}) {
  if (!serviceRegions[province]) return { province, city, district }
  const cities = getCityOptions(province)
  const normalizedCity = cities.includes(city) ? city : first(cities)
  const districts = getDistrictOptions(province, normalizedCity)
  return {
    province,
    city: normalizedCity,
    district: districts.includes(district) ? district : first(districts),
  }
}
