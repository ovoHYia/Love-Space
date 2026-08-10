<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { CalendarDays, ChevronLeft, ChevronRight, Clock3, MapPin, Pencil, Plus, Trash2 } from 'lucide-vue-next'
import { api } from '../api'
import { errorMessage } from '../api/client'
import BaseModal from '../components/BaseModal.vue'
import EmptyState from '../components/EmptyState.vue'
import LoadingState from '../components/LoadingState.vue'
import { useToast } from '../composables/toast'
import { useResourceSync } from '../composables/resourceSync'
import type { CalendarEntry, CalendarEventInput, CalendarSource } from '../types'
import { formatDate, formatDateTime, toBeijingOffsetDateTime, toLocalDateTimeInput, todayInput } from '../utils'
import { createRequestGeneration } from '../utils/latestRequest'

const router = useRouter()
const { show } = useToast()
const today = todayInput()
const [todayYear, todayMonth] = today.split('-').map(Number)
const month = ref(new Date(todayYear, todayMonth - 1, 1))
const selectedDate = ref(today)
const entries = ref<CalendarEntry[]>([])
const loading = ref(true)
const error = ref('')
const modalOpen = ref(false)
const editing = ref<CalendarEntry | null>(null)
const saving = ref(false)
const deleting = ref(false)
const calendarRequests = createRequestGeneration()
const allSources: CalendarSource[] = ['CUSTOM', 'ANNIVERSARY', 'WISH', 'MEMORY', 'DIARY', 'LETTER']
const activeSources = ref<CalendarSource[]>([...allSources])
const weekdays = ['一', '二', '三', '四', '五', '六', '日']

const sourceMeta: Record<CalendarSource, { label: string; route?: string }> = {
  CUSTOM: { label: '共享日程' },
  ANNIVERSARY: { label: '纪念日', route: 'anniversaries' },
  WISH: { label: '愿望', route: 'wishes' },
  MEMORY: { label: '回忆', route: 'memories' },
  DIARY: { label: '日记', route: 'diaries' },
  LETTER: { label: '定时信笺', route: 'letters' },
}
const categories: { value: CalendarEventInput['category']; label: string }[] = [
  { value: 'DATE', label: '约会' },
  { value: 'TRAVEL', label: '出行' },
  { value: 'FAMILY', label: '家庭' },
  { value: 'PERSONAL', label: '个人' },
  { value: 'OTHER', label: '其他' },
]
const form = reactive({
  title: '',
  description: '',
  date: today,
  time: '18:00',
  endDate: '',
  endTime: '19:00',
  allDay: false,
  category: 'DATE' as CalendarEventInput['category'],
  location: '',
})

