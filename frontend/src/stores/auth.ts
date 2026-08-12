import { reactive } from 'vue'
import { api } from '../api'
import { ApiError } from '../api/client'
import { resetCsrfToken } from '../api/client'
import { resetNotifications } from './notifications'
import type { AuthPayload, UserProfile } from '../types'

interface AuthState {
  ready: boolean
  initialized: boolean
  authenticated: boolean
  user: UserProfile | null
  partner: UserProfile | null
  spaceName: string
  loveStartedAt: string
  coupleVersion: number | string | undefined
  forcedLogoutReason: string | null
}

const SESSION_INVALIDATION_CODES = new Set(['UNAUTHORIZED', 'SESSION_INVALID', 'PASSWORD_CHANGED'])

export const authState = reactive<AuthState>({
  ready: false,
  initialized: true,
  authenticated: false,
  user: null,
  partner: null,
  spaceName: '我们的小时光',
  loveStartedAt: '',
  coupleVersion: undefined,
  forcedLogoutReason: null,
})

let bootstrapPromise: Promise<void> | null = null

export function applyAuth(payload: AuthPayload) {
  const nextUser = payload.user
  if (String(authState.user?.id ?? '') !== String(nextUser?.id ?? '')) resetNotifications()
  authState.user = nextUser
  authState.partner = payload.partner
  authState.spaceName = payload.couple.spaceName
  authState.loveStartedAt = payload.couple.loveStartedAt
  authState.coupleVersion = payload.couple.version
  authState.authenticated = true
  authState.forcedLogoutReason = null
}

export function isSessionInvalidationCode(code?: string) {
  return Boolean(code && SESSION_INVALIDATION_CODES.has(code))
}

export function forceLogout(reason = 'UNAUTHORIZED') {
  clearAuth(reason)
}

export async function bootstrapAuth(force = false) {
  if (bootstrapPromise && !force) return bootstrapPromise
  bootstrapPromise = (async () => {
    let completed = false
    try {
      const status = await api.setupStatus()
      authState.initialized = status.initialized
      if (!status.initialized) {
        clearAuth()
        completed = true
        return
      }
      try {
        applyAuth(await api.me())
      } catch (error) {
        if (error instanceof ApiError && isSessionInvalidationCode(error.code)) {
          clearAuth(error.code)
        } else {
          throw error
        }
      }
      completed = true
    } finally {
      // 网络/服务故障不能把首次探测永久标记为 ready；调用方可用 force=true 重试。
      authState.ready = completed
    }
  })()
  try {
    await bootstrapPromise
  } finally {
    bootstrapPromise = null
  }
}

export function clearAuth(reason: string | null = null) {
  authState.authenticated = false
  authState.user = null
  authState.partner = null
  authState.spaceName = '我们的小时光'
  authState.loveStartedAt = ''
  authState.coupleVersion = undefined
  authState.forcedLogoutReason = reason
  resetNotifications()
  resetCsrfToken()
}
