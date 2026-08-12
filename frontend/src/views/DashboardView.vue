<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ArrowRight, BellRing, CalendarHeart, Camera, Clock3, Heart, Mails as MailHeart, MapPin, MessageCircleHeart, RefreshCw, Sparkles } from 'lucide-vue-next'
import { api } from '../api'
import { errorMessage, mediaUrl } from '../api/client'
import BaseAvatar from '../components/BaseAvatar.vue'
import LoadingState from '../components/LoadingState.vue'
import EmptyState from '../components/EmptyState.vue'
import BaseModal from '../components/BaseModal.vue'
import { authState } from '../stores/auth'
import { useToast } from '../composables/toast'
import { useResourceSync } from '../composables/resourceSync'
import type { Anniversary, DashboardPayload, Letter, MediaItem, Memory, Mood } from '../types'
import { daysUntilAnniversary, formatDate, formatDateTime, sameId } from '../utils'
import { isStaleUpdate, STALE_UPDATE_MESSAGE } from '../utils/editConflict'
import { createRequestGeneration } from '../utils/latestRequest'

const { show } = useToast()
const loading = ref(true)
const loadError = ref('')
const data = ref<DashboardPayload | null>(null)
const now = ref(Date.now())
const moodOpen = ref(false)
const moodSaving = ref(false)
const moodConflict = ref(false)
const moodVersion = ref<number | string | undefined>(undefined)
const heartBurst = ref(0)
const selectedRandom = ref<Memory | null>(null)
const randomLoading = ref(false)
const moodForm = reactive({ emoji: '😊', label: '开心', note: '' })
const dashboardRequests = createRequestGeneration()
const moodChoices = [
  { emoji: '😊', label: '开心' }, { emoji: '🥰', label: '甜甜的' }, { emoji: '😌', label: '平静' },
  { emoji: '🥺', label: '想念' }, { emoji: '😴', label: '有点累' }, { emoji: '🌧️', label: '需要抱抱' },
]

const user = computed(() => authState.user)
const partner = computed(() => authState.partner)
const spaceName = computed(() => authState.spaceName)
const startedAt = computed(() => authState.loveStartedAt)
const moods = computed(() => data.value?.todayMoods || [])
const memories = computed(() => (data.value?.recentMemories || []).slice(0, 3))
const letters = computed<Letter[]>(() => data.value?.recentMessages || [])
const anniversaries = computed<Anniversary[]>(() => data.value?.anniversaries || [])
const dueReminders = computed<Anniversary[]>(() => data.value?.dueReminders || [])
const duration = computed(() => {
  const start = new Date(startedAt.value || '').getTime()
  let seconds = Math.max(0, Math.floor((now.value - start) / 1000))
  if (!Number.isFinite(seconds)) seconds = 0
  const days = Math.floor(seconds / 86400); seconds %= 86400
  const hours = Math.floor(seconds / 3600); seconds %= 3600
  return { days, hours, minutes: Math.floor(seconds / 60), seconds: seconds % 60 }
})

let timer: number | undefined
onMounted(() => { void load(); timer = window.setInterval(() => { now.value = Date.now() }, 1000) })
useResourceSync(['moods', 'memories', 'diaries', 'messages', 'anniversaries', 'wishes', 'profile', 'space'], load)
onUnmounted(() => window.clearInterval(timer))

async function load() {
  const request = dashboardRequests.begin()
  loading.value = true
  loadError.value = ''
  try {
    const nextData = await api.dashboard()
    if (!request.isLatest()) return
    data.value = nextData
    return true
  } catch (cause) {
    if (!request.isLatest()) return
    loadError.value = errorMessage(cause)
    return false
  } finally {
    if (request.isLatest()) loading.value = false
  }
}

function moodFor(id?: string | number): Mood | undefined {
  return moods.value.find((mood) => sameId(mood.userId, id))
}

function letterAuthorName(letter: Letter) {
  return letter.authorNickname || '心上人'
}

function letterPending(letter: Letter) {
  return Boolean(letter.scheduled && letter.deliverAt && new Date(letter.deliverAt).getTime() > now.value)
}

