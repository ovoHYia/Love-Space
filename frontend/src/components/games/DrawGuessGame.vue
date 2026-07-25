<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { CheckCircle2, Eraser, LoaderCircle, Palette, Send } from 'lucide-vue-next'
import { api } from '../../api'
import { errorMessage } from '../../api/client'
import { useToast } from '../../composables/toast'
import { authState } from '../../stores/auth'
import type { GamePoint, GameSession, GameStroke } from '../../types'
import { sameId } from '../../utils'

const props = defineProps<{ session: GameSession }>()
const emit = defineEmits<{ updated: [session: GameSession] }>()
const { show } = useToast()
const canvas = ref<HTMLCanvasElement | null>(null)
const color = ref('#c95868')
const width = ref(5)
const guess = ref('')
const busy = ref(false)
const syncing = ref(false)
const currentStroke = ref<GameStroke | null>(null)
const pendingStrokes: GameStroke[] = []
let observer: ResizeObserver | null = null

const isDrawer = computed(() => sameId(props.session.currentTurnUserId, authState.user?.id))
const canDraw = computed(() => props.session.status === 'ACTIVE' && isDrawer.value && !props.session.roundComplete)
const canGuess = computed(() => props.session.status === 'ACTIVE' && !isDrawer.value && !props.session.roundComplete)
const drawerName = computed(() => isDrawer.value ? authState.user?.nickname : authState.partner?.nickname || 'TA')

onMounted(() => {
  observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(resizeCanvas)
  if (canvas.value) observer?.observe(canvas.value)
  void nextTick(resizeCanvas)
})
onBeforeUnmount(() => observer?.disconnect())
watch(() => props.session.strokes, () => void nextTick(redraw), { deep: true })

function resizeCanvas() {
  const element = canvas.value
  if (!element) return
  const rect = element.getBoundingClientRect()
  const ratio = Math.min(window.devicePixelRatio || 1, 2)
  element.width = Math.max(1, Math.round(rect.width * ratio))
  element.height = Math.max(1, Math.round(rect.height * ratio))
  element.getContext('2d')?.setTransform(ratio, 0, 0, ratio, 0, 0)
  redraw()
}

function redraw() {
  const element = canvas.value
  const context = element?.getContext('2d')
  if (!element || !context) return
  const rect = element.getBoundingClientRect()
  context.clearRect(0, 0, rect.width, rect.height)
  ;[...props.session.strokes, ...pendingStrokes].forEach((stroke) => drawStroke(context, stroke, rect))
  if (currentStroke.value) drawStroke(context, currentStroke.value, rect)
}

function drawStroke(context: CanvasRenderingContext2D, stroke: GameStroke, rect: DOMRect) {
  if (!stroke.points.length) return
  context.beginPath()
  context.lineCap = 'round'
  context.lineJoin = 'round'
  context.strokeStyle = stroke.color
  context.lineWidth = stroke.width
  const first = stroke.points[0]
  context.moveTo(first.x * rect.width, first.y * rect.height)
  stroke.points.slice(1).forEach((point) => context.lineTo(point.x * rect.width, point.y * rect.height))
  if (stroke.points.length === 1) context.lineTo(first.x * rect.width + 0.01, first.y * rect.height + 0.01)
  context.stroke()
}

function pointFrom(event: PointerEvent): GamePoint {
  const rect = canvas.value!.getBoundingClientRect()
  return {
    x: Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width)),
    y: Math.max(0, Math.min(1, (event.clientY - rect.top) / rect.height)),
  }
}

function startDrawing(event: PointerEvent) {
  if (!canDraw.value || !canvas.value) return
  event.preventDefault()
  canvas.value.setPointerCapture(event.pointerId)
  currentStroke.value = { color: color.value, width: width.value, points: [pointFrom(event)] }
  redraw()
}

