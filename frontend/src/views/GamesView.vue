<script setup lang="ts">
import { computed, onMounted, ref, type Component } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ArrowLeft, Brush, Gamepad2, HeartHandshake, LoaderCircle, MessageCircleHeart, Sparkles, Square, Trophy } from 'lucide-vue-next'
import { api } from '../api'
import { errorMessage } from '../api/client'
import BaseModal from '../components/BaseModal.vue'
import DrawGuessGame from '../components/games/DrawGuessGame.vue'
import MemoryGuessGame from '../components/games/MemoryGuessGame.vue'
import TacitQuizGame from '../components/games/TacitQuizGame.vue'
import TruthCardGame from '../components/games/TruthCardGame.vue'
import LoadingState from '../components/LoadingState.vue'
import { useResourceSync } from '../composables/resourceSync'
import { useToast } from '../composables/toast'
import { realtimeState } from '../stores/realtime'
import { authState } from '../stores/auth'
import {
  abandonPendingGameStrokes,
  flushAllPendingGameStrokes,
} from '../stores/gameStrokes'
import type { GameSession, GameType } from '../types'
import { formatDateTime } from '../utils'
import { acceptsGameSnapshot } from '../utils/gameSnapshot'
import { createRequestGeneration } from '../utils/latestRequest'

const { show } = useToast()
const sessions = ref<GameSession[]>([])
const selected = ref<GameSession | null>(null)
const loading = ref(true)
const loadError = ref('')
const busy = ref<GameType | 'finish' | null>(null)
const finishConfirmation = ref(false)
const gameRequests = createRequestGeneration()
const activeSessions = computed(() => sessions.value.filter((item) => item.status === 'ACTIVE'))
const recentSessions = computed(() => sessions.value.filter((item) => item.status === 'FINISHED').slice(0, 6))

type GameMeta = {
  name: string
  icon: Component
  recent: (game: GameSession) => string
}

const GAME_META: Record<GameType, GameMeta> = {
  TACIT_QUIZ: { name: '默契问答', icon: MessageCircleHeart, recent: (game) => `${game.score} 次默契` },
  DRAW_GUESS: { name: '你画我猜', icon: Brush, recent: (game) => `${game.score} 次猜中` },
  MEMORY_GUESS: { name: '回忆猜猜看', icon: Trophy, recent: (game) => `${game.score} 次猜中` },
  TRUTH_CARD: { name: '真心话卡牌', icon: Square, recent: (game) => `聊到第 ${game.roundNumber} 张` },
}

const GAME_COMPONENTS: Record<GameType, Component> = {
  TACIT_QUIZ: TacitQuizGame,
  DRAW_GUESS: DrawGuessGame,
  MEMORY_GUESS: MemoryGuessGame,
  TRUTH_CARD: TruthCardGame,
}

onMounted(() => void load())
useResourceSync(['games'], refreshFromSync)
onBeforeRouteLeave(ensurePendingStrokesHandled)

async function load() {
  const request = gameRequests.begin()
  try {
    const nextSessions = await api.games()
    if (!request.isLatest()) return
    nextSessions.forEach(updateSession)
    loadError.value = ''
    if (selected.value) {
      selected.value = sessions.value.find((item) => String(item.id) === String(selected.value?.id)) || null
    }
    return true
  } catch (cause) {
    if (!request.isLatest()) return
    loadError.value = errorMessage(cause)
    return false
  } finally {
    if (request.isLatest()) loading.value = false
  }
}

async function refreshFromSync() {
  return load()
}

async function retryLoad() {
  loading.value = true
  loadError.value = ''
  await load()
}

async function start(gameType: GameType) {
  if (busy.value) return
  busy.value = gameType
  try {
    const game = await api.createGame(gameType)
    gameRequests.cancel()
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
    const updated = await api.finishGame(selected.value.id)
    gameRequests.cancel()
    updateSession(updated)
    finishConfirmation.value = false
    show('这局游戏已经收好，随时可以再来一局。', 'success')
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    busy.value = null
  }
}

function updateSession(game: GameSession) {
  const index = sessions.value.findIndex((item) => String(item.id) === String(game.id))
  if (index >= 0 && !acceptsGameSnapshot(sessions.value[index], game)) return
  if (index < 0) sessions.value.unshift(game)
  else sessions.value[index] = game
  if (String(selected.value?.id) === String(game.id)) selected.value = game
}

function handleGameUpdated(game: GameSession) {
  gameRequests.cancel()
  updateSession(game)
}

