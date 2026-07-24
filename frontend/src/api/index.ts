import { download, request } from './client'
import type { AlbumItem, Anniversary, AppNotification, AuthPayload, CalendarEntry, CalendarEventInput, CoupleSummary, DashboardPayload, Diary, Letter, MediaItem, Memory, MemoryTag, MonthlyReport, NotificationList, NotificationPreferences, SpringPage, TrashItem, UserProfile, Wish, WishInput } from '../types'

export interface MemoryInput {
  title: string
  description: string
  eventAt: string
  location: string
  latitude: number | null
  longitude: number | null
  tags: string[]
}

export const api = {
  setupStatus: () => request<{ initialized: boolean }>('/setup/status'),
  initialize: (body: unknown, setupToken: string) => request<void>('/setup/initialize', { method: 'POST', headers: { 'X-Setup-Token': setupToken }, body: JSON.stringify(body) }),
  login: (username: string, password: string) => {
    const body = new URLSearchParams({ username, password })
    return request<AuthPayload>('/auth/login', { method: 'POST', body })
  },
  logout: () => request<void>('/auth/logout', { method: 'POST' }),
  me: () => request<AuthPayload>('/auth/me'),
  dashboard: () => request<DashboardPayload>('/dashboard'),
  updateProfile: (nickname: string) => request<UserProfile>('/profile', { method: 'PUT', body: JSON.stringify({ nickname }) }),
  updateSpaceName: (spaceName: string) => request<CoupleSummary>('/space', { method: 'PUT', body: JSON.stringify({ spaceName }) }),
  changePassword: (currentPassword: string, newPassword: string) => request<void>('/profile/password', { method: 'PUT', body: JSON.stringify({ currentPassword, newPassword }) }),
  updateAvatar: (avatar: File) => {
    const body = new FormData()
    body.append('avatar', avatar)
    return request<MediaItem>('/profile/avatar', { method: 'POST', body })
  },
  updateMood: (body: { emoji: string; label: string; note: string }) => request('/moods/today', { method: 'PUT', body: JSON.stringify(body) }),
  monthlyReport: (month: string) => request<MonthlyReport>(`/reports/monthly?month=${encodeURIComponent(month)}`),
  memories: (query = '') => request<SpringPage<Memory> | Memory[]>(`/memories${query ? `?${query}` : ''}`),
  memoryMap: (tag = '') => request<Memory[]>(`/memories/map${tag ? `?tag=${encodeURIComponent(tag)}` : ''}`),
  memoryTags: () => request<MemoryTag[]>('/memories/tags'),
  memoryAlbum: (query = '') => request<SpringPage<AlbumItem>>(`/memories/album${query ? `?${query}` : ''}`),
  randomMemory: (excludeId?: Memory['id']) => request<Memory>(`/memories/random${excludeId === undefined ? '' : `?excludeId=${encodeURIComponent(excludeId)}`}`),
  createMemory: (data: MemoryInput, files: File[]) => {
    const body = new FormData()
    body.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
    files.forEach((file) => body.append('files', file))
    return request<Memory>('/memories', { method: 'POST', body })
  },
  updateMemory: (id: Memory['id'], data: MemoryInput) => request<Memory>(`/memories/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  addMemoryMedia: (id: Memory['id'], files: File[]) => {
    const body = new FormData()
    files.forEach((file) => body.append('files', file))
    return request<Memory>(`/memories/${id}/media`, { method: 'POST', body })
  },
  deleteMemoryMedia: (memoryId: Memory['id'], mediaId: NonNullable<MediaItem['id']>) =>
    request<Memory>(`/memories/${memoryId}/media/${mediaId}`, { method: 'DELETE' }),
  deleteMemory: (id: Memory['id']) => request<void>(`/memories/${id}`, { method: 'DELETE' }),
  diaries: (authorId?: number | string) => request<Diary[]>(`/diaries${authorId !== undefined ? `?authorId=${encodeURIComponent(authorId)}` : ''}`),
  createDiary: (body: Omit<Diary, 'id' | 'author' | 'authorId' | 'createdAt' | 'updatedAt'>) => request<Diary>('/diaries', { method: 'POST', body: JSON.stringify(body) }),
  updateDiary: (id: Diary['id'], body: Omit<Diary, 'id' | 'author' | 'authorId' | 'createdAt' | 'updatedAt'>) => request<Diary>(`/diaries/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  deleteDiary: (id: Diary['id']) => request<void>(`/diaries/${id}`, { method: 'DELETE' }),
  messages: (page = 0, size = 50) => request<SpringPage<Letter>>(`/messages?page=${page}&size=${size}`),
  createMessage: (content: string, deliverAt?: string) => request<Letter>('/messages', {
    method: 'POST',
    body: JSON.stringify({ content, deliverAt: deliverAt || null }),
  }),
  readMessage: (id: Letter['id']) => request<Letter>(`/messages/${id}/read`, { method: 'PATCH' }),
  deleteMessage: (id: Letter['id']) => request<void>(`/messages/${id}`, { method: 'DELETE' }),
  anniversaries: () => request<Anniversary[]>('/anniversaries'),
  createAnniversary: (body: Omit<Anniversary, 'id' | 'daysUntil'>) => request<Anniversary>('/anniversaries', { method: 'POST', body: JSON.stringify(body) }),
  updateAnniversary: (id: Anniversary['id'], body: Omit<Anniversary, 'id' | 'daysUntil'>) => request<Anniversary>(`/anniversaries/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  deleteAnniversary: (id: Anniversary['id']) => request<void>(`/anniversaries/${id}`, { method: 'DELETE' }),
  wishes: () => request<Wish[]>('/wishes'),
  createWish: (body: WishInput) => request<Wish>('/wishes', { method: 'POST', body: JSON.stringify(body) }),
  updateWish: (id: Wish['id'], body: WishInput) => request<Wish>(`/wishes/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  completeWish: (id: Wish['id']) => request<Wish>(`/wishes/${id}/complete`, { method: 'PATCH' }),
  reopenWish: (id: Wish['id']) => request<Wish>(`/wishes/${id}/reopen`, { method: 'PATCH' }),
  deleteWish: (id: Wish['id']) => request<void>(`/wishes/${id}`, { method: 'DELETE' }),
  calendar: (from: string, to: string) => request<CalendarEntry[]>(`/calendar?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`),
  createCalendarEvent: (body: CalendarEventInput) => request<CalendarEntry>('/calendar/events', { method: 'POST', body: JSON.stringify(body) }),
  updateCalendarEvent: (id: CalendarEntry['id'], body: CalendarEventInput) => request<CalendarEntry>(`/calendar/events/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  deleteCalendarEvent: (id: CalendarEntry['id']) => request<void>(`/calendar/events/${id}`, { method: 'DELETE' }),
  trash: () => request<TrashItem[]>('/trash'),
  restoreTrash: (item: TrashItem) => request<void>(`/trash/${item.type}/${item.id}/restore`, { method: 'POST' }),
  purgeTrash: (item: TrashItem) => request<void>(`/trash/${item.type}/${item.id}`, { method: 'DELETE' }),
  emptyTrash: () => request<void>('/trash', { method: 'DELETE' }),
  exportData: () => download('/data/export'),
  notifications: (query: { page?: number; size?: number; status?: string; category?: string; keyword?: string } = {}) => {
    const params = new URLSearchParams()
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined && value !== '') params.set(key, String(value))
    })
    return request<NotificationList>(`/notifications${params.size ? `?${params}` : ''}`)
  },
  notificationUnreadCount: () => request<{ unreadCount: number }>('/notifications/unread-count'),
  readNotification: (id: AppNotification['id']) => request<AppNotification>(`/notifications/${id}/read`, { method: 'PATCH' }),
  unreadNotification: (id: AppNotification['id']) => request<AppNotification>(`/notifications/${id}/unread`, { method: 'PATCH' }),
  readAllNotifications: () => request<void>('/notifications/read-all', { method: 'POST' }),
  readNotifications: (ids: AppNotification['id'][]) => request<{ affected: number; unreadCount: number }>('/notifications/batch/read', { method: 'POST', body: JSON.stringify({ ids }) }),
  unreadNotifications: (ids: AppNotification['id'][]) => request<{ affected: number; unreadCount: number }>('/notifications/batch/unread', { method: 'POST', body: JSON.stringify({ ids }) }),
  deleteNotification: (id: AppNotification['id']) => request<void>(`/notifications/${id}`, { method: 'DELETE' }),
  deleteNotifications: (ids: AppNotification['id'][]) => request<{ affected: number; unreadCount: number }>('/notifications/batch', { method: 'DELETE', body: JSON.stringify({ ids }) }),
  deleteReadNotifications: () => request<{ affected: number; unreadCount: number }>('/notifications/read', { method: 'DELETE' }),
  notificationPreferences: () => request<NotificationPreferences>('/notifications/preferences'),
  updateNotificationPreferences: (body: NotificationPreferences) => request<NotificationPreferences>('/notifications/preferences', { method: 'PUT', body: JSON.stringify(body) }),
}