function countdownParts(item: Anniversary) {
  const value = item.daysUntil ?? daysUntilAnniversary(item.eventDate, item.recurringYearly)
  if (value === 0) return { value: '今天', unit: '♡' }
  return { value: String(Math.abs(value)), unit: value < 0 ? '天前' : '天后' }
}

async function saveMood() {
  moodSaving.value = true
  try {
    const updated = await api.updateMood({ ...moodForm, version: moodVersion.value })
    moodVersion.value = updated.version
    show('今天的心情已经悄悄放在首页啦。', 'success')
    moodOpen.value = false
    moodConflict.value = false
    dashboardRequests.cancel()
    await load()
  } catch (cause) {
    if (isStaleUpdate(cause)) {
      moodConflict.value = true
      show(STALE_UPDATE_MESSAGE, 'error')
    } else show(errorMessage(cause), 'error')
  } finally {
    moodSaving.value = false
  }
}

function openMood() {
  const currentMood = moodFor(user.value?.id)
  moodVersion.value = currentMood?.version
  moodConflict.value = false
  Object.assign(moodForm, currentMood
    ? { emoji: currentMood.emoji, label: currentMood.label, note: currentMood.note || '' }
    : { emoji: '😊', label: '开心', note: '' })
  moodOpen.value = true
}

async function loadLatestMood() {
  const result = await load()
  if (result === false) return
  const currentMood = moodFor(user.value?.id)
  moodVersion.value = currentMood?.version
  Object.assign(moodForm, currentMood
    ? { emoji: currentMood.emoji, label: currentMood.label, note: currentMood.note || '' }
    : { emoji: '😊', label: '开心', note: '' })
  moodConflict.value = false
  show('已加载最新心情，请确认后再保存。', 'info')
}

async function pickRandom() {
  if (randomLoading.value) return
  randomLoading.value = true
  try {
    selectedRandom.value = await api.randomMemory(selectedRandom.value?.id)
  } catch (cause) {
    show(errorMessage(cause), 'info')
  } finally {
    randomLoading.value = false
  }
}

function burst() {
  heartBurst.value += 1
}

function firstVisual(memory: Memory): MediaItem | undefined {
  return memory.media.find((item) => {
    const type = (item.contentType || item.mediaType).toLowerCase()
    return type.includes('image') || type.includes('video')
  })
}

function visualType(media?: MediaItem) {
  const type = (media?.contentType || media?.mediaType || '').toLowerCase()
  return type.includes('video') ? 'video' : 'image'
}

function visualUrl(media?: MediaItem) {
  return media ? mediaUrl(media.id, media.url) : ''
}
</script>

