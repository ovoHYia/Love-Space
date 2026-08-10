<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  CalendarDays, Check, CheckCircle2, Clapperboard, HeartHandshake, MapPinned,
  Pencil, Plus, RefreshCw, RotateCcw, Sparkles, Trash2, UtensilsCrossed,
} from 'lucide-vue-next'
import { api } from '../api'
import { errorMessage } from '../api/client'
import BaseModal from '../components/BaseModal.vue'
import EmptyState from '../components/EmptyState.vue'
import LoadingState from '../components/LoadingState.vue'
import { useToast } from '../composables/toast'
import { useResourceSync } from '../composables/resourceSync'
import type { Wish, WishInput } from '../types'
import { formatDate, formatDateTime } from '../utils'

const { show } = useToast()
const wishes = ref<Wish[]>([])
const loading = ref(true)
const error = ref('')
const filter = ref<'ALL' | 'ACTIVE' | 'COMPLETED'>('ACTIVE')
const modalOpen = ref(false)
const editing = ref<Wish | null>(null)
const saving = ref(false)
const workingId = ref<Wish['id'] | null>(null)

const categories = [
  { value: 'TRAVEL', label: '一起旅行', icon: MapPinned },
  { value: 'DATE', label: '约会计划', icon: HeartHandshake },
  { value: 'FOOD', label: '想吃美食', icon: UtensilsCrossed },
  { value: 'MOVIE', label: '影音清单', icon: Clapperboard },
  { value: 'OTHER', label: '小小愿望', icon: Sparkles },
] as const

const form = reactive<WishInput>({ title: '', description: '', category: 'OTHER', targetDate: '' })
const activeCount = computed(() => wishes.value.filter((item) => item.status === 'ACTIVE').length)
const completedCount = computed(() => wishes.value.filter((item) => item.status === 'COMPLETED').length)
const visible = computed(() => {
  const values = filter.value === 'ALL' ? wishes.value : wishes.value.filter((item) => item.status === filter.value)
  return [...values].sort((a, b) => {
    if (a.status !== b.status) return a.status === 'ACTIVE' ? -1 : 1
    if (a.status === 'ACTIVE') {
      const aDate = a.targetDate || '9999-12-31'
      const bDate = b.targetDate || '9999-12-31'
      if (aDate !== bDate) return aDate.localeCompare(bDate)
    }
    return new Date(b.completedAt || b.createdAt || 0).getTime() - new Date(a.completedAt || a.createdAt || 0).getTime()
  })
})

