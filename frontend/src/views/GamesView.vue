<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ArrowLeft, Brush, Gamepad2, HeartHandshake, LoaderCircle, MessageCircleHeart, Sparkles, Square, Trophy } from 'lucide-vue-next'
import { api } from '../api'
import { errorMessage } from '../api/client'
import DrawGuessGame from '../components/games/DrawGuessGame.vue'
import TacitQuizGame from '../components/games/TacitQuizGame.vue'
import LoadingState from '../components/LoadingState.vue'
import { useToast } from '../composables/toast'
import { realtimeState } from '../stores/realtime'
import type { GameSession, GameType, SyncEvent } from '../types'
import { formatDateTime } from '../utils'

const { show } = useToast()
const sessions = ref<GameSession[]>([])
const selected = ref<GameSession | null>(null)
const loading = ref(true)
const busy = ref<GameType | 'finish' | null>(null)
const activeSessions = computed(() => sessions.value.filter((item) => item.status === 'ACTIVE'))
const recentSessions = computed(() => sessions.value.filter((item) => item.status === 'FINISHED').slice(0, 6))

onMounted(() => {
  window.addEventListener('love-space:sync', handleSync)
  void load()
})
onBeforeUnmount(() => window.removeEventListener('love-space:sync', handleSync))

async function load() {
  try {
    sessions.value = await api.games()
    if (selected.value) {
      selected.value = sessions.value.find((item) => String(item.id) === String(selected.value?.id)) || null
    }
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    loading.value = false
  }
}

async function handleSync(event: Event) {
  const detail = (event as CustomEvent<SyncEvent>).detail
  if (detail.resource !== 'games') return
  try {
    if (selected.value) updateSession(await api.game(selected.value.id))
    else await load()
  } catch {
    // A reconnect or another event will refresh the game again.
  }
}

async function start(gameType: GameType) {
  if (busy.value) return
  busy.value = gameType
  try {
    const game = await api.createGame(gameType)
    updateSession(game)
    selected.value = game
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    busy.value = null
  }
}

async function finish() {
  if (!selected.value || busy.value) return
  busy.value = 'finish'
  try {
    updateSession(await api.finishGame(selected.value.id))
    show('这局游戏已经收好，随时可以再来一局。', 'success')
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    busy.value = null
  }
}

function updateSession(game: GameSession) {
  const index = sessions.value.findIndex((item) => String(item.id) === String(game.id))
  if (index < 0) sessions.value.unshift(game)
  else sessions.value[index] = game
  if (String(selected.value?.id) === String(game.id)) selected.value = game
}

function gameName(type: GameType) {
  return type === 'TACIT_QUIZ' ? '默契问答' : '你画我猜'
}
</script>

<template>
  <div class="page-stack games-page">
    <LoadingState v-if="loading" message="正在打开游戏柜…" />

    <template v-else-if="selected">
      <header class="game-room-header">
        <button class="text-button" type="button" @click="selected = null"><ArrowLeft :size="17" />返回游戏柜</button>
        <div>
          <span :class="{ online: realtimeState.connected }">{{ realtimeState.connected ? '双端同步已连接' : '同步重连中' }}</span>
          <button v-if="selected.status === 'ACTIVE'" class="text-button muted" type="button" :disabled="busy === 'finish'" @click="finish">结束本局</button>
        </div>
      </header>
      <article class="game-room card">
        <TacitQuizGame v-if="selected.gameType === 'TACIT_QUIZ'" :session="selected" @updated="updateSession" />
        <DrawGuessGame v-else :session="selected" @updated="updateSession" />
      </article>
    </template>

    <template v-else>
      <header class="page-header games-hero">
        <div><p class="eyebrow"><Gamepad2 :size="16" />PLAY TOGETHER</p><h1>一起玩</h1><p>不比输赢，看看今天是谁更懂谁。</p></div>
        <div class="games-heart"><HeartHandshake :size="34" /></div>
      </header>

      <section v-if="activeSessions.length" class="active-games card">
        <div class="section-heading"><div><p class="eyebrow">CONTINUE</p><h2>正在玩的游戏</h2></div></div>
        <button v-for="game in activeSessions" :key="game.id" type="button" @click="selected = game">
          <component :is="game.gameType === 'TACIT_QUIZ' ? MessageCircleHeart : Brush" :size="22" />
          <span><strong>{{ gameName(game.gameType) }}</strong><small>第 {{ game.roundNumber }} 轮 · {{ game.createdByNickname }} 发起</small></span>
          <Sparkles :size="17" />
        </button>
      </section>

      <section class="game-grid">
        <article class="game-card quiz">
          <span><MessageCircleHeart :size="28" /></span>
          <p class="eyebrow">TACIT QUIZ</p><h2>默契问答</h2>
          <p>双方分别作答，提交后同时揭晓。答案相同就收获一颗默契心。</p>
          <button class="button primary" type="button" :disabled="!!busy" @click="start('TACIT_QUIZ')">
            <LoaderCircle v-if="busy === 'TACIT_QUIZ'" class="spin" :size="17" /><Sparkles v-else :size="17" />开始问答
          </button>
        </article>
        <article class="game-card draw">
          <span><Brush :size="28" /></span>
          <p class="eyebrow">DRAW & GUESS</p><h2>你画我猜</h2>
          <p>一个人画、一个人猜，每轮自动交换角色，画笔会同步到对方屏幕。</p>
          <button class="button primary" type="button" :disabled="!!busy" @click="start('DRAW_GUESS')">
            <LoaderCircle v-if="busy === 'DRAW_GUESS'" class="spin" :size="17" /><Brush v-else :size="17" />开始画画
          </button>
        </article>
        <article class="game-card coming">
          <span><Trophy :size="28" /></span>
          <p class="eyebrow">COMING SOON</p><h2>回忆猜猜看</h2><p>从共同照片中寻找地点、日期和那天的故事。</p>
          <button class="button ghost" type="button" disabled>正在准备</button>
        </article>
        <article class="game-card coming">
          <span><Square :size="28" /></span>
          <p class="eyebrow">COMING SOON</p><h2>真心话卡牌</h2><p>从轻松到认真，轮流抽一张只属于你们的问题。</p>
          <button class="button ghost" type="button" disabled>正在准备</button>
        </article>
      </section>

      <section v-if="recentSessions.length" class="recent-games card">
        <div class="section-heading"><div><p class="eyebrow">RECENT</p><h2>最近玩过</h2></div></div>
        <button v-for="game in recentSessions" :key="game.id" type="button" @click="selected = game">
          <span><strong>{{ gameName(game.gameType) }}</strong><small>{{ formatDateTime(game.finishedAt || game.updatedAt) }}</small></span>
          <em>{{ game.score }} {{ game.gameType === 'TACIT_QUIZ' ? '次默契' : '次猜中' }}</em>
        </button>
      </section>
    </template>
  </div>