<template>
  <LoadingState v-if="loading" />
  <div v-else-if="loadError" class="error-panel" role="alert">
    <span>暂时没能打开今天的小屋。</span><p>{{ loadError }}</p><button class="button secondary" type="button" @click="load"><RefreshCw :size="17" />重新试试</button>
  </div>
  <div v-else class="dashboard page-stack">
    <section class="hero-card">
      <div class="hero-copy">
        <p class="eyebrow"><Sparkles :size="14" /> OUR LITTLE HOME</p>
        <h1>{{ spaceName }}</h1>
        <p class="hero-greeting">嗨，{{ user?.nickname || '你' }}。今天也是认真相爱的第 {{ duration.days + 1 }} 天。</p>
      </div>
      <div class="couple-row">
        <div class="person"><BaseAvatar :user="user" size="lg" /><span>{{ user?.nickname || '我' }}</span></div>
        <button class="heart-seal" type="button" aria-label="送出一颗爱心" @click="burst">
          <Heart :size="25" fill="currentColor" />
          <span v-for="n in 5" v-if="heartBurst" :key="`${heartBurst}-${n}`" class="burst-heart" :style="{ '--i': n }">♥</span>
        </button>
        <div class="person"><BaseAvatar :user="partner" size="lg" /><span>{{ partner?.nickname || '心上人' }}</span></div>
      </div>
      <div class="love-counter" aria-label="在一起时长">
        <div><strong>{{ duration.days }}</strong><span>天</span></div><i></i>
        <div><strong>{{ String(duration.hours).padStart(2, '0') }}</strong><span>时</span></div><i></i>
        <div><strong>{{ String(duration.minutes).padStart(2, '0') }}</strong><span>分</span></div><i></i>
        <div><strong>{{ String(duration.seconds).padStart(2, '0') }}</strong><span>秒</span></div>
      </div>
      <p class="counter-since"><Clock3 :size="14" /> 从 {{ formatDate(startedAt) }} 开始</p>
    </section>

    <div class="dashboard-grid">
      <section class="card mood-card">
        <div class="section-heading"><div><p class="eyebrow">TODAY</p><h2>今天的我们</h2></div><button class="text-button" type="button" @click="openMood">记录心情 <ArrowRight :size="15" /></button></div>
        <div class="mood-pair">
          <div class="mood-person"><BaseAvatar :user="user" size="sm" /><div><small>{{ user?.nickname || '我' }}</small><strong>{{ moodFor(user?.id)?.emoji || '🌤️' }} {{ moodFor(user?.id)?.label || '等你记录' }}</strong><p>{{ moodFor(user?.id)?.note || '给今天留一句轻轻的话吧' }}</p></div></div>
          <div class="mood-person"><BaseAvatar :user="partner" size="sm" /><div><small>{{ partner?.nickname || '对方' }}</small><strong>{{ moodFor(partner?.id)?.emoji || '💭' }} {{ moodFor(partner?.id)?.label || '还没记录' }}</strong><p>{{ moodFor(partner?.id)?.note || '稍后再来看看 TA 的心情' }}</p></div></div>
        </div>
      </section>

      <section class="card anniversary-card">
        <div class="section-heading"><div><p class="eyebrow">COUNTDOWN</p><h2>下一个重要日子</h2></div><RouterLink class="text-button" to="/anniversaries">全部 <ArrowRight :size="15" /></RouterLink></div>
        <div v-if="anniversaries.length" class="anniversary-preview">
          <span class="calendar-icon"><CalendarHeart :size="22" /></span>
          <div><strong>{{ anniversaries[0].title }}</strong><p>{{ formatDate(anniversaries[0].eventDate) }} · {{ anniversaries[0].note || '值得期待的好日子' }}</p></div>
          <div class="countdown-pill"><strong>{{ countdownParts(anniversaries[0]).value }}</strong><span>{{ countdownParts(anniversaries[0]).unit }}</span></div>
        </div>
        <p v-if="dueReminders.length" class="reminder-note"><BellRing :size="15" />{{ dueReminders.map(item => item.title).join('、') }} 的提醒已经到啦</p>
        <EmptyState v-if="!anniversaries.length" title="还没有纪念日" description="把那个重要日子加进来，就不会错过啦。"><RouterLink class="button small secondary" to="/anniversaries">添加一个</RouterLink></EmptyState>
      </section>
    </div>

    <section class="card memory-section">
      <div class="section-heading"><div><p class="eyebrow">RECENT MOMENTS</p><h2>最近收藏的回忆</h2></div><RouterLink class="text-button" to="/memories">走进时间线 <ArrowRight :size="15" /></RouterLink></div>
      <div v-if="memories.length" class="memory-preview-grid">
        <article v-for="memory in memories" :key="memory.id" class="memory-preview">
          <div class="preview-media">
            <img v-if="firstVisual(memory) && visualType(firstVisual(memory)) === 'image'" :src="visualUrl(firstVisual(memory))" :alt="`${memory.title} 的照片`" loading="lazy" />
            <video v-else-if="firstVisual(memory)" :src="visualUrl(firstVisual(memory))" controls preload="metadata" :aria-label="`${memory.title} 的视频`"></video>
            <Camera v-else :size="24" aria-hidden="true" />
          </div>
          <div class="preview-copy"><small>{{ formatDate(memory.eventAt, { month: 'short', day: 'numeric' }) }}</small><h3>{{ memory.title }}</h3><p v-if="memory.location"><MapPin :size="13" />{{ memory.location }}</p></div>
        </article>
      </div>
      <EmptyState v-else title="第一段回忆正在等你" description="一张照片、一句话，都能成为以后想念的入口。"><RouterLink class="button small primary" to="/memories">收藏第一条</RouterLink></EmptyState>
    </section>

    <div class="dashboard-grid lower-grid">
      <section class="card letter-preview-card">
        <div class="section-heading"><div><p class="eyebrow">A NOTE FOR YOU</p><h2>一封小小信笺</h2></div><RouterLink class="text-button" to="/letters">去信箱 <ArrowRight :size="15" /></RouterLink></div>
        <div v-if="letters.length" class="mini-letter"><MailHeart :size="25" /><blockquote v-if="letters[0].content">“{{ letters[0].content }}”</blockquote><RouterLink v-else class="sealed-dashboard-letter" to="/letters">有一封信等你亲手拆开</RouterLink><small>{{ letterPending(letters[0]) ? `等待 ${formatDateTime(letters[0].deliverAt)} 送达` : `${letterAuthorName(letters[0])} · ${formatDateTime(letters[0].createdAt)}` }}</small></div>
        <EmptyState v-else title="信箱里安安静静" description="写一句不着急被回复的话，也很浪漫。"><RouterLink class="button small secondary" to="/letters">写一封信</RouterLink></EmptyState>
      </section>
      <section class="card easter-card">
        <span class="easter-spark"><Sparkles :size="28" /></span>
        <p class="eyebrow">A LITTLE SURPRISE</p><h2>抽一张回忆签</h2><p>让某个旧日瞬间，随机落回今天。</p>
        <button class="button secondary" type="button" :disabled="randomLoading" @click="pickRandom"><span v-if="randomLoading" class="button-spinner"></span><MessageCircleHeart v-else :size="18" />{{ randomLoading ? '正在抽取…' : '看看今天想起什么' }}</button>
      </section>
    </div>
  </div>

  <BaseModal v-if="moodOpen" title="今天是什么心情？" description="每天一条，随时可以更新。" @close="moodOpen = false">
    <form class="stack-form" @submit.prevent="saveMood">
      <div v-if="moodConflict" class="conflict-panel" role="alert"><p>{{ STALE_UPDATE_MESSAGE }}</p><button class="button secondary small" type="button" @click="loadLatestMood">加载最新内容</button></div>
      <div class="mood-choice-grid" role="radiogroup" aria-label="选择心情">
        <button v-for="choice in moodChoices" :key="choice.label" type="button" role="radio" :aria-checked="moodForm.label === choice.label" :class="{ selected: moodForm.label === choice.label }" @click="moodForm.emoji = choice.emoji; moodForm.label = choice.label"><span>{{ choice.emoji }}</span>{{ choice.label }}</button>
      </div>
      <label class="field"><span>想留给对方的一句话（可选）</span><textarea v-model="moodForm.note" maxlength="120" rows="3" placeholder="例如：今天有点忙，但还是很想你。"></textarea><small>{{ moodForm.note.length }}/120</small></label>
      <button class="button primary full" type="submit" :disabled="moodSaving"><span v-if="moodSaving" class="button-spinner"></span><Heart v-else :size="18" />{{ moodSaving ? '正在保存…' : '放到首页' }}</button>
    </form>
  </BaseModal>

  <BaseModal v-if="selectedRandom" title="今日回忆签" @close="selectedRandom = null">
    <div class="random-memory">
      <div class="random-photo">
        <img v-if="firstVisual(selectedRandom) && visualType(firstVisual(selectedRandom)) === 'image'" :src="visualUrl(firstVisual(selectedRandom))" :alt="`${selectedRandom.title} 的照片`" />
        <video v-else-if="firstVisual(selectedRandom)" :src="visualUrl(firstVisual(selectedRandom))" controls preload="metadata" :aria-label="`${selectedRandom.title} 的视频`"></video>
        <Sparkles v-else :size="34" />
      </div>
      <p class="eyebrow">{{ formatDate(selectedRandom.eventAt) }}</p><h3>{{ selectedRandom.title }}</h3><p>{{ selectedRandom.description || '当时没有写下很多，但这一刻依然被好好保存着。' }}</p>
      <button class="button secondary full" type="button" :disabled="randomLoading" @click="pickRandom"><span v-if="randomLoading" class="button-spinner"></span><RefreshCw v-else :size="17" />{{ randomLoading ? '正在抽取…' : '再抽一张' }}</button>
    </div>
  </BaseModal>
</template>
