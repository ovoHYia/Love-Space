import { describe, expect, it } from 'vitest'
import { ApiError } from '../api/client'
import { isRetryableStrokeError, strokeRetryDelay } from './gameStrokeSync'

describe('game stroke synchronization', () => {
  it('retries only transient failures', () => {
    expect(isRetryableStrokeError(new ApiError('offline', 0))).toBe(true)
    expect(isRetryableStrokeError(new ApiError('limited', 429))).toBe(true)
    expect(isRetryableStrokeError(new ApiError('server', 503))).toBe(true)
    expect(isRetryableStrokeError(new ApiError('stale round', 409))).toBe(false)
  })

  it('uses capped exponential backoff', () => {
    expect(strokeRetryDelay(0)).toBe(2000)
    expect(strokeRetryDelay(3)).toBe(16000)
    expect(strokeRetryDelay(10)).toBe(30000)
  })
})
