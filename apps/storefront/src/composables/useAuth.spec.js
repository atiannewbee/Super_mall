import { describe, expect, it, vi } from 'vitest'
import { createAuthStore } from './useAuth'

function memoryStorage() {
  const state = new Map()
  return {
    getItem: (key) => state.get(key) ?? null,
    setItem: (key, value) => state.set(key, value),
    removeItem: (key) => state.delete(key),
  }
}

const authResponse = {
  accessToken: 'signed.jwt.token', tokenType: 'Bearer', expiresIn: 3600,
  user: { id: 7, name: '小超', email: 'user@example.com', gender: 'UNSPECIFIED' },
}

describe('createAuthStore', () => {
  it('logs in through the API and persists only the returned session', async () => {
    const storage = memoryStorage()
    const client = { post: vi.fn().mockResolvedValue(authResponse) }
    const auth = createAuthStore(storage, client)

    await auth.login('user@example.com', 'SecurePass123')

    expect(client.post).toHaveBeenCalledWith('/api/auth/login', { account: 'user@example.com', password: 'SecurePass123' })
    expect(auth.user.value).toMatchObject({ displayName: '小超', email: 'user@example.com' })
    expect(storage.getItem('super-mall-session')).not.toContain('SecurePass123')
    expect(createAuthStore(storage, client).isAuthenticated.value).toBe(true)
  })

  it('registers with the backend and clears the session on logout', async () => {
    const storage = memoryStorage()
    const client = { post: vi.fn().mockResolvedValue(authResponse) }
    const auth = createAuthStore(storage, client)

    await auth.register({ name: ' 小超 ', email: ' user@example.com ', phone: '', password: 'SecurePass123' })
    expect(client.post).toHaveBeenCalledWith('/api/auth/register', { name: '小超', email: 'user@example.com', phone: '', password: 'SecurePass123' })

    auth.logout()
    expect(auth.isAuthenticated.value).toBe(false)
    expect(storage.getItem('super-mall-session')).toBeNull()
  })
})
