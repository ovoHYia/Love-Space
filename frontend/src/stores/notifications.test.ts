import { beforeEach, describe, expect, it, vi } from 'vitest'

const { notificationUnreadCount, notifications } = vi.hoisted(() => ({
  notificationUnreadCount: vi.fn(),
  notifications: vi.fn(),
}))

vi.mock('../api', () => ({
  api: {
    notificationUnreadCount,
    notifications,
    readNotification: vi.fn(),
    readAllNotifications: vi.fn(),
  },
}))

import { loadNotifications, notificationState, refreshUnreadCount, resetNotifications } from './notifications'

describe('notifications version guard', () => {
  beforeEach(() => {
    notificationUnreadCount.mockReset()
    notifications.mockReset()
    resetNotifications()
  })

  it('applies the unread count from the latest poll', async () => {
    notificationUnreadCount.mockResolvedValueOnce({ unreadCount: 3 })
    await refreshUnreadCount()
    expect(notificationState.unreadCount).toBe(3)
  })

  it('ignores a stale unread count that arrives after a reset (e.g. logout)', async () => {
    let resolvePoll!: (value: { unreadCount: number }) => void
    notificationUnreadCount.mockReturnValueOnce(
      new Promise<{ unreadCount: number }>((resolve) => { resolvePoll = resolve }))

    const pending = refreshUnreadCount()
    resetNotifications()
    resolvePoll({ unreadCount: 7 })
    await pending

    expect(notificationState.unreadCount).toBe(0)
  })

  it('ignores a stale notifications payload that arrives after a reset', async () => {
    let resolveList!: (value: { items: { id: number }[]; unreadCount: number }) => void
    notifications.mockReturnValueOnce(
      new Promise<{ items: { id: number }[]; unreadCount: number }>((resolve) => { resolveList = resolve }))

    const pending = loadNotifications()
    expect(notificationState.loading).toBe(true)
    resetNotifications()
    resolveList({ items: [{ id: 1 }], unreadCount: 2 })
    await pending

    expect(notificationState.items).toEqual([])
    expect(notificationState.unreadCount).toBe(0)
    expect(notificationState.loaded).toBe(false)
    expect(notificationState.loading).toBe(false)
  })

  it('loads notifications when no reset happened meanwhile', async () => {
    notifications.mockResolvedValueOnce({ items: [{ id: 1 }], unreadCount: 1 })
    await loadNotifications()
    expect(notificationState.items).toEqual([{ id: 1 }])
    expect(notificationState.unreadCount).toBe(1)
    expect(notificationState.loaded).toBe(true)
    expect(notificationState.loading).toBe(false)
  })
})
