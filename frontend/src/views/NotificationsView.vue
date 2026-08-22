<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  BellRing, CalendarHeart, Check, CheckCheck, ChevronLeft, ChevronRight,
  Circle, ListTodo, Mail, Search, Settings2, SlidersHorizontal, Trash2,
} from 'lucide-vue-next'
import { api } from '../api'
import { errorMessage } from '../api/client'
import ConflictPanel from '../components/ConflictPanel.vue'
import EmptyState from '../components/EmptyState.vue'
import LoadingState from '../components/LoadingState.vue'
import { useToast } from '../composables/toast'
import { useResourceSync } from '../composables/resourceSync'
import { refreshUnreadCount } from '../stores/notifications'
import type { AppNotification, NotificationList, NotificationPreferences } from '../types'
import { formatDateTime, sameId } from '../utils'
import { isStaleUpdate, STALE_UPDATE_MESSAGE } from '../utils/editConflict'
import { createRequestGeneration } from '../utils/latestRequest'

type StatusFilter = 'ALL' | 'UNREAD' | 'READ'
type CategoryFilter = 'ALL' | 'ANNIVERSARY' | 'MESSAGE' | 'WISH'

const router = useRouter()
const { show } = useToast()
const data = ref<NotificationList | null>(null)
const loading = ref(true)
const error = ref('')
const status = ref<StatusFilter>('ALL')
const category = ref<CategoryFilter>('ALL')
const keyword = ref('')
const searchText = ref('')
const page = ref(0)
const selected = ref<Array<number | string>>([])
const acting = ref(false)
const preferences = reactive<NotificationPreferences>({
  anniversaryEnabled: true,
  letterEnabled: true,
  wishEnabled: true,
})
const preferencesLoading = ref(true)
const preferencesSaving = ref(false)
const preferencesConflict = ref(false)
const preferencesBaseline = ref<NotificationPreferences>({ ...preferences })
const notificationRequests = createRequestGeneration()
const preferencesRequests = createRequestGeneration()

const statusFilters: { value: StatusFilter; label: string }[] = [
  { value: 'ALL', label: '全部' },
  { value: 'UNREAD', label: '未读' },
  { value: 'READ', label: '已读' },
]
const categoryFilters: { value: CategoryFilter; label: string }[] = [
  { value: 'ALL', label: '全部来源' },
  { value: 'ANNIVERSARY', label: '纪念日' },
  { value: 'MESSAGE', label: '时光信' },
  { value: 'WISH', label: '共同愿望' },
]
const summaryCards = computed(() => {
  const summary = data.value?.summary
  return [
    { label: '全部通知', value: summary?.total || 0, icon: BellRing, tone: 'rose' },
    { label: '未读消息', value: summary?.unread || 0, icon: Circle, tone: 'gold' },
    { label: '纪念日', value: summary?.anniversaries || 0, icon: CalendarHeart, tone: 'sage' },
    { label: '互动提醒', value: (summary?.letters || 0) + (summary?.wishes || 0), icon: CheckCheck, tone: 'blue' },
  ]
})
const allSelected = computed(() => Boolean(data.value?.items.length)
  && data.value?.items.every(item => selected.value.some(id => sameId(id, item.id))))

onMounted(async () => {
  await Promise.all([load(), loadPreferences()])
})
useResourceSync(['notifications'], async () => {
  const [result, preferencesResult] = await Promise.all([load(), loadPreferences(), refreshUnreadCount()])
  return result !== false && preferencesResult !== false
})
watch([status, category], async () => {
  page.value = 0
  selected.value = []
  await load()
})

async function load() {
  const request = notificationRequests.begin()
  const query = {
    page: page.value,
    size: 12,
    status: status.value,
    category: category.value,
    keyword: keyword.value,
  }
  loading.value = true
  error.value = ''
  try {
    const nextData = await api.notifications(query)
    if (!request.isLatest()) return
    data.value = nextData
    selected.value = selected.value.filter(id => data.value?.items.some(item => sameId(item.id, id)))
    return true
  } catch (cause) {
    if (!request.isLatest()) return
    error.value = errorMessage(cause)
    return false
  } finally {
    if (request.isLatest()) loading.value = false
  }
}