onMounted(load)
useResourceSync(['wishes'], load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    wishes.value = await api.wishes()
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

function categoryMeta(value: Wish['category']) {
  return categories.find((item) => item.value === value) || categories[categories.length - 1]
}

function resetForm() {
  Object.assign(form, { title: '', description: '', category: 'OTHER', targetDate: '' })
}

function openCreate() {
  editing.value = null
  resetForm()
  modalOpen.value = true
}

function openEdit(item: Wish) {
  editing.value = item
  Object.assign(form, {
    title: item.title,
    description: item.description || '',
    category: item.category,
    targetDate: item.targetDate || '',
  })
  modalOpen.value = true
}

async function save() {
  saving.value = true
  const input: WishInput = {
    title: form.title.trim(),
    description: form.description?.trim() || undefined,
    category: form.category,
    targetDate: form.targetDate || undefined,
  }
  try {
    if (editing.value) {
      await api.updateWish(editing.value.id, input)
      show('愿望已经更新。', 'success')
    } else {
      await api.createWish(input)
      show('新的共同愿望已经放进清单。', 'success')
    }
    modalOpen.value = false
    await load()
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    saving.value = false
  }
}

async function complete(item: Wish) {
  workingId.value = item.id
  try {
    await api.completeWish(item.id)
    show('又一起完成了一个愿望 ♡', 'success')
    await load()
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    workingId.value = null
  }
}

async function reopen(item: Wish) {
  workingId.value = item.id
  try {
    await api.reopenWish(item.id)
    show('愿望已经重新放回待完成清单。', 'success')
    await load()
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    workingId.value = null
  }
}

async function remove(item: Wish) {
  if (!window.confirm(`确定将愿望“${item.title}”移入回收站吗？`)) return
  workingId.value = item.id
  try {
    await api.deleteWish(item.id)
    show('这个愿望已移入回收站。', 'success')
    await load()
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    workingId.value = null
  }
}
</script>

<template>
  <div class="page-stack wishes-page">
    <header class="page-header">
      <div><p class="eyebrow">THINGS WE WILL DO TOGETHER</p><h1>共同愿望</h1><p>把“以后一起”认真写下来，再一个个变成“我们做到啦”。</p></div>
      <button class="button primary" type="button" @click="openCreate"><Plus :size="18" />许下愿望</button>
    </header>

    <section class="wish-summary card">
      <span class="summary-icon"><Sparkles :size="26" /></span>
      <div><strong>还有 {{ activeCount }} 个愿望等着我们</strong><p>已经一起完成 {{ completedCount }} 个，慢慢来，每一个都算数。</p></div>
      <div class="wish-progress"><span :style="{ transform: `scaleX(${wishes.length ? completedCount / wishes.length : 0})` }"></span></div>
    </section>

    <div class="wish-filters" role="group" aria-label="筛选愿望">
      <button type="button" :aria-pressed="filter === 'ACTIVE'" :class="{ active: filter === 'ACTIVE' }" @click="filter = 'ACTIVE'">待完成 {{ activeCount }}</button>
      <button type="button" :aria-pressed="filter === 'COMPLETED'" :class="{ active: filter === 'COMPLETED' }" @click="filter = 'COMPLETED'">已完成 {{ completedCount }}</button>
      <button type="button" :aria-pressed="filter === 'ALL'" :class="{ active: filter === 'ALL' }" @click="filter = 'ALL'">全部 {{ wishes.length }}</button>
    </div>

    <LoadingState v-if="loading" label="正在打开愿望清单…" />
    <div v-else-if="error" class="error-panel" role="alert"><p>{{ error }}</p><button class="button secondary" type="button" @click="load"><RefreshCw :size="17" />重新加载</button></div>
    <EmptyState v-else-if="!visible.length" :title="filter === 'COMPLETED' ? '还没有完成的愿望' : '愿望清单还是空白的'" :description="filter === 'COMPLETED' ? '等完成第一个愿望，它会被好好收藏在这里。' : '想去的地方、想做的事，都可以从一句话开始。'"><button v-if="filter !== 'COMPLETED'" class="button primary small" type="button" @click="openCreate"><Plus :size="17" />写下第一个愿望</button></EmptyState>

    <div v-else class="wish-grid">
      <article v-for="item in visible" :key="item.id" class="wish-card card" :class="{ completed: item.status === 'COMPLETED' }">
        <header>
          <span class="wish-category"><component :is="categoryMeta(item.category).icon" :size="18" />{{ categoryMeta(item.category).label }}</span>
          <span v-if="item.status === 'COMPLETED'" class="completed-chip"><CheckCircle2 :size="15" />已完成</span>
          <div v-else class="card-actions"><button class="icon-button" type="button" aria-label="编辑愿望" @click="openEdit(item)"><Pencil :size="16" /></button><button class="icon-button danger" type="button" aria-label="删除愿望" @click="remove(item)"><Trash2 :size="16" /></button></div>
        </header>
        <div class="wish-copy"><h2>{{ item.title }}</h2><p v-if="item.description">{{ item.description }}</p><p v-else class="muted">等我们一起为它添上更多细节。</p></div>
        <div v-if="item.targetDate" class="wish-date"><CalendarDays :size="16" />希望在 {{ formatDate(item.targetDate) }} 前完成</div>
        <footer>
          <small v-if="item.status === 'COMPLETED'">{{ item.completedByNickname }} 于 {{ formatDateTime(item.completedAt || undefined) }} 完成</small>
          <small v-else>{{ item.createdByNickname }} 写下</small>
          <button v-if="item.status === 'ACTIVE'" class="button primary small" type="button" :disabled="workingId === item.id" @click="complete(item)"><Check :size="16" />标记完成</button>
          <span v-else class="completed-actions"><button class="text-button" type="button" :disabled="workingId === item.id" @click="reopen(item)"><RotateCcw :size="14" />重新开启</button><button class="icon-button danger" type="button" aria-label="删除愿望" @click="remove(item)"><Trash2 :size="15" /></button></span>
        </footer>
      </article>
    </div>
  </div>

  <BaseModal v-if="modalOpen" :title="editing ? '编辑共同愿望' : '许下一个共同愿望'" description="两个人都可以补充、完成或重新开启。" @close="modalOpen = false">
    <form class="stack-form" @submit.prevent="save">
      <label class="field"><span>愿望名称</span><input v-model="form.title" required maxlength="120" autofocus placeholder="例如：一起去看一次极光" /></label>
      <label class="field"><span>愿望分类</span><select v-model="form.category"><option v-for="item in categories" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
      <label class="field"><span>希望完成的日期（可选）</span><input v-model="form.targetDate" type="date" /></label>
      <label class="field"><span>想补充的话（可选）</span><textarea v-model="form.description" maxlength="1000" rows="4" placeholder="为什么想一起完成它？"></textarea><small>{{ form.description?.length || 0 }}/1000</small></label>
      <div class="modal-actions"><button class="button ghost" type="button" @click="modalOpen = false">取消</button><button class="button primary" type="submit" :disabled="saving || !form.title.trim()"><span v-if="saving" class="button-spinner"></span><Sparkles v-else :size="17" />{{ saving ? '正在保存…' : '放进愿望清单' }}</button></div>
    </form>
  </BaseModal>
</template>

<style scoped>
.wish-summary { position: relative; display: flex; align-items: center; gap: 14px; padding: 18px 20px 23px; overflow: hidden; }
.summary-icon { flex: 0 0 auto; width: 48px; height: 48px; display: grid; place-items: center; border-radius: 16px; background: var(--rose-pale); color: var(--rose-dark); }
.wish-summary strong { font-size: 17px; }.wish-summary p { margin: 4px 0 0; color: var(--muted); font-size: 12px; }
.wish-progress { position: absolute; left: 0; right: 0; bottom: 0; height: 5px; background: #f5e9e9; }
.wish-progress span { display: block; height: 100%; border-radius: inherit; transform-origin: left center; background: linear-gradient(90deg, var(--rose), #e9a2aa); transition: transform .3s ease; }
.wish-filters { display: flex; gap: 8px; padding: 4px; align-self: flex-start; border: 1px solid var(--line); border-radius: 14px; background: rgba(255,255,255,.7); }
.wish-filters button { padding: 8px 13px; border: 0; border-radius: 10px; background: transparent; color: var(--muted); cursor: pointer; font-size: 12px; font-weight: 700; }
.wish-filters button.active { background: var(--rose-pale); color: var(--rose-dark); }
.wish-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 15px; }
.wish-card { display: flex; min-height: 230px; flex-direction: column; padding: 18px; transition: transform .18s ease, opacity .18s ease; }
.wish-card:hover { transform: translateY(-2px); }.wish-card.completed { opacity: .78; }
.wish-card > header, .wish-card > footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.wish-category, .completed-chip, .wish-date { display: inline-flex; align-items: center; gap: 6px; }
.wish-category { color: var(--rose-dark); font-size: 11px; font-weight: 800; letter-spacing: .04em; }
.completed-chip { padding: 5px 8px; border-radius: 10px; background: #edf6ee; color: #52805a; font-size: 10px; font-weight: 800; }
.wish-copy { flex: 1; padding: 24px 2px 18px; }.wish-copy h2 { margin: 0 0 8px; font-size: 23px; }.wish-copy p { margin: 0; color: #806a70; line-height: 1.7; white-space: pre-wrap; }
.wish-card.completed .wish-copy h2 { text-decoration: line-through; text-decoration-color: #c9aeb2; }
.wish-date { margin-bottom: 15px; color: var(--muted); font-size: 11px; }
.wish-card footer { padding-top: 13px; border-top: 1px solid var(--line); }.wish-card footer small { color: var(--muted); }
.completed-actions { display: inline-flex; align-items: center; gap: 5px; }.completed-actions .text-button { display: inline-flex; align-items: center; gap: 4px; }
@media (max-width: 700px) {
  .wish-grid { grid-template-columns: 1fr; }
  .wish-summary { align-items: flex-start; }
  .wish-filters { width: 100%; }.wish-filters button { flex: 1; padding-inline: 7px; }
}
</style>
