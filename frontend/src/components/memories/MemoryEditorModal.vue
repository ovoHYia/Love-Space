<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { Camera, FileAudio, FileVideo, Images, MapPin, Tags, Trash2, UploadCloud, X } from 'lucide-vue-next'
import { api } from '../../api'
import type { MemoryInput, MemoryUpdateInput } from '../../api'
import { ApiError, errorMessage } from '../../api/client'
import { useToast } from '../../composables/toast'
import type { MediaItem, Memory, MemoryTag } from '../../types'
import { toBeijingOffsetDateTime, toLocalDateTimeInput } from '../../utils'
import { memoryMediaType, memoryMediaUrl, validateUploadFile } from '../../utils/memoryMedia'
import { isStaleUpdate, STALE_UPDATE_MESSAGE } from '../../utils/editConflict'
import { authState } from '../../stores/auth'
import {
  areMemorySnapshotsEqual,
  createMemoryDraft,
  createMemoryEditorSnapshot,
  memoryDraftKey,
  parseMemoryDraft,
  serializeMemoryDraft,
  type MemoryDraft,
  type MemoryEditorSnapshot,
  type MemoryFileSnapshot,
} from '../../utils/memoryDraft'
import BaseModal from '../BaseModal.vue'

const props = defineProps<{ memory: Memory | null; availableTags: MemoryTag[] }>()
const emit = defineEmits<{
  close: []
  saved: []
  updated: [memory: Memory]
}>()
const { show } = useToast()
const currentMemory = ref<Memory | null>(props.memory)
const conflict = ref(false)
const saving = ref(false)
const deletingMediaId = ref<number | string | null>(null)
const fieldErrors = ref<Record<string, string>>({})
const selectedFiles = ref<File[]>([])
const fileInput = ref<HTMLInputElement | null>(null)
const tagInput = ref('')
const draftCandidate = ref<MemoryDraft | null>(null)
const draftMediaMetadata = ref<MemoryFileSnapshot[]>([])
const confirmationContinueButton = ref<HTMLButtonElement | null>(null)
const draftRestoreButton = ref<HTMLButtonElement | null>(null)
const confirmationTrigger = ref<HTMLElement | null>(null)
const confirmation = ref<ConfirmationState | null>(null)
const pendingRouteDecisions: Array<(allowed: boolean) => void> = []
let draftSaveTimer: number | null = null

type CloseRequestSource = 'escape' | 'backdrop' | 'button' | 'route'
type ConfirmationState =
  | { kind: 'discard' }
  | { kind: 'delete-media'; media: MediaItem }

const editing = computed(() => currentMemory.value !== null)
const busy = computed(() => saving.value || deletingMediaId.value !== null)
const draftStorageKey = memoryDraftKey(authState.user?.id, props.memory?.id)
const initialEventAt = props.memory?.eventAt ? toLocalDateTimeInput(props.memory.eventAt) : toLocalDateTimeInput()
const initialEventTimeKnown = props.memory?.eventTimeKnown !== false
const eventDate = ref(initialEventAt.slice(0, 10))
const eventTime = ref(initialEventTimeKnown ? initialEventAt.slice(11, 16) : '')
const form = reactive<MemoryInput>({
  title: props.memory?.title || '',
  description: props.memory?.description || '',
  eventAt: initialEventAt,
  eventTimeKnown: initialEventTimeKnown,
  location: props.memory?.location || '',
  tags: [...(props.memory?.tags || [])],
})

function syncEventAt() {
  form.eventAt = eventDate.value ? `${eventDate.value}T${eventTime.value || '00:00'}` : ''
  form.eventTimeKnown = Boolean(eventTime.value)
}

function setEventAt(value: string, timeKnown = true) {
  if (!value) {
    eventDate.value = ''
    eventTime.value = ''
    syncEventAt()
    return
  }
  const local = toLocalDateTimeInput(value)
  eventDate.value = local.slice(0, 10)
  eventTime.value = timeKnown ? local.slice(11, 16) : ''
  syncEventAt()
}

function fileSnapshot(file: File): MemoryFileSnapshot {
  return { name: file.name, size: file.size, type: file.type }
}

function selectedFileSnapshots() {
  return selectedFiles.value.map(fileSnapshot)
}

function pendingMediaSnapshots() {
  return [...draftMediaMetadata.value, ...selectedFileSnapshots()]
}

function currentSnapshot(): MemoryEditorSnapshot {
  syncEventAt()
  return createMemoryEditorSnapshot(form, tagInput.value, pendingMediaSnapshots())
}

