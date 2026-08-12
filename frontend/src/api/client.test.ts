// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, getClientId, request, resetCsrfToken, startDownload } from './client'
import { api } from './index'

function response(body: string, status = 200, contentType = 'application/json') {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers(contentType ? { 'content-type': contentType } : {}),
    text: vi.fn().mockResolvedValue(body),
  } as unknown as Response
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (cause: unknown) => void
  const promise = new Promise<T>((nextResolve, nextReject) => {
    resolve = nextResolve
    reject = nextReject
  })
  return { promise, resolve, reject }
}

function headerValue(call: [unknown, RequestInit | undefined], name: string) {
  return new Headers(call[1]?.headers).get(name)
}

describe('streaming downloads', () => {
  afterEach(() => {
    resetCsrfToken()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('hands the export URL to the browser without creating a Blob', () => {
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    startDownload('/data/export')

    expect(click).toHaveBeenCalledOnce()
    expect(document.querySelector('a')).toBeNull()
  })

  it('does not start a download when export preparation fails', async () => {
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const responses = [
      response('{"token":"token"}'),
      response('{"code":"EXPORT_PREPARE_FAILED","message":"打包失败"}', 409),
    ]
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => Promise.resolve(responses.shift())))

    await expect(api.exportData()).rejects.toMatchObject({ code: 'EXPORT_PREPARE_FAILED' })
    expect(click).not.toHaveBeenCalled()
  })
})

describe('per-tab client identity', () => {
  it('does not persist the runtime client id in session storage', () => {
    const first = getClientId()
    const second = getClientId()

    expect(first).toBe(second)
    expect(sessionStorage.getItem('love-space-client-id')).toBeNull()
  })
})

describe('API response and CSRF handling', () => {
  afterEach(() => {
    resetCsrfToken()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('reads a damaged JSON body once and still returns an HTTP-coded ApiError', async () => {
    const broken = response('{"message":', 418)
    const fetchMock = vi.fn().mockResolvedValue(broken)
    vi.stubGlobal('fetch', fetchMock)

    const error = await request('/broken').catch(cause => cause)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 418, code: 'HTTP_418' })
    expect(broken.text).toHaveBeenCalledOnce()
  })

  it('does not treat an unclassified 401 as session expiration', async () => {
    const fetchMock = vi.fn().mockResolvedValue(response('{broken', 401))
    const listener = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    window.addEventListener('love-space-unauthenticated', listener)

    const error = await request('/auth/me').catch(cause => cause)

    expect(error).toMatchObject({ status: 401, code: 'HTTP_401' })
    expect(listener).not.toHaveBeenCalled()
    window.removeEventListener('love-space-unauthenticated', listener)
  })

  it('dispatches session expiration only for an explicit authentication code', async () => {
    const fetchMock = vi.fn().mockResolvedValue(response('{"code":"PASSWORD_CHANGED","message":"expired"}', 401))
    const listener = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    window.addEventListener('love-space-unauthenticated', listener)

    await request('/auth/me').catch(() => undefined)

    expect(listener).toHaveBeenCalledOnce()
    expect((listener.mock.calls[0][0] as CustomEvent).detail).toEqual({ code: 'PASSWORD_CHANGED' })
    window.removeEventListener('love-space-unauthenticated', listener)
  })

  it('refreshes CSRF only for an explicit mismatch and retries the business request once', async () => {
    const responses = [
      response('{"token":"old-token"}'),
      response('{"code":"CSRF_TOKEN_INVALID","message":"stale"}', 403),
      response('{"token":"new-token"}'),
      response('{"saved":true}'),
    ]
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(responses.shift()))
    vi.stubGlobal('fetch', fetchMock)

    await expect(request<{ saved: boolean }>('/wishes', { method: 'POST', body: '{}' }))
      .resolves.toEqual({ saved: true })

    expect(fetchMock).toHaveBeenCalledTimes(4)
    expect((fetchMock.mock.calls[1][1] as RequestInit).headers).toBeInstanceOf(Headers)
    expect(headerValue(fetchMock.mock.calls[1] as [unknown, RequestInit | undefined], 'X-XSRF-TOKEN')).toBe('old-token')
    expect(headerValue(fetchMock.mock.calls[3] as [unknown, RequestInit | undefined], 'X-XSRF-TOKEN')).toBe('new-token')
  })

  it('does not retry a normal forbidden response', async () => {
    const responses = [
      response('{"token":"token"}'),
      response('{"code":"FORBIDDEN"}', 403),
    ]
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(responses.shift()))
    vi.stubGlobal('fetch', fetchMock)

    const error = await request('/forbidden', { method: 'POST', body: '{}' }).catch(cause => cause)

    expect(error).toMatchObject({ status: 403, code: 'FORBIDDEN' })
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('does not let a reset race write an old CSRF token back into state', async () => {
    const oldResponse = deferred<Response>()
    const newResponse = deferred<Response>()
    let csrfCalls = 0
    const fetchMock = vi.fn().mockImplementation((url: string) => {
      if (url.endsWith('/auth/csrf')) {
        csrfCalls++
        return csrfCalls === 1 ? oldResponse.promise : newResponse.promise
      }
      return Promise.resolve(response('', 204, ''))
    })
    vi.stubGlobal('fetch', fetchMock)

    const staleRequest = request('/wishes', { method: 'POST', body: '{}' })
    await Promise.resolve()
    resetCsrfToken()
    const freshRequest = request('/wishes', { method: 'POST', body: '{}' })

    oldResponse.resolve(response('{"token":"old-token"}'))
    newResponse.resolve(response('{"token":"new-token"}'))

    await expect(staleRequest).rejects.toMatchObject({ code: 'CSRF_RESET' })
    await expect(freshRequest).resolves.toBeUndefined()
    const businessCall = fetchMock.mock.calls.find(([url]) => !String(url).endsWith('/auth/csrf'))
    expect(headerValue(businessCall as [unknown, RequestInit | undefined], 'X-XSRF-TOKEN')).toBe('new-token')
  })
})
