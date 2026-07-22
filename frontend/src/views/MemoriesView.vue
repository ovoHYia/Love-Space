<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { CalendarDays, Camera, FileAudio, FileVideo, Heart as HeartFill, Images, MapPin, Pencil, Plus, RefreshCw, Search, Trash2, UploadCloud, X } from 'lucide-vue-next'
import { api } from '../api'
import { errorMessage, mediaUrl, unwrapList } from '../api/client'
import { useToast } from '../composables/toast'
import BaseAvatar from '../components/BaseAvatar.vue'
import BaseModal from '../components/BaseModal.vue'
import EmptyState from '../components/EmptyState.vue'
import LoadingState from '../components/LoadingState.vue'
import type { MediaItem, Memory, UserProfile } from '../types'
import { formatDate, toLocalDateTimeInput } from '../utils'

const { show } = useToast()
const memories = ref<Memory[]>([])
const loading = ref(true)
const loadingMore = ref(false)
const saving = ref(false)
const error = ref('')
const page = ref(0)
const totalPages = ref(1)
const formOpen = ref(false)
const editing = ref<Memory | null>(null)
const galleryMemory = ref<Memory | null>(null)
const selectedFiles = ref<File[]>([])
const fileInput = ref<HTMLInputElement | null>(null)
const dateInput = ref<HTMLInputElement | null>(null)
const filters = reactive({ q: '', date: '' })
const form = reactive({ title: '', description: '', eventAt: toLocalDateTimeInput(), location: '' })

const grouped = computed(() => {
  const groups = new Map<string, Memory[]>()
  memories.value.forEach((memory) => {
    const key = formatDate(memory.eventAt, { year: 'numeric', month: 'long' })
    groups.set(key, [...(groups.get(key) || []), memory])
  })
  return [...groups.entries()]
})

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const payload = await api.memories(memoryQuery(0))
    memories.value = unwrapList(payload)
    applyPage(payload, 0)
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || page.value + 1 >= totalPages.value) return
  loadingMore.value = true
  try {
    const nextPage = page.value + 1
    const payload = await api.memories(memoryQuery(nextPage))
    memories.value = [...memories.value, ...unwrapList(payload)]
    applyPage(payload, nextPage)
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    loadingMore.value = false
  }
}

function memoryQuery(targetPage: number) {
  const query = new URLSearchParams({ page: String(targetPage), size: '20' })
  if (filters.q.trim()) query.set('q', filters.q.trim())
  if (filters.date) query.set('date', filters.date)
  return query.toString()
}

function clearDate() {
  if (!filters.date) return
  filters.date = ''
  load()
}

function openDatePicker(event: MouseEvent) {
  if ((event.target as HTMLElement).closest('input, button')) return
  const input = dateInput.value as (HTMLInputElement & { showPicker?: () => void }) | null
  input?.focus()
  input?.showPicker?.()
}

function applyPage(payload: Awaited<ReturnType<typeof api.memories>>, fallbackPage: number) {
  if (Array.isArray(payload)) {
    page.value = 0
    totalPages.value = 1
    return
  }
  page.value = payload.page ?? payload.number ?? fallbackPage
  totalPages.value = payload.totalPages ?? 1
}

function openCreate() {
  editing.value = null
  Object.assign(form, { title: '', description: '', eventAt: toLocalDateTimeInput(), location: '' })
  selectedFiles.value = []
  formOpen.value = true
}

function openEdit(memory: Memory) {
  editing.value = memory
  Object.assign(form, { title: memory.title, description: memory.description || '', eventAt: toLocalDateTimeInput(memory.eventAt), location: memory.location || '' })
  selectedFiles.value = []
  formOpen.value = true
}

