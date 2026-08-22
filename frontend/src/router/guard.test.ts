import { describe, expect, it } from 'vitest'
import { resolveAuthGuard } from './guard'

function auth(overrides: Partial<{ initialized: boolean; authenticated: boolean; forcedLogoutReason: string | null }> = {}) {
  return { initialized: true, authenticated: false, forcedLogoutReason: null, ...overrides }
}

describe('resolveAuthGuard', () => {
  it('sends every route to setup while the space is uninitialized', () => {
    expect(resolveAuthGuard(auth({ initialized: false }), { name: 'home', meta: { auth: true } }))
      .toEqual({ name: 'setup' })
    expect(resolveAuthGuard(auth({ initialized: false }), { name: 'setup' })).toBeUndefined()
  })

  it('redirects setup visitors away once the space is initialized', () => {
    expect(resolveAuthGuard(auth({ authenticated: true }), { name: 'setup' })).toEqual({ name: 'home' })
    expect(resolveAuthGuard(auth(), { name: 'setup' })).toEqual({ name: 'login' })
  })

  it('forces login with the expired flag after a forced logout', () => {
    expect(resolveAuthGuard(
      auth({ forcedLogoutReason: 'PASSWORD_CHANGED' }),
      { name: 'home', meta: { auth: true }, fullPath: '/' },
    )).toEqual({ name: 'login', query: { expired: '1' } })
  })

  it('requires authentication for auth routes and remembers the target path', () => {
    expect(resolveAuthGuard(auth(), { name: 'memories', meta: { auth: true }, fullPath: '/memories' }))
      .toEqual({ name: 'login', query: { redirect: '/memories' } })
  })

  it('sends authenticated users home from the login page', () => {
    expect(resolveAuthGuard(auth({ authenticated: true }), { name: 'login' })).toEqual({ name: 'home' })
  })

  it('allows authenticated access to auth routes', () => {
    expect(resolveAuthGuard(auth({ authenticated: true }), { name: 'home', meta: { auth: true }, fullPath: '/' }))
      .toBeUndefined()
  })
})
