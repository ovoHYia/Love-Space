<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
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

withDefaults(defineProps<{ variant?: 'sidebar' | 'header' }>(), { variant: 'header' })

const { show } = useToast()
const route = useRoute()
const router = useRouter()
const open = ref(false)
const rootEl = ref<HTMLElement | null>(null)

const items = computed(() => notificationState.items)
const unreadCount = computed(() => notificationState.unreadCount)
const loading = computed(() => notificationState.loading)
const badge = computed(() => (unreadCount.value > 99 ? '99+' : String(unreadCount.value)))

async function toggle() {
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
    router.push({ name: 'anniversaries' })
  } else if (item.referenceType === 'MESSAGE' && route.name !== 'letters') {
    router.push({ name: 'letters' })
  } else if (item.referenceType === 'WISH' && route.name !== 'wishes') {
    router.push({ name: 'wishes' })
  }
}

async function readAll() {
  try {
    await markAllNotificationsRead()
  } catch (cause) {
    show(errorMessage(cause), 'error')
  }
}

function onDocumentClick(event: MouseEvent) {
  if (!open.value) return
  if (rootEl.value && !rootEl.value.contains(event.target as Node)) open.value = false
}

watch(open, (value) => {
  if (value) document.addEventListener('click', onDocumentClick, true)
  else document.removeEventListener('click', onDocumentClick, true)
})
watch(() => route.fullPath, () => { open.value = false })
onBeforeUnmount(() => document.removeEventListener('click', onDocumentClick, true))
</script>

<template>
  <div class="notif" :class="variant" ref="rootEl">
    <button
      class="notif-trigger"
      :class="[variant, { active: open }]"
      type="button"
      :aria-label="unreadCount ? `通知，有 ${unreadCount} 条未读` : '通知'"
      :aria-expanded="open"
      @click="toggle"
    >
      <component :is="unreadCount ? BellRing : Bell" :size="variant === 'sidebar' ? 20 : 21" aria-hidden="true" />
      <span v-if="variant === 'sidebar'" class="notif-label">提醒</span>
      <span v-if="unreadCount" class="notif-badge" :class="{ inline: variant === 'sidebar' }">{{ badge }}</span>
    </button>

    <transition name="notif-pop">
      <div v-if="open" class="notif-panel" :class="variant" role="dialog" aria-label="通知列表">
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
      </div>
    </transition>
  </div>
</template>

<style scoped>
.notif { position: relative; }

/* Trigger — header (mobile top bar) variant is a round icon button */
.notif-trigger { position: relative; display: inline-flex; align-items: center; justify-content: center; border: none; cursor: pointer; color: #82666d; transition: .18s ease; }
.notif-trigger.header { width: 42px; height: 42px; border-radius: 13px; background: rgba(255, 243, 243, .8); }
.notif-trigger.header:hover, .notif-trigger.header.active { background: #ffe7e9; color: var(--rose-dark); }

/* Trigger — sidebar variant looks like a side nav item */
.notif-trigger.sidebar { width: 100%; gap: 12px; height: 47px; padding: 0 13px; border-radius: 13px; background: transparent; font-size: 14px; font-weight: 700; justify-content: flex-start; }
.notif-trigger.sidebar:hover, .notif-trigger.sidebar.active { background: #fff3f3; color: var(--rose-dark); }
.notif-label { flex: 1; text-align: left; }

.notif-badge { position: absolute; top: -3px; right: -3px; min-width: 18px; height: 18px; padding: 0 5px; display: inline-flex; align-items: center; justify-content: center; border-radius: 9px; background: var(--rose, #e05568); color: #fff; font-size: 10px; font-weight: 700; box-shadow: 0 0 0 2px var(--paper, #fffdfb); }
.notif-badge.inline { position: static; top: auto; right: auto; box-shadow: none; }

.notif-panel { position: absolute; z-index: 60; top: calc(100% + 9px); width: min(320px, 78vw); border: 1px solid var(--line, #efdadd); border-radius: 18px; background: rgba(255, 253, 251, .98); backdrop-filter: blur(18px); box-shadow: 0 18px 44px rgba(76, 46, 55, .2); overflow: hidden; }
.notif-panel.header { right: 0; }
.notif-panel.sidebar { left: 0; }

.notif-head { display: flex; align-items: center; justify-content: space-between; padding: 13px 15px; border-bottom: 1px solid var(--line, #efdadd); }
.notif-head strong { font-size: 15px; }
.notif-readall { display: inline-flex; align-items: center; gap: 4px; border: none; background: none; cursor: pointer; color: var(--rose-dark, #b23f52); font-size: 12px; font-weight: 700; }
.notif-readall:hover { text-decoration: underline; }

.notif-scroll { max-height: min(60vh, 380px); overflow-y: auto; }
.notif-empty { margin: 0; padding: 28px 16px; text-align: center; color: var(--muted, #a98d93); font-size: 13px; }

.notif-list { list-style: none; margin: 0; padding: 6px; display: flex; flex-direction: column; gap: 2px; }
.notif-item { width: 100%; display: flex; gap: 10px; padding: 11px 11px; border: none; border-radius: 13px; background: none; cursor: pointer; text-align: left; transition: background .16s ease; }
.notif-item:hover { background: #fff3f3; }
.notif-item.unread { background: var(--rose-pale, #fff1f2); }
.notif-item.unread:hover { background: #ffe7e9; }
.notif-mark { flex-shrink: 0; width: 8px; height: 8px; margin-top: 6px; border-radius: 50%; background: transparent; }
.notif-item.unread .notif-mark { background: var(--rose, #e05568); }
.notif-main { display: flex; min-width: 0; flex-direction: column; gap: 3px; }
.notif-title { font-size: 13px; font-weight: 700; color: #6d545a; }
.notif-text { font-size: 12px; line-height: 1.5; color: #8a6f75; }
.notif-time { font-size: 10px; color: var(--muted, #a98d93); }

.notif-pop-enter-active, .notif-pop-leave-active { transition: opacity .16s ease, transform .16s ease; }
.notif-pop-enter-from, .notif-pop-leave-to { opacity: 0; transform: translateY(-6px); }
</style>
