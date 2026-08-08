import { onBeforeUnmount, onMounted } from 'vue'
import type { SyncEvent } from '../types'

export function matchesResource(resources: readonly string[], detail?: Pick<SyncEvent, 'resource'>) {
  return Boolean(detail?.resource && resources.includes(detail.resource))
}

export function createCoalescedRunner(handler: () => void | Promise<void>) {
  let running = false
  let queued = false

  return async function run() {
    if (running) {
      queued = true
      return
    }
    running = true
    try {
      do {
        queued = false
        await handler()
      } while (queued)
    } finally {
      running = false
    }
  }
}

export function useResourceSync(resources: readonly string[], handler: () => void | Promise<void>) {
  const run = createCoalescedRunner(handler)
  const listener = (event: Event) => {
    const detail = (event as CustomEvent<SyncEvent>).detail
    if (matchesResource(resources, detail)) void run()
  }

  onMounted(() => window.addEventListener('love-space:sync', listener))
  onBeforeUnmount(() => window.removeEventListener('love-space:sync', listener))
}
