import { describe, expect, it } from 'vitest'
import { acceptsGameSnapshot, newerGameSnapshot } from './gameSnapshot'
import type { GameSession } from '../types'

function snapshot(revision: number): GameSession {
  return { revision } as GameSession
}

describe('game snapshot revisions', () => {
  it('rejects an older snapshot that arrives after a newer one', () => {
    const current = snapshot(8)
    const stale = snapshot(7)

    expect(acceptsGameSnapshot(current, stale)).toBe(false)
    expect(newerGameSnapshot(current, stale)).toBe(current)
  })

  it('accepts a same-or-newer snapshot', () => {
    const current = snapshot(8)

    expect(acceptsGameSnapshot(current, snapshot(8))).toBe(true)
    expect(newerGameSnapshot(current, snapshot(9))?.revision).toBe(9)
  })
})
