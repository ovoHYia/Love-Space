export const API_BASE = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')

export class ApiError extends Error {
  status: number
  code?: string
  fieldErrors?: Record<string, string>

  constructor(message: string, status: number, code?: string, fieldErrors?: Record<string, string>) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

let csrfToken = ''
let csrfPromise: Promise<string> | null = null
const CLIENT_ID_KEY = 'love-space-client-id'
let clientId = ''

export function getClientId() {
  if (clientId) return clientId
  try {
    clientId = sessionStorage.getItem(CLIENT_ID_KEY) || ''
    if (!clientId) {
      clientId = typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : `client_${Date.now()}_${Math.random().toString(36).slice(2)}`
      sessionStorage.setItem(CLIENT_ID_KEY, clientId)
    }
  } catch {
    clientId = `client_${Date.now()}_${Math.random().toString(36).slice(2)}`
  }
  return clientId
}

async function ensureCsrfToken() {
  if (csrfToken) return csrfToken
  if (csrfPromise) return csrfPromise
  csrfPromise = (async () => {
    let response: Response
    try {
      response = await fetch(`${API_BASE}/auth/csrf`, { credentials: 'include' })
    } catch {
      throw new ApiError('暂时连接不上服务器，请确认服务已启动后重试。', 0, 'NETWORK_ERROR')
    }
    if (!response.ok) throw new ApiError('无法建立安全会话，请刷新页面后重试。', response.status, 'CSRF_INIT_FAILED')
    const payload = await response.json() as { token?: string }
    if (!payload.token) throw new ApiError('无法建立安全会话，请刷新页面后重试。', 500, 'CSRF_INIT_FAILED')
    csrfToken = payload.token
    return csrfToken
  })()
  try {
    return await csrfPromise
  } finally {
    csrfPromise = null
  }
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  const isForm = init.body instanceof FormData || init.body instanceof URLSearchParams
  if (init.body && !isForm && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  const method = (init.method || 'GET').toUpperCase()
  headers.set('X-Love-Client-Id', getClientId())
  if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method) && path !== '/auth/csrf') {
    headers.set('X-XSRF-TOKEN', await ensureCsrfToken())
  }

  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}`, {
      ...init,
      headers,
      credentials: 'include',
    })
  } catch {
    throw new ApiError('暂时连接不上服务器，请确认服务已启动后重试。', 0, 'NETWORK_ERROR')
  }

  if (response.status === 204) return undefined as T
  const contentType = response.headers.get('content-type') || ''
  let payload: unknown = null
  if (contentType.includes('application/json')) {
    try { payload = await response.json() } catch { payload = await response.text() }
  } else {
    payload = await response.text()
  }
  if (!response.ok) {
    const data = typeof payload === 'object' && payload ? payload as Record<string, unknown> : {}
    if (response.status === 401 && path !== '/auth/login') {
      window.dispatchEvent(new CustomEvent('love-space-unauthenticated', {
        detail: { code: data.code ? String(data.code) : undefined },
      }))
    }
    throw new ApiError(
      String(data.message || payload || '请求没有成功，请稍后重试。'),
      response.status,
      data.code ? String(data.code) : undefined,
      data.fieldErrors as Record<string, string> | undefined,
    )
  }
  return payload as T
}

export async function download(path: string): Promise<{ blob: Blob; filename?: string }> {
  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}`, { credentials: 'include' })
  } catch {
    throw new ApiError('暂时连接不上服务器，请确认服务已启动后重试。', 0, 'NETWORK_ERROR')
  }
  if (!response.ok) {
    let payload: Record<string, unknown> = {}
    try { payload = await response.json() as Record<string, unknown> } catch { /* use fallback below */ }
    if (response.status === 401) {
      window.dispatchEvent(new CustomEvent('love-space-unauthenticated', {
        detail: { code: payload.code ? String(payload.code) : undefined },
      }))
    }
    throw new ApiError(String(payload.message || '导出没有成功，请稍后重试。'), response.status,
      payload.code ? String(payload.code) : undefined)
  }
  const disposition = response.headers.get('content-disposition') || ''
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1]
  const plain = /filename="?([^";]+)"?/i.exec(disposition)?.[1]
  let filename = plain
  if (encoded) {
    try { filename = decodeURIComponent(encoded) } catch { filename = encoded }
  }
  return { blob: await response.blob(), filename }
}

export function mediaUrl(id?: number | string | null, directUrl?: string | null) {
  if (directUrl) return directUrl
  return id === undefined || id === null ? '' : `${API_BASE}/media/${id}`
}

export function resetCsrfToken() { csrfToken = '' }

export function unwrapList<T>(payload: T[] | { content?: T[] } | undefined | null): T[] {
  if (Array.isArray(payload)) return payload
  return payload?.content || []
}

export function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : '发生了一点小问题，请再试一次。'
}
