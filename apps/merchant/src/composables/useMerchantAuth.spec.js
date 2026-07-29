import { describe, expect, it, vi } from 'vitest'
import { createMerchantAuthStore, hasValidMerchantSession, readSession } from './useMerchantAuth'
import { SESSION_KEY } from '../services/api'

function memoryStorage() {
  const values = new Map()
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
  }
}

describe('merchant auth store', () => {
  it('uses a merchant-only session key and persists login metadata', async () => {
    const storage = memoryStorage()
    const client = {
      post: vi.fn().mockResolvedValue({
        accessToken: 'merchant-token',
        expiresIn: 1800,
        user: { name: '仓库主管', roles: ['WAREHOUSE'] },
      }),
    }
    const store = createMerchantAuthStore(storage, client)

    await store.login(' OWNER@EXAMPLE.COM ', 'MerchantPass123!')

    expect(client.post).toHaveBeenCalledWith('/api/merchant/auth/login', {
      email: 'owner@example.com',
      password: 'MerchantPass123!',
    })
    expect(JSON.parse(storage.getItem(SESSION_KEY)).accessToken).toBe('merchant-token')
    expect(store.user.value.displayName).toBe('仓库主管')
    expect(hasValidMerchantSession(storage)).toBe(true)
  })

  it('removes expired and malformed sessions', () => {
    const storage = memoryStorage()
    storage.setItem(SESSION_KEY, JSON.stringify({
      accessToken: 'expired',
      user: { name: '过期账号' },
      expiresAt: Date.now() - 1,
    }))
    expect(readSession(storage)).toBeNull()

    storage.setItem(SESSION_KEY, '{')
    expect(readSession(storage)).toBeNull()
  })
})