const initialSnapshot = ref<MemoryEditorSnapshot>(currentSnapshot())
const isDirty = computed(() => !areMemorySnapshotsEqual(currentSnapshot(), initialSnapshot.value))
const draftUpdatedLabel = computed(() => draftCandidate.value
  ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(draftCandidate.value.updatedAt))
  : '')

function chooseFiles(event: Event) {
  const files = Array.from((event.target as HTMLInputElement).files || [])
  const allowed: File[] = []
  for (const file of files) {
    const problem = validateUploadFile(file)
    if (problem) show(problem, 'info')
    else allowed.push(file)
  }
  // 草稿里的媒体只是恢复提示，不是服务器上的真实媒体，不能占用 20 个名额。
  const remaining = Math.max(0, 20 - (currentMemory.value?.media.length || 0) - selectedFiles.value.length)
  const picked = allowed.slice(0, remaining)
  selectedFiles.value.push(...picked)
  if (picked.length) {
    draftMediaMetadata.value = draftMediaMetadata.value.filter((metadata) =>
      !picked.some((file) => file.name === metadata.name && file.size === metadata.size && file.type === metadata.type))
    scheduleDraftSave()
  }
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

// 中文输入法组合期间 keydown 的 key 是 'Process'，全角逗号是 '，'；
// 统一在这里判断，避免组合中误触发、且支持中英文两种逗号。
function handleTagKeydown(event: KeyboardEvent) {
  if (event.isComposing) return
  if (event.key === 'Enter' || event.key === ',' || event.key === '，') {
    event.preventDefault()
    addTag()
  }
}

function clearDraftSaveTimer() {
  if (draftSaveTimer !== null) {
    window.clearTimeout(draftSaveTimer)
    draftSaveTimer = null
  }
}

function removeDraftStorage() {
  try {
    window.sessionStorage.removeItem(draftStorageKey)
  } catch {
    // sessionStorage 受限时安全降级，不阻断表单。
  }
}

function clearDraft() {
  clearDraftSaveTimer()
  removeDraftStorage()
  draftCandidate.value = null
  draftMediaMetadata.value = []
}

function clearDraftMediaMetadata() {
  draftMediaMetadata.value = []
  scheduleDraftSave()
}

function readDraft() {
  try {
    const raw = window.sessionStorage.getItem(draftStorageKey)
    if (!raw) return null
    const parsed = parseMemoryDraft(raw)
    if (!parsed) {
      removeDraftStorage()
      return null
    }
    return parsed
  } catch {
    removeDraftStorage()
    return null
  }
}

function saveDraft() {
  draftSaveTimer = null
  if (!isDirty.value) {
    if (!draftCandidate.value) removeDraftStorage()
    return
  }
  try {
    const draft = createMemoryDraft(form, tagInput.value, pendingMediaSnapshots())
    window.sessionStorage.setItem(draftStorageKey, serializeMemoryDraft(draft))
  } catch {
    // 存储空间或隐私设置不允许时，继续保留当前内存中的编辑内容。
  }
}

function scheduleDraftSave() {
  clearDraftSaveTimer()
  draftSaveTimer = window.setTimeout(saveDraft, 500)
}

function restoreDraft() {
  const candidate = draftCandidate.value
  if (!candidate) return
  form.title = candidate.form.title
  form.description = candidate.form.description
  setEventAt(candidate.form.eventAt, candidate.form.eventTimeKnown)
  form.location = candidate.form.location
  form.tags.splice(0, form.tags.length, ...candidate.form.tags)
  tagInput.value = candidate.form.tagInput
  draftMediaMetadata.value = [...candidate.pendingMedia]
  draftCandidate.value = null
  void nextTick(() => document.getElementById('memory-title')?.focus())
}

function ignoreDraft() {
  clearDraft()
  void nextTick(() => document.getElementById('memory-title')?.focus())
}

function resolveRouteDecisions(allowed: boolean) {
  const decisions = pendingRouteDecisions.splice(0)
  decisions.forEach((resolve) => resolve(allowed))
}

function waitForRouteDecision() {
  return new Promise<boolean>((resolve) => pendingRouteDecisions.push(resolve))
}

function openConfirmation(state: ConfirmationState) {
  confirmationTrigger.value = document.activeElement instanceof HTMLElement ? document.activeElement : null
  confirmation.value = state
  void nextTick(() => confirmationContinueButton.value?.focus())
}

function focusAfterConfirmation() {
  const target = confirmationTrigger.value
  confirmationTrigger.value = null
  void nextTick(() => {
    if (target?.isConnected) {
      target.focus()
      return
    }
    document.getElementById('memory-title')?.focus()
  })
}

function continueEditing() {
  if (!confirmation.value || busy.value) return
  const wasDiscardConfirmation = confirmation.value.kind === 'discard'
  confirmation.value = null
  if (wasDiscardConfirmation) resolveRouteDecisions(false)
  focusAfterConfirmation()
}

function discardChanges() {
  if (!confirmation.value || confirmation.value.kind !== 'discard' || busy.value) return
  confirmation.value = null
  clearDraft()
  resolveRouteDecisions(true)
  emit('close')
}

function requestClose(source: CloseRequestSource = 'button'): Promise<boolean> {
  if (authState.forcedLogoutReason) {
    clearDraftSaveTimer()
    saveDraft()
    resolveRouteDecisions(true)
    return Promise.resolve(true)
  }
  if (busy.value) return Promise.resolve(false)
  if (confirmation.value) {
    if (source === 'route' && confirmation.value.kind === 'discard') return waitForRouteDecision()
    if (source !== 'route') continueEditing()
    return Promise.resolve(false)
  }
  if (!isDirty.value) {
    if (!draftCandidate.value) clearDraft()
    emit('close')
    return Promise.resolve(true)
  }
  openConfirmation({ kind: 'discard' })
  return source === 'route' ? waitForRouteDecision() : Promise.resolve(false)
}

async function save() {
  if (busy.value) return
  syncEventAt()
  if (!eventDate.value) {
    fieldErrors.value = { eventAt: '请选择发生日期' }
    show('请先选择发生日期。', 'info')
    return
  }
  if (tagInput.value.trim()) addTag()
  if (tagInput.value.trim()) {
    show('标签已经达到上限，请先删除一个标签或清空输入。', 'info')
    return
  }
  saving.value = true
  fieldErrors.value = {}
  const payload: MemoryInput = {
    title: form.title.trim(),
    description: form.description.trim(),
    eventAt: toBeijingOffsetDateTime(form.eventAt),
    eventTimeKnown: Boolean(eventTime.value),
    location: form.location.trim(),
    tags: form.tags.map((tag) => tag.trim()),
  }
  form.tags.splice(0, form.tags.length, ...payload.tags)
  try {
    if (currentMemory.value) {
      const update: MemoryUpdateInput = { ...payload, version: currentMemory.value.version! }
      const updated = await api.updateMemory(currentMemory.value.id, update, selectedFiles.value)
      currentMemory.value = updated
      selectedFiles.value = []
      draftMediaMetadata.value = []
      initialSnapshot.value = currentSnapshot()
      clearDraft()
      emit('updated', updated)
      show('这段回忆已经更新。', 'success')
    } else {
      await api.createMemory(payload, selectedFiles.value)
      selectedFiles.value = []
      draftMediaMetadata.value = []
      initialSnapshot.value = currentSnapshot()
      clearDraft()
      show('新的回忆已经收藏好。', 'success')
    }
    emit('saved')
  } catch (cause) {
    if (isStaleUpdate(cause)) {
      conflict.value = true
      show(STALE_UPDATE_MESSAGE, 'error')
      return
    }
    fieldErrors.value = cause instanceof ApiError ? cause.fieldErrors || {} : {}
    show(errorMessage(cause), 'error')
  } finally {
    saving.value = false
  }
}

async function loadLatest() {
  if (!currentMemory.value) return
  try {
    const latest = await api.memory(currentMemory.value.id)
    currentMemory.value = latest
    form.title = latest.title
    form.description = latest.description || ''
    setEventAt(latest.eventAt, latest.eventTimeKnown)
    form.location = latest.location || ''
    form.tags.splice(0, form.tags.length, ...(latest.tags || []))
    conflict.value = false
    show('已加载最新内容，请确认后再保存。', 'info')
  } catch (cause) {
    show(errorMessage(cause), 'error')
  }
}

function requestDeleteMedia(item: MediaItem) {
  if (!currentMemory.value || busy.value) return
  openConfirmation({ kind: 'delete-media', media: item })
}

async function confirmDeleteMedia() {
  const state = confirmation.value
  if (!state || state.kind !== 'delete-media' || !currentMemory.value || busy.value) return
  deletingMediaId.value = state.media.id
  try {
    const updated = await api.deleteMemoryMedia(currentMemory.value.id, state.media.id)
    currentMemory.value = updated
    emit('updated', updated)
    show('媒体已删除。', 'success')
    confirmation.value = null
    confirmationTrigger.value = null
    void nextTick(() => document.getElementById('memory-title')?.focus())
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    deletingMediaId.value = null
  }
}

function fileIcon(file: File) {
  return file.type.startsWith('video/') ? FileVideo : file.type.startsWith('audio/') ? FileAudio : Camera
}

function formatBytes(bytes: number) {
  return bytes < 1048576 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1048576).toFixed(1)} MB`
}

function handleBeforeUnload(event: BeforeUnloadEvent) {
  if (!isDirty.value && !busy.value) return
  event.preventDefault()
  event.returnValue = ''
}

watch(
  () => ({
    title: form.title,
    description: form.description,
    eventAt: form.eventAt,
    location: form.location,
    tags: [...form.tags],
    tagInput: tagInput.value,
    pendingMedia: pendingMediaSnapshots(),
  }),
  scheduleDraftSave,
  { deep: true },
)

watch([eventDate, eventTime], syncEventAt)

onBeforeRouteLeave(() => requestClose('route'))
onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  draftCandidate.value = readDraft()
  if (draftCandidate.value) void nextTick(() => draftRestoreButton.value?.focus())
})
onBeforeUnmount(() => {
  clearDraftSaveTimer()
  window.removeEventListener('beforeunload', handleBeforeUnload)
  resolveRouteDecisions(false)
})
</script>

<template>
  <BaseModal
    :title="editing ? '编辑这段回忆' : '收藏一段新回忆'"
    description="照片、地点和标签会一起同步给彼此。"
    wide
    :close-disabled="busy"
    @close="requestClose"
  >
    <div class="memory-editor-shell">
      <div class="memory-editor-underlay" :inert="Boolean(confirmation)">
        <div v-if="conflict" class="conflict-panel" role="alert">
          <p>对方或另一台设备已修改此内容，请加载最新版本后重新确认。</p>
          <button class="button secondary small" type="button" @click="loadLatest">加载最新内容</button>
        </div>
        <section v-if="draftCandidate" class="memory-draft-recovery" role="status" aria-live="polite">
          <div>
            <strong>发现未完成草稿</strong>
            <p>上次编辑于 {{ draftUpdatedLabel }}。<span v-if="draftCandidate.pendingMedia.length">媒体需要重新选择。</span></p>
          </div>
          <div class="memory-draft-recovery-actions">
            <button ref="draftRestoreButton" class="button secondary small" type="button" @click="restoreDraft">恢复草稿</button>
            <button class="button ghost small" type="button" @click="ignoreDraft">忽略</button>
          </div>
        </section>
        <p v-if="draftMediaMetadata.length" class="memory-draft-media-note" role="status">草稿中的媒体文件未被浏览器保留，媒体需要重新选择。<button class="text-button" type="button" @click="clearDraftMediaMetadata">清除媒体提示</button></p>

        <form class="stack-form" :inert="Boolean(draftCandidate)" @submit.prevent="save">
          <div class="form-two">
            <label class="field"><span>回忆标题</span><input id="memory-title" v-model="form.title" required maxlength="120" placeholder="例如：海边吹风的那个下午" :aria-invalid="Boolean(fieldErrors.title)" :aria-describedby="fieldErrors.title ? 'memory-title-error' : undefined" /><small v-if="fieldErrors.title" id="memory-title-error" class="field-error">{{ fieldErrors.title }}</small></label>
            <label class="field"><span>发生日期</span><input id="memory-event-date" v-model="eventDate" required type="date" :aria-invalid="Boolean(fieldErrors.eventAt)" :aria-describedby="fieldErrors.eventAt ? 'memory-event-at-error' : undefined" /><small v-if="fieldErrors.eventAt" id="memory-event-at-error" class="field-error">{{ fieldErrors.eventAt }}</small></label>
            <label class="field"><span>时刻（可选）</span><input id="memory-event-time" v-model="eventTime" type="time" /><small>只记得哪一天时可以留空。</small></label>
          </div>
          <label class="field"><span>地点名称（可选）</span><span class="input-with-icon"><MapPin :size="17" /><input id="memory-location" v-model="form.location" maxlength="200" placeholder="例如：厦门 · 环岛路" :aria-invalid="Boolean(fieldErrors.location)" :aria-describedby="fieldErrors.location ? 'memory-location-error' : undefined" /></span><small v-if="fieldErrors.location" id="memory-location-error" class="field-error">{{ fieldErrors.location }}</small></label>
          <label class="field"><span>想记住的话（可选）</span><textarea id="memory-description" v-model="form.description" maxlength="10000" rows="4" placeholder="那天发生了什么？当时是什么心情？" :aria-invalid="Boolean(fieldErrors.description)" :aria-describedby="fieldErrors.description ? 'memory-description-error' : undefined"></textarea><small v-if="fieldErrors.description" id="memory-description-error" class="field-error">{{ fieldErrors.description }}</small><small>{{ form.description.length }}/10000</small></label>
          <div class="tag-editor">
            <label class="field"><span>回忆标签（最多 12 个）</span><div class="tag-input"><Tags :size="17" /><input id="memory-tags" v-model="tagInput" maxlength="30" placeholder="旅行、约会、美食…" :aria-invalid="Boolean(fieldErrors.tags)" :aria-describedby="fieldErrors.tags ? 'memory-tags-error' : undefined" @keydown="handleTagKeydown" /><button type="button" @click="addTag()">添加</button></div><small v-if="fieldErrors.tags" id="memory-tags-error" class="field-error">{{ fieldErrors.tags }}</small></label>
            <div v-if="form.tags.length" class="tag-row editable"><button v-for="(tag, index) in form.tags" :key="tag" type="button" @click="form.tags.splice(index, 1)"># {{ tag }} <X :size="12" /></button></div>
            <div v-if="availableTags.length" class="known-tags"><span>常用：</span><button v-for="tag in availableTags.slice(0, 8)" :key="tag.name" type="button" @click="addTag(tag.name)">{{ tag.name }}</button></div>
          </div>
          <div class="upload-field">
            <input ref="fileInput" class="sr-only" type="file" multiple accept="image/*,video/*,audio/*" @change="chooseFiles" />
            <button class="upload-drop compact" type="button" @click="fileInput?.click()"><span><UploadCloud :size="22" /></span><strong>{{ editing ? '继续添加媒体' : '选择照片、视频或声音' }}</strong><small>每段回忆最多 20 个文件</small></button>
            <div v-if="currentMemory?.media.length" class="edit-media-list">
              <div v-for="media in currentMemory.media" :key="media.id">
                <img v-if="memoryMediaType(media) === 'image'" :src="memoryMediaUrl(media)" alt="" />
                <component :is="memoryMediaType(media) === 'video' ? FileVideo : FileAudio" v-else :size="22" />
                <span>{{ media.originalName }}</span>
                <button type="button" aria-label="删除媒体" :disabled="busy" @click="requestDeleteMedia(media)"><Trash2 :size="15" /></button>
              </div>
            </div>
            <ul v-if="selectedFiles.length" class="file-list">
              <li v-for="(file, index) in selectedFiles" :key="`${file.name}-${index}`"><component :is="fileIcon(file)" :size="17" /><span><strong>{{ file.name }}</strong><small>{{ formatBytes(file.size) }}</small></span><button type="button" aria-label="移除文件" :disabled="busy" @click="selectedFiles.splice(index, 1)"><X :size="17" /></button></li>
            </ul>
          </div>
          <div class="modal-actions"><button class="button ghost" type="button" :disabled="busy" @click="requestClose('button')">取消</button><button class="button primary" type="submit" :disabled="busy"><span v-if="saving" class="button-spinner"></span><Images v-else :size="18" />{{ saving ? '正在收藏…' : (editing ? '保存修改' : '收藏这段回忆') }}</button></div>
        </form>
      </div>

      <div
        v-if="confirmation"
        class="memory-confirmation-layer"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="memory-confirmation-title"
        aria-describedby="memory-confirmation-description"
        @click.stop
      >
        <div class="memory-confirmation-card">
          <p class="eyebrow">需要确认</p>
          <h3 id="memory-confirmation-title">{{ confirmation.kind === 'discard' ? '要放弃这次修改吗？' : '确定删除这份媒体吗？' }}</h3>
          <p id="memory-confirmation-description">{{ confirmation.kind === 'discard' ? '还没有保存的文字、标签和新选择的媒体会丢失。' : `“${confirmation.media.originalName}”删除后不会进入回收站，之后无法恢复。` }}</p>
          <div class="modal-actions">
            <button ref="confirmationContinueButton" class="button secondary" type="button" :disabled="busy" @click="continueEditing">{{ confirmation.kind === 'discard' ? '继续编辑' : '取消' }}</button>
            <button class="button danger-button" type="button" :disabled="busy" @click="confirmation.kind === 'discard' ? discardChanges() : confirmDeleteMedia()">{{ confirmation.kind === 'discard' ? '放弃修改' : '删除媒体' }}</button>
          </div>
        </div>
      </div>
    </div>
  </BaseModal>
</template>
