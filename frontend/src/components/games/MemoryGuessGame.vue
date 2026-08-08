<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { CalendarDays, Check, ImageOff, LoaderCircle, MapPin, Sparkles, Trophy } from 'lucide-vue-next'
import { api } from '../../api'
import { errorMessage } from '../../api/client'
import { useToast } from '../../composables/toast'
import { authState } from '../../stores/auth'
import type { GameSession } from '../../types'
import { formatDate } from '../../utils'

const props = defineProps<{ session: GameSession }>()
const emit = defineEmits<{ updated: [session: GameSession] }>()
const { show } = useToast()
const selected = ref(props.session.myAnswer || '')
const busy = ref(false)
const imageReady = ref(false)
const imageFailed = ref(false)
const imageKey = ref(0)
const promptHeading = ref<HTMLElement | null>(null)

const active = computed(() => props.session.status === 'ACTIVE')
const revealed = computed(() => props.session.answersRevealed || props.session.status === 'FINISHED')
const myCorrect = computed(() => Boolean(
  props.session.myAnswer && props.session.secretWord && props.session.myAnswer === props.session.secretWord,
))
const partnerCorrect = computed(() => Boolean(
  props.session.partnerAnswer && props.session.secretWord && props.session.partnerAnswer === props.session.secretWord,
))

watch(() => props.session.myAnswer, (answer) => { selected.value = answer || '' })
watch(() => [props.session.roundNumber, props.session.memory?.imageUrl], async () => {
  imageReady.value = false
  imageFailed.value = false
  imageKey.value += 1
  await nextTick()
  promptHeading.value?.focus()
})

async function answer(value: string) {
  if (!active.value || props.session.myAnswer || busy.value || !imageReady.value) return
  selected.value = value
  busy.value = true
  try {
    emit('updated', await api.answerGame(props.session.id, value))
  } catch (cause) {
    show(errorMessage(cause), 'error')
    try {
      const latest = await api.game(props.session.id)
      selected.value = latest.myAnswer || ''
      emit('updated', latest)
    } catch {
      selected.value = props.session.myAnswer || ''
    }
  } finally {
    busy.value = false
  }
}

async function nextRound() {
  if (!active.value || busy.value) return
  busy.value = true
  try {
    emit('updated', await api.nextGameRound(props.session.id, props.session.roundNumber))
  } catch (cause) {
    show(errorMessage(cause), 'error')
    try { emit('updated', await api.game(props.session.id)) } catch { /* 后续同步事件会再次刷新。 */ }
  } finally {
    busy.value = false
  }
}

function retryImage() {
  imageFailed.value = false
  imageReady.value = false
  imageKey.value += 1
}
</script>