function preferencesDirty() {
  return preferences.anniversaryEnabled !== preferencesBaseline.value.anniversaryEnabled
    || preferences.letterEnabled !== preferencesBaseline.value.letterEnabled
    || preferences.wishEnabled !== preferencesBaseline.value.wishEnabled
}

async function loadPreferences(forceEditorValues = false) {
  const request = preferencesRequests.begin()
  preferencesLoading.value = true
  try {
    const nextPreferences = await api.notificationPreferences()
    if (request.isLatest()) {
      if (forceEditorValues || !preferencesDirty()) {
        Object.assign(preferences, nextPreferences)
        preferencesBaseline.value = { ...nextPreferences }
        preferencesConflict.value = false
      } else preferencesConflict.value = true
      return true
    }
  } catch (cause) {
    if (request.isLatest()) {
      show(errorMessage(cause), 'error')
      return false
    }
  } finally {
    if (request.isLatest()) preferencesLoading.value = false
  }
}

async function savePreferences() {
  preferencesSaving.value = true
  try {
    const updated = await api.updateNotificationPreferences({ ...preferences })
    preferencesRequests.cancel()
    preferencesLoading.value = false
    Object.assign(preferences, updated)
    preferencesBaseline.value = { ...updated }
    preferencesConflict.value = false
    show('提醒偏好已经保存。', 'success')
  } catch (cause) {
    if (isStaleUpdate(cause)) {
      preferencesConflict.value = true
      show(STALE_UPDATE_MESSAGE, 'error')
    } else show(errorMessage(cause), 'error')
  } finally {
    preferencesSaving.value = false
  }
}

async function loadLatestPreferences() {
  const result = await loadPreferences(true)
  if (result !== false) show('已加载最新提醒偏好，请确认后再保存。', 'info')
}

async function search() {
  keyword.value = searchText.value.trim()
  page.value = 0
  selected.value = []
  await load()
}