</template>

<style scoped>
.games-page { max-width: 1080px; margin: 0 auto; }.games-hero { position: relative; padding: 27px 31px; overflow: hidden; border: 1px solid #edd9dc; border-radius: var(--radius-lg); background: linear-gradient(130deg, #fffdfb, #fff0f1 62%, #ffe4d5); box-shadow: var(--shadow-sm); }
.games-hero h1 { margin-bottom: 5px; }.games-hero > div:first-child > p:last-child { margin: 0; color: var(--muted); }.games-heart { width: 76px; height: 76px; display: grid; place-items: center; border-radius: 25px; background: rgba(255,255,255,.74); color: var(--rose); transform: rotate(5deg); }
.game-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 15px; }.game-card { min-height: 285px; display: flex; flex-direction: column; align-items: flex-start; padding: 23px; overflow: hidden; border: 1px solid var(--line); border-radius: var(--radius); box-shadow: var(--shadow-sm); }
.game-card.quiz { background: linear-gradient(145deg, #fffafa, #fff0f3); }.game-card.draw { background: linear-gradient(145deg, #fffdf8, #f7f1df); }.game-card.coming { min-height: 235px; background: #faf6f3; }
.game-card > span { width: 50px; height: 50px; display: grid; place-items: center; margin-bottom: 18px; border-radius: 16px; background: white; color: var(--rose-dark); box-shadow: var(--shadow-sm); }
.game-card h2 { margin: 0; font-size: 25px; }.game-card > p:not(.eyebrow) { flex: 1; margin: 8px 0 20px; color: var(--muted); font-size: 12px; line-height: 1.7; }.game-card .button { min-width: 135px; }
.active-games, .recent-games { padding: 19px 21px; }.active-games > button, .recent-games > button { width: 100%; display: flex; align-items: center; gap: 11px; padding: 12px; border: 0; border-radius: 13px; background: #fff8f7; color: var(--rose-dark); cursor: pointer; text-align: left; }.active-games > button + button, .recent-games > button + button { margin-top: 7px; }
.active-games button span, .recent-games button span { min-width: 0; flex: 1; display: flex; flex-direction: column; }.active-games small, .recent-games small { margin-top: 3px; color: var(--muted); font-size: 9px; }.recent-games em { color: var(--sage); font-size: 11px; font-style: normal; font-weight: 800; }
.game-room-header { display: flex; align-items: center; justify-content: space-between; gap: 10px; }.game-room-header > div { display: flex; align-items: center; gap: 10px; }.game-room-header span { color: #aa858b; font-size: 10px; }.game-room-header span.online { color: var(--sage); }.game-room { padding: clamp(17px, 3vw, 28px); }
.spin { animation: spin 1s linear infinite; }
@media (max-width: 620px) {
  .games-hero { padding: 22px 18px; }.games-heart { width: 56px; height: 56px; }.game-grid { grid-template-columns: 1fr; }.game-card { min-height: 260px; padding: 20px; }.game-card.coming { min-height: 210px; }.game-room-header { align-items: flex-start; }.game-room-header > div { align-items: flex-end; flex-direction: column; }
}
</style>
