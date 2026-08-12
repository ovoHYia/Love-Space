import type { Letter, SpringPage } from '../types'

export interface LetterPageState {
  letters: Letter[]
  page: number
  totalElements: number
  totalPages: number
  last: boolean
}

/**
 * 合并信笺分页结果。重载第一页时丢弃旧页，加载后续页时按 ID 去重。
 * Map 保留首次出现的位置，同时允许后续响应更新同一封信的内容。
 */
export function mergeLetterPage(
  current: readonly Letter[],
  incoming: SpringPage<Letter>,
  replace = false,
): LetterPageState {
  const merged = new Map<string, Letter>()
  const source = replace ? incoming.content : [...current, ...incoming.content]
  source.forEach((letter) => {
    const key = String(letter.id)
    merged.set(key, merged.has(key) ? { ...merged.get(key), ...letter } : letter)
  })
  return {
    letters: [...merged.values()],
    page: incoming.page,
    totalElements: incoming.totalElements,
    totalPages: incoming.totalPages,
    last: incoming.last,
  }
}
