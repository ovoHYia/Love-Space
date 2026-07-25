<script setup lang="ts">
import { FileVideo, Image } from 'lucide-vue-next'
import EmptyState from '../EmptyState.vue'
import type { AlbumItem, MediaItem } from '../../types'
import { formatDate } from '../../utils'
import { memoryMediaType, memoryMediaUrl } from '../../utils/memoryMedia'

defineProps<{ items: AlbumItem[] }>()
const emit = defineEmits<{
  create: []
  selectTag: [tag: string]
  openMedia: [payload: { title: string; media: MediaItem }]
}>()
</script>

<template>
  <EmptyState v-if="!items.length" title="相册还没有影像" description="回忆中的照片和视频会自动汇集到这里，声音仍保留在时间线中。">
    <button class="button primary small" type="button" @click="emit('create')"><Image :size="17" />加入第一段影像</button>
  </EmptyState>
  <div v-else class="album-grid">
    <article v-for="item in items" :key="item.media.id" class="album-tile">
      <button type="button" :aria-label="`查看${item.memoryTitle}的${memoryMediaType(item.media) === 'video' ? '视频' : '照片'}`" @click="emit('openMedia', { title: item.memoryTitle, media: item.media })">
        <img v-if="memoryMediaType(item.media) === 'image'" :src="memoryMediaUrl(item.media)" :alt="`${item.memoryTitle} 的照片`" loading="lazy" />
        <video v-else :src="memoryMediaUrl(item.media)" preload="metadata" muted playsinline aria-hidden="true"></video>
        <span v-if="memoryMediaType(item.media) === 'video'" class="album-video-badge" aria-hidden="true"><FileVideo :size="15" />视频</span>
      </button>
      <div>
        <strong>{{ item.memoryTitle }}</strong>
        <span>{{ formatDate(item.eventAt, { year: 'numeric', month: 'short', day: 'numeric' }) }}<template v-if="item.location"> · {{ item.location }}</template></span>
        <p v-if="item.tags.length"><button v-for="tag in item.tags" :key="tag" type="button" @click="emit('selectTag', tag)">#{{ tag }}</button></p>
      </div>
    </article>
  </div>
</template>
