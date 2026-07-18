import { reactive } from 'vue'

export type ToastTone = 'success' | 'error' | 'info'
export interface ToastItem { id: number; message: string; tone: ToastTone }

const state = reactive<{ items: ToastItem[] }>({ items: [] })
let id = 0

export function useToast() {
  function show(message: string, tone: ToastTone = 'info') {
    const item = { id: ++id, message, tone }
    state.items.push(item)
    window.setTimeout(() => dismiss(item.id), 3500)
  }
  function dismiss(target: number) {
    const index = state.items.findIndex((item) => item.id === target)
    if (index >= 0) state.items.splice(index, 1)
  }
  return { toasts: state.items, show, dismiss }
}
