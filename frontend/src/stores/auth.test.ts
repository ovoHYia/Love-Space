// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'

const { setupStatus, me } = vi.hoisted(() => ({ setupStatus: vi.fn(), me: vi.fn() }))

vi.mock('../api', () => ({
  api: {
    setupStatus,
    me,
  },
}))

import { authState, bootstrapAuth, clearAuth } from './auth'

describe('auth bootstrap recovery', () => {
  afterEach(() => {
    setupStatus.mockReset()
    me.mockReset()
    clearAuth()
    authState.initialized = true
    authState.ready = false
  })

  it('does not permanently mark auth ready after the first network failure', async () => {
    setupStatus.mockRejectedValueOnce(new ApiError('offline', 0, 'NETWORK_ERROR'))

    await expect(bootstrapAuth(true)).rejects.toMatchObject({ code: 'NETWORK_ERROR' })
    expect(authState.ready).toBe(false)

    setupStatus.mockResolvedValueOnce({ initialized: true })
    me.mockRejectedValueOnce(new ApiError('expired', 401, 'UNAUTHORIZED'))
    await expect(bootstrapAuth(true)).resolves.toBeUndefined()
    expect(authState.ready).toBe(true)
    expect(authState.authenticated).toBe(false)
  })
})
