import { api } from '../api'
import type { GameSession, GameStroke } from '../types'
import { createStrokeOperationId } from '../utils/gameStrokeSync'

const MAX_BATCH_STROKES = 12

interface StrokeQueueState {
  sessionId: string
  roundNumber: number
  strokes: GameStroke[]
  operationId: string | null
  inFlight: Promise<GameSession | null> | null
}

const queues = new Map<string, StrokeQueueState>()
const collectors = new Map<string, Set<() => void>>()

function queueKey(sessionId: number | string, roundNumber: number) {
  return `${String(sessionId)}:${roundNumber}`
}

function queueFor(sessionId: number | string, roundNumber: number) {
  const key = queueKey(sessionId, roundNumber)
  let state = queues.get(key)
  if (!state) {
    state = {
      sessionId: String(sessionId),
      roundNumber,
      strokes: [],
      operationId: null,
      inFlight: null,
    }
    queues.set(key, state)
  }
  return state
}

export function pendingStrokesFor(sessionId: number | string, roundNumber: number) {
  return queueFor(sessionId, roundNumber).strokes
}

export function enqueueGameStroke(sessionId: number | string, roundNumber: number, stroke: GameStroke) {
  queueFor(sessionId, roundNumber).strokes.push(stroke)
}

export function registerGameStrokeCollector(sessionId: number | string, collector: () => void) {
  const key = String(sessionId)
  const values = collectors.get(key) || new Set<() => void>()
  values.add(collector)
  collectors.set(key, values)
  return () => {
    values.delete(collector)
    if (!values.size) collectors.delete(key)
  }
}

function collectPendingStrokes() {
  collectors.forEach(values => values.forEach(collector => collector()))
}

export function hasPendingGameStrokes() {
  return [...queues.values()].some(state => state.strokes.length > 0 || state.inFlight !== null)
}

export function pendingGameStrokeCount() {
  return [...queues.values()].reduce((total, state) => total + state.strokes.length, 0)
}

export function abandonPendingGameStrokes() {
  queues.forEach(state => {
    state.strokes.splice(0)
    state.operationId = null
  })
}

export async function flushPendingGameStrokes(sessionId: number | string, roundNumber: number) {
  collectPendingStrokes()
  const state = queueFor(sessionId, roundNumber)
  if (state.inFlight) return state.inFlight
  if (!state.strokes.length) return null

  const operation = (async () => {
    let latest: GameSession | null = null
    while (state.strokes.length) {
      const strokes = state.strokes.slice(0, MAX_BATCH_STROKES)
      const operationId = state.operationId || createStrokeOperationId()
      state.operationId = operationId
      latest = await api.addGameStrokes(state.sessionId, state.roundNumber, operationId, strokes)
      state.strokes.splice(0, strokes.length)
      state.operationId = null
    }
    return latest
  })()
  state.inFlight = operation
  try {
    return await operation
  } finally {
    if (state.inFlight === operation) state.inFlight = null
  }
}

export async function flushAllPendingGameStrokes() {
  collectPendingStrokes()
  let completed = true
  for (const state of queues.values()) {
    if (!state.strokes.length && !state.inFlight) continue
    try {
      await flushPendingGameStrokes(state.sessionId, state.roundNumber)
    } catch {
      completed = false
    }
  }
  return { completed, pending: pendingGameStrokeCount() }
}

export function resetGameStrokeQueues() {
  queues.clear()
  collectors.clear()
}
