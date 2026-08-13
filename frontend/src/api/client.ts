export const API_BASE = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
const CONNECTION_ERROR_MESSAGE = '小屋暂时连接不上，请稍后再试。若问题持续出现，请联系管理员。'

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
let csrfRefreshPromise: Promise<string> | null = null
let csrfController: AbortController | null = null
let csrfGeneration = 0
function createClientId() {
  return typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `client_${Date.now()}_${Math.random().toString(36).slice(2)}`
}

// 只保存在当前 JS 模块生命周期内；sessionStorage 会被复制标签页继承，不能用作连接身份。
const clientId = createClientId()

export function getClientId() {
  return clientId
}

interface ParsedResponse {
  payload: unknown
  text: string
  malformedJson: boolean
}

async function parseResponse(response: Response): Promise<ParsedResponse> {
  let text = ''
  try {
    text = await response.text()
  } catch {
    return { payload: null, text: '', malformedJson: true }
  }

  const contentType = response.headers.get('content-type') || ''
  if (!contentType.toLowerCase().includes('json') || !text.trim()) {
    return { payload: text, text, malformedJson: false }
  }

  try {
    return { payload: JSON.parse(text), text, malformedJson: false }
  } catch {
    return { payload: null, text, malformedJson: true }
  }
}

function objectPayload(payload: unknown) {
  return typeof payload === 'object' && payload !== null
    ? payload as Record<string, unknown>
    : {}
}

function responseCode(status: number, payload: unknown) {
  const code = objectPayload(payload).code
  return code ? String(code) : `HTTP_${status}`
}

function throwResponseError(path: string, response: Response, parsed: ParsedResponse): never {
  const data = objectPayload(parsed.payload)
  const code = responseCode(response.status, parsed.payload)
  const sessionInvalidation = ['UNAUTHORIZED', 'SESSION_INVALID', 'PASSWORD_CHANGED'].includes(code)
  if (path !== '/auth/login' && sessionInvalidation && typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('love-space-unauthenticated', {
      detail: { code },
    }))
  }
  const message = typeof data.message === 'string'
    ? data.message
    : parsed.malformedJson
      ? `请求失败（HTTP ${response.status}）`
      : String(parsed.payload || '请求没有成功，请稍后重试。')
  throw new ApiError(message, response.status, code, data.fieldErrors as Record<string, string> | undefined)
}

function isCsrfMismatch(response: Response, parsed: ParsedResponse) {
  if (response.status !== 403) return false
  const code = objectPayload(parsed.payload).code
  return code === 'CSRF_TOKEN_MISSING' || code === 'CSRF_TOKEN_INVALID'
}

function invalidateCsrfState() {
  csrfGeneration++
  csrfToken = ''
  csrfController?.abort()
  csrfController = null
  csrfPromise = null
}

async function ensureCsrfToken() {
  if (csrfToken) return csrfToken
  if (csrfPromise) return csrfPromise
  const generation = csrfGeneration
  const controller = typeof AbortController === 'undefined' ? null : new AbortController()
  csrfController = controller
  const operation = (async () => {
    let response: Response
    try {
      response = await fetch(`${API_BASE}/auth/csrf`, {
        credentials: 'include',
        ...(controller ? { signal: controller.signal } : {}),
      })
    } catch (cause) {
      if (generation !== csrfGeneration) {
        throw new ApiError('安全会话已重置，请重新发起请求。', 0, 'CSRF_RESET')
      }
      if (cause instanceof ApiError) throw cause
      throw new ApiError(CONNECTION_ERROR_MESSAGE, 0, 'NETWORK_ERROR')
    }
    const parsed = await parseResponse(response)
    if (!response.ok) throw new ApiError('无法建立安全会话，请刷新页面后重试。', response.status, 'CSRF_INIT_FAILED')
    const payload = objectPayload(parsed.payload) as { token?: unknown }
    if (typeof payload.token !== 'string' || !payload.token) {
      throw new ApiError('无法建立安全会话，请刷新页面后重试。', 500, 'CSRF_INIT_FAILED')
    }
    if (generation !== csrfGeneration) {
      throw new ApiError('安全会话已重置，请重新发起请求。', 0, 'CSRF_RESET')
    }
    csrfToken = payload.token
    return payload.token
  })()
  csrfPromise = operation
  try {
    return await operation
  } finally {
    if (csrfPromise === operation) {
      csrfPromise = null
      csrfController = null
    }
  }
}

async function refreshCsrfToken() {
  if (csrfRefreshPromise) return csrfRefreshPromise
  invalidateCsrfState()
  const operation = ensureCsrfToken()
  csrfRefreshPromise = operation
  try {
    return await operation
  } finally {
    if (csrfRefreshPromise === operation) csrfRefreshPromise = null
  }
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const isForm = init.body instanceof FormData || init.body instanceof URLSearchParams
  const method = (init.method || 'GET').toUpperCase()
  const needsCsrf = !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method) && path !== '/auth/csrf'

  const execute = async () => {
    const headers = new Headers(init.headers)
    if (init.body && !isForm && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
    headers.set('X-Love-Client-Id', getClientId())
    if (needsCsrf) headers.set('X-XSRF-TOKEN', await ensureCsrfToken())

    let response: Response
    try {
      response = await fetch(`${API_BASE}${path}`, {
        ...init,
        headers,
        credentials: 'include',
      })
    } catch {
      throw new ApiError(CONNECTION_ERROR_MESSAGE, 0, 'NETWORK_ERROR')
    }
    return { response, parsed: response.status === 204 ? null : await parseResponse(response) }
  }

  let result = await execute()
  if (result.parsed && isCsrfMismatch(result.response, result.parsed)) {
    await refreshCsrfToken()
    result = await execute()
  }

  if (result.response.status === 204) return undefined as T
  if (!result.response.ok) throwResponseError(path, result.response, result.parsed!)
  return result.parsed?.payload as T
}

export function jsonRequest<T>(
  path: string,
  method: 'POST' | 'PUT' | 'PATCH' | 'DELETE',
  body: unknown,
  init: RequestInit = {},
) {
  return request<T>(path, { ...init, method, body: JSON.stringify(body) })
}

export function startDownload(path: string) {
  const anchor = document.createElement('a')
  anchor.href = `${API_BASE}${path}`
  anchor.download = ''
  anchor.rel = 'noopener'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
}

// <a> 直连下载是流式的（大文件不进内存），但失败时浏览器只会静默下载一个错误响应。
// 先用 HEAD 预检，失败时抛 ApiError 让调用方给出提示；HEAD 无响应体，只能按状态码给文案。
export async function verifyDownload(path: string) {
  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}`, { method: 'HEAD', credentials: 'include' })
  } catch {
    throw new ApiError(CONNECTION_ERROR_MESSAGE, 0, 'NETWORK_ERROR')
  }
  if (response.ok) return
  if (response.status === 401 && typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('love-space-unauthenticated', { detail: { code: 'UNAUTHORIZED' } }))
  }
  throw new ApiError('导出文件暂时不可用，请稍后重试。', response.status, `HTTP_${response.status}`)
}

export function mediaUrl(id?: number | string | null, directUrl?: string | null) {
  if (directUrl) return directUrl
  return id === undefined || id === null ? '' : `${API_BASE}/media/${id}`
}

export function resetCsrfToken() {
  invalidateCsrfState()
  csrfRefreshPromise = null
}

export function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : '发生了一点小问题，请再试一次。'
}
