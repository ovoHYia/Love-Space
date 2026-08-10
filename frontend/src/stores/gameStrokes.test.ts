import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api'
import {
  enqueueGameStroke,
  flushAllPendingGameStrokes,
  flushPendingGameStrokes,
  pendingStrokesFor,
  resetGameStrokeQueues,
} from './gameStrokes'
import type { GameStroke } from '../types'

vi.mock('../api', () => ({
  api: { addGameStrokes: vi.fn() },
}))

const stroke: GameStroke = {
  tool: 'DRAW',
  color: '#c95868',
  width: 5,
  points: [{ x: 0.1, y: 0.2 }],
}

describe('session-level game stroke queue', () => {
  afterEach(() => {
    resetGameStrokeQueues()
    vi.mocked(api.addGameStrokes).mockReset()
  })

  it('keeps strokes available after a component is rebound to the same session', () => {
    enqueueGameStroke('game-1', 2, stroke)

    expect(pendingStrokesFor('game-1', 2)).toEqual([stroke])
    expect(pendingStrokesFor('game-1', 2)).toHaveLength(1)
  })

  it('shares one in-flight flush and removes strokes only after the server accepts them', async () => {
    let resolve!: (value: object) => void
    vi.mocked(api.addGameStrokes).mockReturnValue(new Promise((nextResolve) => { resolve = nextResolve }) as never)
    enqueueGameStroke('game-1', 1, stroke)

    const first = flushPendingGameStrokes('game-1', 1)
    const second = flushPendingGameStrokes('game-1', 1)
    expect(api.addGameStrokes).toHaveBeenCalledOnce()
    expect(pendingStrokesFor('game-1', 1)).toHaveLength(1)

    resolve({ revision: 3 })
    await expect(first).resolves.toMatchObject({ revision: 3 })
    await expect(second).resolves.toMatchObject({ revision: 3 })
    expect(pendingStrokesFor('game-1', 1)).toHaveLength(0)
  })

  it('reports pending strokes when leaving cannot complete the send', async () => {
    vi.mocked(api.addGameStrokes).mockRejectedValue(new Error('offline'))
    enqueueGameStroke('game-2', 1, stroke)

    await expect(flushAllPendingGameStrokes()).resolves.toEqual({ completed: false, pending: 1 })
  })
})