async function ensurePendingStrokesHandled() {
  if (authState.forcedLogoutReason) {
    // 会话已经失效时不能再用未发送笔画阻塞离开；队列只保存在当前运行期，安全清理即可。
    abandonPendingGameStrokes()
    return true
  }
  const result = await flushAllPendingGameStrokes()
  if (result.completed) return true
  const abandon = window.confirm(
    '还有 ' + result.pending + ' 笔画没有同步成功。确定放弃这些笔画并离开游戏页面吗？',
  )
  if (!abandon) {
    show('笔画还在发送队列中，完成同步或确认放弃后才能离开。', 'info')
    return false
  }
  abandonPendingGameStrokes()
  return true
}

async function leaveRoom() {
  if (!(await ensurePendingStrokesHandled())) return
  selected.value = null
  finishConfirmation.value = false
}

function gameName(type: GameType) {
  return GAME_META[type]?.name || '未知游戏'
}

function gameIcon(type: GameType) {
  return GAME_META[type]?.icon || Gamepad2
}

function gameComponent(type: GameType) {
  return GAME_COMPONENTS[type] || null
}

function recentSummary(game: GameSession) {
  return GAME_META[game.gameType]?.recent(game) || '已结束'
}

function progressLabel(game: GameSession) {
  const unit = game.gameType === 'TRUTH_CARD' || game.gameType === 'MEMORY_GUESS' ? '张' : '轮'
  return `第 ${game.roundNumber} ${unit}`
}
</script>

<template>
  <div class="page-stack games-page">
    <LoadingState v-if="loading" label="正在打开游戏柜…" />

    <section v-else-if="loadError && !sessions.length" class="error-panel" role="alert">
      <Gamepad2 :size="32" aria-hidden="true" />
      <h2>游戏柜暂时没有打开</h2>
      <p>{{ loadError }}</p>
      <button class="button secondary" type="button" @click="retryLoad">重新加载</button>
    </section>

    <template v-else-if="selected">
      <header class="game-room-header">
        <button class="text-button room-action" type="button" @click="leaveRoom"><ArrowLeft :size="17" />返回游戏柜</button>
        <div>
          <span :class="{ online: realtimeState.connected }">{{ realtimeState.connected ? '双端同步已连接' : '同步重连中' }}</span>
          <button v-if="selected.status === 'ACTIVE'" class="text-button muted room-action" type="button" :disabled="busy === 'finish'" @click="finishConfirmation = true">结束本局</button>
        </div>
      </header>
      <article class="game-room card">
        <component
          :is="gameComponent(selected.gameType)"
          v-if="gameComponent(selected.gameType)"
          :session="selected"
          @updated="handleGameUpdated"
        />
        <div v-else class="error-panel" role="alert">
          <Gamepad2 :size="30" aria-hidden="true" />
          <h2>暂时无法打开这局游戏</h2>
          <p>游戏类型无法识别，请返回游戏柜后重新选择。</p>
        </div>
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
          <component :is="gameIcon(game.gameType)" :size="22" aria-hidden="true" />
          <span><strong>{{ gameName(game.gameType) }}</strong><small>{{ progressLabel(game) }} · {{ game.createdByNickname }} 发起</small></span>
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
        <article class="game-card memory">
          <span><Trophy :size="28" /></span>
          <p class="eyebrow">MEMORY GUESS</p><h2>回忆猜猜看</h2><p>看共同照片猜地点、日期或故事，双方都选择后再一起揭晓。</p>
          <small class="game-requirement">需要至少 2 段带照片的回忆</small>
          <button class="button primary" type="button" :disabled="!!busy" @click="start('MEMORY_GUESS')">
            <LoaderCircle v-if="busy === 'MEMORY_GUESS'" class="spin" :size="17" /><Trophy v-else :size="17" />开始猜回忆
          </button>
        </article>
        <article class="game-card truth">
          <span><Square :size="28" /></span>
          <p class="eyebrow">TRUTH CARDS</p><h2>真心话卡牌</h2><p>从轻松到认真轮流抽题，当面回答；不想聊的题可以直接跳过。</p>
          <small class="game-requirement">回答不会保存，也没有倒计时</small>
          <button class="button primary" type="button" :disabled="!!busy" @click="start('TRUTH_CARD')">
            <LoaderCircle v-if="busy === 'TRUTH_CARD'" class="spin" :size="17" /><Square v-else :size="17" />抽一张卡牌
          </button>
        </article>
      </section>

      <section v-if="recentSessions.length" class="recent-games card">
        <div class="section-heading"><div><p class="eyebrow">RECENT</p><h2>最近玩过</h2></div></div>
        <button v-for="game in recentSessions" :key="game.id" type="button" @click="selected = game">
          <span><strong>{{ gameName(game.gameType) }}</strong><small>{{ formatDateTime(game.finishedAt || game.updatedAt) }}</small></span>
          <em>{{ recentSummary(game) }}</em>
        </button>
      </section>
    </template>

    <BaseModal
      v-if="finishConfirmation && selected?.status === 'ACTIVE'"
      title="结束这局游戏？"
      :description="`会保留当前的${gameName(selected.gameType)}记录，之后可以重新开一局。`"
      @close="finishConfirmation = false"
    >
      <div class="finish-confirmation">
        <p>结束后这局不能继续提交答案或进入下一轮。</p>
        <div class="modal-actions">
          <button class="button ghost" type="button" :disabled="busy === 'finish'" @click="finishConfirmation = false">继续玩</button>
          <button class="button danger-button" type="button" :disabled="busy === 'finish'" @click="finish">
            <LoaderCircle v-if="busy === 'finish'" class="spin" :size="17" />确认结束
          </button>
        </div>
      </div>
    </BaseModal>
  </div>
