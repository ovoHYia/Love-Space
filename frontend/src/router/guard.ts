/**
 * 路由守卫的纯决策逻辑（与路由实例、组件解耦，便于单测）。
 * 返回 `undefined` 表示放行；返回对象表示重定向目标。
 */

export interface GuardAuthSnapshot {
  initialized: boolean
  authenticated: boolean
  forcedLogoutReason: string | null
}

export interface GuardTarget {
  name?: string | symbol | null
  meta?: Record<string, unknown>
  fullPath?: string
}

export interface GuardRedirect {
  name: string
  query?: Record<string, string>
}

export function resolveAuthGuard(
  auth: GuardAuthSnapshot,
  to: GuardTarget,
): GuardRedirect | undefined {
  if (!auth.initialized && to.name !== 'setup') return { name: 'setup' }
  if (auth.initialized && to.name === 'setup') {
    return auth.authenticated ? { name: 'home' } : { name: 'login' }
  }
  if (auth.forcedLogoutReason && to.meta?.auth) return { name: 'login', query: { expired: '1' } }
  if (to.meta?.auth && !auth.authenticated) {
    return { name: 'login', query: { redirect: to.fullPath ?? '/' } }
  }
  if (to.name === 'login' && auth.authenticated) return { name: 'home' }
  return undefined
}
