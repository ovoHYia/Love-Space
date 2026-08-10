import { describe, expect, it } from 'vitest'
import {
  MEMORY_DRAFT_MAX_AGE_MS,
  MEMORY_DRAFT_VERSION,
  areMemorySnapshotsEqual,
  createMemoryDraft,
  createMemoryEditorSnapshot,
  isMemoryDraftExpired,
  parseMemoryDraft,
  serializeMemoryDraft,
} from './memoryDraft'

const form = {
  title: '  海边  ',
  description: '  风很轻  ',
  eventAt: ' 2026-08-10T18:30 ',
  location: ' 厦门 ',
  tags: ['  旅行 ', '约会'],
}

describe('回忆编辑器快照', () => {
  it('比较时 trim 字符串，同时保留标签顺序和新文件元数据', () => {
    const first = createMemoryEditorSnapshot(form, '  还没写完  ', [{ name: ' beach.jpg ', size: 12, type: ' image/jpeg ' }])
    const same = createMemoryEditorSnapshot({
      ...form,
      title: '海边',
      description: '风很轻',
      eventAt: '2026-08-10T18:30',
      location: '厦门',
      tags: ['旅行', '约会'],
    }, '还没写完', [{ name: 'beach.jpg', size: 12, type: 'image/jpeg' }])
    const reordered = createMemoryEditorSnapshot({ ...form, tags: ['约会', '旅行'] }, '还没写完', [{ name: 'beach.jpg', size: 12, type: 'image/jpeg' }])

    expect(areMemorySnapshotsEqual(first, same)).toBe(true)
    expect(areMemorySnapshotsEqual(first, reordered)).toBe(false)
  })
})

describe('回忆草稿序列化', () => {
  it('保存文字、标签、更新时间和媒体元数据，不保存 File 对象', () => {
    const draft = createMemoryDraft(form, '  输入中的标签 ', [{ name: 'photo.png', size: 2048, type: 'image/png' }], 1234)
    const raw = serializeMemoryDraft(draft)
    const parsed = parseMemoryDraft(raw, 1234)

    expect(parsed).toEqual({
      version: MEMORY_DRAFT_VERSION,
      updatedAt: 1234,
      form: { ...form, tags: [...form.tags], tagInput: '  输入中的标签 ' },
      pendingMedia: [{ name: 'photo.png', size: 2048, type: 'image/png' }],
    })
    expect(raw).not.toContain('File')
  })

  it('解析失败或版本不支持时安全返回 null', () => {
    expect(parseMemoryDraft('{not-json}', 1234)).toBeNull()
    expect(parseMemoryDraft(JSON.stringify({ version: 99, updatedAt: 1234 }), 1234)).toBeNull()
  })
})

describe('回忆草稿过期判断', () => {
  it('24 小时内有效，超过 24 小时失效', () => {
    const now = 10_000_000
    expect(isMemoryDraftExpired(now - MEMORY_DRAFT_MAX_AGE_MS, now)).toBe(false)
    expect(isMemoryDraftExpired(now - MEMORY_DRAFT_MAX_AGE_MS - 1, now)).toBe(true)
  })
})
