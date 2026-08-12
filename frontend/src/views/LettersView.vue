<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { CalendarClock, Clock3, Heart, Mail, MailCheck, Mails as MailHeart, PenLine, RefreshCw, Send, Trash2 } from 'lucide-vue-next'
import { api } from '../api'
import { errorMessage } from '../api/client'
import { useToast } from '../composables/toast'
import { useResourceSync } from '../composables/resourceSync'
import { authState } from '../stores/auth'
import BaseAvatar from '../components/BaseAvatar.vue'
import BaseModal from '../components/BaseModal.vue'
import EmptyState from '../components/EmptyState.vue'
import LoadingState from '../components/LoadingState.vue'
import type { Letter, UserProfile } from '../types'
import { formatDate, formatDateTime, sameId, toBeijingOffsetDateTime, toLocalDateTimeInput } from '../utils'
import { createRequestGeneration } from '../utils/latestRequest'
import { mergeLetterPage } from '../utils/lettersPagination'

const { show } = useToast()
const letters = ref<Letter[]>([])
const currentPage = ref(0)
const totalElements = ref(0)
const totalPages = ref(0)
const loading = ref(true)
const loadingMore = ref(false)
const saving = ref(false)
const error = ref('')
const composerOpen = ref(false)
const content = ref('')
const deliveryMode = ref<'now' | 'scheduled'>('now')
const deliverAt = ref('')
const minimumDelivery = ref('')
const now = ref(Date.now())
const messageRequests = createRequestGeneration()
let clockTimer: number | undefined

const sortedLetters = computed(() => [...letters.value].sort((a, b) =>
  new Date(b.deliverAt || b.createdAt).getTime() - new Date(a.deliverAt || a.createdAt).getTime()))
const hasMore = computed(() => totalPages.value > 0 && currentPage.value < totalPages.value - 1)

onMounted(() => {
  void load()
  clockTimer = window.setInterval(() => { now.value = Date.now() }, 30000)
})
useResourceSync(['messages'], load)
onBeforeUnmount(() => {
  messageRequests.cancel()
  if (clockTimer !== undefined) window.clearInterval(clockTimer)
})

async function load(reset = true) {
  if (!reset && (loadingMore.value || !hasMore.value)) return
  const request = messageRequests.begin()
  if (reset) {
    loading.value = true
    error.value = ''
  } else {
    loadingMore.value = true
  }
  const targetPage = reset ? 0 : currentPage.value + 1
  try {
    const page = await api.messages(targetPage)
    if (!request.isLatest()) return
    const merged = mergeLetterPage(letters.value, { ...page, content: page.content ?? [] }, reset)
    letters.value = merged.letters
    currentPage.value = merged.page
    totalElements.value = merged.totalElements
    totalPages.value = merged.totalPages
    return true
  } catch (cause) {
    if (!request.isLatest()) return
    error.value = errorMessage(cause)
    return false
  } finally {
    if (request.isLatest()) {
      loading.value = false
      loadingMore.value = false
    }
  }
}

function sender(letter: Letter): UserProfile {
  return { id: letter.authorId, nickname: letter.authorNickname }
}
function mine(letter: Letter) { return sameId(letter.authorId, authState.user?.id) }
function unread(letter: Letter) { return !mine(letter) && !letter.readAt }
function pending(letter: Letter) {
  return Boolean(letter.scheduled && letter.deliverAt && new Date(letter.deliverAt).getTime() > now.value)
}

function cancelMessageLoads() {
  messageRequests.cancel()
  loading.value = false
  loadingMore.value = false
}

function openComposer() {
  const earliest = new Date(Date.now() + 60000)
  minimumDelivery.value = toLocalDateTimeInput(earliest)
  const suggested = new Date()
  suggested.setDate(suggested.getDate() + 1)
  suggested.setHours(9, 0, 0, 0)
  deliverAt.value = toLocalDateTimeInput(suggested > earliest ? suggested : earliest)
  deliveryMode.value = 'now'
  composerOpen.value = true
}

