<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { BookHeart, Feather, Heart, LockOpen, Pencil, Plus, RefreshCw, Search, Trash2 } from 'lucide-vue-next'
import { api } from '../api'
import { errorMessage, unwrapList } from '../api/client'
import { useToast } from '../composables/toast'
import { authState } from '../stores/auth'
import BaseAvatar from '../components/BaseAvatar.vue'
import BaseModal from '../components/BaseModal.vue'
import EmptyState from '../components/EmptyState.vue'
import LoadingState from '../components/LoadingState.vue'
import type { Diary, UserProfile } from '../types'
import { formatDate, sameId, todayInput } from '../utils'

const { show } = useToast()
const diaries = ref<Diary[]>([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const filter = ref<'all' | 'mine' | 'partner'>('all')
const search = ref('')
const modalOpen = ref(false)
const editing = ref<Diary | null>(null)
const expanded = ref(new Set<string>())
const form = reactive({ title: '', content: '', diaryDate: todayInput(), mood: '温柔' })
const moodOptions = ['开心', '温柔', '想念', '平静', '感动', '有点累', '需要抱抱']

const filtered = computed(() => diaries.value.filter((diary) => {
  const authorId = diary.author?.id ?? diary.authorId
  if (filter.value === 'mine' && !sameId(authorId, authState.user?.id)) return false
  if (filter.value === 'partner' && !sameId(authorId, authState.partner?.id)) return false
  const q = search.value.trim().toLowerCase()
  return !q || `${diary.title} ${diary.content}`.toLowerCase().includes(q)
}))

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    diaries.value = unwrapList(await api.diaries())
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  Object.assign(form, { title: '', content: '', diaryDate: todayInput(), mood: '温柔' })
  modalOpen.value = true
}

function openEdit(diary: Diary) {
  editing.value = diary
  Object.assign(form, { title: diary.title, content: diary.content, diaryDate: diary.diaryDate.slice(0, 10), mood: diary.mood || '温柔' })
  modalOpen.value = true
}

function canEdit(diary: Diary) { return sameId(diary.author?.id ?? diary.authorId, authState.user?.id) }
function authorOf(diary: Diary): UserProfile | undefined {
  if (diary.author) return diary.author
  if (diary.authorNickname) return { id: diary.authorId ?? '', nickname: diary.authorNickname }
  return canEdit(diary) ? authState.user || undefined : authState.partner || undefined
}

async function save() {
  saving.value = true
  try {
    const payload = { ...form, title: form.title.trim(), content: form.content.trim() }
    if (editing.value) {
      await api.updateDiary(editing.value.id, payload)
      show('日记已经重新誊写好。', 'success')
    } else {
      await api.createDiary(payload)
      show('今天这一页，已经好好收进日记本。', 'success')
    }
    modalOpen.value = false
    await load()
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    saving.value = false
  }
}

async function remove(diary: Diary) {
  if (!window.confirm(`确定将“${diary.title}”移入回收站吗？`)) return
  try {
    await api.deleteDiary(diary.id)
    diaries.value = diaries.value.filter((item) => item.id !== diary.id)
    show('这篇日记已移入回收站。', 'success')
  } catch (cause) {
    show(errorMessage(cause), 'error')
  }
}

function toggle(id: Diary['id']) {
  const next = new Set(expanded.value)
  const key = String(id)
  next.has(key) ? next.delete(key) : next.add(key)
  expanded.value = next
}
</script>

<template>
  <div class="page-stack diaries-page">
    <header class="page-header">
      <div><p class="eyebrow">TWO VOICES, ONE BOOK</p><h1>我们的日记本</h1><p>各自落笔，彼此可见。这里不需要写得漂亮，只要写得真。</p></div>
      <button class="button primary" type="button" @click="openCreate"><Feather :size="18" />写下今天</button>
    </header>

    <div class="toolbar-row">
      <div class="segmented" role="tablist" aria-label="按作者筛选">
        <button role="tab" :aria-selected="filter === 'all'" :class="{ active: filter === 'all' }" @click="filter = 'all'">全部</button>
        <button role="tab" :aria-selected="filter === 'mine'" :class="{ active: filter === 'mine' }" @click="filter = 'mine'">{{ authState.user?.nickname || '我' }}写的</button>
        <button role="tab" :aria-selected="filter === 'partner'" :class="{ active: filter === 'partner' }" @click="filter = 'partner'">{{ authState.partner?.nickname || 'TA' }}写的</button>
      </div>
      <label class="search-field compact"><Search :size="17" /><span class="sr-only">搜索日记</span><input v-model="search" placeholder="在日记里找一句话" /></label>
    </div>

    <div class="privacy-banner"><LockOpen :size="18" /><p><strong>这本日记对你们两个人开放。</strong><span>只能编辑或删除自己写下的内容。</span></p></div>

    <LoadingState v-if="loading" label="正在翻开日记本…" />
    <div v-else-if="error" class="error-panel" role="alert"><p>{{ error }}</p><button class="button secondary" type="button" @click="load"><RefreshCw :size="17" />重新加载</button></div>
    <EmptyState v-else-if="!filtered.length" :title="search ? '没有找到这句话' : '这一页还是空白的'" :description="search ? '换个关键词试试，也许它藏在另一种表达里。' : '不必等到特别的日子，普通的一天也值得写下来。'"><button v-if="!search" class="button primary small" type="button" @click="openCreate"><Plus :size="17" />写第一篇</button></EmptyState>
    <div v-else class="diary-grid">
      <article v-for="diary in filtered" :key="diary.id" class="diary-card">
        <div class="diary-date"><strong>{{ new Date(diary.diaryDate).getDate().toString().padStart(2, '0') }}</strong><span>{{ formatDate(diary.diaryDate, { month: 'short', year: 'numeric' }) }}</span></div>
        <div class="diary-paper">
          <header><span class="mood-stamp">{{ diary.mood || '日常' }}</span><div v-if="canEdit(diary)" class="card-actions"><button class="icon-button" type="button" aria-label="编辑日记" @click="openEdit(diary)"><Pencil :size="17" /></button><button class="icon-button danger" type="button" aria-label="删除日记" @click="remove(diary)"><Trash2 :size="17" /></button></div></header>
          <h2>{{ diary.title }}</h2>
          <p class="diary-content" :class="{ expanded: expanded.has(String(diary.id)) }">{{ diary.content }}</p>
          <button v-if="diary.content.length > 150" class="text-button read-more" type="button" @click="toggle(diary.id)">{{ expanded.has(String(diary.id)) ? '收起这一页' : '继续读完' }}</button>
          <footer><span class="diary-author"><BaseAvatar :user="authorOf(diary)" size="sm" />{{ authorOf(diary)?.nickname }} 写下</span><Heart :size="15" aria-hidden="true" /></footer>
        </div>
      </article>
    </div>
  </div>

  <BaseModal v-if="modalOpen" :title="editing ? '重新誊写这一页' : '写下今天'" description="对方也会看到这篇日记。" wide @close="modalOpen = false">
    <form class="stack-form diary-form" @submit.prevent="save">
      <div class="form-two">
        <label class="field"><span>日期</span><input v-model="form.diaryDate" required type="date" /></label>
        <label class="field"><span>那天的心情</span><select v-model="form.mood"><option v-for="mood in moodOptions" :key="mood">{{ mood }}</option></select></label>
      </div>
      <label class="field"><span>标题</span><input v-model="form.title" required maxlength="100" placeholder="给今天取一个小标题" /></label>
      <label class="field"><span>正文</span><textarea v-model="form.content" required maxlength="10000" rows="12" placeholder="慢慢写，不着急。今天发生了什么，你想留下什么？"></textarea><small>{{ form.content.length }}/10000</small></label>
      <div class="modal-actions"><button class="button ghost" type="button" @click="modalOpen = false">取消</button><button class="button primary" type="submit" :disabled="saving"><span v-if="saving" class="button-spinner"></span><BookHeart v-else :size="18" />{{ saving ? '正在保存…' : (editing ? '保存这一页' : '收进日记本') }}</button></div>
    </form>
  </BaseModal>
</template>
