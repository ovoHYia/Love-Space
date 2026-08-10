import { describe, expect, it } from 'vitest'
import { dateDay, formatDate, formatDateTime, toBeijingOffsetDateTime, toLocalDateTimeInput } from './utils'

describe('北京时间与日期模型', () => {
  it('按年月日处理纯日期，不受运行环境时区影响', () => {
    expect(dateDay('2026-02-03')).toBe(3)
    expect(formatDate('2026-02-03', { year: 'numeric', month: '2-digit', day: '2-digit' })).toContain('2026')
    expect(formatDate('2026-02-03', { year: 'numeric', month: '2-digit', day: '2-digit' })).toContain('03')
  })

  it('把带 offset 的时刻统一展示和编辑为北京时间', () => {
    expect(toLocalDateTimeInput('2025-12-31T16:30:00Z')).toBe('2026-01-01T00:30')
    expect(toLocalDateTimeInput('2026-01-01T00:30:00+08:00')).toBe('2026-01-01T00:30')
    expect(formatDateTime('2025-12-31T16:30:00Z')).toContain('1月1日')
    expect(toBeijingOffsetDateTime('2026-01-01T00:30')).toBe('2026-01-01T00:30:00+08:00')
  })
})
