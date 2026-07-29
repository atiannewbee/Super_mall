import { computed, ref } from 'vue'
import { api } from '../services/api'

export const SESSION_KEY = 'super-mall-session'

function normalizeUser(user) {
  return user ? { ...user, displayName: user.name || user.displayName || '商城用户' } : null
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

export function hasValidSession(storage) {
  return Boolean(readSession(storage))
}

export function createAuthStore(storage, client = api) {
  const initialSession = readSession(storage)
  const session = ref(initialSession)
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

  async function login(account, password) {
    loading.value = true
    try {
      return save(await client.post('/api/auth/login', { account: String(account).trim(), password }))
    } finally {
      loading.value = false
    }
  }

  async function register({ name, email = '', phone = '', password }) {
    loading.value = true
    try {
      return save(await client.post('/api/auth/register', {
        name: String(name).trim(),
        email: String(email).trim(),
        phone: String(phone).trim(),
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

  function replaceUser(nextUser) {
    if (!session.value) return
    session.value = { ...session.value, user: normalizeUser(nextUser) }
    storage?.setItem(SESSION_KEY, JSON.stringify(session.value))
  }

  return { session, user, isAuthenticated, loading, login, register, logout, replaceUser }
}

let browserStore

export function useAuth() {
  if (!browserStore) browserStore = createAuthStore(typeof window === 'undefined' ? undefined : window.localStorage)
  return browserStore
}
