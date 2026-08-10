<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { Camera, FileAudio, FileVideo, Images, MapPin, Tags, Trash2, UploadCloud, X } from 'lucide-vue-next'
import { api } from '../../api'
import type { MemoryInput } from '../../api'
import { errorMessage } from '../../api/client'
import { useToast } from '../../composables/toast'
import type { MediaItem, Memory, MemoryTag } from '../../types'
import { toBeijingOffsetDateTime, toLocalDateTimeInput } from '../../utils'
import { memoryMediaType, memoryMediaUrl } from '../../utils/memoryMedia'
import BaseModal from '../BaseModal.vue'

const props = defineProps<{ memory: Memory | null; availableTags: MemoryTag[] }>()
const emit = defineEmits<{
  close: []
  saved: []
  updated: [memory: Memory]
}>()
const { show } = useToast()
const currentMemory = ref<Memory | null>(props.memory)
const saving = ref(false)
const selectedFiles = ref<File[]>([])
const fileInput = ref<HTMLInputElement | null>(null)
const tagInput = ref('')
const editing = computed(() => currentMemory.value !== null)
const form = reactive<MemoryInput>({
  title: props.memory?.title || '',
  description: props.memory?.description || '',
  eventAt: toLocalDateTimeInput(props.memory?.eventAt),
  location: props.memory?.location || '',
  tags: [...(props.memory?.tags || [])],
})

function chooseFiles(event: Event) {
  const files = Array.from((event.target as HTMLInputElement).files || [])
  const allowed = files.filter((file) => /^(image|video|audio)\//.test(file.type))
  if (allowed.length !== files.length) show('已忽略不支持的文件，只能上传图片、视频和音频。', 'info')
  const remaining = Math.max(0, 20 - (currentMemory.value?.media.length || 0) - selectedFiles.value.length)
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
  const payload: MemoryInput = {
    title: form.title.trim(),
    description: form.description.trim(),
    eventAt: toBeijingOffsetDateTime(form.eventAt),
    location: form.location.trim(),
    tags: form.tags,
  }
  try {
    if (currentMemory.value) {
      let updated = await api.updateMemory(currentMemory.value.id, payload)
      if (selectedFiles.value.length) updated = await api.addMemoryMedia(updated.id, selectedFiles.value)
      emit('updated', updated)
      show('这段回忆已经更新。', 'success')
    } else {
      await api.createMemory(payload, selectedFiles.value)
      show('新的回忆已经收藏好。', 'success')
    }
    emit('saved')
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    saving.value = false
  }
}

async function removeMedia(item: MediaItem) {
  if (!currentMemory.value || !window.confirm(`确定删除“${item.originalName}”吗？此操作不会进入回收站。`)) return
  try {
    const updated = await api.deleteMemoryMedia(currentMemory.value.id, item.id)
    currentMemory.value = updated
    emit('updated', updated)
    show('媒体已删除。', 'success')
  } catch (cause) {
    show(errorMessage(cause), 'error')
  }
}

function fileIcon(file: File) {
  return file.type.startsWith('video/') ? FileVideo : file.type.startsWith('audio/') ? FileAudio : Camera
}

function formatBytes(bytes: number) {
  return bytes < 1048576 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1048576).toFixed(1)} MB`
}
</script>

<template>
  <BaseModal :title="editing ? '编辑这段回忆' : '收藏一段新回忆'" description="照片、地点和标签会一起同步给彼此。" wide @close="emit('close')">
    <form class="stack-form" @submit.prevent="save">
      <div class="form-two">
        <label class="field"><span>回忆标题</span><input v-model="form.title" required maxlength="120" placeholder="例如：海边吹风的那个下午" /></label>
        <label class="field"><span>发生时间</span><input v-model="form.eventAt" required type="datetime-local" /></label>
      </div>
      <label class="field"><span>地点名称（可选）</span><span class="input-with-icon"><MapPin :size="17" /><input v-model="form.location" maxlength="200" placeholder="例如：厦门 · 环岛路" /></span></label>
      <label class="field"><span>想记住的话（可选）</span><textarea v-model="form.description" maxlength="10000" rows="4" placeholder="那天发生了什么？当时是什么心情？"></textarea><small>{{ form.description.length }}/10000</small></label>
      <div class="tag-editor">
        <label class="field"><span>回忆标签（最多 12 个）</span><div class="tag-input"><Tags :size="17" /><input v-model="tagInput" maxlength="30" placeholder="旅行、约会、美食…" @keydown.enter.prevent="addTag()" @keydown.,.prevent="addTag()" /><button type="button" @click="addTag()">添加</button></div></label>
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
            <button type="button" aria-label="删除媒体" @click="removeMedia(media)"><Trash2 :size="15" /></button>
          </div>
        </div>
        <ul v-if="selectedFiles.length" class="file-list">
          <li v-for="(file, index) in selectedFiles" :key="`${file.name}-${index}`"><component :is="fileIcon(file)" :size="17" /><span><strong>{{ file.name }}</strong><small>{{ formatBytes(file.size) }}</small></span><button type="button" aria-label="移除文件" @click="selectedFiles.splice(index, 1)"><X :size="17" /></button></li>
        </ul>
      </div>
      <div class="modal-actions"><button class="button ghost" type="button" @click="emit('close')">取消</button><button class="button primary" type="submit" :disabled="saving"><span v-if="saving" class="button-spinner"></span><Images v-else :size="18" />{{ saving ? '正在收藏…' : (editing ? '保存修改' : '收藏这段回忆') }}</button></div>
    </form>
  </BaseModal>
</template>