<template>
  <section class="memory-guess-game">
    <header class="memory-game-heading">
      <div>
        <span>第 {{ session.roundNumber }} 张回忆</span>
        <h2 ref="promptHeading" tabindex="-1">{{ session.prompt }}</h2>
      </div>
      <div class="memory-score" aria-label="累计猜中次数">
        <Trophy :size="18" aria-hidden="true" />
        <strong>{{ session.score }}</strong>
        <small>次猜中</small>
      </div>
    </header>

    <div class="memory-play-area">
      <div class="memory-photo" :class="{ loading: !imageReady && !imageFailed }">
        <img
          v-if="session.memory?.imageUrl && !imageFailed"
          :key="imageKey"
          :src="session.memory.imageUrl"
          :alt="revealed && session.memory.title ? session.memory.title : '本轮待猜的共同回忆照片'"
          @load="imageReady = true"
          @error="imageFailed = true"
        />
        <LoaderCircle v-if="session.memory?.imageUrl && !imageReady && !imageFailed" class="spin photo-loader" :size="28" aria-label="照片加载中" />
        <div v-if="imageFailed || !session.memory?.imageUrl" class="photo-error" role="status">
          <ImageOff :size="30" aria-hidden="true" />
          <strong>这张照片暂时没有加载出来</strong>
          <p>检查网络后可以重新加载；本轮答案仍安全保留。</p>
          <button class="button secondary small" type="button" @click="retryImage">重新加载照片</button>
        </div>
      </div>

      <div class="memory-question-panel">
        <p class="memory-instruction">先看照片凭第一印象选择，双方提交后才会揭晓完整回忆。</p>
        <div class="memory-options" role="group" :aria-label="session.prompt || '回忆答案选项'" :aria-busy="busy">
          <button
            v-for="option in session.options"
            :key="option"
            type="button"
            :class="{
              selected: selected === option,
              correct: revealed && option === session.secretWord,
              incorrect: revealed && selected === option && option !== session.secretWord,
            }"
            :aria-pressed="selected === option"
            :disabled="!active || !!session.myAnswer || busy || !imageReady"
            @click="answer(option)"
          >
            <Check v-if="selected === option || (revealed && option === session.secretWord)" :size="17" aria-hidden="true" />
            <span>{{ option }}</span>
          </button>
        </div>

        <div v-if="revealed" class="memory-result" role="status" aria-live="polite">
          <div class="result-title">
            <Sparkles :size="22" aria-hidden="true" />
            <div>
              <strong>{{ myCorrect ? '你猜中了这段回忆' : '答案已经揭晓' }}</strong>
              <p>正确答案：{{ session.secretWord || '未记录' }}</p>
            </div>
          </div>
          <dl class="answer-pair">
            <div><dt>{{ authState.user?.nickname || '我' }}</dt><dd :class="{ right: myCorrect }">{{ session.myAnswer || '本轮未作答' }}</dd></div>
            <div><dt>{{ authState.partner?.nickname || 'TA' }}</dt><dd :class="{ right: partnerCorrect }">{{ session.partnerAnswer || '本轮未作答' }}</dd></div>
          </dl>
          <div class="memory-story">
            <h3>{{ session.memory?.title || '这段共同回忆' }}</h3>
            <p class="memory-meta">
              <span><CalendarDays :size="15" aria-hidden="true" />{{ formatDate(session.memory?.eventAt || undefined) }}</span>
              <span v-if="session.memory?.location"><MapPin :size="15" aria-hidden="true" />{{ session.memory.location }}</span>
            </p>
            <p class="memory-description">{{ session.memory?.description || '照片已经替你们记住了那一天。' }}</p>
          </div>
          <button v-if="active" class="button primary" type="button" :disabled="busy" @click="nextRound">
            <LoaderCircle v-if="busy" class="spin" :size="17" aria-hidden="true" />
            <Sparkles v-else :size="17" aria-hidden="true" />下一张回忆
          </button>
        </div>
        <div v-else-if="session.myAnswer" class="memory-waiting" role="status" aria-live="polite">
          <LoaderCircle class="spin" :size="18" aria-hidden="true" />
          <span>你的答案已经藏好，等待 {{ authState.partner?.nickname || 'TA' }}…</span>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.memory-guess-game { display: grid; gap: 22px; }