async function changePage(next: number) {
  if (!data.value || next < 0 || next >= data.value.totalPages) return
  page.value = next
  selected.value = []
  await load()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function toggleAll() {
  selected.value = allSelected.value ? [] : (data.value?.items.map(item => item.id) || [])
}

function toggleOne(id: AppNotification['id']) {
  selected.value = selected.value.some(value => sameId(value, id))
    ? selected.value.filter(value => !sameId(value, id))
    : [...selected.value, id]
}

async function openItem(item: AppNotification) {
  acting.value = true
  try {
    if (!item.readAt) await api.readNotification(item.id)
    notificationRequests.cancel()
    await load()
    await refreshUnreadCount()
    await navigate(item)
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    acting.value = false
  }
}

async function navigate(item: AppNotification) {
  const name = {
    ANNIVERSARY: 'anniversaries',
    MESSAGE: 'letters',
    WISH: 'wishes',
  }[item.referenceType || '']
  if (!name) return
  await router.push({ name })
  if (name === 'letters') {
    window.dispatchEvent(new CustomEvent('love-space:sync', {
      detail: { action: 'NOTIFICATION_CLICK', resource: 'messages', actorId: 0, occurredAt: new Date().toISOString() },
    }))
  }
}

async function toggleRead(item: AppNotification) {
  acting.value = true
  try {
    if (item.readAt) await api.unreadNotification(item.id)
    else await api.readNotification(item.id)
    await afterAction(item.readAt ? '已标记为未读。' : '已标记为已读。')
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    acting.value = false
  }
}

async function removeOne(item: AppNotification) {
  if (!window.confirm(`确定删除通知“${item.title}”吗？`)) return
  acting.value = true
  try {
    await api.deleteNotification(item.id)
    await afterAction('通知已删除。')
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    acting.value = false
  }
}

async function batchRead(read: boolean) {
  if (!selected.value.length) return
  acting.value = true
  try {
    if (read) await api.readNotifications(selected.value)
    else await api.unreadNotifications(selected.value)
    await afterAction(read ? '所选通知已标记为已读。' : '所选通知已标记为未读。')
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    acting.value = false
  }
}

async function deleteSelected() {
  if (!selected.value.length || !window.confirm(`确定删除选中的 ${selected.value.length} 条通知吗？`)) return
  acting.value = true
  try {
    await api.deleteNotifications(selected.value)
    await afterAction('所选通知已删除。')
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    acting.value = false
  }
}

async function readAll() {
  acting.value = true
  try {
    await api.readAllNotifications()
    await afterAction('全部通知已标记为已读。')
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    acting.value = false
  }
}

async function clearRead() {
  const count = data.value?.summary.read || 0
  if (!count || !window.confirm(`确定清理全部 ${count} 条已读通知吗？此操作无法撤销。`)) return
  acting.value = true
  try {
    await api.deleteReadNotifications()
    await afterAction('已读通知已清理。')
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    acting.value = false
  }
}

async function afterAction(message: string) {
  notificationRequests.cancel()
  selected.value = []
  await Promise.all([load(), refreshUnreadCount()])
  show(message, 'success')
}

function typeMeta(item: AppNotification) {
  if (item.referenceType === 'ANNIVERSARY') return { label: '纪念日', icon: CalendarHeart, tone: 'anniversary' }
  if (item.referenceType === 'MESSAGE') return { label: '时光信', icon: Mail, tone: 'letter' }
  if (item.referenceType === 'WISH') return { label: '共同愿望', icon: ListTodo, tone: 'wish' }
  return { label: '系统提醒', icon: BellRing, tone: 'system' }
}
</script>

<template>
  <div class="page-stack notification-center-page">
    <header class="page-header">
      <div><p class="eyebrow">NOTIFICATION CENTER 2.0</p><h1>通知中心</h1><p>重要的日子、抵达的信和彼此的心愿，都在这里妥帖收好。</p></div>
      <div class="page-header-actions">
        <button class="button ghost" type="button" :disabled="acting || !data?.summary.read" @click="clearRead"><Trash2 :size="16" />清理已读</button>
        <button class="button secondary" type="button" :disabled="acting || !data?.summary.unread" @click="readAll"><CheckCheck :size="17" />全部已读</button>
      </div>
    </header>

    <section class="notification-summary">
      <article v-for="card in summaryCards" :key="card.label" :class="['card', `tone-${card.tone}`]">
        <span><component :is="card.icon" :size="20" /></span>
        <div><strong>{{ card.value }}</strong><small>{{ card.label }}</small></div>
      </article>
    </section>

    <section class="notification-layout">
      <div class="notification-main">
        <section class="notification-toolbar card">
          <div class="status-tabs" role="group" aria-label="通知状态筛选">
            <button v-for="item in statusFilters" :key="item.value" type="button" :aria-pressed="status === item.value" :class="{ active: status === item.value }" @click="status = item.value">{{ item.label }}</button>
          </div>
          <form class="notification-search" @submit.prevent="search">
            <Search :size="16" />
            <input v-model="searchText" maxlength="100" aria-label="搜索通知" placeholder="搜索标题或内容" />
            <button type="submit">搜索</button>
          </form>
          <label class="category-filter"><SlidersHorizontal :size="15" /><select v-model="category"><option v-for="item in categoryFilters" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
        </section>

        <div v-if="selected.length" class="batch-bar">
          <strong>已选择 {{ selected.length }} 条</strong>
          <button type="button" :disabled="acting" @click="batchRead(true)"><Check :size="14" />标为已读</button>
          <button type="button" :disabled="acting" @click="batchRead(false)"><Circle :size="13" />标为未读</button>
          <button class="danger" type="button" :disabled="acting" @click="deleteSelected"><Trash2 :size="14" />删除</button>
        </div>

        <LoadingState v-if="loading" label="正在整理通知…" />
        <div v-else-if="error" class="error-panel" role="alert"><p>{{ error }}</p><button class="button secondary" type="button" @click="load">重新加载</button></div>
        <EmptyState v-else-if="!data?.items.length" title="这里已经很清静啦" description="换一个筛选条件看看，新的提醒也会在到来时出现在这里。" />
        <template v-else>
          <div class="select-page-row">
            <label><input type="checkbox" :checked="allSelected" @change="toggleAll" />选择本页</label>
            <span>共 {{ data.totalElements }} 条结果</span>
          </div>
          <div class="notification-list">
            <article v-for="item in data.items" :key="item.id" :class="['notification-card', { unread: !item.readAt }]">
              <label class="notification-check" @click.stop><input type="checkbox" :checked="selected.some(id => sameId(id, item.id))" :aria-label="`选择通知 ${item.title}`" @change="toggleOne(item.id)" /></label>
              <button class="notification-open" type="button" :disabled="acting" @click="openItem(item)">
                <span :class="['notification-type-icon', typeMeta(item).tone]"><component :is="typeMeta(item).icon" :size="20" /></span>
                <span class="notification-copy">
                  <span class="notification-card-head"><small>{{ typeMeta(item).label }}</small><i v-if="!item.readAt">未读</i></span>
                  <strong>{{ item.title }}</strong>
                  <span>{{ item.body }}</span>
                  <time>{{ formatDateTime(item.createdAt) }}</time>
                </span>
              </button>
              <div class="notification-actions">
                <button type="button" :title="item.readAt ? '标为未读' : '标为已读'" :aria-label="item.readAt ? '标为未读' : '标为已读'" :disabled="acting" @click="toggleRead(item)">
                  <Circle v-if="item.readAt" :size="16" /><Check v-else :size="16" />
                </button>
                <button class="danger" type="button" title="删除" aria-label="删除通知" :disabled="acting" @click="removeOne(item)"><Trash2 :size="16" /></button>
              </div>
            </article>
          </div>
          <nav v-if="data.totalPages > 1" class="notification-pagination" aria-label="通知分页">
            <button class="icon-button" type="button" :disabled="data.first" aria-label="上一页" @click="changePage(page - 1)"><ChevronLeft :size="18" /></button>
            <span>第 {{ page + 1 }} / {{ data.totalPages }} 页</span>
            <button class="icon-button" type="button" :disabled="data.last" aria-label="下一页" @click="changePage(page + 1)"><ChevronRight :size="18" /></button>
          </nav>
        </template>
      </div>

      <aside class="notification-preferences card">
        <div class="preferences-heading"><span><Settings2 :size="19" /></span><div><p class="eyebrow">PREFERENCES</p><h2>提醒偏好</h2></div></div>
        <p class="preferences-intro">关闭某类提醒后，只影响之后新产生的通知，不会删除已有记录。</p>
        <LoadingState v-if="preferencesLoading" label="正在读取偏好…" />
        <form v-else @submit.prevent="savePreferences">
          <ConflictPanel v-if="preferencesConflict" @reload="loadLatestPreferences" />
          <label class="preference-option">
            <span class="preference-icon anniversary"><CalendarHeart :size="18" /></span>
            <span><strong>纪念日提醒</strong><small>在设定的提前天数内提醒</small></span>
            <input v-model="preferences.anniversaryEnabled" type="checkbox" />
          </label>
          <label class="preference-option">
            <span class="preference-icon letter"><Mail :size="18" /></span>
            <span><strong>时光信抵达</strong><small>定时信笺送达时提醒</small></span>
            <input v-model="preferences.letterEnabled" type="checkbox" />
          </label>
          <label class="preference-option">
            <span class="preference-icon wish"><ListTodo :size="18" /></span>
            <span><strong>共同愿望动态</strong><small>新增或完成愿望时提醒</small></span>
            <input v-model="preferences.wishEnabled" type="checkbox" />
          </label>
          <button class="button primary full" type="submit" :disabled="preferencesSaving"><span v-if="preferencesSaving" class="button-spinner"></span><Check v-else :size="16" />{{ preferencesSaving ? '正在保存…' : '保存提醒偏好' }}</button>
        </form>
      </aside>
    </section>
  </div>
</template>

<style scoped>
.page-header-actions { display: flex; gap: 9px; }
.notification-summary { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.notification-summary article { display: flex; align-items: center; gap: 12px; padding: 16px; }
.notification-summary article > span { width: 42px; height: 42px; display: grid; place-items: center; border-radius: 13px; }
.notification-summary article strong, .notification-summary article small { display: block; }
.notification-summary article strong { font: 700 23px Georgia, serif; }
.notification-summary article small { margin-top: 2px; color: var(--muted); font-size: 9px; }
.tone-rose > span { background: var(--rose-pale); color: var(--rose); }.tone-gold > span { background: #fff5df; color: #bd8742; }
.tone-sage > span { background: var(--sage-pale); color: var(--sage); }.tone-blue > span { background: #eaf4f5; color: #5e8991; }
.notification-layout { display: grid; grid-template-columns: minmax(0, 1fr) 300px; align-items: start; gap: 18px; }
.notification-main { min-width: 0; display: grid; gap: 12px; }
.notification-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; padding: 12px; }
.status-tabs { display: flex; padding: 3px; border-radius: 11px; background: #f5edeb; }
.status-tabs button { min-height: 34px; padding: 6px 13px; border: 0; border-radius: 9px; background: transparent; color: var(--muted); cursor: pointer; font-size: 10px; font-weight: 800; }
.status-tabs button.active { background: white; color: var(--rose-dark); box-shadow: 0 3px 9px rgba(92,54,64,.08); }
.notification-search { flex: 1; min-width: 210px; display: flex; align-items: center; gap: 7px; padding-left: 11px; border: 1px solid var(--line); border-radius: 11px; background: white; color: var(--muted); }
.notification-search input { min-width: 0; flex: 1; height: 38px; border: 0; outline: 0; background: transparent; color: var(--ink); font-size: 11px; }
.notification-search button { align-self: stretch; padding: 0 12px; border: 0; border-left: 1px solid var(--line); background: transparent; color: var(--rose-dark); cursor: pointer; font-size: 10px; font-weight: 800; }
.category-filter { display: flex; align-items: center; gap: 5px; padding: 0 8px; border: 1px solid var(--line); border-radius: 11px; background: white; color: var(--muted); }
.category-filter select { height: 38px; border: 0; outline: 0; background: transparent; color: var(--ink); font-size: 10px; font-weight: 700; }
.batch-bar { display: flex; align-items: center; gap: 8px; padding: 10px 13px; border: 1px solid #efc9cf; border-radius: 13px; background: var(--rose-pale); }
.batch-bar strong { margin-right: auto; font-size: 11px; }
.batch-bar button { display: inline-flex; align-items: center; gap: 4px; padding: 6px 8px; border: 0; border-radius: 8px; background: white; color: var(--muted); cursor: pointer; font-size: 9px; font-weight: 800; }
.batch-bar button.danger { color: #b54052; }
.select-page-row { display: flex; align-items: center; justify-content: space-between; padding: 1px 4px; color: var(--muted); font-size: 9px; }
.select-page-row label { display: flex; align-items: center; gap: 6px; cursor: pointer; }
.select-page-row input, .notification-check input { accent-color: var(--rose); }
.notification-list { display: grid; gap: 8px; }
.notification-card { display: grid; grid-template-columns: auto 1fr auto; align-items: center; border: 1px solid var(--line); border-radius: 16px; background: rgba(255,253,251,.92); box-shadow: var(--shadow-sm); }
.notification-card.unread { border-color: #edc1c8; background: linear-gradient(90deg, #fff2f3, #fffdfb 38%); }
.notification-check { align-self: stretch; display: grid; place-items: center; padding: 0 4px 0 14px; cursor: pointer; }
.notification-open { min-width: 0; display: grid; grid-template-columns: auto 1fr; align-items: center; gap: 12px; padding: 14px 10px; border: 0; background: transparent; text-align: left; cursor: pointer; }
.notification-type-icon { width: 43px; height: 43px; display: grid; place-items: center; border-radius: 14px; }
.notification-type-icon.anniversary { background: #fff0e3; color: #bd7a39; }.notification-type-icon.letter { background: #f5eaf1; color: #9a6581; }
.notification-type-icon.wish { background: #ece9f7; color: #8067a5; }.notification-type-icon.system { background: var(--rose-pale); color: var(--rose); }
.notification-copy { min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.notification-card-head { display: flex; align-items: center; gap: 7px; }
.notification-card-head small { color: var(--muted); font-size: 8px; font-weight: 800; }
.notification-card-head i { padding: 2px 5px; border-radius: 999px; background: var(--rose); color: white; font-size: 7px; font-style: normal; font-weight: 800; }
.notification-copy > strong { overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.notification-copy > span:not(.notification-card-head) { overflow: hidden; color: var(--muted); font-size: 10px; line-height: 1.5; text-overflow: ellipsis; white-space: nowrap; }
.notification-copy time { margin-top: 2px; color: #aa9297; font-size: 8px; }
.notification-actions { display: flex; gap: 4px; padding-right: 12px; }
.notification-actions button { width: 32px; height: 32px; display: grid; place-items: center; border: 1px solid var(--line); border-radius: 9px; background: white; color: var(--muted); cursor: pointer; }
.notification-actions button:hover { color: var(--rose-dark); border-color: #efb9c1; }.notification-actions button.danger:hover { color: #b33f50; background: #fff0f2; }
.notification-pagination { display: flex; align-items: center; justify-content: center; gap: 13px; padding-top: 5px; color: var(--muted); font-size: 10px; }
.notification-pagination button:disabled { opacity: .35; cursor: not-allowed; }
.notification-preferences { position: sticky; top: 20px; padding: 20px; }
.preferences-heading { display: flex; align-items: center; gap: 10px; }
.preferences-heading > span { width: 39px; height: 39px; display: grid; place-items: center; border-radius: 12px; background: var(--rose-pale); color: var(--rose); }
.preferences-heading .eyebrow { margin-bottom: 2px; font-size: 8px; }
.preferences-heading h2 { margin: 0; font-size: 21px; }
.preferences-intro { margin: 14px 0 16px; color: var(--muted); font-size: 9px; line-height: 1.7; }
.notification-preferences .loading-state { min-height: 180px; }
.notification-preferences form { display: grid; gap: 10px; }
.preference-option { display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 9px; padding: 11px; border: 1px solid var(--line); border-radius: 13px; background: #fffaf8; cursor: pointer; }
.preference-icon { width: 34px; height: 34px; display: grid; place-items: center; border-radius: 10px; }
.preference-icon.anniversary { background: #fff0e3; color: #bd7a39; }.preference-icon.letter { background: #f5eaf1; color: #9a6581; }.preference-icon.wish { background: #ece9f7; color: #8067a5; }
.preference-option > span:nth-child(2) { display: flex; flex-direction: column; gap: 2px; }
.preference-option strong { font-size: 10px; }.preference-option small { color: var(--muted); font-size: 8px; }
.preference-option input { width: 18px; height: 18px; accent-color: var(--rose); }
.notification-preferences .button { margin-top: 5px; }
@media (max-width: 1050px) {
  .notification-summary { grid-template-columns: repeat(2, 1fr); }
  .notification-layout { grid-template-columns: 1fr; }
  .notification-preferences { position: static; }
}
@media (max-width: 620px) {
  .page-header-actions { align-items: stretch; flex-direction: column; }
  .page-header-actions .button { width: 100%; }
  .notification-summary { gap: 8px; }
  .notification-summary article { padding: 12px; }
  .notification-toolbar { align-items: stretch; }
  .status-tabs { width: 100%; }.status-tabs button { flex: 1; }
  .notification-search { order: 3; width: 100%; }
  .category-filter { flex: 1; }.category-filter select { width: 100%; }
  .batch-bar { align-items: stretch; flex-wrap: wrap; }.batch-bar strong { width: 100%; }
  .notification-card { grid-template-columns: auto 1fr; }
  .notification-open { grid-template-columns: 1fr; padding-left: 8px; }
  .notification-type-icon { width: 35px; height: 35px; position: absolute; opacity: .15; }
  .notification-actions { grid-column: 1 / -1; justify-content: flex-end; padding: 0 10px 10px; }
}
</style>
