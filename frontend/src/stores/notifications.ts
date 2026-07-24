import { reactive } from 'vue'
import { api } from '../api'
import { sameId } from '../utils'
import type { AppNotification } from '../types'

interface NotificationState {
  items: AppNotification[]
  unreadCount: number
  loading: boolean
  loaded: boolean
}

export const notificationState = reactive<NotificationState>({
  items: [],
  unreadCount: 0,
  loading: false,
  loaded: false,
})

let pollTimer: number | undefined
let stateVersion = 0

export async function refreshUnreadCount() {
  const version = stateVersion
  try {
    const { unreadCount } = await api.notificationUnreadCount()
    if (version !== stateVersion) return
    notificationState.unreadCount = unreadCount
  } catch {
    // Stay quiet: a failed background poll should never interrupt the couple's browsing.
  }
}

export async function loadNotifications() {
  const version = stateVersion
  notificationState.loading = true
  try {
    const data = await api.notifications({ size: 8 })
    if (version !== stateVersion) return
    notificationState.items = data.items || []
    notificationState.unreadCount = data.unreadCount ?? 0
    notificationState.loaded = true
  } finally {
    if (version === stateVersion) notificationState.loading = false
  }
}

export async function markNotificationRead(id: AppNotification['id']) {
  const version = stateVersion
  const updated = await api.readNotification(id)
  if (version !== stateVersion) return
  const index = notificationState.items.findIndex((item) => sameId(item.id, id))
  if (index >= 0) notificationState.items[index] = updated
  await refreshUnreadCount()
}

export async function markAllNotificationsRead() {
  const version = stateVersion
  await api.readAllNotifications()
  if (version !== stateVersion) return
  const now = new Date().toISOString()
  notificationState.items = notificationState.items.map((item) => ({ ...item, readAt: item.readAt || now }))
  notificationState.unreadCount = 0
}

export function startNotificationPolling() {
  stopNotificationPolling()
  refreshUnreadCount()
  pollTimer = window.setInterval(refreshUnreadCount, 60000)
}

export function stopNotificationPolling() {
  if (pollTimer) {
    window.clearInterval(pollTimer)
    pollTimer = undefined
  }
}

export function resetNotifications() {
  stateVersion++
  stopNotificationPolling()
  notificationState.items = []
  notificationState.unreadCount = 0
  notificationState.loading = false
  notificationState.loaded = false
}