function chooseFiles(event: Event) {
  const files = Array.from((event.target as HTMLInputElement).files || [])
  const allowed = files.filter((file) => /^(image|video|audio)\//.test(file.type))
  if (allowed.length !== files.length) show('已忽略不支持的文件，只能上传图片、视频和音频。', 'info')
  selectedFiles.value.push(...allowed)
  if (fileInput.value) fileInput.value.value = ''
}

async function save() {
  saving.value = true
  try {
    const payload = { ...form, title: form.title.trim(), description: form.description.trim(), location: form.location.trim() }
    if (editing.value) {
      await api.updateMemory(editing.value.id, payload)
      show('这段回忆已经更新。', 'success')
    } else {
      await api.createMemory(payload, selectedFiles.value)
      show('新的回忆已经收藏好。', 'success')
    }
    formOpen.value = false
    await load()
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    saving.value = false
  }
}

async function remove(memory: Memory) {
  if (!window.confirm(`确定删除“${memory.title}”吗？这段回忆和其中的媒体将无法恢复。`)) return
  try {
    await api.deleteMemory(memory.id)
    memories.value = memories.value.filter((item) => item.id !== memory.id)
    show('这段回忆已删除。', 'success')
  } catch (cause) {
    show(errorMessage(cause), 'error')
  }
}

function mediaItems(memory: Memory) { return [...(memory.media || []), ...(memory.files || [])] }
function itemType(item: MediaItem) {
  const type = (item.contentType || item.mediaType || item.type || '').toLowerCase()
  if (type.includes('video')) return 'video'
  if (type.includes('audio')) return 'audio'
  return 'image'
}
function itemUrl(item: MediaItem) { return mediaUrl(item.id || item.mediaId, item.url) }
function authorOf(memory: Memory): UserProfile | undefined {
  if (memory.author) return memory.author
  return memory.authorNickname ? { id: memory.authorId ?? '', nickname: memory.authorNickname } : undefined
}
function fileIcon(file: File) { return file.type.startsWith('video/') ? FileVideo : file.type.startsWith('audio/') ? FileAudio : Camera }
function formatBytes(bytes: number) { return bytes < 1048576 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1048576).toFixed(1)} MB` }
</script>

<template>
  <div class="page-stack memories-page">
    <header class="page-header">
      <div><p class="eyebrow">OUR STORYLINE</p><h1>回忆时间线</h1><p>照片会褪色，但认真写下的那一天不会。</p></div>
      <button class="button primary" type="button" @click="openCreate"><Plus :size="18" />收藏回忆</button>
    </header>

    <form class="filter-bar" role="search" @submit.prevent="load">
      <label class="search-field"><Search :size="18" aria-hidden="true" /><span class="sr-only">搜索回忆</span><input v-model="filters.q" placeholder="搜索标题、文字或地点" /></label>
      <div class="date-filter" @click="openDatePicker"><CalendarDays :size="17" aria-hidden="true" /><label class="sr-only" for="memories-date">按具体日期筛选</label><input id="memories-date" ref="dateInput" v-model="filters.date" :class="{ empty: !filters.date }" type="date" aria-label="按具体日期筛选" @change="load" /><span v-if="!filters.date" class="date-filter-placeholder">选择日期</span><button v-if="filters.date" class="date-clear" type="button" aria-label="清除日期" @click.stop="clearDate"><X :size="14" /></button></div>
      <button class="button secondary small" type="submit">筛选</button>
    </form>

    <LoadingState v-if="loading" label="正在沿着时间线往回走…" />
    <div v-else-if="error" class="error-panel" role="alert"><p>{{ error }}</p><button class="button secondary" type="button" @click="load"><RefreshCw :size="17" />重新加载</button></div>
    <EmptyState v-else-if="!memories.length" title="时间线还是空白的" description="从今天开始，收藏第一张照片、第一段声音，或第一句舍不得忘的话。"><button class="button primary small" type="button" @click="openCreate"><Plus :size="17" />收藏第一条</button></EmptyState>
    <div v-else class="timeline">
      <section v-for="([month, items], groupIndex) in grouped" :key="month" class="timeline-group">
        <div class="timeline-month"><span>{{ month }}</span><i></i></div>
        <article v-for="memory in items" :key="memory.id" class="memory-card" :class="{ featured: groupIndex === 0 }">
          <div class="memory-dot" aria-hidden="true"><HeartFill /></div>
          <div class="memory-card-inner">
            <div v-if="mediaItems(memory).length" class="memory-media" :class="{ gallery: mediaItems(memory).length > 1 }">
              <template v-for="media in mediaItems(memory).slice(0, 4)" :key="media.id || media.mediaId">
                <img v-if="itemType(media) === 'image'" :src="itemUrl(media)" :alt="`${memory.title} 的照片`" loading="lazy" />
                <video v-else-if="itemType(media) === 'video'" :src="itemUrl(media)" controls preload="metadata" :aria-label="`${memory.title} 的视频`"></video>
                <div v-else class="audio-tile"><FileAudio :size="25" /><span>{{ media.originalName || '一段声音' }}</span><audio :src="itemUrl(media)" controls :aria-label="`${memory.title} 的音频`"></audio></div>
              </template>
              <button v-if="mediaItems(memory).length > 4" class="more-media" type="button" @click="galleryMemory = memory">+{{ mediaItems(memory).length - 4 }}</button>
            </div>
            <div class="memory-content">
              <div class="memory-meta"><span><CalendarDays :size="14" />{{ formatDate(memory.eventAt, { year: 'numeric', month: 'short', day: 'numeric', weekday: 'short' }) }}</span><span v-if="memory.location"><MapPin :size="14" />{{ memory.location }}</span></div>
              <div class="memory-title-row"><div><h2>{{ memory.title }}</h2><span v-if="authorOf(memory)" class="author-chip"><BaseAvatar :user="authorOf(memory)" size="sm" />{{ authorOf(memory)?.nickname }} 收藏</span></div><div class="card-actions"><button class="icon-button" type="button" aria-label="编辑回忆" @click="openEdit(memory)"><Pencil :size="17" /></button><button class="icon-button danger" type="button" aria-label="删除回忆" @click="remove(memory)"><Trash2 :size="17" /></button></div></div>
              <p v-if="memory.description" class="memory-description">{{ memory.description }}</p>
            </div>
          </div>
        </article>
      </section>
      <div v-if="page + 1 < totalPages" class="load-more-row">
        <button class="button secondary" type="button" :disabled="loadingMore" @click="loadMore">
          <span v-if="loadingMore" class="button-spinner"></span><Plus v-else :size="17" />{{ loadingMore ? '正在翻找…' : '加载更早的回忆' }}
        </button>
      </div>
    </div>
  </div>

  <BaseModal v-if="formOpen" :title="editing ? '编辑这段回忆' : '收藏一段新回忆'" :description="editing ? '媒体文件会保持不变。' : '一次可以放入多张照片、视频或声音。'" wide @close="formOpen = false">
    <form class="stack-form" @submit.prevent="save">
      <div class="form-two">
        <label class="field"><span>回忆标题</span><input v-model="form.title" required maxlength="80" placeholder="例如：海边吹风的那个下午" /></label>
        <label class="field"><span>发生时间</span><input v-model="form.eventAt" required type="datetime-local" /></label>
      </div>
      <label class="field"><span>地点（可选）</span><span class="input-with-icon"><MapPin :size="17" /><input v-model="form.location" maxlength="100" placeholder="例如：厦门 · 环岛路" /></span></label>
      <label class="field"><span>想记住的话（可选）</span><textarea v-model="form.description" maxlength="1000" rows="5" placeholder="那天发生了什么？当时是什么心情？"></textarea><small>{{ form.description.length }}/1000</small></label>
      <div v-if="!editing" class="upload-field">
        <input ref="fileInput" class="sr-only" type="file" multiple accept="image/*,video/*,audio/*" @change="chooseFiles" />
        <button class="upload-drop" type="button" @click="fileInput?.click()"><span><UploadCloud :size="25" /></span><strong>选择照片、视频或声音</strong><small>可以一次选择多个文件</small></button>
        <ul v-if="selectedFiles.length" class="file-list">
          <li v-for="(file, index) in selectedFiles" :key="`${file.name}-${index}`"><component :is="fileIcon(file)" :size="17" /><span><strong>{{ file.name }}</strong><small>{{ formatBytes(file.size) }}</small></span><button type="button" aria-label="移除文件" @click="selectedFiles.splice(index, 1)"><X :size="17" /></button></li>
        </ul>
      </div>
      <div class="modal-actions"><button class="button ghost" type="button" @click="formOpen = false">取消</button><button class="button primary" type="submit" :disabled="saving"><span v-if="saving" class="button-spinner"></span><Images v-else :size="18" />{{ saving ? '正在收藏…' : (editing ? '保存修改' : '收藏这段回忆') }}</button></div>
    </form>
  </BaseModal>
  <BaseModal v-if="galleryMemory" :title="`${galleryMemory.title} 的全部媒体`" wide @close="galleryMemory = null">
    <div class="media-gallery">
      <template v-for="media in mediaItems(galleryMemory)" :key="media.id || media.mediaId">
        <img v-if="itemType(media) === 'image'" :src="itemUrl(media)" :alt="`${galleryMemory.title} 的照片`" />
        <video v-else-if="itemType(media) === 'video'" :src="itemUrl(media)" controls preload="metadata" :aria-label="`${galleryMemory.title} 的视频`"></video>
        <audio v-else :src="itemUrl(media)" controls preload="none" :aria-label="`${galleryMemory.title} 的音频`"></audio>
      </template>
    </div>
  </BaseModal>
</template>
