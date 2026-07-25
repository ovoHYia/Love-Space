<script setup lang="ts">
import { computed } from 'vue'
import { CalendarDays, FileAudio, Heart as HeartFill, MapPin, Pencil, Plus, Trash2 } from 'lucide-vue-next'
import BaseAvatar from '../BaseAvatar.vue'
import EmptyState from '../EmptyState.vue'
import type { MediaItem, Memory, UserProfile } from '../../types'
import { formatDate } from '../../utils'
import { memoryMediaType, memoryMediaUrl } from '../../utils/memoryMedia'

const props = defineProps<{ memories: Memory[] }>()
const emit = defineEmits<{
  create: []
  edit: [memory: Memory]
  remove: [memory: Memory]
  selectTag: [tag: string]
  openMedia: [payload: { title: string; media: MediaItem }]
  openGallery: [memory: Memory]
}>()

const grouped = computed(() => {
  const groups = new Map<string, Memory[]>()
  props.memories.forEach((memory) => {
    const key = formatDate(memory.eventAt, { year: 'numeric', month: 'long' })
    groups.set(key, [...(groups.get(key) || []), memory])
  })
  return [...groups.entries()]
})

function authorOf(memory: Memory): UserProfile {
  return { id: memory.authorId, nickname: memory.authorNickname }
}
</script>

<template>
  <EmptyState v-if="!memories.length" title="时间线还是空白的" description="从今天开始，收藏第一张照片、第一段声音，或第一句舍不得忘的话。">
    <button class="button primary small" type="button" @click="emit('create')"><Plus :size="17" />收藏第一条</button>
  </EmptyState>
  <div v-else class="timeline">
    <section v-for="([month, items], groupIndex) in grouped" :key="month" class="timeline-group">
      <div class="timeline-month"><span>{{ month }}</span><i></i></div>
      <article v-for="memory in items" :key="memory.id" class="memory-card" :class="{ featured: groupIndex === 0 }">
        <div class="memory-dot" aria-hidden="true"><HeartFill /></div>
        <div class="memory-card-inner">
          <div v-if="memory.media.length" class="memory-media" :class="{ gallery: memory.media.length > 1 }">
            <template v-for="media in memory.media.slice(0, 4)" :key="media.id">
              <button v-if="memoryMediaType(media) === 'image'" class="media-trigger" type="button" @click="emit('openMedia', { title: memory.title, media })">
                <img :src="memoryMediaUrl(media)" :alt="`${memory.title} 的照片`" loading="lazy" draggable="false" />
              </button>
              <button v-else-if="memoryMediaType(media) === 'video'" class="media-trigger" type="button" @click="emit('openMedia', { title: memory.title, media })">
                <video :src="memoryMediaUrl(media)" preload="metadata" playsinline aria-hidden="true"></video>
                <span class="media-view-hint" aria-hidden="true">点击查看原视频</span>
              </button>
              <button v-else class="audio-tile media-trigger" type="button" @click="emit('openMedia', { title: memory.title, media })">
                <FileAudio :size="25" /><span>{{ media.originalName || '一段声音' }}</span><small>点击查看原音频</small>
              </button>
            </template>
            <button v-if="memory.media.length > 4" class="more-media" type="button" @click="emit('openGallery', memory)">+{{ memory.media.length - 4 }}</button>
          </div>
          <div class="memory-content">
            <div class="memory-meta">
              <span><CalendarDays :size="14" />{{ formatDate(memory.eventAt, { year: 'numeric', month: 'short', day: 'numeric', weekday: 'short' }) }}</span>
              <span v-if="memory.location"><MapPin :size="14" />{{ memory.location }}</span>
            </div>
            <div class="memory-title-row">
              <div><h2>{{ memory.title }}</h2><span class="author-chip"><BaseAvatar :user="authorOf(memory)" size="sm" />{{ memory.authorNickname }} 收藏</span></div>
              <div class="card-actions">
                <button class="icon-button" type="button" aria-label="编辑回忆" @click="emit('edit', memory)"><Pencil :size="17" /></button>
                <button class="icon-button danger" type="button" aria-label="删除回忆" @click="emit('remove', memory)"><Trash2 :size="17" /></button>
              </div>
            </div>
            <div v-if="memory.tags?.length" class="tag-row">
              <button v-for="tag in memory.tags" :key="tag" type="button" @click="emit('selectTag', tag)"># {{ tag }}</button>
            </div>
            <p v-if="memory.description" class="memory-description">{{ memory.description }}</p>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>
