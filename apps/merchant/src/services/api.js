export const SESSION_KEY = 'super-mall-merchant-session'

export class ApiError extends Error {
  constructor(message, { status = 0, code = 'NETWORK_ERROR', details = {}, path = '' } = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.details = details
    this.path = path
  }
}

function getAccessToken() {
  if (typeof window === 'undefined') return ''
  try {
    return JSON.parse(window.localStorage.getItem(SESSION_KEY) || 'null')?.accessToken || ''
  } catch {
    return ''
  }
}

async function parseBody(response) {
  if (response.status === 204) return null
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

export function apiUrl(path) {
  const baseUrl = String(import.meta.env.VITE_API_BASE_URL || '').replace(/\/+$/, '')
  return `${baseUrl}${path}`
}

export async function request(path, options = {}) {
  const headers = new Headers(options.headers || {})
  const token = options.auth === false ? '' : getAccessToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (options.body != null && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }

  let response
  try {
    response = await fetch(apiUrl(path), { ...options, headers })
  } catch (cause) {
    throw new ApiError('无法连接运营服务，请确认后端已经启动', {
      code: 'NETWORK_ERROR',
      details: { cause: cause?.message },
    })
  }

  const body = await parseBody(response)
  if (response.ok) return body

  if (response.status === 401 && typeof window !== 'undefined') {
    window.localStorage.removeItem(SESSION_KEY)
    window.dispatchEvent(new CustomEvent('super-mall-merchant:unauthorized'))
  }

  throw new ApiError(body?.message || `请求失败（${response.status}）`, {
    status: response.status,
    code: body?.code || `HTTP_${response.status}`,
    details: body?.details || {},
    path: body?.path || path,
  })
}

export const api = {
  get: (path, options) => request(path, { ...options, method: 'GET' }),
  post: (path, body, options = {}) => request(path, {
    ...options,
    method: 'POST',
    body: body == null ? undefined : JSON.stringify(body),
  }),
  put: (path, body, options = {}) => request(path, {
    ...options,
    method: 'PUT',
    body: JSON.stringify(body),
  }),
  delete: (path, options) => request(path, { ...options, method: 'DELETE' }),
}

export function errorMessage(error, fallback = '操作失败，请稍后重试') {
  return error instanceof ApiError ? error.message : fallback
}