function continueDrawing(event: PointerEvent) {
  if (!currentStroke.value || !canvas.value) return
  event.preventDefault()
  const next = pointFrom(event)
  const previous = currentStroke.value.points.at(-1)
  if (previous && Math.hypot(next.x - previous.x, next.y - previous.y) < 0.002) return
  currentStroke.value.points.push(next)
  redraw()
}

function endDrawing(event: PointerEvent) {
  if (!currentStroke.value) return
  event.preventDefault()
  const stroke = currentStroke.value
  currentStroke.value = null
  pendingStrokes.push(stroke)
  redraw()
  void flushStrokes()
}

async function flushStrokes() {
  if (syncing.value || !pendingStrokes.length) return
  syncing.value = true
  const count = pendingStrokes.length
  const batch = pendingStrokes.slice(0, count)
  try {
    const updated = await api.addGameStrokes(props.session.id, batch)
    pendingStrokes.splice(0, count)
    emit('updated', updated)
  } catch (cause) {
    pendingStrokes.splice(0, count)
    show(errorMessage(cause), 'error')
    redraw()
  } finally {
    syncing.value = false
    if (pendingStrokes.length) void flushStrokes()
  }
}

async function clearCanvas() {
  if (busy.value || syncing.value) return
  busy.value = true
  try {
    pendingStrokes.splice(0)
    emit('updated', await api.clearGameCanvas(props.session.id))
  } catch (cause) {
    show(errorMessage(cause), 'error')
  } finally {
    busy.value = false
  }
}

