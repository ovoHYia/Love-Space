import { request } from './client'
import type { Anniversary, AuthPayload, CoupleSummary, DashboardPayload, Diary, Letter, MediaItem, Memory, SpringPage, UserProfile } from '../types'

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
  resetPassword: (username: string, recoveryToken: string, newPassword: string) => request<void>('/auth/reset-password', { method: 'POST', body: JSON.stringify({ username, recoveryToken, newPassword }) }),
  updateAvatar: (avatar: File) => {
    const body = new FormData()
    body.append('avatar', avatar)
    return request<MediaItem>('/profile/avatar', { method: 'POST', body })
  },
  updateMood: (body: { emoji: string; label: string; note: string }) => request('/moods/today', { method: 'PUT', body: JSON.stringify(body) }),
  memories: (query = '') => request<SpringPage<Memory> | Memory[]>(`/memories${query ? `?${query}` : ''}`),
  randomMemory: (excludeId?: Memory['id']) => request<Memory>(`/memories/random${excludeId === undefined ? '' : `?excludeId=${encodeURIComponent(excludeId)}`}`),
  createMemory: (data: { title: string; description: string; eventAt: string; location: string }, files: File[]) => {
    const body = new FormData()
    body.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
    files.forEach((file) => body.append('files', file))
    return request<Memory>('/memories', { method: 'POST', body })
  },
  updateMemory: (id: Memory['id'], data: { title: string; description: string; eventAt: string; location: string }) => request<Memory>(`/memories/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteMemory: (id: Memory['id']) => request<void>(`/memories/${id}`, { method: 'DELETE' }),
  diaries: (authorId?: number | string) => request<Diary[]>(`/diaries${authorId !== undefined ? `?authorId=${encodeURIComponent(authorId)}` : ''}`),
  createDiary: (body: Omit<Diary, 'id' | 'author' | 'authorId' | 'createdAt' | 'updatedAt'>) => request<Diary>('/diaries', { method: 'POST', body: JSON.stringify(body) }),
  updateDiary: (id: Diary['id'], body: Omit<Diary, 'id' | 'author' | 'authorId' | 'createdAt' | 'updatedAt'>) => request<Diary>(`/diaries/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  deleteDiary: (id: Diary['id']) => request<void>(`/diaries/${id}`, { method: 'DELETE' }),
  messages: (page = 0, size = 50) => request<SpringPage<Letter>>(`/messages?page=${page}&size=${size}`),
  createMessage: (content: string) => request<Letter>('/messages', { method: 'POST', body: JSON.stringify({ content }) }),
  readMessage: (id: Letter['id']) => request<Letter>(`/messages/${id}/read`, { method: 'PATCH' }),
  deleteMessage: (id: Letter['id']) => request<void>(`/messages/${id}`, { method: 'DELETE' }),
  anniversaries: () => request<Anniversary[]>('/anniversaries'),
  createAnniversary: (body: Omit<Anniversary, 'id' | 'daysUntil'>) => request<Anniversary>('/anniversaries', { method: 'POST', body: JSON.stringify(body) }),
  updateAnniversary: (id: Anniversary['id'], body: Omit<Anniversary, 'id' | 'daysUntil'>) => request<Anniversary>(`/anniversaries/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  deleteAnniversary: (id: Anniversary['id']) => request<void>(`/anniversaries/${id}`, { method: 'DELETE' }),
}
