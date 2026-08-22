import { mediaUrl } from '../api/client'
import type { MediaItem } from '../types'

// 与后端 MediaStorageService 的白名单/单文件上限保持一致，上传前先拦截
export const UPLOAD_MAX_BYTES = 200 * 1024 * 1024
const ALLOWED_UPLOAD_TYPES = new Set([
  'image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/avif', 'image/heic',
  'video/mp4', 'video/webm', 'video/quicktime', 'video/x-matroska',
  'audio/mpeg', 'audio/mp4', 'audio/wav', 'audio/x-wav', 'audio/ogg', 'audio/webm', 'audio/aac', 'audio/x-m4a',
])

export function validateUploadFile(file: File, imageOnly = false): string | null {
  const type = file.type.toLowerCase()
  if (!ALLOWED_UPLOAD_TYPES.has(type)) return `不支持的文件类型：${file.name || '未命名文件'}`
  if (imageOnly && !type.startsWith('image/')) return '头像请选择图片文件。'
  if (file.size > UPLOAD_MAX_BYTES) return `单个文件不能超过 200MB：${file.name}`
  return null
}

export function memoryMediaType(item: MediaItem) {
  const type = (item.contentType || item.mediaType).toLowerCase()
  if (type.includes('video')) return 'video'
  if (type.includes('audio')) return 'audio'
  return 'image'
}

export function memoryMediaUrl(item: MediaItem) {
  return mediaUrl(item.id, item.url)
}