async function submitGuess() {
  const value = guess.value.trim()
  if (!value || busy.value) return
  busy.value = true
  try {
    const updated = await api.guessGame(props.session.id, value)
    guess.value = ''
    emit('updated', updated)
    if (updated.roundComplete) show('猜对啦！你们太有默契了。', 'success')
  } catch (cause) {
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
  <section class="draw-game">
    <header class="draw-status">
      <div>
        <span>第 {{ session.roundNumber }} 轮 · {{ drawerName }} 作画</span>
        <h2 v-if="isDrawer">请画：<strong>{{ session.secretWord }}</strong></h2>
        <h2 v-else>{{ session.roundComplete ? `答案是“${session.secretWord}”` : '猜猜 TA 画的是什么？' }}</h2>
      </div>
      <div class="draw-score"><CheckCircle2 :size="18" /><strong>{{ session.score }}</strong><small>猜中</small></div>
    </header>

    <div class="canvas-shell">
      <canvas
        ref="canvas"
        :class="{ drawable: canDraw }"
        aria-label="你画我猜画布"
        @pointerdown="startDrawing"
        @pointermove="continueDrawing"
        @pointerup="endDrawing"
        @pointercancel="endDrawing"
      ></canvas>
      <span v-if="syncing" class="canvas-sync"><LoaderCircle class="spin" :size="13" />同步画笔</span>
    </div>

    <div v-if="canDraw" class="draw-tools">
      <Palette :size="18" />
      <button v-for="value in ['#c95868', '#374151', '#4f7c67', '#4169a1', '#e69a35']" :key="value" type="button" :class="{ active: color === value }" :style="{ background: value }" :aria-label="`选择颜色 ${value}`" @click="color = value"></button>
      <input v-model.number="width" type="range" min="2" max="14" aria-label="画笔粗细" />
      <button class="text-button" type="button" :disabled="busy || syncing" @click="clearCanvas"><Eraser :size="16" />清空</button>
    </div>

    <form v-if="canGuess" class="guess-form" @submit.prevent="submitGuess">
      <input v-model="guess" maxlength="80" autocomplete="off" placeholder="输入你的答案…" />
      <button class="button primary" type="submit" :disabled="busy || !guess.trim()"><Send :size="17" />猜一下</button>
    </form>

    <div v-if="session.guesses.length" class="guess-history">
      <p v-for="item in session.guesses" :key="`${item.createdAt}-${item.text}`" :class="{ correct: item.correct }">
        <strong>{{ item.nickname }}</strong><span>{{ item.text }}</span><CheckCircle2 v-if="item.correct" :size="15" />
      </p>
    </div>

    <div v-if="session.roundComplete" class="round-complete">
      <div><CheckCircle2 :size="23" /><span><strong>猜对了！</strong><small>下一轮交换作画角色</small></span></div>
      <button v-if="session.status === 'ACTIVE'" class="button primary small" type="button" :disabled="busy || syncing" @click="nextRound">下一轮</button>
    </div>
  </section>
</template>

<style scoped>
.draw-game { display: grid; gap: 15px; }.draw-status { display: flex; align-items: flex-start; justify-content: space-between; gap: 15px; }
.draw-status span { color: var(--rose-dark); font-size: 11px; font-weight: 800; }.draw-status h2 { margin: 5px 0 0; font-size: clamp(21px, 4vw, 29px); }.draw-status h2 strong { color: var(--rose-dark); }
.draw-score { min-width: 75px; display: grid; grid-template-columns: auto auto; align-items: center; justify-content: center; gap: 3px 6px; padding: 10px; border-radius: 15px; background: var(--sage-pale); color: #587052; }.draw-score small { grid-column: 1 / -1; text-align: center; font-size: 9px; }
.canvas-shell { position: relative; overflow: hidden; border: 1px solid #e6d9d4; border-radius: 18px; background-color: #fffdfa; background-image: linear-gradient(#f4ece7 1px, transparent 1px), linear-gradient(90deg, #f4ece7 1px, transparent 1px); background-size: 24px 24px; box-shadow: inset 0 0 25px rgba(104,75,66,.04); }
canvas { width: 100%; height: clamp(280px, 48dvh, 520px); display: block; touch-action: pan-y; }.drawable { cursor: crosshair; touch-action: none; }
.canvas-sync { position: absolute; right: 10px; top: 10px; display: flex; align-items: center; gap: 4px; padding: 5px 8px; border-radius: 999px; background: rgba(255,255,255,.9); color: var(--muted); font-size: 9px; }
.draw-tools { min-height: 45px; display: flex; align-items: center; gap: 8px; padding: 7px 9px; overflow-x: auto; border-radius: 13px; background: #f8f2ef; color: var(--muted); }
.draw-tools > button:not(.text-button) { width: 28px; height: 28px; flex: 0 0 auto; border: 3px solid white; border-radius: 50%; cursor: pointer; box-shadow: 0 0 0 1px #d8cbc7; }.draw-tools > button.active { box-shadow: 0 0 0 2px var(--rose); }
.draw-tools input { min-width: 80px; flex: 1; }.draw-tools .text-button { white-space: nowrap; }
.guess-form { display: flex; gap: 9px; }.guess-form input { min-width: 0; flex: 1; border: 1px solid var(--line); border-radius: 13px; padding: 11px 13px; background: white; }
.guess-history { display: flex; flex-wrap: wrap; gap: 6px; }.guess-history p { display: flex; align-items: center; gap: 5px; margin: 0; padding: 6px 9px; border-radius: 999px; background: #f5efec; color: var(--muted); font-size: 10px; }.guess-history p.correct { background: var(--sage-pale); color: #557050; }.guess-history strong { color: var(--ink); }
.round-complete { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 16px; border-radius: 15px; background: linear-gradient(135deg, #fff0f2, #fff8e7); color: var(--rose-dark); }.round-complete > div { display: flex; align-items: center; gap: 9px; }.round-complete span { display: flex; flex-direction: column; }.round-complete small { margin-top: 2px; color: var(--muted); }
.spin { animation: spin 1s linear infinite; }
@media (max-width: 620px) {
  canvas { height: 330px; }.guess-form { align-items: stretch; }.guess-form .button { padding-left: 13px; padding-right: 13px; }.round-complete { align-items: stretch; flex-direction: column; }.round-complete .button { width: 100%; }
}
</style>