.memory-game-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; }
.memory-game-heading span { color: var(--rose-dark); font-size: 12px; font-weight: 800; }
.memory-game-heading h2 { margin: 6px 0 0; max-width: 22ch; overflow-wrap: anywhere; font-size: clamp(22px, 4vw, 31px); }
.memory-game-heading h2:focus { outline: none; }
.memory-score { min-width: 90px; display: grid; grid-template-columns: auto auto; align-items: center; justify-content: center; gap: 2px 7px; padding: 11px; border-radius: 16px; background: #fff5dd; color: #9a6628; }
.memory-score strong { font-size: 19px; }.memory-score small { grid-column: 1 / -1; text-align: center; font-size: 11px; }
.memory-play-area { display: grid; grid-template-columns: minmax(0, 1.08fr) minmax(300px, .92fr); align-items: start; gap: clamp(18px, 3vw, 30px); }
.memory-photo { position: relative; aspect-ratio: 4 / 3; min-height: 260px; display: grid; place-items: center; overflow: hidden; border-radius: 16px; background: linear-gradient(145deg, #f7e7e5, #eee5d6); }
.memory-photo img { width: 100%; height: 100%; display: block; object-fit: contain; background: #241e20; }
.photo-loader { color: var(--rose-dark); }.photo-error { max-width: 330px; display: grid; justify-items: center; gap: 8px; padding: 24px; color: #7b5f65; text-align: center; }
.photo-error strong { color: var(--ink); }.photo-error p { margin: 0 0 4px; font-size: 13px; line-height: 1.6; }
.memory-question-panel { min-width: 0; display: grid; gap: 14px; }
.memory-instruction { margin: 0; color: var(--muted); font-size: 14px; line-height: 1.7; }
.memory-options { display: grid; gap: 10px; }
.memory-options button { min-height: 52px; display: flex; align-items: center; gap: 9px; padding: 12px 14px; border: 1px solid var(--line); border-radius: 14px; background: white; color: var(--ink); cursor: pointer; text-align: left; font-weight: 750; transition: border-color .18s ease, background .18s ease, transform .18s ease; }
.memory-options button:hover:not(:disabled) { transform: translateY(-1px); border-color: #e7a8b2; background: #fff8f8; }
.memory-options button.selected { border-color: #df8f9d; background: var(--rose-pale); color: var(--rose-dark); }
.memory-options button.correct { border-color: #92ad8a; background: #edf5e9; color: #536c4e; }
.memory-options button.incorrect { border-color: #e3a3ad; background: #fff0f2; color: #a44455; }
.memory-options button:disabled { cursor: default; opacity: .72; }.memory-options button.selected, .memory-options button.correct { opacity: 1; }
.memory-options span { min-width: 0; overflow-wrap: anywhere; }
.memory-result { display: grid; gap: 14px; padding: 16px; border-radius: 16px; background: #f8f3ef; }
.result-title { display: flex; align-items: flex-start; gap: 10px; color: var(--rose-dark); }.result-title > div { min-width: 0; }
.result-title strong { color: var(--ink); }.result-title p { margin: 3px 0 0; overflow-wrap: anywhere; color: var(--muted); font-size: 13px; }
.answer-pair { display: grid; grid-template-columns: 1fr 1fr; gap: 9px; margin: 0; }
.answer-pair > div { min-width: 0; padding: 10px; border-radius: 12px; background: white; }.answer-pair dt { color: var(--muted); font-size: 11px; }.answer-pair dd { margin: 4px 0 0; overflow-wrap: anywhere; font-size: 13px; font-weight: 750; }.answer-pair dd.right { color: #5c7956; }
.memory-story { padding-top: 13px; border-top: 1px solid #e7d8d4; }.memory-story h3 { margin: 0 0 7px; overflow-wrap: anywhere; font-size: 20px; }
.memory-meta { display: flex; flex-wrap: wrap; gap: 7px 14px; margin: 0; color: var(--muted); font-size: 12px; }.memory-meta span { display: inline-flex; align-items: center; gap: 4px; }
.memory-description { margin: 10px 0 0; white-space: pre-wrap; overflow-wrap: anywhere; color: #67545a; font-size: 14px; line-height: 1.8; }
.memory-result > .button { justify-self: start; }.memory-waiting { min-height: 52px; display: flex; align-items: center; gap: 10px; padding: 13px 15px; border-radius: 14px; background: #f5f0ec; color: var(--muted); font-size: 13px; }
.spin { animation: spin 1s linear infinite; }
@media (max-width: 780px) {
  .memory-play-area { grid-template-columns: 1fr; }.memory-photo { min-height: 220px; }.memory-question-panel { gap: 12px; }
}
@media (max-width: 520px) {
  .memory-game-heading { gap: 12px; }.memory-score { min-width: 78px; }.memory-photo { min-height: 190px; }
  .answer-pair { grid-template-columns: 1fr; }.memory-result > .button { width: 100%; }
}
@media (prefers-reduced-motion: reduce) {
  .memory-options button { transition: none; }.memory-options button:hover:not(:disabled) { transform: none; }
}
</style>
