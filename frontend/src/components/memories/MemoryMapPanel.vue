<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as L from 'leaflet'
import { MapPin, Pencil } from 'lucide-vue-next'
import EmptyState from '../EmptyState.vue'
import type { Memory } from '../../types'
import { formatDate } from '../../utils'
import { createMemoryMarkerIcon, createMemoryTileLayer, MEMORY_MAP_CENTER } from '../../utils/memoryMap'

const props = defineProps<{ memories: Memory[] }>()
const emit = defineEmits<{ create: []; edit: [memory: Memory] }>()
const mapElement = ref<HTMLElement | null>(null)
const selected = ref<Memory | null>(props.memories[0] || null)
let map: L.Map | null = null
let layer: L.LayerGroup | null = null

onMounted(renderMap)
onBeforeUnmount(destroyMap)
watch(() => props.memories, async (memories) => {
  selected.value = memories.find((item) => String(item.id) === String(selected.value?.id)) || memories[0] || null
  if (!memories.length) {
    destroyMap()
    return
  }
  await nextTick()
  renderMap()
})

function renderMap() {
  if (!mapElement.value || !props.memories.length) return
  if (!map) {
    map = L.map(mapElement.value, { zoomControl: true }).setView(MEMORY_MAP_CENTER, 4)
    createMemoryTileLayer().addTo(map)
    layer = L.layerGroup().addTo(map)
  }
  layer?.clearLayers()
  const bounds: L.LatLngExpression[] = []
  props.memories.forEach((memory) => {
    if (memory.latitude == null || memory.longitude == null) return
    const point: L.LatLngExpression = [memory.latitude, memory.longitude]
    bounds.push(point)
    const marker = L.marker(point, { icon: createMemoryMarkerIcon(), title: memory.title })
    marker.on('click', () => { selected.value = memory })
    marker.bindTooltip(memory.title, { direction: 'top', offset: [0, -24] })
    marker.addTo(layer!)
  })
  if (bounds.length) map.fitBounds(L.latLngBounds(bounds), { padding: [45, 45], maxZoom: 14 })
  window.setTimeout(() => map?.invalidateSize(), 0)
}

function focus(memory: Memory) {
  selected.value = memory
  if (memory.latitude != null && memory.longitude != null) map?.flyTo([memory.latitude, memory.longitude], 14)
}

function destroyMap() {
  map?.remove()
  map = null
  layer = null
}
</script>

<template>
  <EmptyState v-if="!memories.length" title="地图上还没有足迹" description="编辑或新建回忆，在地图上点选发生地点，就能把故事钉在共同走过的路上。">
    <button class="button primary small" type="button" @click="emit('create')"><MapPin :size="17" />添加第一个地点</button>
  </EmptyState>
  <div v-else class="memory-map-layout">
    <div ref="mapElement" class="story-map" aria-label="回忆地图"></div>
    <aside class="map-memory-list">
      <p><MapPin :size="16" />{{ memories.length }} 个共同足迹</p>
      <button v-for="memory in memories" :key="memory.id" type="button" :class="{ active: selected?.id === memory.id }" @click="focus(memory)">
        <strong>{{ memory.title }}</strong><span>{{ memory.location || '地图坐标' }} · {{ formatDate(memory.eventAt, { year: 'numeric', month: 'short', day: 'numeric' }) }}</span>
      </button>
    </aside>
    <article v-if="selected" class="map-memory-card card">
      <div>
        <p class="eyebrow">SELECTED MEMORY</p><h2>{{ selected.title }}</h2>
        <p>{{ selected.description || '这一天值得被地图记住。' }}</p>
        <div class="tag-row"><span v-for="tag in selected.tags" :key="tag"># {{ tag }}</span></div>
      </div>
      <button class="button secondary small" type="button" @click="emit('edit', selected)"><Pencil :size="16" />编辑回忆</button>
    </article>
  </div>
</template>
