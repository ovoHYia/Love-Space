import type { GameSession } from '../types'

function revisionOf(game: Pick<GameSession, 'revision'>) {
  const value = Number(game.revision)
  return Number.isFinite(value) ? value : 0
}

export function acceptsGameSnapshot(current: GameSession | null | undefined, incoming: GameSession) {
  return !current || revisionOf(incoming) >= revisionOf(current)
}

export function newerGameSnapshot(current: GameSession | null | undefined, incoming: GameSession) {
  return acceptsGameSnapshot(current, incoming) ? incoming : current || null
}
