// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest'
import { createCoalescedRunner, matchesResource, registerResyncHandler } from './resourceSync'

describe('resource sync', () => {
  it('matches only declared resources', () => {
    expect(matchesResource(['diaries'], { resource: 'diaries' })).toBe(true)
    expect(matchesResource(['diaries'], { resource: 'wishes' })).toBe(false)
  })

  it('coalesces events that arrive while a refresh is running', async () => {
    let calls = 0
    let release = () => {}
    const firstRefresh = new Promise<void>((resolve) => { release = resolve })
    const runner = createCoalescedRunner(async () => {
      calls++
      if (calls === 1) await firstRefresh
    })

    const first = runner()
    void runner()
    void runner()
    release()
    await first

    expect(calls).toBe(2)
  })

  it('lets the trash page react to both sides of a restore event', () => {
    const subscribed = ['trash', 'memories', 'diaries', 'messages', 'anniversaries', 'wishes', 'calendar']

    expect(subscribed.every(resource => matchesResource(subscribed, { resource }))).toBe(true)
  })

  it('refreshes registered resources after every ready resync and reports completion', async () => {
    const refresh = vi.fn().mockResolvedValue(undefined)
    const unregister = registerResyncHandler(refresh)
    const complete = new Promise<CustomEvent>((resolve) => {
      const listener = (event: Event) => {
        window.removeEventListener('love-space:resync-complete', listener)
        resolve(event as CustomEvent)
      }
      window.addEventListener('love-space:resync-complete', listener)
    })

    window.dispatchEvent(new CustomEvent('love-space:resync', { detail: { generation: 7 } }))
    const event = await complete
    unregister()

    expect(refresh).toHaveBeenCalledOnce()
    expect(event.detail).toEqual({ generation: 7, ok: true })
  })

  it('reports a failed resync so the connection remains retryable', async () => {
    const refresh = vi.fn().mockResolvedValue(false)
    const unregister = registerResyncHandler(refresh)
    const complete = new Promise<CustomEvent>((resolve) => {
      const listener = (event: Event) => {
        window.removeEventListener('love-space:resync-complete', listener)
        resolve(event as CustomEvent)
      }
      window.addEventListener('love-space:resync-complete', listener)
    })

    window.dispatchEvent(new CustomEvent('love-space:resync', { detail: { generation: 8 } }))
    const event = await complete
    unregister()

    expect(refresh).toHaveBeenCalledOnce()
    expect(event.detail).toEqual({ generation: 8, ok: false })
  })
})
