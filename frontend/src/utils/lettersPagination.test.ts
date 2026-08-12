import { describe, expect, it } from 'vitest'
import type { Letter, SpringPage } from '../types'
import { mergeLetterPage } from './lettersPagination'

function letter(id: number): Letter {
  return {
    id,
    content: `信笺 ${id}`,
    createdAt: `2026-08-${String((id % 28) + 1).padStart(2, '0')}T00:00:00+08:00`,
    readAt: null,
    scheduled: false,
    deliverAt: `2026-08-${String((id % 28) + 1).padStart(2, '0')}T00:00:00+08:00`,
    authorId: 1,
    authorNickname: '小爱',
    recipientId: 2,
    recipientNickname: '小宝',
  }
}

function page(content: Letter[], pageNumber: number, totalElements: number, totalPages: number): SpringPage<Letter> {
  return {
    content,
    page: pageNumber,
    size: content.length,
    totalElements,
    totalPages,
    first: pageNumber === 0,
    last: pageNumber === totalPages - 1,
  }
}

describe('信笺分页合并', () => {
  it('加载第二页后可以访问第 51 封，并使用后端总数', () => {
    const first = mergeLetterPage([], page(Array.from({ length: 50 }, (_, index) => letter(index + 1)), 0, 51, 2), true)
    const second = mergeLetterPage(first.letters, page([letter(51)], 1, 51, 2))

    expect(second.letters).toHaveLength(51)
    expect(second.letters.some((item) => item.id === 51)).toBe(true)
    expect(second.totalElements).toBe(51)
    expect(second.totalPages).toBe(2)
  })

  it('新信重载第一页再加载更多时按 ID 去重并保持所有信笺', () => {
    const first = mergeLetterPage([], page(Array.from({ length: 50 }, (_, index) => letter(index + 1)), 0, 51, 2), true)
    const loaded = mergeLetterPage(first.letters, page([letter(51)], 1, 51, 2))
    const refreshed = mergeLetterPage([], page(Array.from({ length: 50 }, (_, index) => letter(index + 3)), 0, 52, 2), true)
    const merged = mergeLetterPage(refreshed.letters, page([letter(3), letter(2), letter(1)], 1, 52, 2))
    const ids = merged.letters.map((item) => String(item.id))

    expect(loaded.letters).toHaveLength(51)
    expect(new Set(ids).size).toBe(ids.length)
    expect(ids).toHaveLength(52)
    expect(merged.totalElements).toBe(52)
  })
})