async function openLetter(letter: Letter) {
  if (!unread(letter)) return
  cancelMessageLoads()
  try {
    const opened = await api.readMessage(letter.id)
    letters.value = letters.value.map(value => String(value.id) === String(letter.id)
      ? { ...value, content: opened.content, readAt: opened.readAt || new Date().toISOString(), version: opened.version }
      : value)
  } catch (cause) {
    show(errorMessage(cause), 'error')
  }
}

async function send() {
  cancelMessageLoads()
  saving.value = true
  try {
    const scheduled = deliveryMode.value === 'scheduled'
    await api.createMessage(content.value.trim(), scheduled ? toBeijingOffsetDateTime(deliverAt.value) : undefined)
    content.value = ''
    composerOpen.value = false
    show(scheduled ? '时光胶囊已经封存，会在约定时间送达。' : '信笺已经送到对方的信箱。', 'success')
    await load()
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    saving.value = false
  }
}

async function remove(letter: Letter) {
  if (!window.confirm('确定将这封信笺移入回收站吗？')) return
  cancelMessageLoads()
  try {
    await api.deleteMessage(letter.id)
    totalElements.value = Math.max(0, totalElements.value - 1)
    letters.value = letters.value.filter((item) => !sameId(item.id, letter.id))
    if (!letters.value.length && totalElements.value > 0) await load()
    show('这封信笺已移入回收站。', 'success')
  } catch (cause) {
    show(errorMessage(cause), 'error')
  }
}
</script>

