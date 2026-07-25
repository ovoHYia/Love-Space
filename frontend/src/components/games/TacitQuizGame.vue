<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Check, Heart, LoaderCircle, Sparkles } from 'lucide-vue-next'
import { api } from '../../api'
import { errorMessage } from '../../api/client'
import { useToast } from '../../composables/toast'
import { authState } from '../../stores/auth'
import type { GameSession } from '../../types'

const props = defineProps<{ session: GameSession }>()
const emit = defineEmits<{ updated: [session: GameSession] }>()
const { show } = useToast()
const selected = ref(props.session.myAnswer || '')
const busy = ref(false)
const active = computed(() => props.session.status === 'ACTIVE')

watch(() => props.session.myAnswer, (answer) => { selected.value = answer || '' })

async function answer(value: string) {
  if (!active.value || props.session.myAnswer || busy.value) return
  selected.value = value
  busy.value = true
  try {
    emit('updated', await api.answerGame(props.session.id, value))
  } catch (cause) {
    selected.value = ''
    show(errorMessage(cause), 'error')
  } finally {
    busy.value = false
  }
}

async function nextRound() {
  busy.value = true
  try {
    emit('updated', await api.nextGameRound(props.session.id))
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="tacit-game">
    <header>
      <div><span>第 {{ session.roundNumber }} 题</span><h2>{{ session.prompt }}</h2></div>
      <div class="score"><Heart :size="17" fill="currentColor" /><strong>{{ session.score }}</strong><small>默契</small></div>
    </header>

    <div class="quiz-options" :aria-busy="busy">
      <button
        v-for="option in session.options"
        :key="option"
        type="button"
        :class="{ selected: selected === option, revealed: session.answersRevealed }"
        :disabled="!active || !!session.myAnswer || busy"
        @click="answer(option)"
      >
        <Check v-if="selected === option" :size="17" />{{ option }}
      </button>
    </div>

    <div v-if="session.answersRevealed" class="quiz-result" :class="{ matched: session.matched }">
      <Sparkles :size="24" />
      <div>
        <strong>{{ session.matched ? '心有灵犀！' : '答案不同也很可爱' }}</strong>
        <p>{{ authState.user?.nickname }}：{{ session.myAnswer }}　·　{{ authState.partner?.nickname || 'TA' }}：{{ session.partnerAnswer }}</p>
      </div>
      <button v-if="active" class="button primary small" type="button" :disabled="busy" @click="nextRound">
        <LoaderCircle v-if="busy" class="spin" :size="16" />下一题
      </button>
    </div>
    <div v-else-if="session.myAnswer" class="waiting-answer">
      <LoaderCircle class="spin" :size="18" /><span>答案已经藏好，等待 {{ authState.partner?.nickname || 'TA' }}…</span>
    </div>
    <p v-else class="quiz-tip">先凭第一感觉作答，双方提交后才会同时揭晓。</p>
  </section>
</template>

<style scoped>
.tacit-game { display: grid; gap: 22px; }
header { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; }
header span { color: var(--rose-dark); font-size: 11px; font-weight: 800; }
h2 { margin: 6px 0 0; font-size: clamp(22px, 4vw, 31px); }
.score { min-width: 82px; display: grid; grid-template-columns: auto auto; align-items: center; justify-content: center; gap: 2px 6px; padding: 11px; border-radius: 16px; background: var(--rose-pale); color: var(--rose-dark); }
.score small { grid-column: 1 / -1; text-align: center; font-size: 9px; }.score strong { font-size: 19px; }
.quiz-options { display: grid; grid-template-columns: 1fr 1fr; gap: 11px; }
.quiz-options button { min-height: 64px; padding: 13px; border: 1px solid var(--line); border-radius: 15px; background: white; color: var(--ink); cursor: pointer; font-weight: 750; transition: .18s ease; }
.quiz-options button:hover:not(:disabled), .quiz-options button.selected { border-color: #e8a8b2; background: var(--rose-pale); color: var(--rose-dark); }
.quiz-options button:disabled { cursor: default; opacity: .72; }.quiz-options button.selected { opacity: 1; }
.quiz-result, .waiting-answer { display: flex; align-items: center; gap: 12px; padding: 15px 17px; border-radius: 16px; background: #f6f1ed; color: var(--muted); }
.quiz-result.matched { background: linear-gradient(135deg, #fff0f2, #fff8e7); color: var(--rose-dark); }
.quiz-result > div { flex: 1; }.quiz-result strong { color: var(--ink); }.quiz-result p { margin: 4px 0 0; font-size: 11px; }
.quiz-tip { margin: 0; color: var(--muted); text-align: center; font-size: 11px; }.spin { animation: spin 1s linear infinite; }
@media (max-width: 620px) {
  .quiz-options { grid-template-columns: 1fr; }.quiz-result { align-items: flex-start; flex-wrap: wrap; }.quiz-result .button { width: 100%; }
}
</style>
