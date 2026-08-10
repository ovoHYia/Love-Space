<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch, type Component } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { BarChart3, CalendarDays, CalendarHeart, Feather, Gamepad2, Heart, Home, Images, ListTodo, Mails as MailHeart, MoreHorizontal, UserRound, Wifi, WifiOff } from 'lucide-vue-next'
import BaseAvatar from './BaseAvatar.vue'
import MobileMoreMenu from './MobileMoreMenu.vue'
import NotificationBell from './NotificationBell.vue'
import { useToast } from '../composables/toast'
import { authState, bootstrapAuth } from '../stores/auth'
import { refreshUnreadCount, startNotificationPolling, stopNotificationPolling } from '../stores/notifications'
import { realtimeState, startRealtimeSync, stopRealtimeSync } from '../stores/realtime'

type MobilePlacement = 'core' | 'more'
type MobileGroup = '记录' | '共同计划' | '我们'
type NavigationItem = {
  to: string
  label: string
  mobileLabel?: string
  icon: Component
  name: string
  mobilePlacement: MobilePlacement
  mobileGroup?: MobileGroup
  mobileOrder?: number
}

const route = useRoute()
const { show } = useToast()
const authRetryDelays = [1000, 3000]
let authRefreshPromise: Promise<void> | null = null
let mounted = false
const nav: NavigationItem[] = [
  { to: '/', label: '小窝', icon: Home, name: 'home', mobilePlacement: 'core' },
  { to: '/calendar', label: '日历', icon: CalendarDays, name: 'calendar', mobilePlacement: 'core' },
  { to: '/reports', label: '月报', icon: BarChart3, name: 'reports', mobilePlacement: 'more', mobileGroup: '共同计划', mobileOrder: 3 },
  { to: '/memories', label: '回忆', icon: Images, name: 'memories', mobilePlacement: 'core' },
  { to: '/games', label: '一起玩', icon: Gamepad2, name: 'games', mobilePlacement: 'core' },
  { to: '/diaries', label: '日记', icon: Feather, name: 'diaries', mobilePlacement: 'more', mobileGroup: '记录', mobileOrder: 1 },
  { to: '/letters', label: '信笺', icon: MailHeart, name: 'letters', mobilePlacement: 'more', mobileGroup: '记录', mobileOrder: 2 },
  { to: '/anniversaries', label: '日子', mobileLabel: '重要日子', icon: CalendarHeart, name: 'anniversaries', mobilePlacement: 'more', mobileGroup: '共同计划', mobileOrder: 1 },
  { to: '/wishes', label: '愿望', icon: ListTodo, name: 'wishes', mobilePlacement: 'more', mobileGroup: '共同计划', mobileOrder: 2 },
  { to: '/profile', label: '我们', mobileLabel: '个人资料', icon: UserRound, name: 'profile', mobilePlacement: 'more', mobileGroup: '我们', mobileOrder: 1 },
]
const mobileMoreGroupOrder: MobileGroup[] = ['记录', '共同计划', '我们']
const coreNav = computed(() => nav.filter(item => item.mobilePlacement === 'core'))
const moreGroups = computed(() => mobileMoreGroupOrder.map(label => ({
  label,
  items: nav
    .filter(item => item.mobilePlacement === 'more' && item.mobileGroup === label)
    .sort((a, b) => (a.mobileOrder || 0) - (b.mobileOrder || 0)),
})))
const moreOpen = ref(false)
const moreMenuId = 'mobile-more-menu'
const moreActive = computed(() => nav.some(item => item.mobilePlacement === 'more' && item.name === route.name))

function closeMoreMenu() {
  moreOpen.value = false
}

function toggleMoreMenu() {
  moreOpen.value = !moreOpen.value
}

function handleMoreKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' && event.key !== ' ' && event.key !== 'Spacebar') return
  event.preventDefault()
  toggleMoreMenu()
}

watch(() => route.fullPath, closeMoreMenu)

function delay(milliseconds: number) {
  return new Promise<void>((resolve) => window.setTimeout(resolve, milliseconds))
}

