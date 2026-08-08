import { describe, expect, it } from 'vitest'
import { createCoalescedRunner, matchesResource } from './resourceSync'

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
})
