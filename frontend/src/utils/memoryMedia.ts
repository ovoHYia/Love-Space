import { mediaUrl } from '../api/client'
import type { MediaItem } from '../types'

export function memoryMediaType(item: MediaItem) {
  const type = (item.contentType || item.mediaType).toLowerCase()
  if (type.includes('video')) return 'video'
  if (type.includes('audio')) return 'audio'
  return 'image'
}

export function memoryMediaUrl(item: MediaItem) {
  return mediaUrl(item.id, item.url)
}
