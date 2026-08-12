<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, BellRing, Check } from 'lucide-vue-next'
import { errorMessage } from '../api/client'
import { useToast } from '../composables/toast'
import { formatDateTime } from '../utils'
import {
  loadNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  notificationState,
} from '../stores/notifications'
import type { AppNotification } from '../types'

const props = withDefaults(defineProps<{ variant?: 'sidebar' | 'header' }>(), { variant: 'header' })

const { show } = useToast()
const route = useRoute()
const router = useRouter()
const open = ref(false)
const rootEl = ref<HTMLElement | null>(null)
const triggerEl = ref<HTMLButtonElement | null>(null)
const panelEl = ref<HTMLElement | null>(null)
const panelId = computed(() => `notification-popover-${props.variant}`)

const items = computed(() => notificationState.items)
const unreadCount = computed(() => notificationState.unreadCount)
const loading = computed(() => notificationState.loading)
const badge = computed(() => (unreadCount.value > 99 ? '99+' : String(unreadCount.value)))

async function toggle() {
  if (props.variant === 'sidebar') {
    openCenter()
    return
  }
  open.value = !open.value
  if (open.value) {
    try {
      await loadNotifications()
    } catch (cause) {
      show(errorMessage(cause), 'error')
    }
  }
}

async function openItem(item: AppNotification) {
  try {
    if (!item.readAt) await markNotificationRead(item.id)
  } catch (cause) {
    show(errorMessage(cause), 'error')
  }
  open.value = false
  if (item.referenceType === 'ANNIVERSARY' && route.name !== 'anniversaries') {
    await router.push({ name: 'anniversaries' })
  } else if (item.referenceType === 'MESSAGE' && route.name !== 'letters') {
    await router.push({ name: 'letters' })
    refreshLettersPage()
  } else if (item.referenceType === 'MESSAGE') {
    refreshLettersPage()
  } else if (item.referenceType === 'WISH' && route.name !== 'wishes') {
    await router.push({ name: 'wishes' })
  }
}

function refreshLettersPage() {
  window.dispatchEvent(new CustomEvent('love-space:sync', {
    detail: { action: 'NOTIFICATION_CLICK', resource: 'messages', actorId: 0, occurredAt: new Date().toISOString() },
  }))
}

async function readAll() {
  try {
    await markAllNotificationsRead()
  } catch (cause) {
    show(errorMessage(cause), 'error')
  }
}

function openCenter() {
  open.value = false
  if (route.name !== 'notifications') router.push({ name: 'notifications' })
}

function onDocumentClick(event: MouseEvent) {
  if (!open.value) return
  if (rootEl.value && !rootEl.value.contains(event.target as Node)) open.value = false
}

function focusableElements() {
  return Array.from(panelEl.value?.querySelectorAll<HTMLElement>(
    'button:not(:disabled), a[href], input:not(:disabled), [tabindex]:not([tabindex="-1"])',
  ) || [])
}

function onDocumentKeydown(event: KeyboardEvent) {
  if (!open.value) return
  if (event.key === 'Escape') {
    event.preventDefault()
    open.value = false
    void nextTick(() => triggerEl.value?.focus())
    return
  }
  if (event.key !== 'Tab') return
  const focusable = focusableElements()
  if (!focusable.length) {
    event.preventDefault()
    return
  }
  const first = focusable[0]
  const last = focusable.at(-1)!
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

watch(open, async (value) => {
  if (value) {
    document.addEventListener('click', onDocumentClick, true)
    document.addEventListener('keydown', onDocumentKeydown)
    await nextTick()
    focusableElements()[0]?.focus()
  } else {
    document.removeEventListener('click', onDocumentClick, true)
    document.removeEventListener('keydown', onDocumentKeydown)
  }
})
watch(() => route.fullPath, () => { open.value = false })
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick, true)
  document.removeEventListener('keydown', onDocumentKeydown)
})
</script>

<template>
  <div class="notif" :class="variant" ref="rootEl">
    <button
      ref="triggerEl"
      class="notif-trigger"
      :class="[variant, { active: open || (variant === 'sidebar' && route.name === 'notifications') }]"
      type="button"
      :aria-label="unreadCount ? `通知，有 ${unreadCount} 条未读` : '通知'"
      :aria-expanded="open"
      :aria-controls="variant === 'header' ? panelId : undefined"
      @click="toggle"
    >
      <component :is="unreadCount ? BellRing : Bell" :size="variant === 'sidebar' ? 20 : 21" aria-hidden="true" />
      <span v-if="variant === 'sidebar'" class="notif-label">通知中心</span>
      <span v-if="unreadCount" class="notif-badge" :class="{ inline: variant === 'sidebar' }">{{ badge }}</span>
    </button>

    <transition name="notif-pop">
      <div v-if="open" :id="panelId" ref="panelEl" class="notif-panel" :class="variant" role="dialog" aria-label="通知列表" aria-modal="false">
        <header class="notif-head">
          <strong>提醒</strong>
          <button v-if="unreadCount" class="notif-readall" type="button" @click="readAll">
            <Check :size="14" />全部已读
          </button>
        </header>
        <div class="notif-scroll">
          <p v-if="loading" class="notif-empty">正在加载提醒…</p>
          <p v-else-if="!items.length" class="notif-empty">还没有提醒，安心生活呀 ♡</p>
          <ul v-else class="notif-list">
            <li v-for="item in items" :key="item.id">
              <button class="notif-item" :class="{ unread: !item.readAt }" type="button" @click="openItem(item)">
                <span class="notif-mark" aria-hidden="true"></span>
                <span class="notif-main">
                  <span class="notif-title">{{ item.title }}</span>
                  <span class="notif-text">{{ item.body }}</span>
                  <span class="notif-time">{{ formatDateTime(item.createdAt) }}</span>
                </span>
              </button>
            </li>
          </ul>
        </div>
        <footer class="notif-footer">
          <button type="button" @click="openCenter">查看全部通知</button>
        </footer>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.notif { position: relative; }

