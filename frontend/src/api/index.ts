import { jsonRequest, request, startDownload } from './client'
import type { AlbumItem, Anniversary, AppNotification, AuthPayload, CalendarEntry, CalendarEventInput, CoupleSummary, DashboardPayload, Diary, GameSession, GameStroke, GameType, Letter, MediaItem, Memory, MemoryTag, MonthlyReport, NotificationList, NotificationPreferences, SpringPage, TrashItem, UserProfile, Wish, WishInput } from '../types'

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
  initialize: (body: unknown, setupToken: string) => jsonRequest<void>('/setup/initialize', 'POST', body, { headers: { 'X-Setup-Token': setupToken } }),
  login: (username: string, password: string) => {
    const body = new URLSearchParams({ username, password })
    return request<AuthPayload>('/auth/login', { method: 'POST', body })
  },
  logout: () => request<void>('/auth/logout', { method: 'POST' }),
  me: () => request<AuthPayload>('/auth/me'),
  dashboard: () => request<DashboardPayload>('/dashboard'),
  updateProfile: (nickname: string) => jsonRequest<UserProfile>('/profile', 'PUT', { nickname }),
  updateSpaceName: (spaceName: string) => jsonRequest<CoupleSummary>('/space', 'PUT', { spaceName }),
  changePassword: (currentPassword: string, newPassword: string) => jsonRequest<void>('/profile/password', 'PUT', { currentPassword, newPassword }),
  updateAvatar: (avatar: File) => {
    const body = new FormData()
    body.append('avatar', avatar)
    return request<MediaItem>('/profile/avatar', { method: 'POST', body })
  },
  updateMood: (body: { emoji: string; label: string; note: string }) => jsonRequest('/moods/today', 'PUT', body),
  monthlyReport: (month: string) => request<MonthlyReport>(`/reports/monthly?month=${encodeURIComponent(month)}`),
  memories: (query = '') => request<SpringPage<Memory>>(`/memories${query ? `?${query}` : ''}`),
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
  updateMemory: (id: Memory['id'], data: MemoryInput) => jsonRequest<Memory>(`/memories/${id}`, 'PUT', data),
  addMemoryMedia: (id: Memory['id'], files: File[]) => {
    const body = new FormData()
    files.forEach((file) => body.append('files', file))
    return request<Memory>(`/memories/${id}/media`, { method: 'POST', body })
  },
  deleteMemoryMedia: (memoryId: Memory['id'], mediaId: NonNullable<MediaItem['id']>) =>
    request<Memory>(`/memories/${memoryId}/media/${mediaId}`, { method: 'DELETE' }),
  deleteMemory: (id: Memory['id']) => request<void>(`/memories/${id}`, { method: 'DELETE' }),
  diaries: (authorId?: number | string) => request<Diary[]>(`/diaries${authorId !== undefined ? `?authorId=${encodeURIComponent(authorId)}` : ''}`),
  createDiary: (body: Omit<Diary, 'id' | 'authorId' | 'authorNickname' | 'createdAt' | 'updatedAt'>) => jsonRequest<Diary>('/diaries', 'POST', body),
  updateDiary: (id: Diary['id'], body: Omit<Diary, 'id' | 'authorId' | 'authorNickname' | 'createdAt' | 'updatedAt'>) => jsonRequest<Diary>(`/diaries/${id}`, 'PUT', body),
  deleteDiary: (id: Diary['id']) => request<void>(`/diaries/${id}`, { method: 'DELETE' }),
  messages: (page = 0, size = 50) => request<SpringPage<Letter>>(`/messages?page=${page}&size=${size}`),
  createMessage: (content: string, deliverAt?: string) =>
    jsonRequest<Letter>('/messages', 'POST', { content, deliverAt: deliverAt || null }),
  readMessage: (id: Letter['id']) => request<Letter>(`/messages/${id}/read`, { method: 'PATCH' }),
  deleteMessage: (id: Letter['id']) => request<void>(`/messages/${id}`, { method: 'DELETE' }),
  anniversaries: () => request<Anniversary[]>('/anniversaries'),
  createAnniversary: (body: Omit<Anniversary, 'id' | 'daysUntil'>) => jsonRequest<Anniversary>('/anniversaries', 'POST', body),
  updateAnniversary: (id: Anniversary['id'], body: Omit<Anniversary, 'id' | 'daysUntil'>) => jsonRequest<Anniversary>(`/anniversaries/${id}`, 'PUT', body),
  deleteAnniversary: (id: Anniversary['id']) => request<void>(`/anniversaries/${id}`, { method: 'DELETE' }),
  wishes: () => request<Wish[]>('/wishes'),
  createWish: (body: WishInput) => jsonRequest<Wish>('/wishes', 'POST', body),
  updateWish: (id: Wish['id'], body: WishInput) => jsonRequest<Wish>(`/wishes/${id}`, 'PUT', body),
  completeWish: (id: Wish['id']) => request<Wish>(`/wishes/${id}/complete`, { method: 'PATCH' }),
  reopenWish: (id: Wish['id']) => request<Wish>(`/wishes/${id}/reopen`, { method: 'PATCH' }),
  deleteWish: (id: Wish['id']) => request<void>(`/wishes/${id}`, { method: 'DELETE' }),
  calendar: (from: string, to: string) => request<CalendarEntry[]>(`/calendar?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`),
  createCalendarEvent: (body: CalendarEventInput) => jsonRequest<CalendarEntry>('/calendar/events', 'POST', body),
  updateCalendarEvent: (id: CalendarEntry['id'], body: CalendarEventInput) => jsonRequest<CalendarEntry>(`/calendar/events/${id}`, 'PUT', body),
  deleteCalendarEvent: (id: CalendarEntry['id']) => request<void>(`/calendar/events/${id}`, { method: 'DELETE' }),
  trash: () => request<TrashItem[]>('/trash'),
  restoreTrash: (item: TrashItem) => request<void>(`/trash/${item.type}/${item.id}/restore`, { method: 'POST' }),
  purgeTrash: (item: TrashItem) => request<void>(`/trash/${item.type}/${item.id}`, { method: 'DELETE' }),
  emptyTrash: () => request<void>('/trash', { method: 'DELETE' }),
  exportData: () => startDownload('/data/export'),
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
  readNotifications: (ids: AppNotification['id'][]) => jsonRequest<{ affected: number; unreadCount: number }>('/notifications/batch/read', 'POST', { ids }),
  unreadNotifications: (ids: AppNotification['id'][]) => jsonRequest<{ affected: number; unreadCount: number }>('/notifications/batch/unread', 'POST', { ids }),
  deleteNotification: (id: AppNotification['id']) => request<void>(`/notifications/${id}`, { method: 'DELETE' }),
  deleteNotifications: (ids: AppNotification['id'][]) => jsonRequest<{ affected: number; unreadCount: number }>('/notifications/batch', 'DELETE', { ids }),
  deleteReadNotifications: () => request<{ affected: number; unreadCount: number }>('/notifications/read', { method: 'DELETE' }),
  notificationPreferences: () => request<NotificationPreferences>('/notifications/preferences'),
  updateNotificationPreferences: (body: NotificationPreferences) => jsonRequest<NotificationPreferences>('/notifications/preferences', 'PUT', body),
  games: () => request<GameSession[]>('/games'),
  game: (id: GameSession['id']) => request<GameSession>(`/games/${id}`),
  createGame: (gameType: GameType) => jsonRequest<GameSession>('/games', 'POST', { gameType }),
  answerGame: (id: GameSession['id'], answer: string) => jsonRequest<GameSession>(`/games/${id}/answer`, 'POST', { answer }),
  addGameStrokes: (id: GameSession['id'], roundNumber: number, operationId: string, strokes: GameStroke[]) =>
    jsonRequest<GameSession>(`/games/${id}/strokes`, 'POST', { roundNumber, operationId, strokes }),
  clearGameCanvas: (id: GameSession['id'], roundNumber: number) =>
    request<GameSession>(`/games/${id}/canvas?roundNumber=${roundNumber}`, { method: 'DELETE' }),
  guessGame: (id: GameSession['id'], guess: string) => jsonRequest<GameSession>(`/games/${id}/guess`, 'POST', { guess }),
  nextGameRound: (id: GameSession['id'], roundNumber: number) =>
    request<GameSession>(`/games/${id}/next?roundNumber=${roundNumber}`, { method: 'POST' }),
  finishGame: (id: GameSession['id']) => request<GameSession>(`/games/${id}/finish`, { method: 'PATCH' }),
}
