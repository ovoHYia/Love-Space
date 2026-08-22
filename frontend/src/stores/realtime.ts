import { reactive } from 'vue'
import { API_BASE, getClientId } from '../api/client'
import type { SyncEvent } from '../types'

export const realtimeState = reactive({
  connected: false,
  connecting: false,
  lastEventAt: '',
})

let stream: EventSource | null = null
let resyncGeneration = 0

export interface ResyncDetail {
  generation?: number
  ok?: boolean
}

/**
 * resync 完成后的连接状态判定（纯函数，便于单测）：
 * 世代不匹配的回调直接忽略；resync 瞬时失败但 SSE 流本身健康时，
 * 不应把连接状态误标为断开。
 */
export function resolveResyncOutcome(
  detail: ResyncDetail | null | undefined,
  expectedGeneration: number,
  streamOpen: boolean,
): { connected: boolean; connecting: boolean } | null {
  if (!detail || detail.generation !== expectedGeneration) return null
  const connected = detail.ok === true || streamOpen
  return { connected, connecting: !connected }
}

function handleResyncComplete(event: Event) {
  const detail = (event as CustomEvent<ResyncDetail>).detail
  const outcome = resolveResyncOutcome(detail, resyncGeneration, stream?.readyState === EventSource.OPEN)
  if (!outcome) return
  realtimeState.connected = outcome.connected
  realtimeState.connecting = outcome.connecting
}

export function startRealtimeSync() {
  if (stream) return
  window.addEventListener('love-space:resync-complete', handleResyncComplete)
  realtimeState.connecting = true
  const url = `${API_BASE}/sync/stream?clientId=${encodeURIComponent(getClientId())}`
  stream = new EventSource(url, { withCredentials: true })
  stream.addEventListener('ready', () => {
    resyncGeneration += 1
    realtimeState.connected = false
    realtimeState.connecting = true
    window.dispatchEvent(new CustomEvent('love-space:resync', {
      detail: { generation: resyncGeneration, reason: 'sse-ready' },
    }))
  })
  stream.addEventListener('sync', (event) => {
    try {
      const detail = JSON.parse((event as MessageEvent).data) as SyncEvent
      realtimeState.lastEventAt = detail.occurredAt
      realtimeState.connected = true
      realtimeState.connecting = false
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
  window.removeEventListener('love-space:resync-complete', handleResyncComplete)
  realtimeState.connected = false
  realtimeState.connecting = false
}