/* Trigger — header (mobile top bar) variant is a round icon button */
.notif-trigger { position: relative; min-width: var(--tap-target, 44px); min-height: var(--tap-target, 44px); display: inline-flex; align-items: center; justify-content: center; border: none; cursor: pointer; color: var(--muted, #765a62); transition: .18s ease; }
.notif-trigger.header { width: var(--tap-target, 44px); height: var(--tap-target, 44px); border-radius: 13px; background: rgba(255, 243, 243, .8); }
.notif-trigger.header:hover, .notif-trigger.header.active { background: #ffe7e9; color: var(--rose-dark); }

/* Trigger — sidebar variant looks like a side nav item */
.notif-trigger.sidebar { width: 100%; gap: 12px; height: 47px; padding: 0 13px; border-radius: 13px; background: transparent; font-size: 14px; font-weight: 700; justify-content: flex-start; }
.notif-trigger.sidebar:hover, .notif-trigger.sidebar.active { background: #fff3f3; color: var(--rose-dark); }
.notif-label { flex: 1; text-align: left; }

.notif-badge { position: absolute; top: -3px; right: -3px; min-width: 18px; height: 18px; padding: 0 5px; display: inline-flex; align-items: center; justify-content: center; border-radius: 9px; background: var(--rose-action, #a63750); color: #fff; font-size: 10px; font-weight: 700; box-shadow: 0 0 0 2px var(--paper, #fffdfb); }
.notif-badge.inline { position: static; top: auto; right: auto; box-shadow: none; }

.notif-panel { position: absolute; z-index: 60; top: calc(100% + 9px); width: min(320px, 78vw); max-height: calc(100dvh - 82px); display: flex; flex-direction: column; border: 1px solid var(--line, #efdadd); border-radius: 18px; background: rgba(255, 253, 251, .98); backdrop-filter: blur(18px); box-shadow: 0 18px 44px rgba(76, 46, 55, .2); overflow: hidden; }
.notif-panel.header { right: 0; }
.notif-panel.sidebar { position: fixed; top: auto; right: auto; bottom: 20px; left: 256px; width: min(360px, calc(100vw - 276px)); max-height: calc(100dvh - 40px); }

.notif-head { display: flex; align-items: center; justify-content: space-between; padding: 13px 15px; border-bottom: 1px solid var(--line, #efdadd); }
.notif-head strong { font-size: 15px; }
.notif-readall { min-height: 44px; display: inline-flex; align-items: center; gap: 4px; border: none; background: none; cursor: pointer; color: var(--rose-dark, #b23f52); font-size: 12px; font-weight: 700; }
.notif-readall:hover { text-decoration: underline; }

.notif-scroll { min-height: 0; max-height: min(60vh, 380px); overflow-y: auto; overscroll-behavior: contain; scrollbar-gutter: stable; }
.notif-panel.sidebar .notif-scroll { flex: 1; max-height: none; }
.notif-empty { margin: 0; padding: 28px 16px; text-align: center; color: var(--muted, #765a62); font-size: 13px; }

.notif-list { list-style: none; margin: 0; padding: 6px; display: flex; flex-direction: column; gap: 2px; }
.notif-item { width: 100%; min-height: var(--tap-target, 44px); display: flex; gap: 10px; padding: 11px 11px; border: none; border-radius: 13px; background: none; cursor: pointer; text-align: left; transition: background .16s ease; }
.notif-item:hover { background: #fff3f3; }
.notif-item.unread { background: var(--rose-pale, #fff1f2); }
.notif-item.unread:hover { background: #ffe7e9; }
.notif-mark { flex-shrink: 0; width: 8px; height: 8px; margin-top: 6px; border-radius: 50%; background: transparent; }
.notif-item.unread .notif-mark { background: var(--rose, #e05568); }
.notif-main { display: flex; min-width: 0; flex-direction: column; gap: 3px; }
.notif-title { font-size: 13px; font-weight: 700; color: var(--ink, #593c43); }
.notif-text { font-size: 12px; line-height: 1.5; color: var(--muted, #765a62); }
.notif-time { font-size: 10px; color: var(--muted, #765a62); }
.notif-footer { padding: 8px; border-top: 1px solid var(--line, #efdadd); }
.notif-footer button { width: 100%; min-height: var(--tap-target, 44px); padding: 8px; border: 0; border-radius: 10px; background: var(--rose-pale, #fff1f2); color: var(--rose-dark, #b23f52); cursor: pointer; font-size: 11px; font-weight: 800; }
.notif-footer button:hover { background: #ffe4e7; }

.notif-trigger:focus-visible,
.notif-readall:focus-visible,
.notif-item:focus-visible,
.notif-footer button:focus-visible { outline: 3px solid var(--focus-ring, #8f3047); outline-offset: 2px; }

.notif-pop-enter-active, .notif-pop-leave-active { transition: opacity .16s ease, transform .16s ease; }
.notif-pop-enter-from, .notif-pop-leave-to { opacity: 0; transform: translateY(-6px); }
</style>
