import { describe, expect, it } from 'vitest'
import { resolveResyncOutcome } from './realtime'

describe('resolveResyncOutcome', () => {
  it('ignores callbacks without a detail or with a stale generation', () => {
    expect(resolveResyncOutcome(null, 3, true)).toBeNull()
    expect(resolveResyncOutcome(undefined, 3, true)).toBeNull()
    expect(resolveResyncOutcome({ generation: 2, ok: true }, 3, true)).toBeNull()
  })

  it('marks the stream connected when resync succeeds', () => {
    expect(resolveResyncOutcome({ generation: 3, ok: true }, 3, false))
      .toEqual({ connected: true, connecting: false })
  })

  it('keeps connected when resync fails but the SSE stream is healthy', () => {
    expect(resolveResyncOutcome({ generation: 3, ok: false }, 3, true))
      .toEqual({ connected: true, connecting: false })
  })

  it('marks disconnected when resync fails and the stream is down', () => {
    expect(resolveResyncOutcome({ generation: 3, ok: false }, 3, false))
      .toEqual({ connected: false, connecting: true })
  })
})
