import { computed, ref } from 'vue'
import { api, SESSION_KEY } from '../services/api'

function normalizeUser(user) {
  return user ? { ...user, displayName: user.name || '商家成员' } : null
}

export function readSession(storage = typeof window === 'undefined' ? undefined : window.localStorage) {
  if (!storage) return null
  try {
    const session = JSON.parse(storage.getItem(SESSION_KEY) || 'null')
    if (!session?.accessToken || !session?.user) return null
    if (session.expiresAt && session.expiresAt <= Date.now()) {
      storage.removeItem(SESSION_KEY)
      return null
    }
    return { ...session, user: normalizeUser(session.user) }
  } catch {
    storage.removeItem(SESSION_KEY)
    return null
  }
}

export function hasValidMerchantSession(storage) {
  return Boolean(readSession(storage))
}

export function createMerchantAuthStore(storage, client = api) {
  const session = ref(readSession(storage))
  const user = computed(() => session.value?.user || null)
  const isAuthenticated = computed(() => Boolean(session.value?.accessToken))
  const loading = ref(false)

  function save(authResponse) {
    const next = {
      accessToken: authResponse.accessToken,
      tokenType: authResponse.tokenType || 'Bearer',
      expiresAt: Date.now() + Number(authResponse.expiresIn || 0) * 1000,
      user: normalizeUser(authResponse.user),
    }
    session.value = next
    storage?.setItem(SESSION_KEY, JSON.stringify(next))
    return next.user
  }

  async function login(email, password) {
    loading.value = true
    try {
      return save(await client.post('/api/merchant/auth/login', {
        email: String(email).trim().toLowerCase(),
        password,
      }))
    } finally {
      loading.value = false
    }
  }

  function logout() {
    session.value = null
    storage?.removeItem(SESSION_KEY)
  }

  return { session, user, isAuthenticated, loading, login, logout }
}

let browserStore

export function useMerchantAuth() {
  if (!browserStore) {
    browserStore = createMerchantAuthStore(
      typeof window === 'undefined' ? undefined : window.localStorage,
    )
  }
  return browserStore
}