<template>
  <div class="page-stack letters-page">
    <header class="page-header">
      <div><p class="eyebrow">SLOW WORDS, WARM HEART</p><h1>给你的信笺</h1><p>不追求秒回，只把此刻想告诉你的话，认真寄出去。</p></div>
      <button class="button primary" type="button" @click="openComposer"><PenLine :size="18" />写一封信</button>
    </header>

    <section class="mailbox-summary">
      <div class="mailbox-icon"><MailHeart :size="25" /></div>
      <div><strong>{{ authState.user?.nickname }} 与 {{ authState.partner?.nickname || '心上人' }} 的小信箱</strong><p>共有 {{ totalElements }} 封信，其中 {{ letters.filter(unread).length }} 封还没拆开，{{ letters.filter(pending).length }} 封等待送达。</p></div>
      <span class="postmark">LOVE<br />POST</span>
    </section>

    <LoadingState v-if="loading" label="正在打开信箱…" />
    <div v-else-if="error" class="error-panel" role="alert"><p>{{ error }}</p><button class="button secondary" type="button" @click="load()"><RefreshCw :size="17" />重新加载</button></div>
    <EmptyState v-else-if="!letters.length" title="信箱里还没有信" description="写一句平时不好意思当面说的话，让它慢慢抵达。"><button class="button primary small" type="button" @click="openComposer"><PenLine :size="17" />写第一封信</button></EmptyState>
    <div v-else class="letter-grid">
      <article v-for="(letter, index) in sortedLetters" :key="letter.id" class="letter-card" :class="{ incoming: !mine(letter), unread: unread(letter), capsule: pending(letter), rose: index % 3 === 1, sage: index % 3 === 2 }">
        <div class="letter-top"><span class="letter-from"><BaseAvatar :user="sender(letter)" size="sm" /><span><small>FROM</small><strong>{{ sender(letter)?.nickname || (mine(letter) ? authState.user?.nickname : authState.partner?.nickname) }}</strong></span></span><span class="letter-status"><CalendarClock v-if="pending(letter)" :size="15" /><Mail v-else-if="unread(letter)" :size="15" /><MailCheck v-else :size="15" />{{ pending(letter) ? '等待送达' : (unread(letter) ? '等待拆开' : (mine(letter) ? '已寄出' : '已读')) }}</span></div>
        <div class="letter-body">
          <template v-if="!unread(letter)"><span class="quote-mark">“</span><p>{{ letter.content }}</p><span class="letter-heart"><Heart :size="17" fill="currentColor" /></span></template>
          <button v-else class="sealed-letter" type="button" @click="openLetter(letter)"><span class="wax-seal"><Heart :size="22" fill="currentColor" /></span><strong>有一封信等你亲手拆开</strong><small>轻点封印，看看 TA 想说什么</small></button>
        </div>
        <footer><span>{{ pending(letter) ? `将于 ${formatDateTime(letter.deliverAt) } 送达` : formatDateTime(letter.createdAt) }}</span><button v-if="mine(letter)" class="icon-button danger" type="button" aria-label="删除这封信" @click="remove(letter)"><Trash2 :size="16" /></button><span v-else-if="letter.readAt">于 {{ formatDate(letter.readAt, { month: 'short', day: 'numeric' }) }} 拆阅</span></footer>
      </article>
    </div>
    <div v-if="!loading && !error && letters.length && hasMore" class="load-more-row">
      <button class="button secondary" type="button" :disabled="loadingMore" @click="load(false)"><span v-if="loadingMore" class="button-spinner"></span><RefreshCw v-else :size="17" />{{ loadingMore ? '正在加载…' : '加载更多信笺' }}</button>
    </div>
  </div>

  <BaseModal v-if="composerOpen" title="写给心上人的信" :description="`收信人：${authState.partner?.nickname || '你的心上人'}`" @close="composerOpen = false">
    <form class="stack-form letter-form" @submit.prevent="send">
      <div class="letter-paper-input"><span class="paper-to">TO：{{ authState.partner?.nickname || '宝贝' }}</span><textarea v-model="content" required minlength="1" maxlength="2000" rows="10" autofocus placeholder="想对 TA 说些什么？不必很长，真心就好。"></textarea><div class="paper-sign">FROM：{{ authState.user?.nickname }} ♡</div></div>
      <div class="delivery-options" aria-label="选择送达方式">
        <button type="button" :aria-pressed="deliveryMode === 'now'" :class="{ selected: deliveryMode === 'now' }" @click="deliveryMode = 'now'"><Send :size="18" /><span><strong>现在寄出</strong><small>马上出现在对方信箱</small></span></button>
        <button type="button" :aria-pressed="deliveryMode === 'scheduled'" :class="{ selected: deliveryMode === 'scheduled' }" @click="deliveryMode = 'scheduled'"><CalendarClock :size="18" /><span><strong>封存到未来</strong><small>到约定时间才会出现</small></span></button>
      </div>
      <label v-if="deliveryMode === 'scheduled'" class="field capsule-time"><span>送达时间（北京时间）</span><span class="input-with-icon"><Clock3 :size="17" /><input v-model="deliverAt" type="datetime-local" :min="minimumDelivery" required /></span><small>送达前只有你能看见，也可以随时收回。</small></label>
      <div class="character-row"><span>{{ content.length }}/2000</span><span>{{ deliveryMode === 'scheduled' ? '到约定时间才会送达' : '发送后会出现在对方的信箱' }}</span></div>
      <button class="button primary full" type="submit" :disabled="saving || !content.trim() || (deliveryMode === 'scheduled' && !deliverAt)"><span v-if="saving" class="button-spinner"></span><CalendarClock v-else-if="deliveryMode === 'scheduled'" :size="18" /><Send v-else :size="18" />{{ saving ? '正在寄出…' : (deliveryMode === 'scheduled' ? '封存这封时光胶囊' : '把这封信寄出去') }}</button>
    </form>
  </BaseModal>
</template>

<style scoped>
.delivery-options { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.load-more-row { display: flex; justify-content: center; padding: 8px 0 4px; }
.delivery-options > button { display: flex; align-items: center; gap: 10px; padding: 12px; border: 1px solid var(--line); border-radius: 14px; background: var(--paper); color: var(--ink); cursor: pointer; text-align: left; }
.delivery-options > button.selected { border-color: var(--rose); background: var(--rose-pale); color: var(--rose-dark); box-shadow: 0 0 0 2px rgba(224, 85, 104, .08); }
.delivery-options span { display: flex; flex-direction: column; gap: 2px; }
.delivery-options strong { font-size: 13px; }
.delivery-options small, .capsule-time small { color: var(--muted); font-size: 11px; }
.letter-card.capsule { border-style: dashed; }
.letter-card.capsule .letter-status { color: var(--rose-dark); }
@media (max-width: 520px) {
  .delivery-options { grid-template-columns: 1fr; }
}
</style>
