import { ApiError } from '../api/client'

export function isRetryableStrokeError(cause: unknown) {
  return cause instanceof ApiError && (cause.status === 0 || cause.status === 429 || cause.status >= 500)
}

export function strokeRetryDelay(attempt: number, base = 2000, maximum = 30000) {
  return Math.min(base * (2 ** Math.max(0, attempt)), maximum)
}

export function createStrokeOperationId() {
  return typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `stroke_${Date.now()}_${Math.random().toString(36).slice(2)}`
}