async function refreshAuthAfterSync() {
  if (authRefreshPromise) return authRefreshPromise

  const refreshTask = (async () => {
    for (let attempt = 0; attempt <= authRetryDelays.length; attempt++) {
      try {
        await bootstrapAuth(true)
        return
      } catch {
        if (attempt === authRetryDelays.length || !mounted) break
        await delay(authRetryDelays[attempt])
      }
    }
    if (mounted) show('账号信息同步失败，请稍后刷新页面重试。', 'error')
  })()

  authRefreshPromise = refreshTask
  try {
    await refreshTask
  } finally {
    if (authRefreshPromise === refreshTask) authRefreshPromise = null
  }
}

function handleSync(event: Event) {
  const detail = (event as CustomEvent<{ resource?: string }>).detail
  void refreshUnreadCount()
  if (detail?.resource === 'profile' || detail?.resource === 'space') {
    void refreshAuthAfterSync()
  }
}

onMounted(() => {
  mounted = true
  startNotificationPolling()
  startRealtimeSync()
  window.addEventListener('love-space:sync', handleSync)
})
onBeforeUnmount(() => {
  mounted = false
  stopNotificationPolling()
  stopRealtimeSync()
  window.removeEventListener('love-space:sync', handleSync)
})
</script>

<template>
  <div class="app-layout">
    <aside class="side-nav" aria-label="主导航">
      <RouterLink class="brand" to="/" aria-label="Love Space 首页">
        <span class="brand-mark"><Heart :size="21" fill="currentColor" /></span>
        <span><strong>Love Space</strong><small>我们的小时光</small></span>
      </RouterLink>
      <nav class="side-links">
        <RouterLink v-for="item in nav" :key="item.name" :to="item.to" :class="{ active: route.name === item.name }" :aria-current="route.name === item.name ? 'page' : undefined">
          <component :is="item.icon" :size="20" aria-hidden="true" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>
      <NotificationBell variant="sidebar" class="side-notif" />
      <div class="sync-status" :class="{ online: realtimeState.connected }" :title="realtimeState.connected ? '双端实时同步已连接' : '正在重新连接实时同步'">
        <Wifi v-if="realtimeState.connected" :size="14" /><WifiOff v-else :size="14" />
        <span>{{ realtimeState.connected ? '双端同步中' : '同步重连中' }}</span>
      </div>
      <RouterLink class="side-profile" to="/profile">
        <BaseAvatar :user="authState.user" size="sm" />
        <span><strong>{{ authState.user?.nickname }}</strong><small>和 {{ authState.partner?.nickname || '心上人' }} 的空间</small></span>
      </RouterLink>
    </aside>

    <div class="app-column">
      <header class="mobile-header">
        <RouterLink class="mobile-brand" to="/">
          <span class="brand-mark"><Heart :size="17" fill="currentColor" /></span>
          <span><strong>{{ authState.spaceName }}</strong><small>Love Space</small></span>
        </RouterLink>
        <div class="mobile-actions">
          <NotificationBell variant="header" />
          <RouterLink to="/profile" aria-label="打开个人资料"><BaseAvatar :user="authState.user" size="sm" /></RouterLink>
        </div>
      </header>
      <main class="page-container">
        <RouterView />
      </main>
      <nav class="bottom-nav" aria-label="移动主导航">
        <RouterLink v-for="item in coreNav" :key="item.name" class="bottom-nav-item" :to="item.to" :class="{ active: route.name === item.name }" :aria-label="item.mobileLabel || item.label" :aria-current="route.name === item.name ? 'page' : undefined">
          <component :is="item.icon" :size="20" aria-hidden="true" />
          <span>{{ item.mobileLabel || item.label }}</span>
        </RouterLink>
        <button
          class="bottom-nav-item bottom-nav-more"
          :class="{ active: moreActive }"
          type="button"
          aria-label="更多入口"
          aria-haspopup="dialog"
          :aria-expanded="moreOpen"
          :aria-controls="moreMenuId"
          @click="toggleMoreMenu"
          @keydown="handleMoreKeydown"
        >
          <MoreHorizontal :size="20" aria-hidden="true" />
          <span>更多</span>
        </button>
      </nav>
      <MobileMoreMenu :open="moreOpen" :groups="moreGroups" :menu-id="moreMenuId" @close="closeMoreMenu" />
    </div>
  </div>
</template>

<style scoped>
.side-notif { margin-top: 6px; }
.mobile-actions { display: flex; align-items: center; gap: 10px; }
.sync-status { display: flex; align-items: center; gap: 6px; margin: 8px 10px 0; color: #9b7e84; font-size: 10px; }
.sync-status.online { color: #6f936c; }
</style>
