<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import * as L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import {
  CalendarDays, Camera, Check, FileAudio, FileVideo, Heart as HeartFill, Image,
  Images, LocateFixed, Map as MapIcon, MapPin, Pencil, Plus, RefreshCw, Search, Tags,
  Trash2, UploadCloud, X,
} from 'lucide-vue-next'
import { api } from '../api'
import { errorMessage, mediaUrl, unwrapList } from '../api/client'
import { useToast } from '../composables/toast'
import BaseAvatar from '../components/BaseAvatar.vue'
import BaseModal from '../components/BaseModal.vue'
import EmptyState from '../components/EmptyState.vue'
import LoadingState from '../components/LoadingState.vue'
import type { AlbumItem, MediaItem, Memory, MemoryTag, UserProfile } from '../types'
import { formatDate, toLocalDateTimeInput } from '../utils'

type MemoryViewMode = 'timeline' | 'map' | 'album'

const { show } = useToast()
const activeView = ref<MemoryViewMode>('timeline')
const memories = ref<Memory[]>([])
const mapMemories = ref<Memory[]>([])
const albumItems = ref<AlbumItem[]>([])
const availableTags = ref<MemoryTag[]>([])
const selectedMapMemory = ref<Memory | null>(null)
const loading = ref(true)
const loadingMore = ref(false)
const saving = ref(false)
const error = ref('')
const page = ref(0)
const totalPages = ref(1)
const formOpen = ref(false)
const editing = ref<Memory | null>(null)
const galleryMemory = ref<Memory | null>(null)
const mediaViewer = ref<{ title: string; media: MediaItem } | null>(null)
const selectedFiles = ref<File[]>([])
const fileInput = ref<HTMLInputElement | null>(null)
const mapElement = ref<HTMLElement | null>(null)
const formMapElement = ref<HTMLElement | null>(null)
const tagInput = ref('')
const filters = reactive({ q: '', tag: '' })
const form = reactive({
  title: '',
  description: '',
  eventAt: toLocalDateTimeInput(),
  location: '',
  latitude: null as number | null,
  longitude: null as number | null,
  tags: [] as string[],
})

let storyMap: L.Map | null = null
let storyLayer: L.LayerGroup | null = null
let pickerMap: L.Map | null = null
let pickerMarker: L.Marker | null = null

const grouped = computed(() => {
  const groups = new Map<string, Memory[]>()
  memories.value.forEach((memory) => {
    const key = formatDate(memory.eventAt, { year: 'numeric', month: 'long' })
    groups.set(key, [...(groups.get(key) || []), memory])
  })
  return [...groups.entries()]
})

const viewOptions = [
  { id: 'timeline' as const, label: '时间线', icon: CalendarDays },
  { id: 'map' as const, label: '回忆地图', icon: MapIcon },
  { id: 'album' as const, label: '我们的相册', icon: Images },
]

onMounted(async () => {
  await Promise.all([load(), loadTags()])
})

onBeforeUnmount(destroyMaps)

watch(activeView, (next, previous) => {
  if (previous === 'map' && next !== 'map') destroyStoryMap()
  loadCurrentView()
})
watch(() => filters.tag, () => loadCurrentView())
watch(formOpen, async (open) => {
  if (!open) {
    pickerMap?.remove()
    pickerMap = null
    pickerMarker = null
    return
  }
  await nextTick()
  initPickerMap()
})
watch([() => form.latitude, () => form.longitude], updatePickerMarker)

async function loadCurrentView() {
  if (activeView.value === 'timeline') await load()
  if (activeView.value === 'map') await loadMap()
  if (activeView.value === 'album') await loadAlbum()
}

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

