<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ArrowLeft, Database, Download, FileArchive, RotateCcw, ShieldCheck, Trash2 } from 'lucide-vue-next'
import { api } from '../api'
import { errorMessage } from '../api/client'
import EmptyState from '../components/EmptyState.vue'
import LoadingState from '../components/LoadingState.vue'
import { useToast } from '../composables/toast'
import type { TrashItem } from '../types'
import { formatDateTime } from '../utils'

const { show } = useToast()
const items = ref<TrashItem[]>([])
const loading = ref(true)
const exporting = ref(false)
const emptying = ref(false)
const workingKey = ref('')
const error = ref('')

const typeLabels: Record<TrashItem['type'], string> = {
  MEMORY: '回忆',
  DIARY: '日记',
  MESSAGE: '信笺',
  ANNIVERSARY: '纪念日',
  WISH: '愿望',
}
const countText = computed(() => items.value.length ? `${items.value.length} 项待处理` : '回收站为空')

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    items.value = await api.trash()
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

async function exportData() {
  exporting.value = true
  try {
    const result = await api.exportData()
    const url = URL.createObjectURL(result.blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = result.filename || `love-space-export-${new Date().toISOString().slice(0, 10)}.zip`
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
    show('数据压缩包已经开始下载。', 'success')
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    exporting.value = false
  }
}

function key(item: TrashItem) {
  return `${item.type}-${item.id}`
}

async function restore(item: TrashItem) {
  workingKey.value = key(item)
  try {
    await api.restoreTrash(item)
    items.value = items.value.filter(value => key(value) !== key(item))
    show(`“${item.title}”已经恢复。`, 'success')
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    workingKey.value = ''
  }
}

async function purge(item: TrashItem) {
  if (!window.confirm(`确定永久删除“${item.title}”吗？此操作无法恢复。`)) return
  workingKey.value = key(item)
  try {
    await api.purgeTrash(item)
    items.value = items.value.filter(value => key(value) !== key(item))
    show('内容已永久删除。', 'success')
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    workingKey.value = ''
  }
}

async function emptyTrash() {
  if (!items.value.length || !window.confirm(`确定永久删除回收站中的 ${items.value.length} 项内容吗？此操作无法恢复。`)) return
  emptying.value = true
  try {
    await api.emptyTrash()
    items.value = []
    show('回收站已经清空。', 'success')
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    emptying.value = false
  }
}
</script>

<template>
  <div class="page-stack data-management-page">
    <header class="page-header">
      <div>
        <RouterLink class="back-link" to="/profile"><ArrowLeft :size="16" />返回关于我们</RouterLink>
        <p class="eyebrow">DATA & PRIVACY</p>
        <h1>数据与回收站</h1>
        <p>把属于你的内容带走，也给误删留一次回头的机会。</p>
      </div>
    </header>

    <section class="card export-card">
      <span class="feature-icon"><FileArchive :size="24" /></span>
      <div>
        <p class="eyebrow">PORTABLE COPY</p>
        <h2>导出我的数据</h2>
        <p>下载 ZIP 压缩包，包含可访问内容的 JSON 数据和媒体原文件，不包含密码等安全信息。</p>
        <div class="privacy-note"><ShieldCheck :size="16" />未送达的对方定时信笺不会进入你的导出文件。</div>
      </div>
      <button class="button primary" type="button" :disabled="exporting" @click="exportData">
        <span v-if="exporting" class="button-spinner"></span><Download v-else :size="17" />
        {{ exporting ? '正在打包…' : '下载数据' }}
      </button>
    </section>

    <section class="trash-section">
      <div class="section-heading">
        <div><p class="eyebrow">RECYCLE BIN</p><h2>回收站</h2><span>{{ countText }}</span></div>
        <button v-if="items.length" class="button danger-button small" type="button" :disabled="emptying || !!workingKey" @click="emptyTrash">
          <span v-if="emptying" class="button-spinner"></span><Trash2 v-else :size="16" />清空回收站
        </button>
      </div>
      <div class="trash-hint"><Database :size="17" /><span>这里只显示由你删除的内容；恢复后会回到原来的位置。</span></div>
      <LoadingState v-if="loading" label="正在查看回收站…" />
      <div v-else-if="error" class="error-panel" role="alert"><p>{{ error }}</p><button class="button secondary" type="button" @click="load">重新加载</button></div>
      <EmptyState v-else-if="!items.length" title="回收站空空的" description="删除的回忆、日记、信笺、纪念日和愿望会暂存在这里。" />
      <div v-else class="trash-list">
        <article v-for="item in items" :key="key(item)" class="card trash-item">
          <span class="trash-type">{{ typeLabels[item.type] }}</span>
          <div><h3>{{ item.title }}</h3><p>{{ formatDateTime(item.deletedAt) }} 移入回收站</p></div>
          <div class="trash-actions">
            <button class="button secondary small" type="button" :disabled="!!workingKey || emptying" @click="restore(item)">
              <span v-if="workingKey === key(item)" class="button-spinner"></span><RotateCcw v-else :size="15" />恢复
            </button>
            <button class="icon-button danger" type="button" aria-label="永久删除" :disabled="!!workingKey || emptying" @click="purge(item)"><Trash2 :size="16" /></button>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.back-link { display: inline-flex; align-items: center; gap: 5px; margin-bottom: 17px; color: var(--muted); font-size: 12px; font-weight: 700; }
.export-card { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 18px; padding: 24px; background: linear-gradient(135deg, rgba(255,253,251,.98), rgba(255,241,239,.92)); }
.feature-icon { width: 52px; height: 52px; display: grid; place-items: center; border-radius: 17px; background: var(--rose-pale); color: var(--rose); }
.export-card h2 { margin: 2px 0 5px; font-size: 23px; }
.export-card > div > p:not(.eyebrow) { max-width: 660px; margin: 0; color: var(--muted); font-size: 12px; line-height: 1.7; }
.privacy-note { display: flex; align-items: center; gap: 6px; margin-top: 9px; color: #668063; font-size: 10px; }
.trash-section { padding: 24px; border: 1px solid var(--line); border-radius: var(--radius); background: rgba(255,253,251,.72); }
.section-heading > div > span { display: inline-block; margin-top: 4px; color: var(--muted); font-size: 10px; }
.trash-hint { display: flex; align-items: center; gap: 8px; margin: 18px 0; padding: 11px 13px; border-radius: 12px; background: var(--sage-pale); color: #62745e; font-size: 11px; }
.trash-list { display: grid; gap: 10px; }
.trash-item { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 13px; padding: 15px 16px; }
.trash-type { padding: 5px 8px; border-radius: 9px; background: var(--rose-pale); color: var(--rose-dark); font-size: 9px; font-weight: 800; }
.trash-item h3 { margin: 0 0 3px; font-size: 16px; }
.trash-item p { margin: 0; color: var(--muted); font-size: 10px; }
.trash-actions { display: flex; align-items: center; gap: 7px; }
@media (max-width: 700px) {
  .export-card { grid-template-columns: auto 1fr; padding: 19px 16px; }
  .export-card .button { grid-column: 1 / -1; width: 100%; }
  .trash-section { padding: 19px 14px; }
  .trash-item { grid-template-columns: auto 1fr; }
  .trash-actions { grid-column: 1 / -1; justify-content: flex-end; }
}
</style>