const gridDays = computed(() => {
  const first = new Date(month.value.getFullYear(), month.value.getMonth(), 1)
  const mondayOffset = (first.getDay() + 6) % 7
  const start = new Date(first.getFullYear(), first.getMonth(), 1 - mondayOffset)
  return Array.from({ length: 42 }, (_, index) => {
    const value = new Date(start.getFullYear(), start.getMonth(), start.getDate() + index)
    return {
      value,
      key: dateKey(value),
      day: value.getDate(),
      current: value.getMonth() === month.value.getMonth(),
      today: dateKey(value) === today,
    }
  })
})
const monthLabel = computed(() => new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long' }).format(month.value))
const visibleEntries = computed(() => entries.value.filter(item => activeSources.value.includes(item.sourceType)))
const entriesByDate = computed(() => {
  const result = new Map<string, CalendarEntry[]>()
  for (const item of visibleEntries.value) {
    const start = parseDate(toLocalDateTimeInput(item.startAt).slice(0, 10))
    const end = parseDate(toLocalDateTimeInput(item.endAt || item.startAt).slice(0, 10))
    for (let cursor = start; cursor <= end; cursor = new Date(cursor.getFullYear(), cursor.getMonth(), cursor.getDate() + 1)) {
      const key = dateKey(cursor)
      const values = result.get(key) || []
      values.push(item)
      result.set(key, values)
    }
  }
  return result
})
const selectedEntries = computed(() => entriesByDate.value.get(selectedDate.value) || [])

onMounted(load)
useResourceSync(['calendar', 'anniversaries', 'wishes', 'memories', 'diaries', 'messages'], load)

function dateKey(value: Date) {
  const year = value.getFullYear()
  const monthValue = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${monthValue}-${day}`
}

function parseDate(value: string) {
  const [year, monthValue, day] = value.split('-').map(Number)
  return new Date(year, monthValue - 1, day)
}

async function load() {
  const request = calendarRequests.begin()
  const grid = gridDays.value
  loading.value = true
  error.value = ''
  try {
    const nextEntries = await api.calendar(grid[0].key, grid[41].key)
    if (!request.isLatest()) return
    entries.value = nextEntries
  } catch (cause) {
    if (!request.isLatest()) return
    error.value = errorMessage(cause)
  } finally {
    if (request.isLatest()) loading.value = false
  }
}

async function changeMonth(offset: number) {
  month.value = new Date(month.value.getFullYear(), month.value.getMonth() + offset, 1)
  selectedDate.value = dateKey(month.value)
  await load()
}

async function goToday() {
  month.value = new Date(todayYear, todayMonth - 1, 1)
  selectedDate.value = today
  await load()
}

function toggleSource(source: CalendarSource) {
  activeSources.value = activeSources.value.includes(source)
    ? activeSources.value.filter(value => value !== source)
    : [...activeSources.value, source]
}

function selectDay(day: { key: string; current: boolean; value: Date }) {
  selectedDate.value = day.key
  if (!day.current) {
    month.value = new Date(day.value.getFullYear(), day.value.getMonth(), 1)
    load()
  }
}

function resetForm(date = selectedDate.value) {
  editing.value = null
  Object.assign(form, {
    title: '',
    description: '',
    date,
    time: '18:00',
    endDate: '',
    endTime: '19:00',
    allDay: false,
    category: 'DATE',
    location: '',
  })
}

function openCreate(date = selectedDate.value) {
  resetForm(date)
  modalOpen.value = true
}

function openEdit(item: CalendarEntry) {
  if (!item.editable) return
  const start = toLocalDateTimeInput(item.startAt)
  const end = item.endAt ? toLocalDateTimeInput(item.endAt) : ''
  editing.value = item
  Object.assign(form, {
    title: item.title,
    description: item.description || '',
    date: start.slice(0, 10),
    time: start.slice(11, 16) || '18:00',
    endDate: end.slice(0, 10),
    endTime: end.slice(11, 16) || '19:00',
    allDay: item.allDay,
    category: categories.some(value => value.value === item.category) ? item.category : 'OTHER',
    location: item.location || '',
  })
  modalOpen.value = true
}

function input(): CalendarEventInput {
  const startAt = `${form.date}T${form.allDay ? '00:00' : form.time}:00`
  const endAt = form.endDate
    ? `${form.endDate}T${form.allDay ? '23:59' : form.endTime}:00`
    : null
  return {
    title: form.title.trim(),
    description: form.description.trim(),
    startAt: toBeijingOffsetDateTime(startAt),
    endAt: endAt ? toBeijingOffsetDateTime(endAt) : null,
    allDay: form.allDay,
    category: form.category,
    location: form.location.trim(),
  }
}

async function save() {
  saving.value = true
  try {
    if (editing.value) {
      await api.updateCalendarEvent(editing.value.id, input())
      show('共享日程已经更新。', 'success')
    } else {
      await api.createCalendarEvent(input())
      show('新的共享日程已放进日历。', 'success')
    }
    modalOpen.value = false
    await load()
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    saving.value = false
  }
}

async function remove(item: CalendarEntry) {
  if (!window.confirm(`确定将日程“${item.title}”移入回收站吗？`)) return
  deleting.value = true
  try {
    await api.deleteCalendarEvent(item.id)
    modalOpen.value = false
    show('共享日程已移入回收站。', 'success')
    await load()
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    deleting.value = false
  }
}

function openSource(item: CalendarEntry) {
  if (item.editable) {
    openEdit(item)
    return
  }
  const route = sourceMeta[item.sourceType].route
  if (route) router.push({ name: route })
}
</script>

<template>
  <div class="page-stack calendar-page">
    <header class="page-header">
      <div><p class="eyebrow">OUR SHARED CALENDAR</p><h1>情侣日历</h1><p>把约定、纪念和已经发生的温柔，都放在同一张日历里。</p></div>
      <button class="button primary" type="button" @click="openCreate()"><Plus :size="18" />添加共享日程</button>
    </header>

    <section class="calendar-toolbar card">
      <div class="month-switcher">
        <button class="icon-button" type="button" aria-label="上个月" @click="changeMonth(-1)"><ChevronLeft :size="19" /></button>
        <div><p class="eyebrow">MONTH VIEW</p><h2>{{ monthLabel }}</h2></div>
        <button class="icon-button" type="button" aria-label="下个月" @click="changeMonth(1)"><ChevronRight :size="19" /></button>
      </div>
      <button class="button ghost small" type="button" @click="goToday">回到今天</button>
      <div class="calendar-filters" aria-label="日历来源筛选">
        <button v-for="source in allSources" :key="source" type="button"
          :class="['source-filter', `source-${source.toLowerCase()}`, { inactive: !activeSources.includes(source) }]"
          @click="toggleSource(source)">
          <i></i>{{ sourceMeta[source].label }}
        </button>
      </div>
    </section>

    <LoadingState v-if="loading" label="正在翻开这个月的日历…" />
    <div v-else-if="error" class="error-panel" role="alert"><p>{{ error }}</p><button class="button secondary" type="button" @click="load">重新加载</button></div>
    <template v-else>
      <section class="calendar-layout">
        <div class="calendar-board card">
          <div v-for="weekday in weekdays" :key="weekday" class="weekday">{{ weekday }}</div>
          <button v-for="day in gridDays" :key="day.key" type="button"
            :class="['calendar-day', { muted: !day.current, today: day.today, selected: selectedDate === day.key }]"
            @click="selectDay(day)" @dblclick="openCreate(day.key)">
            <span class="day-number">{{ day.day }}</span>
            <span class="day-events">
              <span v-for="item in (entriesByDate.get(day.key) || []).slice(0, 3)"
                :key="`${item.sourceType}-${item.id}-${item.startAt}`"
                :class="['day-event', `source-${item.sourceType.toLowerCase()}`]">{{ item.title }}</span>
              <small v-if="(entriesByDate.get(day.key) || []).length > 3">还有 {{ (entriesByDate.get(day.key) || []).length - 3 }} 项</small>
            </span>
          </button>
        </div>

        <aside class="day-agenda">
          <div class="agenda-heading">
            <div><p class="eyebrow">DAY AGENDA</p><h2>{{ formatDate(selectedDate, { month: 'long', day: 'numeric', weekday: 'long' }) }}</h2></div>
            <button class="icon-button" type="button" aria-label="添加当天日程" @click="openCreate(selectedDate)"><Plus :size="18" /></button>
          </div>
          <EmptyState v-if="!selectedEntries.length" title="这一天还很轻盈" description="双击日期，或点击加号安排一个属于你们的约定。" />
          <div v-else class="agenda-list">
            <article v-for="item in selectedEntries" :key="`${item.sourceType}-${item.id}-${item.startAt}`"
              :class="['agenda-item', `source-${item.sourceType.toLowerCase()}`]" @click="openSource(item)">
              <span class="agenda-dot"></span>
              <div>
                <small>{{ sourceMeta[item.sourceType].label }} · {{ item.createdByNickname }}</small>
                <h3>{{ item.title }}</h3>
                <p v-if="item.description">{{ item.description }}</p>
                <footer>
                  <span><CalendarDays v-if="item.allDay" :size="13" /><Clock3 v-else :size="13" />{{ item.allDay ? '全天' : formatDateTime(item.startAt) }}</span>
                  <span v-if="item.location"><MapPin :size="13" />{{ item.location }}</span>
                </footer>
              </div>
              <Pencil v-if="item.editable" :size="15" class="agenda-edit" />
            </article>
          </div>
        </aside>
      </section>
    </template>
  </div>

  <BaseModal v-if="modalOpen" :title="editing ? '编辑共享日程' : '添加共享日程'" description="日程会同时出现在你们两个人的日历中。" @close="modalOpen = false">
    <form class="stack-form" @submit.prevent="save">
      <label class="field"><span>日程名称</span><input v-model="form.title" required maxlength="120" placeholder="例如：一起去看展" /></label>
      <div class="form-two">
        <label class="field"><span>日期</span><input v-model="form.date" required type="date" /></label>
        <label class="field"><span>时间</span><input v-model="form.time" :disabled="form.allDay" required type="time" /></label>
      </div>
      <label class="check-row"><input v-model="form.allDay" type="checkbox" /><span><strong>全天日程</strong><small>不显示具体开始时间</small></span></label>
      <div class="form-two">
        <label class="field"><span>结束日期（可选）</span><input v-model="form.endDate" type="date" :min="form.date" /></label>
        <label class="field"><span>结束时间</span><input v-model="form.endTime" :disabled="form.allDay || !form.endDate" type="time" /></label>
      </div>
      <div class="form-two">
        <label class="field"><span>分类</span><select v-model="form.category"><option v-for="item in categories" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
        <label class="field"><span>地点（可选）</span><input v-model="form.location" maxlength="200" placeholder="在哪里见面" /></label>
      </div>
      <label class="field"><span>说明（可选）</span><textarea v-model="form.description" maxlength="1000" rows="4" placeholder="写下需要准备的事情或一句期待…" /></label>
      <div class="modal-actions">
        <button v-if="editing" class="button danger-button" type="button" :disabled="deleting || saving" @click="remove(editing)"><Trash2 :size="16" />移入回收站</button>
        <button class="button ghost" type="button" @click="modalOpen = false">取消</button>
        <button class="button primary" type="submit" :disabled="saving || !form.title.trim()"><span v-if="saving" class="button-spinner"></span><CalendarDays v-else :size="17" />{{ saving ? '正在保存…' : '保存日程' }}</button>
      </div>
    </form>
  </BaseModal>
</template>

<style scoped>
.calendar-toolbar { display: flex; align-items: center; gap: 15px; padding: 16px 18px; }
.month-switcher { display: flex; align-items: center; gap: 12px; }
.month-switcher > div { min-width: 125px; text-align: center; }
.month-switcher h2 { margin: 1px 0 0; font-size: 21px; }
.calendar-filters { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 7px; margin-left: auto; }
.source-filter { display: inline-flex; align-items: center; gap: 5px; padding: 6px 9px; border: 1px solid var(--line); border-radius: 999px; background: white; color: var(--muted); cursor: pointer; font-size: 9px; font-weight: 800; }
.source-filter i { width: 7px; height: 7px; border-radius: 50%; background: currentColor; }
.source-filter.inactive { opacity: .36; filter: grayscale(1); }
.calendar-layout { display: grid; grid-template-columns: minmax(0, 1fr) 310px; align-items: start; gap: 18px; }
.calendar-board { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); overflow: hidden; }
.weekday { padding: 11px 6px; border-bottom: 1px solid var(--line); color: var(--muted); text-align: center; font-size: 10px; font-weight: 800; }
.calendar-day { min-width: 0; min-height: 112px; padding: 8px; border: 0; border-right: 1px solid var(--line); border-bottom: 1px solid var(--line); background: rgba(255,253,251,.94); text-align: left; cursor: pointer; }
.calendar-day:nth-child(7n) { border-right: 0; }
.calendar-day:hover { background: #fff7f5; }
.calendar-day.muted { background: rgba(248,244,241,.7); color: #b9aaad; }
.calendar-day.selected { box-shadow: inset 0 0 0 2px rgba(221,99,118,.42); background: #fff8f7; }
.day-number { width: 25px; height: 25px; display: grid; place-items: center; border-radius: 50%; font: 700 12px Georgia, serif; }
.calendar-day.today .day-number { background: var(--rose); color: white; }
.day-events { display: grid; gap: 3px; margin-top: 4px; }
.day-event { overflow: hidden; padding: 3px 5px; border-left: 3px solid currentColor; border-radius: 4px; background: color-mix(in srgb, currentColor 10%, white); color: #87646b; font-size: 8px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.day-events small { color: var(--muted); font-size: 8px; }
.day-agenda { min-height: 420px; padding: 20px; border: 1px solid var(--line); border-radius: var(--radius); background: rgba(255,253,251,.78); }
.agenda-heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 15px; }
.agenda-heading h2 { margin: 2px 0 0; font-size: 20px; }
.day-agenda .empty-state { min-height: 300px; padding: 20px 5px; border: 0; background: transparent; }
.agenda-list { display: grid; gap: 9px; }
.agenda-item { position: relative; display: grid; grid-template-columns: auto 1fr auto; gap: 9px; padding: 13px; border: 1px solid var(--line); border-radius: 14px; background: white; cursor: pointer; }
.agenda-item:hover { border-color: #eabcc3; transform: translateY(-1px); }
.agenda-dot { width: 8px; height: 8px; margin-top: 5px; border-radius: 50%; background: currentColor; }
.agenda-item small { color: var(--muted); font-size: 8px; font-weight: 800; }
.agenda-item h3 { margin: 3px 0 4px; font-size: 15px; }
.agenda-item p { display: -webkit-box; overflow: hidden; margin: 0 0 7px; color: var(--muted); font-size: 10px; line-height: 1.5; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.agenda-item footer { display: flex; flex-wrap: wrap; gap: 8px; color: var(--muted); font-size: 9px; }
.agenda-item footer span { display: inline-flex; align-items: center; gap: 4px; }
.agenda-edit { color: var(--muted); }
.source-custom { color: #d45d73; }
.source-anniversary { color: #c88745; }
.source-wish { color: #8d69ad; }
.source-memory { color: #4f8ca4; }
.source-diary { color: #7c9a66; }
.source-letter { color: #a66f82; }
.check-row { display: flex; align-items: center; gap: 10px; padding: 11px 13px; border: 1px solid var(--line); border-radius: 13px; background: #fff9f7; cursor: pointer; }
.check-row input { width: 17px; height: 17px; accent-color: var(--rose); }
.check-row span { display: flex; flex-direction: column; gap: 2px; }
.check-row strong { font-size: 11px; }
.check-row small { color: var(--muted); font-size: 9px; }
.modal-actions .danger-button { margin-right: auto; }
@media (max-width: 980px) {
  .calendar-layout { grid-template-columns: 1fr; }
  .day-agenda { min-height: 0; }
  .day-agenda .empty-state { min-height: 150px; }
}
@media (max-width: 700px) {
  .calendar-toolbar { align-items: stretch; flex-wrap: wrap; }
  .month-switcher { justify-content: space-between; flex: 1; }
  .calendar-toolbar > .button { width: 100%; order: 3; }
  .calendar-filters { justify-content: flex-start; margin-left: 0; }
  .calendar-board { overflow-x: auto; grid-template-columns: repeat(7, minmax(76px, 1fr)); }
  .calendar-day { min-height: 93px; padding: 6px; }
  .day-event { max-width: 68px; }
}
</style>