async function loadMap() {
  destroyStoryMap()
  loading.value = true
  error.value = ''
  try {
    mapMemories.value = await api.memoryMap(filters.tag)
    selectedMapMemory.value = mapMemories.value[0] || null
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
  if (!error.value && mapMemories.value.length) {
    await nextTick()
    renderStoryMap()
  }
}

async function loadAlbum(targetPage = 0, append = false) {
  loading.value = !append
  loadingMore.value = append
  error.value = ''
  try {
    const query = new URLSearchParams({ page: String(targetPage), size: '30' })
    if (filters.q.trim()) query.set('q', filters.q.trim())
    if (filters.tag) query.set('tag', filters.tag)
    const payload = await api.memoryAlbum(query.toString())
    albumItems.value = append ? [...albumItems.value, ...payload.content] : payload.content
    page.value = payload.page ?? payload.number ?? targetPage
    totalPages.value = payload.totalPages ?? 1
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

async function loadTags() {
  try {
    availableTags.value = await api.memoryTags()
  } catch {
    availableTags.value = []
  }
}

async function loadMore() {
  if (loadingMore.value || page.value + 1 >= totalPages.value) return
  if (activeView.value === 'album') {
    await loadAlbum(page.value + 1, true)
    return
  }
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
  if (filters.tag) query.set('tag', filters.tag)
  return query.toString()
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
  Object.assign(form, {
    title: '', description: '', eventAt: toLocalDateTimeInput(), location: '',
    latitude: null, longitude: null, tags: [],
  })
  selectedFiles.value = []
  tagInput.value = ''
  formOpen.value = true
}

function openEdit(memory: Memory) {
  editing.value = memory
  Object.assign(form, {
    title: memory.title,
    description: memory.description || '',
    eventAt: toLocalDateTimeInput(memory.eventAt),
    location: memory.location || '',
    latitude: memory.latitude ?? null,
    longitude: memory.longitude ?? null,
    tags: [...(memory.tags || [])],
  })
  selectedFiles.value = []
  tagInput.value = ''
  formOpen.value = true
}

function chooseFiles(event: Event) {
  const files = Array.from((event.target as HTMLInputElement).files || [])
  const allowed = files.filter((file) => /^(image|video|audio)\//.test(file.type))
  if (allowed.length !== files.length) show('已忽略不支持的文件，只能上传图片、视频和音频。', 'info')
  const remaining = Math.max(0, 20 - mediaItems(editing.value).length - selectedFiles.value.length)
  selectedFiles.value.push(...allowed.slice(0, remaining))
  if (allowed.length > remaining) show('每段回忆最多保存 20 个媒体文件。', 'info')
  if (fileInput.value) fileInput.value.value = ''
}

function addTag(value = tagInput.value) {
  const normalized = value.trim().replace(/\s+/g, ' ')
  if (!normalized || form.tags.some((tag) => tag.toLowerCase() === normalized.toLowerCase())) {
    tagInput.value = ''
    return
  }
  if (form.tags.length >= 12) {
    show('每段回忆最多添加 12 个标签。', 'info')
    return
  }
  form.tags.push(normalized.slice(0, 30))
  tagInput.value = ''
}

async function save() {
  if (tagInput.value.trim()) addTag()
  saving.value = true
  try {
    const payload = {
      title: form.title.trim(),
      description: form.description.trim(),
      eventAt: form.eventAt,
      location: form.location.trim(),
      latitude: form.latitude,
      longitude: form.longitude,
      tags: form.tags,
    }
    if (editing.value) {
      await api.updateMemory(editing.value.id, payload)
      if (selectedFiles.value.length) await api.addMemoryMedia(editing.value.id, selectedFiles.value)
      show('这段回忆已经更新。', 'success')
    } else {
      await api.createMemory(payload, selectedFiles.value)
      show('新的回忆已经收藏好。', 'success')
    }
    formOpen.value = false
    await Promise.all([loadCurrentView(), loadTags()])
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    saving.value = false
  }
}

async function remove(memory: Memory) {
  if (!window.confirm(`确定将“${memory.title}”和其中的媒体移入回收站吗？`)) return
  try {
    await api.deleteMemory(memory.id)
    await Promise.all([loadCurrentView(), loadTags()])
    show('这段回忆已移入回收站。', 'success')
  } catch (cause) {
    show(errorMessage(cause), 'error')
  }
}

async function removeMedia(memory: Memory, item: MediaItem) {
  if (!item.id || !window.confirm(`确定删除“${item.originalName || '这个媒体'}”吗？此操作不会进入回收站。`)) return
  try {
    const updated = await api.deleteMemoryMedia(memory.id, item.id)
    editing.value = updated
    const index = memories.value.findIndex((value) => String(value.id) === String(updated.id))
    if (index >= 0) memories.value[index] = updated
    show('媒体已删除。', 'success')
  } catch (cause) {
    show(errorMessage(cause), 'error')
  }
}

function mediaItems(memory: Memory | null) {
  return memory ? [...(memory.media || []), ...(memory.files || [])] : []
}
function itemType(item: MediaItem) {
  const type = (item.contentType || item.mediaType || item.type || '').toLowerCase()
  if (type.includes('video')) return 'video'
  if (type.includes('audio')) return 'audio'
  return 'image'
}
function itemUrl(item: MediaItem) { return mediaUrl(item.id || item.mediaId, item.url) }
function openMediaViewer(title: string, media: MediaItem) { mediaViewer.value = { title, media } }
function authorOf(memory: Memory): UserProfile | undefined {
  if (memory.author) return memory.author
  return memory.authorNickname ? { id: memory.authorId ?? '', nickname: memory.authorNickname } : undefined
}
function fileIcon(file: File) { return file.type.startsWith('video/') ? FileVideo : file.type.startsWith('audio/') ? FileAudio : Camera }
function formatBytes(bytes: number) { return bytes < 1048576 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1048576).toFixed(1)} MB` }

function tileLayer() {
  return L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; OpenStreetMap contributors',
  })
}

function memoryIcon() {
  return L.divIcon({
    className: 'memory-map-marker-wrap',
    html: '<span class="memory-map-marker">♥</span>',
    iconSize: [34, 34],
    iconAnchor: [17, 30],
  })
}

function renderStoryMap() {
  if (!mapElement.value) return
  if (!storyMap) {
    storyMap = L.map(mapElement.value, { zoomControl: true }).setView([35.86, 104.19], 4)
    tileLayer().addTo(storyMap)
    storyLayer = L.layerGroup().addTo(storyMap)
  }
  storyLayer?.clearLayers()
  const bounds: L.LatLngExpression[] = []
  mapMemories.value.forEach((memory) => {
    if (memory.latitude == null || memory.longitude == null) return
    const point: L.LatLngExpression = [memory.latitude, memory.longitude]
    bounds.push(point)
    const marker = L.marker(point, { icon: memoryIcon(), title: memory.title })
    marker.on('click', () => { selectedMapMemory.value = memory })
    marker.bindTooltip(memory.title, { direction: 'top', offset: [0, -24] })
    marker.addTo(storyLayer!)
  })
  if (bounds.length) storyMap.fitBounds(L.latLngBounds(bounds), { padding: [45, 45], maxZoom: 14 })
  setTimeout(() => storyMap?.invalidateSize(), 0)
}

function initPickerMap() {
  if (!formMapElement.value || pickerMap) return
  const center: L.LatLngExpression = form.latitude != null && form.longitude != null
    ? [form.latitude, form.longitude]
    : [35.86, 104.19]
  pickerMap = L.map(formMapElement.value).setView(center, form.latitude == null ? 4 : 13)
  tileLayer().addTo(pickerMap)
  pickerMap.on('click', (event: L.LeafletMouseEvent) => setCoordinates(event.latlng.lat, event.latlng.lng))
  updatePickerMarker()
  setTimeout(() => pickerMap?.invalidateSize(), 0)
}

function updatePickerMarker() {
  if (!pickerMap) return
  if (form.latitude == null || form.longitude == null) {
    pickerMarker?.remove()
    pickerMarker = null
    return
  }
  const point: L.LatLngExpression = [form.latitude, form.longitude]
  if (!pickerMarker) pickerMarker = L.marker(point, { icon: memoryIcon() }).addTo(pickerMap)
  else pickerMarker.setLatLng(point)
}

function setCoordinates(latitude: number, longitude: number) {
  form.latitude = Number(latitude.toFixed(6))
  form.longitude = Number(longitude.toFixed(6))
}

function locateMe() {
  if (!navigator.geolocation) {
    show('当前设备不支持定位。', 'error')
    return
  }
  navigator.geolocation.getCurrentPosition(
    ({ coords }) => {
      setCoordinates(coords.latitude, coords.longitude)
      pickerMap?.setView([coords.latitude, coords.longitude], 15)
    },
    () => show('未能取得当前位置，请检查浏览器定位权限。', 'error'),
    { enableHighAccuracy: true, timeout: 10000 },
  )
}

function clearCoordinates() {
  form.latitude = null
  form.longitude = null
}

function focusMapMemory(memory: Memory) {
  selectedMapMemory.value = memory
  if (memory.latitude != null && memory.longitude != null) storyMap?.flyTo([memory.latitude, memory.longitude], 14)
}

function destroyMaps() {
  destroyStoryMap()
  pickerMap?.remove()
  pickerMap = null
  pickerMarker = null
}

function destroyStoryMap() {
  storyMap?.remove()
  storyMap = null
  storyLayer = null
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
      <label class="search-field"><Search :size="18" aria-hidden="true" /><span class="sr-only">搜索回忆</span><input v-model="filters.q" placeholder="搜索标题、文字或地点" :disabled="activeView === 'map'" /></label>
      <label class="tag-filter"><Tags :size="17" /><span class="sr-only">按标签筛选</span>
        <select v-model="filters.tag"><option value="">全部标签</option><option v-for="tag in availableTags" :key="tag.name" :value="tag.name">{{ tag.name }} · {{ tag.memoryCount }}</option></select>
      </label>
      <button v-if="activeView !== 'map'" class="button secondary small" type="submit">筛选</button>
    </form>

    <LoadingState v-if="loading" :label="activeView === 'map' ? '正在铺开回忆地图…' : activeView === 'album' ? '正在翻开相册…' : '正在沿着时间线往回走…'" />
    <div v-else-if="error" class="error-panel" role="alert"><p>{{ error }}</p><button class="button secondary" type="button" @click="loadCurrentView"><RefreshCw :size="17" />重新加载</button></div>

    <template v-else-if="activeView === 'timeline'">
      <EmptyState v-if="!memories.length" title="时间线还是空白的" description="从今天开始，收藏第一张照片、第一段声音，或第一句舍不得忘的话。"><button class="button primary small" type="button" @click="openCreate"><Plus :size="17" />收藏第一条</button></EmptyState>
      <div v-else class="timeline">
        <section v-for="([month, items], groupIndex) in grouped" :key="month" class="timeline-group">
          <div class="timeline-month"><span>{{ month }}</span><i></i></div>
          <article v-for="memory in items" :key="memory.id" class="memory-card" :class="{ featured: groupIndex === 0 }">
            <div class="memory-dot" aria-hidden="true"><HeartFill /></div>
            <div class="memory-card-inner">
              <div v-if="mediaItems(memory).length" class="memory-media" :class="{ gallery: mediaItems(memory).length > 1 }">
                <template v-for="media in mediaItems(memory).slice(0, 4)" :key="media.id || media.mediaId">
                  <button v-if="itemType(media) === 'image'" class="media-trigger" type="button" @click="openMediaViewer(memory.title, media)"><img :src="itemUrl(media)" :alt="`${memory.title} 的照片`" loading="lazy" draggable="false" /></button>
                  <button v-else-if="itemType(media) === 'video'" class="media-trigger" type="button" @click="openMediaViewer(memory.title, media)"><video :src="itemUrl(media)" preload="metadata" playsinline aria-hidden="true"></video><span class="media-view-hint" aria-hidden="true">点击查看原视频</span></button>
                  <button v-else class="audio-tile media-trigger" type="button" @click="openMediaViewer(memory.title, media)"><FileAudio :size="25" /><span>{{ media.originalName || '一段声音' }}</span><small>点击查看原音频</small></button>
                </template>
                <button v-if="mediaItems(memory).length > 4" class="more-media" type="button" @click="galleryMemory = memory">+{{ mediaItems(memory).length - 4 }}</button>
              </div>
              <div class="memory-content">
                <div class="memory-meta"><span><CalendarDays :size="14" />{{ formatDate(memory.eventAt, { year: 'numeric', month: 'short', day: 'numeric', weekday: 'short' }) }}</span><span v-if="memory.location"><MapPin :size="14" />{{ memory.location }}</span></div>
                <div class="memory-title-row"><div><h2>{{ memory.title }}</h2><span v-if="authorOf(memory)" class="author-chip"><BaseAvatar :user="authorOf(memory)" size="sm" />{{ authorOf(memory)?.nickname }} 收藏</span></div><div class="card-actions"><button class="icon-button" type="button" aria-label="编辑回忆" @click="openEdit(memory)"><Pencil :size="17" /></button><button class="icon-button danger" type="button" aria-label="删除回忆" @click="remove(memory)"><Trash2 :size="17" /></button></div></div>
                <div v-if="memory.tags?.length" class="tag-row"><button v-for="tag in memory.tags" :key="tag" type="button" @click="filters.tag = tag"># {{ tag }}</button></div>
                <p v-if="memory.description" class="memory-description">{{ memory.description }}</p>
              </div>
            </div>
          </article>
        </section>
      </div>
    </template>

    <template v-else-if="activeView === 'map'">
      <EmptyState v-if="!mapMemories.length" title="地图上还没有足迹" description="编辑或新建回忆，在地图上点选发生地点，就能把故事钉在共同走过的路上。"><button class="button primary small" type="button" @click="openCreate"><MapPin :size="17" />添加第一个地点</button></EmptyState>
      <div v-else class="memory-map-layout">
        <div ref="mapElement" class="story-map" aria-label="回忆地图"></div>
        <aside class="map-memory-list">
          <p><MapPin :size="16" />{{ mapMemories.length }} 个共同足迹</p>
          <button v-for="memory in mapMemories" :key="memory.id" type="button" :class="{ active: selectedMapMemory?.id === memory.id }" @click="focusMapMemory(memory)">
            <strong>{{ memory.title }}</strong><span>{{ memory.location || '地图坐标' }} · {{ formatDate(memory.eventAt, { year: 'numeric', month: 'short', day: 'numeric' }) }}</span>
          </button>
        </aside>
        <article v-if="selectedMapMemory" class="map-memory-card card">
          <div>
            <p class="eyebrow">SELECTED MEMORY</p><h2>{{ selectedMapMemory.title }}</h2>
            <p>{{ selectedMapMemory.description || '这一天值得被地图记住。' }}</p>
            <div class="tag-row"><span v-for="tag in selectedMapMemory.tags" :key="tag"># {{ tag }}</span></div>
          </div>
          <button class="button secondary small" type="button" @click="openEdit(selectedMapMemory)"><Pencil :size="16" />编辑回忆</button>
        </article>
      </div>
    </template>

    <template v-else>
      <EmptyState v-if="!albumItems.length" title="相册还没有影像" description="回忆中的照片和视频会自动汇集到这里，声音仍保留在时间线中。"><button class="button primary small" type="button" @click="openCreate"><Image :size="17" />加入第一段影像</button></EmptyState>
      <div v-else class="album-grid">
        <article v-for="item in albumItems" :key="item.media.id" class="album-tile">
          <button type="button" :aria-label="`查看${item.memoryTitle}的${itemType(item.media) === 'video' ? '视频' : '照片'}`" @click="openMediaViewer(item.memoryTitle, item.media)">
            <img v-if="itemType(item.media) === 'image'" :src="itemUrl(item.media)" :alt="`${item.memoryTitle} 的照片`" loading="lazy" />
            <video v-else :src="itemUrl(item.media)" preload="metadata" muted playsinline aria-hidden="true"></video>
            <span v-if="itemType(item.media) === 'video'" class="album-video-badge" aria-hidden="true"><FileVideo :size="15" />视频</span>
          </button>
          <div><strong>{{ item.memoryTitle }}</strong><span>{{ formatDate(item.eventAt, { year: 'numeric', month: 'short', day: 'numeric' }) }}<template v-if="item.location"> · {{ item.location }}</template></span><p v-if="item.tags.length"><button v-for="tag in item.tags" :key="tag" type="button" @click="filters.tag = tag">#{{ tag }}</button></p></div>
        </article>
      </div>
    </template>

    <div v-if="activeView !== 'map' && page + 1 < totalPages && !loading" class="load-more-row">
      <button class="button secondary" type="button" :disabled="loadingMore" @click="loadMore">
        <span v-if="loadingMore" class="button-spinner"></span><Plus v-else :size="17" />{{ loadingMore ? '正在翻找…' : (activeView === 'album' ? '加载更多影像' : '加载更早的回忆') }}
      </button>
    </div>
  </div>

  <BaseModal v-if="formOpen" :title="editing ? '编辑这段回忆' : '收藏一段新回忆'" description="照片、地点和标签会一起同步给彼此。" wide @close="formOpen = false">
    <form class="stack-form" @submit.prevent="save">
      <div class="form-two">
        <label class="field"><span>回忆标题</span><input v-model="form.title" required maxlength="120" placeholder="例如：海边吹风的那个下午" /></label>
        <label class="field"><span>发生时间</span><input v-model="form.eventAt" required type="datetime-local" /></label>
      </div>
      <label class="field"><span>地点名称（可选）</span><span class="input-with-icon"><MapPin :size="17" /><input v-model="form.location" maxlength="200" placeholder="例如：厦门 · 环岛路" /></span></label>
      <div class="location-picker">
        <div class="location-picker-head"><div><strong>地图位置（可选）</strong><small>点击地图选择精确位置，仅在情侣空间内可见</small></div><div><button class="text-button" type="button" @click="locateMe"><LocateFixed :size="16" />定位到我</button><button v-if="form.latitude != null" class="text-button muted" type="button" @click="clearCoordinates"><X :size="16" />清除</button></div></div>
        <div ref="formMapElement" class="picker-map"></div>
        <p v-if="form.latitude != null && form.longitude != null"><Check :size="15" />已选择 {{ form.latitude.toFixed(6) }}, {{ form.longitude.toFixed(6) }}</p>
      </div>
      <label class="field"><span>想记住的话（可选）</span><textarea v-model="form.description" maxlength="10000" rows="4" placeholder="那天发生了什么？当时是什么心情？"></textarea><small>{{ form.description.length }}/10000</small></label>
      <div class="tag-editor">
        <label class="field"><span>回忆标签（最多 12 个）</span><div class="tag-input"><Tags :size="17" /><input v-model="tagInput" maxlength="30" placeholder="旅行、约会、美食…" @keydown.enter.prevent="addTag()" @keydown.,.prevent="addTag()" /><button type="button" @click="addTag()">添加</button></div></label>
        <div v-if="form.tags.length" class="tag-row editable"><button v-for="(tag, index) in form.tags" :key="tag" type="button" @click="form.tags.splice(index, 1)"># {{ tag }} <X :size="12" /></button></div>
        <div v-if="availableTags.length" class="known-tags"><span>常用：</span><button v-for="tag in availableTags.slice(0, 8)" :key="tag.name" type="button" @click="addTag(tag.name)">{{ tag.name }}</button></div>
      </div>
      <div class="upload-field">
        <input ref="fileInput" class="sr-only" type="file" multiple accept="image/*,video/*,audio/*" @change="chooseFiles" />
        <button class="upload-drop compact" type="button" @click="fileInput?.click()"><span><UploadCloud :size="22" /></span><strong>{{ editing ? '继续添加媒体' : '选择照片、视频或声音' }}</strong><small>每段回忆最多 20 个文件</small></button>
        <div v-if="editing && mediaItems(editing).length" class="edit-media-list">
          <div v-for="media in mediaItems(editing)" :key="media.id || media.mediaId"><img v-if="itemType(media) === 'image'" :src="itemUrl(media)" alt="" /><component :is="itemType(media) === 'video' ? FileVideo : FileAudio" v-else :size="22" /><span>{{ media.originalName }}</span><button type="button" aria-label="删除媒体" @click="removeMedia(editing!, media)"><Trash2 :size="15" /></button></div>
        </div>
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
        <video v-else-if="itemType(media) === 'video'" :src="itemUrl(media)" controls preload="metadata"></video>
        <audio v-else :src="itemUrl(media)" controls preload="none"></audio>
      </template>
    </div>
  </BaseModal>
  <BaseModal v-if="mediaViewer" :title="`查看${mediaViewer.title}的原媒体`" wide @close="mediaViewer = null">
    <div class="media-viewer">
      <img v-if="itemType(mediaViewer.media) === 'image'" :src="itemUrl(mediaViewer.media)" :alt="`${mediaViewer.title} 的原图`" />
      <video v-else-if="itemType(mediaViewer.media) === 'video'" :src="itemUrl(mediaViewer.media)" controls autoplay playsinline preload="metadata"></video>
      <audio v-else :src="itemUrl(mediaViewer.media)" controls autoplay preload="metadata"></audio>
      <p v-if="mediaViewer.media.originalName" class="media-viewer-name">{{ mediaViewer.media.originalName }}</p>
    </div>
  </BaseModal>
</template>
