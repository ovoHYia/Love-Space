import { onBeforeUnmount, onMounted } from 'vue'
import type { SyncEvent } from '../types'

export type SyncHandlerResult = boolean | void | Promise<boolean | void>

export function matchesResource(resources: readonly string[], detail?: Pick<SyncEvent, 'resource'>) {
  return Boolean(detail?.resource && resources.includes(detail.resource))
}

export function createCoalescedRunner(handler: () => SyncHandlerResult) {
  let running = false
  let queued = false
  let active: Promise<boolean> | null = null

  return function run(): Promise<boolean> {
    if (running) {
      queued = true
      return active || Promise.resolve(true)
    }
    running = true
    active = (async () => {
      let successful = true
      try {
        do {
          queued = false
          if (await handler() === false) successful = false
        } while (queued)
        return successful
      } finally {
        running = false
        active = null
      }
    })()
    return active
  }
}

type ResyncHandler = () => SyncHandlerResult
const resyncHandlers = new Set<ResyncHandler>()
let resyncQueue = Promise.resolve()

export function registerResyncHandler(handler: ResyncHandler) {
  resyncHandlers.add(handler)
  return () => resyncHandlers.delete(handler)
}

if (typeof window !== 'undefined') {
  window.addEventListener('love-space:resync', (event: Event) => {
    const detail = (event as CustomEvent<{ generation?: number }>).detail || {}
    const generation = detail.generation || 0
    resyncQueue = resyncQueue.then(async () => {
      const results = await Promise.allSettled([...resyncHandlers].map(async handler => (await handler()) !== false))
      window.dispatchEvent(new CustomEvent('love-space:resync-complete', {
        detail: { generation, ok: results.every(result => result.status === 'fulfilled' && result.value) },
      }))
    })
  })
}

export function useResourceSync(resources: readonly string[], handler: () => SyncHandlerResult) {
  const run = createCoalescedRunner(handler)
  const resyncHandler = () => run()
  const listener = (event: Event) => {
    const detail = (event as CustomEvent<SyncEvent>).detail
    if (matchesResource(resources, detail)) void run()
  }

  onMounted(() => {
    window.addEventListener('love-space:sync', listener)
    resyncHandlers.add(resyncHandler)
  })
  onBeforeUnmount(() => {
    window.removeEventListener('love-space:sync', listener)
    resyncHandlers.delete(resyncHandler)
  })
}
