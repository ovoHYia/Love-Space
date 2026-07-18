import { reactive } from 'vue'
import { api } from '../api'
import { ApiError } from '../api/client'
import { resetCsrfToken } from '../api/client'
import type { AuthPayload, UserProfile } from '../types'

interface AuthState {
  ready: boolean
  initialized: boolean
  authenticated: boolean
  user: UserProfile | null
  partner: UserProfile | null
  spaceName: string
  loveStartedAt: string
}

export const authState = reactive<AuthState>({
  ready: false,
  initialized: true,
  authenticated: false,
  user: null,
  partner: null,
  spaceName: '我们的小时光',
  loveStartedAt: '',
})

let bootstrapPromise: Promise<void> | null = null

export function applyAuth(payload: AuthPayload) {
  authState.user = payload.currentUser || payload.user || null
  authState.partner = payload.partner || null
  authState.spaceName = payload.couple?.spaceName || payload.spaceName || authState.spaceName
  authState.loveStartedAt = payload.couple?.loveStartedAt || payload.loveStartedAt || authState.loveStartedAt
  authState.authenticated = Boolean(authState.user)
}

export async function bootstrapAuth(force = false) {
  if (bootstrapPromise && !force) return bootstrapPromise
  bootstrapPromise = (async () => {
    try {
      const status = await api.setupStatus()
      authState.initialized = status.initialized
      if (!status.initialized) {
        authState.authenticated = false
        authState.user = null
        return
      }
      try {
        applyAuth(await api.me())
      } catch (error) {
        if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
          authState.authenticated = false
          authState.user = null
          authState.partner = null
        } else {
          throw error
        }
      }
    } finally {
      authState.ready = true
    }
  })()
  try {
    await bootstrapPromise
  } finally {
    bootstrapPromise = null
  }
}

export function clearAuth() {
  authState.authenticated = false
  authState.user = null
  authState.partner = null
  resetCsrfToken()
}
