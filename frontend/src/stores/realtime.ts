import { reactive } from 'vue'
import { API_BASE, getClientId } from '../api/client'
import type { SyncEvent } from '../types'

export const realtimeState = reactive({
  connected: false,
  connecting: false,
  lastEventAt: '',
})

let stream: EventSource | null = null

export function startRealtimeSync() {
  if (stream) return
  realtimeState.connecting = true
  const url = `${API_BASE}/sync/stream?clientId=${encodeURIComponent(getClientId())}`
  stream = new EventSource(url, { withCredentials: true })
  stream.addEventListener('ready', () => {
    realtimeState.connected = true
    realtimeState.connecting = false
  })
  stream.addEventListener('sync', (event) => {
    try {
      const detail = JSON.parse((event as MessageEvent).data) as SyncEvent
      realtimeState.connected = true
      realtimeState.connecting = false
      realtimeState.lastEventAt = detail.occurredAt
      window.dispatchEvent(new CustomEvent<SyncEvent>('love-space:sync', { detail }))
    } catch {
      // Keep the stream alive if a malformed event is ever received.
    }
  })
  stream.onerror = () => {
    realtimeState.connected = false
    realtimeState.connecting = true
  }
}

export function stopRealtimeSync() {
  stream?.close()
  stream = null
  realtimeState.connected = false
  realtimeState.connecting = false
}
