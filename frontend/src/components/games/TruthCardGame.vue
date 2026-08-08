<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { Heart, LoaderCircle, MessageCircleHeart, RefreshCw, ShieldCheck, Sparkles } from 'lucide-vue-next'
import { api } from '../../api'
import { errorMessage } from '../../api/client'
import { useToast } from '../../composables/toast'
import { authState } from '../../stores/auth'
import type { GameSession } from '../../types'
import { sameId } from '../../utils'

const props = defineProps<{ session: GameSession }>()
const emit = defineEmits<{ updated: [session: GameSession] }>()
const { show } = useToast()
const busy = ref(false)
const revealed = ref(props.session.status === 'FINISHED')
const cardHeading = ref<HTMLElement | null>(null)

const active = computed(() => props.session.status === 'ACTIVE')
const isMyTurn = computed(() => sameId(props.session.currentTurnUserId, authState.user?.id))
const answererName = computed(() => isMyTurn.value
  ? authState.user?.nickname || '你'
  : authState.partner?.nickname || 'TA')
const categoryTone = computed(() => {
  if (props.session.cardCategory === '认真聊聊') return 'deep'
  if (props.session.cardCategory === '心动时刻') return 'heart'
  return 'light'
})

watch(() => props.session.roundNumber, async () => {
  revealed.value = props.session.status === 'FINISHED'
  await nextTick()
  cardHeading.value?.focus()
})
watch(() => props.session.status, (status) => {
  if (status === 'FINISHED') revealed.value = true
})

