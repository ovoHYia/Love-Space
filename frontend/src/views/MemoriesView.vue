<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { CalendarDays, Images, Plus, RefreshCw, Search, Tags } from 'lucide-vue-next'
import { api } from '../api'
import { errorMessage } from '../api/client'
import BaseModal from '../components/BaseModal.vue'
import LoadingState from '../components/LoadingState.vue'
import MemoryAlbum from '../components/memories/MemoryAlbum.vue'
import MemoryEditorModal from '../components/memories/MemoryEditorModal.vue'
import MemoryTimeline from '../components/memories/MemoryTimeline.vue'
import { useToast } from '../composables/toast'
import { useResourceSync } from '../composables/resourceSync'
import type { AlbumItem, MediaItem, Memory, MemoryTag } from '../types'
import { memoryMediaType, memoryMediaUrl } from '../utils/memoryMedia'
import { createRequestGeneration } from '../utils/latestRequest'

type MemoryViewMode = 'timeline' | 'album'

const { show } = useToast()
const activeView = ref<MemoryViewMode>('timeline')
const memories = ref<Memory[]>([])
const albumItems = ref<AlbumItem[]>([])
const availableTags = ref<MemoryTag[]>([])
const loading = ref(true)
const loadingMore = ref(false)
const error = ref('')
const page = ref(0)
const totalPages = ref(1)
const formOpen = ref(false)
const editing = ref<Memory | null>(null)
const galleryMemory = ref<Memory | null>(null)
const mediaViewer = ref<{ title: string; media: MediaItem } | null>(null)
const filters = reactive({ q: '', tag: '' })
const viewRequests = createRequestGeneration()
const tagRequests = createRequestGeneration()

const viewOptions = [
  { id: 'timeline' as const, label: '时间线', icon: CalendarDays },
  { id: 'album' as const, label: '我们的相册', icon: Images },
]

onMounted(() => { void Promise.all([loadTimeline(), loadTags()]) })
useResourceSync(['memories', 'media'], async () => {
  await Promise.all([loadCurrentView(), loadTags()])
})
watch(activeView, () => { void loadCurrentView() })
watch(() => filters.tag, () => { void loadCurrentView() })

async function loadCurrentView() {
  const view = activeView.value
  if (view === 'timeline') await loadTimeline()
  if (view === 'album') await loadAlbum()
}

async function loadTimeline() {
  const request = viewRequests.begin()
  loading.value = true
  error.value = ''
  try {
    const payload = await api.memories(memoryQuery(0))
    if (!request.isLatest()) return
    memories.value = payload.content
    applyPage(payload)
  } catch (cause) {
    if (!request.isLatest()) return
    error.value = errorMessage(cause)
  } finally {
    if (request.isLatest()) loading.value = false
  }
}

async function loadAlbum(targetPage = 0, append = false) {
  const request = viewRequests.begin()
  loading.value = !append
  loadingMore.value = append
  error.value = ''
  try {
    const query = new URLSearchParams({ page: String(targetPage), size: '30' })
    if (filters.q.trim()) query.set('q', filters.q.trim())
    if (filters.tag) query.set('tag', filters.tag)
    const payload = await api.memoryAlbum(query.toString())
    if (!request.isLatest()) return
    albumItems.value = append ? [...albumItems.value, ...payload.content] : payload.content
    applyPage(payload)
  } catch (cause) {
    if (!request.isLatest()) return
    error.value = errorMessage(cause)
  } finally {
    if (request.isLatest()) {
      loading.value = false
      loadingMore.value = false
    }
  }
}

async function loadTags() {
  const request = tagRequests.begin()
  try {
    const tags = await api.memoryTags()
    if (request.isLatest()) availableTags.value = tags
  } catch {
    if (request.isLatest()) availableTags.value = []
  }
}

async function loadMore() {
  if (loadingMore.value || page.value + 1 >= totalPages.value) return
  if (activeView.value === 'album') {
    await loadAlbum(page.value + 1, true)
    return
  }
  const request = viewRequests.begin()
  loadingMore.value = true
  try {
    const payload = await api.memories(memoryQuery(page.value + 1))
    if (!request.isLatest()) return
    memories.value = [...memories.value, ...payload.content]
    applyPage(payload)
  } catch (cause) {
    if (!request.isLatest()) return
    show(errorMessage(cause), 'error')
  } finally {
    if (request.isLatest()) loadingMore.value = false
  }
}

function memoryQuery(targetPage: number) {
  const query = new URLSearchParams({ page: String(targetPage), size: '20' })
  if (filters.q.trim()) query.set('q', filters.q.trim())
  if (filters.tag) query.set('tag', filters.tag)
  return query.toString()
}

function applyPage(payload: { page: number; totalPages: number }) {
  page.value = payload.page
  totalPages.value = payload.totalPages
}

function openCreate() {
  editing.value = null
  formOpen.value = true
}

function openEdit(memory: Memory) {
  editing.value = memory
  formOpen.value = true
}

function closeEditor() {
  formOpen.value = false
  editing.value = null
}

async function editorSaved() {
  closeEditor()
  await Promise.all([loadCurrentView(), loadTags()])
}

