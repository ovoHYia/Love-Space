<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Bell, CalendarDays, CalendarHeart, CakeSlice, HeartHandshake, MapPinned, Pencil, Plus, RefreshCw, Sparkles, Star, Trash2 } from 'lucide-vue-next'
import { api } from '../api'
import { errorMessage } from '../api/client'
import { useToast } from '../composables/toast'
import { useResourceSync } from '../composables/resourceSync'
import BaseModal from '../components/BaseModal.vue'
import ConflictPanel from '../components/ConflictPanel.vue'
import EmptyState from '../components/EmptyState.vue'
import LoadingState from '../components/LoadingState.vue'
import type { Anniversary } from '../types'
import { daysUntilAnniversary, formatDate, todayInput } from '../utils'
import { isStaleUpdate, STALE_UPDATE_MESSAGE } from '../utils/editConflict'
import { createRequestGeneration } from '../utils/latestRequest'

const { show } = useToast()
const anniversaries = ref<Anniversary[]>([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const modalOpen = ref(false)
const editing = ref<Anniversary | null>(null)
const conflict = ref(false)
const anniversaryRequests = createRequestGeneration()
const form = reactive({ title: '', eventDate: todayInput(), type: 'CUSTOM', recurringYearly: true, reminderDays: 7, note: '' })
const types = [
  { value: 'LOVE_ANNIVERSARY', label: '恋爱纪念日', icon: HeartHandshake },
  { value: 'BIRTHDAY', label: '生日', icon: CakeSlice },
  { value: 'FIRST_MEETING', label: '第一次见面', icon: Sparkles },
  { value: 'TRIP', label: '旅行 / 约会', icon: MapPinned },
  { value: 'CUSTOM', label: '自定义日子', icon: Star },
]
const sorted = computed(() => [...anniversaries.value].sort((a, b) => {
  const left = days(a); const right = days(b)
  if ((left < 0) !== (right < 0)) return left < 0 ? 1 : -1
  return left < 0 ? Math.abs(left) - Math.abs(right) : left - right
}))

onMounted(() => { void load() })
useResourceSync(['anniversaries'], load)

async function load() {
  const request = anniversaryRequests.begin()
  loading.value = true
  error.value = ''
  try {
    const nextAnniversaries = await api.anniversaries()
    if (!request.isLatest()) return
    anniversaries.value = nextAnniversaries
    return true
  } catch (cause) {
    if (!request.isLatest()) return
    error.value = errorMessage(cause)
    return false
  } finally {
    if (request.isLatest()) loading.value = false
  }
}

function days(item: Anniversary) { return item.daysUntil ?? daysUntilAnniversary(item.eventDate, item.recurringYearly) }
function countdownPrefix(item: Anniversary) { return days(item) === 0 ? '就是今天' : days(item) < 0 ? '已经过去' : '还有' }
function countdownValue(item: Anniversary) { return Math.abs(days(item)) }
function countdownLabel(item: Anniversary) { return days(item) === 0 ? '今天' : days(item) < 0 ? `已过 ${Math.abs(days(item))} 天` : `${days(item)} 天后` }
function typeMeta(value: string) { return types.find((item) => item.value === value) || types[4] }

function openCreate() {
  editing.value = null
  conflict.value = false
  Object.assign(form, { title: '', eventDate: todayInput(), type: 'CUSTOM', recurringYearly: true, reminderDays: 7, note: '' })
  modalOpen.value = true
}

function openEdit(item: Anniversary) {
  editing.value = item
  conflict.value = false
  Object.assign(form, { title: item.title, eventDate: item.eventDate.slice(0, 10), type: item.type, recurringYearly: item.recurringYearly, reminderDays: item.reminderDays, note: item.note || '' })
  modalOpen.value = true
}

async function save() {
  saving.value = true
  try {
    const payload = { ...form, title: form.title.trim(), note: form.note.trim(), reminderDays: Number(form.reminderDays) }
    if (editing.value) {
      await api.updateAnniversary(editing.value.id, { ...payload, version: editing.value.version! })
      show('这个重要日子已经更新。', 'success')
    } else {
      await api.createAnniversary(payload)
      show('重要日子已经记在心上。', 'success')
    }
    modalOpen.value = false
    anniversaryRequests.cancel()
    await load()
  } catch (cause) {
    if (isStaleUpdate(cause)) {
      conflict.value = true
      show(STALE_UPDATE_MESSAGE, 'error')
      return
    }
    show(errorMessage(cause), 'error')
  } finally {
    saving.value = false
  }
}

async function loadLatest() {
  if (!editing.value) return
  try {
    await load()
    if (error.value) return
    const latest = anniversaries.value.find(item => String(item.id) === String(editing.value?.id))
    if (!latest) {
      show('这个纪念日已经不存在，请关闭编辑框后重新加载。', 'info')
      return
    }
    editing.value = latest
    Object.assign(form, {
      title: latest.title,
      eventDate: latest.eventDate.slice(0, 10),
      type: latest.type,
      recurringYearly: latest.recurringYearly,
      reminderDays: latest.reminderDays,
      note: latest.note || '',
    })
    conflict.value = false
    show('已加载最新内容，请确认后再保存。', 'info')
  } catch (cause) {
    show(errorMessage(cause), 'error')
  }
}

async function remove(item: Anniversary) {
  if (!window.confirm(`确定将“${item.title}”移入回收站吗？`)) return
  try {
    await api.deleteAnniversary(item.id)
    anniversaryRequests.cancel()
    loading.value = false
    anniversaries.value = anniversaries.value.filter((entry) => entry.id !== item.id)
    show('这个纪念日已移入回收站。', 'success')
  } catch (cause) {
    show(errorMessage(cause), 'error')
  }
}
</script>

<template>
  <div class="page-stack anniversaries-page">
    <header class="page-header">
      <div><p class="eyebrow">DAYS WORTH WAITING FOR</p><h1>重要的日子</h1><p>有人一起期待，倒数的每一天也变得有意义。</p></div>
      <button class="button primary" type="button" @click="openCreate"><Plus :size="18" />添加日子</button>
    </header>

    <LoadingState v-if="loading" label="正在数一数值得期待的日子…" />
    <div v-else-if="error" class="error-panel" role="alert"><p>{{ error }}</p><button class="button secondary" type="button" @click="load"><RefreshCw :size="17" />重新加载</button></div>
    <EmptyState v-else-if="!anniversaries.length" title="还没有重要日子" description="从恋爱纪念日、生日或第一次见面开始，把期待放进日历。"><button class="button primary small" type="button" @click="openCreate"><Plus :size="17" />添加第一个</button></EmptyState>
    <template v-else>
      <section class="next-date-card">
        <div class="next-date-copy"><span class="date-icon"><component :is="typeMeta(sorted[0].type).icon" :size="25" /></span><div><p class="eyebrow">NEXT MILESTONE</p><h2>{{ sorted[0].title }}</h2><p>{{ formatDate(sorted[0].eventDate, { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }) }} · {{ sorted[0].note || '我们一起等这一天到来' }}</p></div></div>
        <div class="big-countdown"><small>{{ countdownPrefix(sorted[0]) }}</small><strong>{{ countdownValue(sorted[0]) }}</strong><span>{{ days(sorted[0]) === 0 ? '♡' : '天' }}</span></div>
        <div class="next-actions"><button class="icon-button" type="button" aria-label="编辑纪念日" @click="openEdit(sorted[0])"><Pencil :size="17" /></button><button class="icon-button danger" type="button" aria-label="删除纪念日" @click="remove(sorted[0])"><Trash2 :size="17" /></button></div>
      </section>

      <section class="date-list-section">
        <div class="section-heading"><div><p class="eyebrow">ALL OUR DATES</p><h2>所有纪念日</h2></div><span class="date-total">共 {{ anniversaries.length }} 个</span></div>
        <div class="date-grid">
          <article v-for="item in sorted.slice(1)" :key="item.id" class="date-card">
            <header><span class="date-type-icon"><component :is="typeMeta(item.type).icon" :size="20" /></span><span class="type-label">{{ typeMeta(item.type).label }}</span><div class="card-actions"><button class="icon-button" type="button" aria-label="编辑纪念日" @click="openEdit(item)"><Pencil :size="16" /></button><button class="icon-button danger" type="button" aria-label="删除纪念日" @click="remove(item)"><Trash2 :size="16" /></button></div></header>
            <h3>{{ item.title }}</h3><p>{{ item.note || '每一次想起，还是会觉得这一天很特别。' }}</p>
            <footer><span><CalendarDays :size="15" />{{ formatDate(item.eventDate, { month: 'long', day: 'numeric' }) }}</span><strong>{{ countdownLabel(item) }}</strong></footer>
            <small v-if="item.recurringYearly" class="yearly-tag">每年重复</small>
          </article>
        </div>
      </section>
    </template>
  </div>

  <BaseModal v-if="modalOpen" :title="editing ? '编辑重要日子' : '记住一个重要日子'" description="设置后会在首页显示最近的倒计时。" @close="modalOpen = false">
    <ConflictPanel v-if="conflict" @reload="loadLatest" />
    <form class="stack-form" @submit.prevent="save">
      <label class="field"><span>日子名称</span><input v-model="form.title" required maxlength="80" placeholder="例如：我们在一起的纪念日" /></label>
      <div class="form-two"><label class="field"><span>日期</span><input v-model="form.eventDate" required type="date" /></label><label class="field"><span>类型</span><select v-model="form.type"><option v-for="type in types" :key="type.value" :value="type.value">{{ type.label }}</option></select></label></div>
      <label class="field"><span>备注（可选）</span><textarea v-model="form.note" maxlength="500" rows="3" placeholder="那一天为什么重要？"></textarea></label>
      <div class="form-two align-end"><label class="field"><span>提前几天提醒</span><span class="input-with-icon"><Bell :size="17" /><input v-model.number="form.reminderDays" type="number" min="0" max="365" required /></span></label><label class="switch-field"><input v-model="form.recurringYearly" type="checkbox" /><span class="switch" aria-hidden="true"></span><span><strong>每年重复</strong><small>适合生日和周年纪念</small></span></label></div>
      <div class="modal-actions"><button class="button ghost" type="button" @click="modalOpen = false">取消</button><button class="button primary" type="submit" :disabled="saving"><span v-if="saving" class="button-spinner"></span><CalendarHeart v-else :size="18" />{{ saving ? '正在保存…' : '记住这一天' }}</button></div>
    </form>
  </BaseModal>
</template>