async function advance() {
  if (!active.value || !isMyTurn.value || busy.value) return
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
</script>

<template>
  <section class="truth-card-game">
    <header class="truth-heading">
      <div>
        <span>第 {{ session.roundNumber }} 张</span>
        <h2 ref="cardHeading" tabindex="-1">轮到 {{ answererName }} 分享</h2>
      </div>
      <div class="turn-badge" :class="{ mine: isMyTurn }">
        <MessageCircleHeart :size="18" aria-hidden="true" />
        {{ isMyTurn ? '你的回合' : `听 ${answererName} 说` }}
      </div>
    </header>

    <div class="truth-stage" :class="categoryTone">
      <button v-if="!revealed" class="truth-card-back" type="button" @click="revealed = true">
        <span class="card-mark"><Heart :size="34" fill="currentColor" aria-hidden="true" /></span>
        <strong>翻开这张真心话</strong>
        <small>没有标准答案，只要认真听见彼此</small>
      </button>

      <article v-else class="truth-card-face" aria-live="polite">
        <div class="card-topline">
          <span>{{ session.cardCategory || '真心话' }}</span>
          <Sparkles :size="19" aria-hidden="true" />
        </div>
        <p>{{ session.prompt }}</p>
        <div class="card-signature"><Heart :size="17" fill="currentColor" aria-hidden="true" /> LOVE SPACE</div>
      </article>
    </div>

    <div class="truth-guidance" role="status" aria-live="polite">
      <template v-if="active && revealed && isMyTurn">
        <p>慢慢回答就好；不想聊这题，也可以直接跳过，没有惩罚。</p>
        <div class="truth-actions">
          <button class="button primary" type="button" :disabled="busy" @click="advance">
            <LoaderCircle v-if="busy" class="spin" :size="17" aria-hidden="true" />
            <MessageCircleHeart v-else :size="17" aria-hidden="true" />聊完了，下一张
          </button>
          <button class="button ghost" type="button" :disabled="busy" @click="advance">
            <RefreshCw :size="16" aria-hidden="true" />跳过这张
          </button>
        </div>
      </template>
      <template v-else-if="active && revealed">
        <LoaderCircle class="spin" :size="18" aria-hidden="true" />
        <p>这一张属于 {{ answererName }}。认真听完，下一张就轮到你。</p>
      </template>
      <template v-else-if="active">
        <p>翻开后再决定要不要回答，题目没有倒计时。</p>
      </template>
      <template v-else>
        <p>这局一共聊到了第 {{ session.roundNumber }} 张，想继续时可以再开一局。</p>
      </template>
    </div>

    <p class="privacy-note"><ShieldCheck :size="16" aria-hidden="true" />回答只留在你们的对话里，系统不会保存内容。</p>
  </section>
</template>

<style scoped>
.truth-card-game { display: grid; gap: 22px; }
.truth-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; }
.truth-heading span { color: var(--rose-dark); font-size: 12px; font-weight: 800; }
.truth-heading h2 { margin: 6px 0 0; overflow-wrap: anywhere; font-size: clamp(22px, 4vw, 31px); }.truth-heading h2:focus { outline: none; }
.turn-badge { min-height: 44px; display: inline-flex; align-items: center; gap: 7px; padding: 9px 13px; border-radius: 14px; background: #f3efec; color: #725f65; font-size: 13px; font-weight: 800; }
.turn-badge.mine { background: var(--rose-pale); color: var(--rose-dark); }
.truth-stage { width: min(100%, 660px); min-height: 340px; display: grid; place-items: stretch; justify-self: center; perspective: 1000px; }
.truth-card-back, .truth-card-face { min-height: 340px; border-radius: 16px; }
.truth-card-back { display: grid; align-content: center; justify-items: center; gap: 12px; padding: 30px; border: 1px solid #dca5ae; background: radial-gradient(circle at 50% 36%, #f58c99, #d9586d 64%, #bd435a); color: white; cursor: pointer; box-shadow: 0 18px 42px rgba(157, 53, 73, .24); }
.truth-card-back::before { content: ""; position: absolute; inset: 12px; border: 1px solid rgba(255,255,255,.38); border-radius: 11px; pointer-events: none; }
.truth-card-back { position: relative; }.card-mark { width: 72px; height: 72px; display: grid; place-items: center; border-radius: 50%; background: rgba(255,255,255,.17); }
.truth-card-back strong { max-width: 9ch; overflow-wrap: anywhere; text-align: center; line-height: 1.35; font-size: clamp(22px, 4vw, 30px); }.truth-card-back small { max-width: 23ch; color: rgba(255,255,255,.86); text-align: center; font-size: 13px; line-height: 1.6; }
.truth-card-back:focus-visible { outline: 3px solid #74434d; outline-offset: 4px; }
.truth-card-face { display: flex; flex-direction: column; justify-content: space-between; gap: 30px; padding: clamp(27px, 6vw, 52px); border: 1px solid #e2c5aa; background: #fffaf0; color: #4f3e3b; box-shadow: 0 18px 42px rgba(91, 62, 47, .15); animation: cardReveal 420ms cubic-bezier(.16, 1, .3, 1) both; }
.truth-stage.heart .truth-card-face { border-color: #e5b8c0; background: #fff5f5; }.truth-stage.deep .truth-card-face { border-color: #b9c8b6; background: #f3f7ef; }
.card-topline { display: flex; align-items: center; justify-content: space-between; gap: 12px; color: #9b6048; font-size: 13px; font-weight: 850; }.heart .card-topline { color: var(--rose-dark); }.deep .card-topline { color: #5e795a; }
.truth-card-face > p { max-width: 20ch; margin: auto; overflow-wrap: anywhere; color: var(--ink); text-align: center; text-wrap: balance; font: 700 clamp(24px, 5vw, 38px)/1.55 STKaiti, KaiTi, serif; }
.card-signature { display: flex; align-items: center; justify-content: flex-end; gap: 6px; color: #b9836e; font-size: 11px; font-weight: 800; letter-spacing: .08em; }
.truth-guidance { min-height: 72px; display: flex; align-items: center; justify-content: center; gap: 11px; color: var(--muted); text-align: center; }
.truth-guidance > p { max-width: 60ch; margin: 0; font-size: 14px; line-height: 1.7; }.truth-guidance > template { display: contents; }
.truth-actions { display: flex; flex-wrap: wrap; justify-content: center; gap: 9px; }.truth-actions .button { min-height: 44px; }
.privacy-note { justify-self: center; display: flex; align-items: center; gap: 6px; margin: 0; color: #62735f; font-size: 12px; }
.spin { animation: spin 1s linear infinite; }
@keyframes cardReveal {
  from { opacity: .7; transform: rotateY(-10deg) translateY(8px); filter: blur(4px); clip-path: inset(5% 3% round 16px); box-shadow: 0 7px 16px rgba(91,62,47,.08); }
  to { opacity: 1; transform: none; filter: blur(0); clip-path: inset(0 round 16px); box-shadow: 0 18px 42px rgba(91,62,47,.15); }
}
@media (max-width: 620px) {
  .truth-heading { flex-direction: column; gap: 12px; }.turn-badge { align-self: flex-start; white-space: nowrap; }
  .truth-stage, .truth-card-back, .truth-card-face { min-height: 310px; }.truth-card-face { padding: 28px 22px; }
  .truth-guidance { flex-direction: column; }.truth-actions { width: 100%; }.truth-actions .button { flex: 1 1 170px; }
}
@media (prefers-reduced-motion: reduce) {
  .truth-card-face { animation: none; }.truth-card-back { scroll-behavior: auto; }
}
</style>