function replaceMemory(updated: Memory) {
  editing.value = updated
  memories.value = replaceIn(memories.value, updated)
  if (galleryMemory.value?.id === updated.id) galleryMemory.value = updated
  if (activeView.value === 'album') void loadAlbum()
}

function replaceIn(values: Memory[], updated: Memory) {
  return values.map((item) => String(item.id) === String(updated.id) ? updated : item)
}

async function removeMemory(memory: Memory) {
  if (!window.confirm(`确定将“${memory.title}”和其中的媒体移入回收站吗？`)) return
  try {
    await api.deleteMemory(memory.id)
    await Promise.all([loadCurrentView(), loadTags()])
    show('这段回忆已移入回收站。', 'success')
  } catch (cause) {
    show(errorMessage(cause), 'error')
  }
}
</script>

<template>
  <div class="page-stack memories-page">
    <header class="page-header">
      <div><p class="eyebrow">OUR STORY ARCHIVE</p><h1>回忆收藏馆</h1><p>沿着时间、地点和照片，把共同走过的路重新连起来。</p></div>
      <button class="button primary" type="button" @click="openCreate"><Plus :size="18" />收藏回忆</button>
    </header>

    <div class="memory-view-switch" role="tablist" aria-label="回忆视图">
      <button v-for="option in viewOptions" :key="option.id" type="button" role="tab"
        :aria-selected="activeView === option.id" :class="{ active: activeView === option.id }"
        @click="activeView = option.id">
        <component :is="option.icon" :size="18" />{{ option.label }}
      </button>
    </div>

    <form class="filter-bar memory-filter" role="search" @submit.prevent="loadCurrentView">
      <label class="search-field"><Search :size="18" aria-hidden="true" /><span class="sr-only">搜索回忆</span><input v-model="filters.q" placeholder="搜索标题、文字或地点" /></label>
      <label class="tag-filter"><Tags :size="17" /><span class="sr-only">按标签筛选</span>
        <select v-model="filters.tag"><option value="">全部标签</option><option v-for="tag in availableTags" :key="tag.name" :value="tag.name">{{ tag.name }} · {{ tag.memoryCount }}</option></select>
      </label>
      <button class="button secondary small" type="submit">筛选</button>
    </form>

    <LoadingState v-if="loading" :label="activeView === 'album' ? '正在翻开相册…' : '正在沿着时间线往回走…'" />
    <div v-else-if="error" class="error-panel" role="alert"><p>{{ error }}</p><button class="button secondary" type="button" @click="loadCurrentView"><RefreshCw :size="17" />重新加载</button></div>
    <MemoryTimeline v-else-if="activeView === 'timeline'" :memories="memories"
      @create="openCreate" @edit="openEdit" @remove="removeMemory"
      @select-tag="filters.tag = $event" @open-media="mediaViewer = $event" @open-gallery="galleryMemory = $event" />
    <MemoryAlbum v-else :items="albumItems" @create="openCreate" @select-tag="filters.tag = $event" @open-media="mediaViewer = $event" />

    <div v-if="page + 1 < totalPages && !loading" class="load-more-row">
      <button class="button secondary" type="button" :disabled="loadingMore" @click="loadMore">
        <span v-if="loadingMore" class="button-spinner"></span><Plus v-else :size="17" />{{ loadingMore ? '正在翻找…' : (activeView === 'album' ? '加载更多影像' : '加载更早的回忆') }}
      </button>
    </div>
  </div>

  <MemoryEditorModal v-if="formOpen" :memory="editing" :available-tags="availableTags"
    @close="closeEditor" @saved="editorSaved" @updated="replaceMemory" />

  <BaseModal v-if="galleryMemory" :title="`${galleryMemory.title} 的全部媒体`" wide @close="galleryMemory = null">
    <div class="media-gallery">
      <template v-for="media in galleryMemory.media" :key="media.id">
        <img v-if="memoryMediaType(media) === 'image'" :src="memoryMediaUrl(media)" :alt="`${galleryMemory.title} 的照片`" />
        <video v-else-if="memoryMediaType(media) === 'video'" :src="memoryMediaUrl(media)" controls preload="metadata"></video>
        <audio v-else :src="memoryMediaUrl(media)" controls preload="none"></audio>
      </template>
    </div>
  </BaseModal>
  <BaseModal v-if="mediaViewer" :title="`查看${mediaViewer.title}的原媒体`" wide @close="mediaViewer = null">
    <div class="media-viewer">
      <img v-if="memoryMediaType(mediaViewer.media) === 'image'" :src="memoryMediaUrl(mediaViewer.media)" :alt="`${mediaViewer.title} 的原图`" />
      <video v-else-if="memoryMediaType(mediaViewer.media) === 'video'" :src="memoryMediaUrl(mediaViewer.media)" controls autoplay playsinline preload="metadata"></video>
      <audio v-else :src="memoryMediaUrl(mediaViewer.media)" controls autoplay preload="metadata"></audio>
      <p v-if="mediaViewer.media.originalName" class="media-viewer-name">{{ mediaViewer.media.originalName }}</p>
    </div>
  </BaseModal>
</template>
