import { ApiError } from '../api/client'

export const STALE_UPDATE_MESSAGE = '对方或另一台设备已修改此内容，请加载最新版本后重新确认。'

export function isStaleUpdate(error: unknown): boolean {
  return error instanceof ApiError && error.code === 'STALE_UPDATE'
}

/**
 * 冲突处理只返回提示，不改变编辑器数据；调用方因此可以保持弹窗和用户输入。
 */
export function staleEditorState<T>(value: T) {
  return { value, keepOpen: true, message: STALE_UPDATE_MESSAGE }
}