</template>

<style scoped>
.games-page { max-width: 1080px; margin: 0 auto; }.games-hero { position: relative; padding: 27px 31px; overflow: hidden; border: 1px solid #edd9dc; border-radius: var(--radius-lg); background: linear-gradient(130deg, #fffdfb, #fff0f1 62%, #ffe4d5); box-shadow: var(--shadow-sm); }
.games-hero h1 { margin-bottom: 5px; }.games-hero > div:first-child > p:last-child { margin: 0; color: var(--muted); }.games-heart { width: 76px; height: 76px; display: grid; place-items: center; border-radius: 25px; background: rgba(255,255,255,.74); color: var(--rose); transform: rotate(5deg); }
.game-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 15px; }.game-card { min-height: 285px; display: flex; flex-direction: column; align-items: flex-start; padding: 23px; overflow: hidden; border: 1px solid var(--line); border-radius: var(--radius); box-shadow: var(--shadow-sm); }
.game-card.quiz { background: linear-gradient(145deg, #fffafa, #fff0f3); }.game-card.draw { background: linear-gradient(145deg, #fffdf8, #f7f1df); }
.game-card.memory { background: linear-gradient(145deg, #fffaf4, #f5eadc); }.game-card.truth { background: linear-gradient(145deg, #fff7f8, #f3ecea); }
.game-card > span { width: 50px; height: 50px; display: grid; place-items: center; margin-bottom: 18px; border-radius: 16px; background: white; color: var(--rose-dark); box-shadow: var(--shadow-sm); }
.game-card h2 { margin: 0; font-size: 25px; }.game-card > p:not(.eyebrow) { flex: 1; margin: 8px 0 20px; color: var(--muted); font-size: 12px; line-height: 1.7; }.game-card .button { min-width: 135px; }
.game-requirement { display: block; margin: -9px 0 14px; color: #80696f; font-size: 11px; line-height: 1.5; }
.active-games, .recent-games { padding: 19px 21px; }.active-games > button, .recent-games > button { width: 100%; display: flex; align-items: center; gap: 11px; padding: 12px; border: 0; border-radius: 13px; background: #fff8f7; color: var(--rose-dark); cursor: pointer; text-align: left; }.active-games > button + button, .recent-games > button + button { margin-top: 7px; }
.active-games button span, .recent-games button span { min-width: 0; flex: 1; display: flex; flex-direction: column; }.active-games small, .recent-games small { margin-top: 3px; color: var(--muted); font-size: 9px; }.recent-games em { color: var(--sage); font-size: 11px; font-style: normal; font-weight: 800; }
.game-room-header { display: flex; align-items: center; justify-content: space-between; gap: 10px; }.game-room-header > div { display: flex; align-items: center; gap: 10px; }.game-room { padding: clamp(17px, 3vw, 28px); }
.game-room-header span { color: #765f65; font-size: 12px; }.game-room-header span.online { color: #557451; }.room-action { min-height: 44px; padding: 9px 7px; }
.finish-confirmation { display: grid; gap: 18px; }.finish-confirmation > p { margin: 0; color: var(--muted); font-size: 13px; line-height: 1.7; }
.error-panel h2 { margin: 10px 0 2px; }.error-panel p { max-width: 55ch; margin: 0 0 14px; line-height: 1.7; }
.spin { animation: spin 1s linear infinite; }
@media (max-width: 620px) {
  .games-hero { padding: 22px 18px; }.games-heart { width: 56px; height: 56px; }.game-grid { grid-template-columns: 1fr; }.game-card { min-height: 260px; padding: 20px; }.game-room-header { align-items: flex-start; }.game-room-header > div { align-items: flex-end; flex-direction: column; }
}
</style>
