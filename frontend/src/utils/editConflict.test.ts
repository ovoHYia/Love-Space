import { describe, expect, it } from 'vitest'
import { ApiError } from '../api/client'
import { isStaleUpdate, staleEditorState, STALE_UPDATE_MESSAGE } from './editConflict'

describe('edit conflict handling', () => {
  it('recognizes only the stable stale-update error code', () => {
    expect(isStaleUpdate(new ApiError(STALE_UPDATE_MESSAGE, 409, 'STALE_UPDATE'))).toBe(true)
    expect(isStaleUpdate(new ApiError('登录已失效', 401, 'UNAUTHORIZED'))).toBe(false)
  })

  it('keeps the editor value and open state after a conflict', () => {
    const form = { title: '用户尚未提交的内容', note: '不要清空' }
    const result = staleEditorState(form)
    expect(result.keepOpen).toBe(true)
    expect(result.value).toBe(form)
    expect(result.message).toBe(STALE_UPDATE_MESSAGE)
  })
})
