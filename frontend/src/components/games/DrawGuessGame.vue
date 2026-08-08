<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { CheckCircle2, Eraser, LoaderCircle, Palette, Send, Trash2 } from 'lucide-vue-next'
import { api } from '../../api'
import { errorMessage } from '../../api/client'
import { useToast } from '../../composables/toast'
import { authState } from '../../stores/auth'
import type { GamePoint, GameSession, GameStroke } from '../../types'
import { sameId } from '../../utils'
import { createStrokeOperationId, isRetryableStrokeError, strokeRetryDelay } from '../../utils/gameStrokeSync'

const props = defineProps<{ session: GameSession }>()
const emit = defineEmits<{ updated: [session: GameSession] }>()
const { show } = useToast()
const canvas = ref<HTMLCanvasElement | null>(null)
const color = ref('#c95868')
const width = ref(5)
const tool = ref<'DRAW' | 'ERASE'>('DRAW')
const eraserPreview = ref<{ x: number; y: number; size: number } | null>(null)
const keyboardCursor = ref({ x: 0.5, y: 0.5 })
const canvasFocused = ref(false)
const keyboardDrawing = ref(false)
const guess = ref('')
const busy = ref(false)
const syncing = ref(false)
const currentStroke = ref<GameStroke | null>(null)
const pendingStrokes: GameStroke[] = []
const MAX_STROKE_POINTS = 480
const MAX_BATCH_STROKES = 12
const TOUCH_ERASER_OFFSET = 44
const STROKE_RETRY_DELAY = 2000
const MAX_STROKE_RETRY_DELAY = 30000
type PendingBatch = { roundNumber: number; operationId: string; strokes: GameStroke[] }
let observer: ResizeObserver | null = null
let activePointerId: number | null = null
let flushRetryTimer: number | null = null
let activeBatch: PendingBatch | null = null
let retryAttempt = 0
let retryNoticeShown = false

const isDrawer = computed(() => sameId(props.session.currentTurnUserId, authState.user?.id))
const canDraw = computed(() => props.session.status === 'ACTIVE' && isDrawer.value && !props.session.roundComplete)
const canGuess = computed(() => props.session.status === 'ACTIVE' && !isDrawer.value && !props.session.roundComplete)
const drawerName = computed(() => isDrawer.value ? authState.user?.nickname : authState.partner?.nickname || 'TA')
const eraserWidth = computed(() => Math.max(width.value, 14))

onMounted(() => {
  observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(resizeCanvas)
  if (canvas.value) observer?.observe(canvas.value)
  void nextTick(resizeCanvas)
})
onBeforeUnmount(() => {
  observer?.disconnect()
  cancelStrokeRetry()
  activePointerId = null
  currentStroke.value = null
  eraserPreview.value = null
})
watch(() => props.session.strokes, () => void nextTick(redraw), { deep: true })
watch(
  () => [props.session.id, props.session.roundNumber, props.session.currentTurnUserId,
    props.session.status, props.session.roundComplete] as const,
  (next, previous) => {
    if (!previous || next.every((value, index) => String(value) === String(previous[index]))) return
    discardPendingStrokes(pendingStrokes.length > 0)
  },
)
watch(tool, (value) => {
  if (value !== 'ERASE') eraserPreview.value = null
})

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
  context.save()
  context.globalCompositeOperation = stroke.tool === 'ERASE' ? 'destination-out' : 'source-over'
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
  context.restore()
}

function pointFrom(event: PointerEvent, drawingTool: GameStroke['tool']): GamePoint {
  const rect = canvas.value!.getBoundingClientRect()
  const touchOffset = drawingTool === 'ERASE' && event.pointerType === 'touch'
    ? TOUCH_ERASER_OFFSET
    : 0
  return {
    x: Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width)),
    y: Math.max(0, Math.min(1, (event.clientY - rect.top - touchOffset) / rect.height)),
  }
}

function startDrawing(event: PointerEvent) {
  if (!canDraw.value || !canvas.value || activePointerId !== null) return
  event.preventDefault()
  activePointerId = event.pointerId
  canvas.value.setPointerCapture(event.pointerId)
  const startPoint = pointFrom(event, tool.value)
  currentStroke.value = {
    tool: tool.value,
    color: color.value,
    width: tool.value === 'ERASE' ? eraserWidth.value : width.value,
    points: [startPoint],
  }
  updateEraserPreview(event, startPoint)
  redraw()
}

function continueDrawing(event: PointerEvent) {
  if (event.pointerId !== activePointerId || !currentStroke.value || !canvas.value) return
  event.preventDefault()
  const next = pointFrom(event, currentStroke.value.tool)
  updateEraserPreview(event, next)
  const previous = currentStroke.value.points.at(-1)
  if (previous && Math.hypot(next.x - previous.x, next.y - previous.y) < 0.002) return
  currentStroke.value.points.push(next)
  if (currentStroke.value.points.length > MAX_STROKE_POINTS * 2) {
    currentStroke.value.points = compactPoints(currentStroke.value.points)
  }
  redraw()
}

function endDrawing(event: PointerEvent) {
  if (event.pointerId !== activePointerId || !currentStroke.value) return
  event.preventDefault()
  const stroke = { ...currentStroke.value, points: compactPoints(currentStroke.value.points) }
  currentStroke.value = null
  eraserPreview.value = null
  releaseActivePointer(event.pointerId)
  pendingStrokes.push(stroke)
  redraw()
  void flushStrokes()
}

function cancelDrawing(event: PointerEvent) {
  if (event.pointerId !== activePointerId) return
  event.preventDefault()
  currentStroke.value = null
  eraserPreview.value = null
  releaseActivePointer(event.pointerId)
  redraw()
}

function commitKeyboardStroke() {
  if (!currentStroke.value) return
  const stroke = { ...currentStroke.value, points: compactPoints(currentStroke.value.points) }
  currentStroke.value = null
  keyboardDrawing.value = false
  pendingStrokes.push(stroke)
  redraw()
  void flushStrokes()
}

function handleCanvasKey(event: KeyboardEvent) {
  if (!canDraw.value) return
  const movements: Record<string, [number, number]> = {
    ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1],
  }
  const movement = movements[event.key]
  if (movement) {
    event.preventDefault()
    const step = event.shiftKey ? 0.05 : 0.015
    keyboardCursor.value = {
      x: Math.max(0, Math.min(1, keyboardCursor.value.x + movement[0] * step)),
      y: Math.max(0, Math.min(1, keyboardCursor.value.y + movement[1] * step)),
    }
    if (keyboardDrawing.value && currentStroke.value) {
      currentStroke.value.points.push({ ...keyboardCursor.value })
      redraw()
    }
    return
  }
  if (event.key === ' ') {
    event.preventDefault()
    if (keyboardDrawing.value) commitKeyboardStroke()
    else {
      keyboardDrawing.value = true
      currentStroke.value = {
        tool: tool.value,
        color: color.value,
        width: tool.value === 'ERASE' ? eraserWidth.value : width.value,
        points: [{ ...keyboardCursor.value }],
      }
      redraw()
    }
  } else if (event.key === 'Enter' && keyboardDrawing.value) {
    event.preventDefault()
    commitKeyboardStroke()
  } else if (event.key === 'Escape' && keyboardDrawing.value) {
    event.preventDefault()
    keyboardDrawing.value = false
    currentStroke.value = null
    redraw()
  }
}

function updateEraserPreview(event: PointerEvent, point: GamePoint) {
  if (event.pointerType !== 'touch' || currentStroke.value?.tool !== 'ERASE' || !canvas.value) {
    eraserPreview.value = null
    return
  }
  const rect = canvas.value.getBoundingClientRect()
  eraserPreview.value = {
    x: point.x * rect.width,
    y: point.y * rect.height,
    size: currentStroke.value.width,
  }
}

function releaseActivePointer(pointerId: number) {
  const element = canvas.value
  if (element?.hasPointerCapture(pointerId)) element.releasePointerCapture(pointerId)
  activePointerId = null
}

function compactPoints(points: GamePoint[]): GamePoint[] {
  if (points.length <= MAX_STROKE_POINTS) return points
  return Array.from({ length: MAX_STROKE_POINTS }, (_, index) => {
    const sourceIndex = Math.round(index * (points.length - 1) / (MAX_STROKE_POINTS - 1))
    return points[sourceIndex]
  })
}

function cancelStrokeRetry() {
  if (flushRetryTimer === null) return
  window.clearTimeout(flushRetryTimer)
  flushRetryTimer = null
}

function scheduleStrokeRetry() {
  if (flushRetryTimer !== null || !pendingStrokes.length || !canDraw.value || !activeBatch) return
  const delay = strokeRetryDelay(retryAttempt, STROKE_RETRY_DELAY, MAX_STROKE_RETRY_DELAY)
  retryAttempt++
  flushRetryTimer = window.setTimeout(() => {
    flushRetryTimer = null
    void flushStrokes()
  }, delay)
}

function discardPendingStrokes(notify: boolean) {
  cancelStrokeRetry()
  activeBatch = null
  retryAttempt = 0
  retryNoticeShown = false
  pendingStrokes.splice(0)
  currentStroke.value = null
  activePointerId = null
  if (notify) show('画板局次已经变化，上一轮未同步的笔画已停止上传。', 'info')
  void nextTick(redraw)
}

async function flushStrokes() {
  if (syncing.value || !pendingStrokes.length || !canDraw.value) return
  if (!activeBatch) {
    activeBatch = {
      roundNumber: props.session.roundNumber,
      operationId: createStrokeOperationId(),
      strokes: pendingStrokes.slice(0, MAX_BATCH_STROKES),
    }
  }
  if (activeBatch.roundNumber !== props.session.roundNumber) {
    discardPendingStrokes(true)
    return
  }
  syncing.value = true
  const batch = activeBatch
  try {
    const updated = await api.addGameStrokes(
      props.session.id, batch.roundNumber, batch.operationId, batch.strokes,
    )
    pendingStrokes.splice(0, batch.strokes.length)
    activeBatch = null
    retryAttempt = 0
    if (retryNoticeShown) show('画笔已经重新同步。', 'success')
    retryNoticeShown = false
    emit('updated', updated)
  } catch (cause) {
    redraw()
    if (isRetryableStrokeError(cause) && canDraw.value) {
      if (!retryNoticeShown) {
        show('画笔同步暂时中断，正在自动重试。', 'error')
        retryNoticeShown = true
      }
      scheduleStrokeRetry()
    } else {
      show(errorMessage(cause), 'error')
      discardPendingStrokes(false)
    }
  } finally {
    syncing.value = false
    if (pendingStrokes.length && flushRetryTimer === null) void flushStrokes()
  }
}

async function clearCanvas() {
  if (busy.value || syncing.value) return
  busy.value = true
  cancelStrokeRetry()
  try {
    const updated = await api.clearGameCanvas(props.session.id, props.session.roundNumber)
    pendingStrokes.splice(0)
    activeBatch = null
    retryAttempt = 0
    retryNoticeShown = false
    emit('updated', updated)
  } catch (cause) {
    show(errorMessage(cause), 'error')
    redraw()
    if (activeBatch && isRetryableStrokeError(cause)) scheduleStrokeRetry()
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
    emit('updated', await api.nextGameRound(props.session.id, props.session.roundNumber))
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
        :class="{ drawable: canDraw, erasing: canDraw && tool === 'ERASE' }"
        role="application"
        :tabindex="canDraw ? 0 : -1"
        aria-label="你画我猜画布"
        aria-describedby="canvas-keyboard-help"
        @focus="canvasFocused = true"
        @blur="canvasFocused = false"
        @keydown="handleCanvasKey"
        @pointerdown="startDrawing"
        @pointermove="continueDrawing"
        @pointerup="endDrawing"
        @pointercancel="cancelDrawing"
      ></canvas>
      <p id="canvas-keyboard-help" class="sr-only">键盘作画：方向键移动光标，空格开始或结束一笔，回车完成，Escape 取消；按住 Shift 可加速移动。</p>
      <span
        v-if="canvasFocused && canDraw"
        class="keyboard-cursor"
        :class="{ drawing: keyboardDrawing }"
        :style="{ left: `${keyboardCursor.x * 100}%`, top: `${keyboardCursor.y * 100}%` }"
        aria-hidden="true"
      ></span>
      <span
        v-if="eraserPreview"
        class="eraser-preview"
        :style="{
          left: `${eraserPreview.x}px`,
          top: `${eraserPreview.y}px`,
          width: `${eraserPreview.size}px`,
          height: `${eraserPreview.size}px`,
        }"
        aria-hidden="true"
      ></span>
      <span v-if="syncing" class="canvas-sync"><LoaderCircle class="spin" :size="13" />同步画笔</span>
    </div>

    <div v-if="canDraw" class="draw-tools">
      <button class="tool-button" :class="{ active: tool === 'DRAW' }" type="button" @click="tool = 'DRAW'"><Palette :size="16" />画笔</button>
      <button v-for="value in ['#c95868', '#374151', '#4f7c67', '#4169a1', '#e69a35']" :key="value" type="button" class="color-button" :class="{ active: tool === 'DRAW' && color === value }" :style="{ background: value }" :aria-label="`选择颜色 ${value}`" @click="color = value; tool = 'DRAW'"></button>
      <input v-model.number="width" type="range" min="2" max="24" :aria-label="tool === 'ERASE' ? '橡皮擦粗细' : '画笔粗细'" />
      <button class="tool-button" :class="{ active: tool === 'ERASE' }" type="button" @click="tool = 'ERASE'"><Eraser :size="16" />橡皮</button>
      <span v-if="tool === 'ERASE'" class="eraser-size" aria-live="polite">
        <i :style="{ width: `${eraserWidth}px`, height: `${eraserWidth}px` }"></i>
        <small>{{ eraserWidth }} px</small>
      </span>
      <button class="text-button" type="button" :disabled="busy || syncing" @click="clearCanvas"><Trash2 :size="16" />清空</button>
    </div>

    <form v-if="canGuess" class="guess-form" @submit.prevent="submitGuess">
      <input v-model="guess" maxlength="80" autocomplete="off" aria-label="你的答案" placeholder="输入你的答案…" />
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
canvas { width: 100%; height: clamp(280px, 48dvh, 520px); display: block; touch-action: pan-y; }.drawable { cursor: crosshair; touch-action: none; }.drawable.erasing { cursor: cell; }
.eraser-preview { position: absolute; z-index: 2; box-sizing: border-box; pointer-events: none; transform: translate(-50%, -50%); border: 2px solid rgba(55,65,81,.9); border-radius: 50%; background: rgba(255,255,255,.38); box-shadow: 0 0 0 2px rgba(255,255,255,.8), 0 2px 7px rgba(55,65,81,.22); }
.eraser-preview::after { position: absolute; top: 100%; left: 50%; width: 2px; height: 30px; content: ''; transform: translateX(-50%); background: linear-gradient(rgba(55,65,81,.65), rgba(55,65,81,.08)); }
.keyboard-cursor { position: absolute; z-index: 3; width: 16px; height: 16px; pointer-events: none; transform: translate(-50%, -50%); border: 2px solid #374151; border-radius: 50%; background: rgba(255,255,255,.75); box-shadow: 0 0 0 2px rgba(255,255,255,.9); }.keyboard-cursor.drawing { background: var(--rose); }
.canvas-sync { position: absolute; right: 10px; top: 10px; display: flex; align-items: center; gap: 4px; padding: 5px 8px; border-radius: 999px; background: rgba(255,255,255,.9); color: var(--muted); font-size: 9px; }
.draw-tools { min-height: 45px; display: flex; align-items: center; gap: 8px; padding: 7px 9px; overflow-x: auto; border-radius: 13px; background: #f8f2ef; color: var(--muted); }
.draw-tools .color-button { width: 44px; height: 44px; flex: 0 0 auto; border: 5px solid white; border-radius: 50%; cursor: pointer; box-shadow: 0 0 0 1px #d8cbc7; }.draw-tools .color-button.active { box-shadow: 0 0 0 2px var(--rose); }
.draw-tools .tool-button { min-height: 44px; display: inline-flex; align-items: center; gap: 4px; flex: 0 0 auto; padding: 8px 10px; border: 1px solid #ddcfca; border-radius: 10px; background: white; color: var(--muted); cursor: pointer; white-space: nowrap; }.draw-tools .tool-button.active { border-color: var(--rose); background: #fff0f2; color: var(--rose-dark); }
.eraser-size { display: none; align-items: center; gap: 5px; flex: 0 0 auto; min-width: 52px; color: var(--muted); }.eraser-size i { box-sizing: border-box; display: block; flex: 0 0 auto; border: 2px solid #59616e; border-radius: 50%; background: rgba(255,255,255,.65); }.eraser-size small { white-space: nowrap; font-size: 9px; }
.draw-tools input { min-width: 80px; flex: 1; }.draw-tools .text-button { white-space: nowrap; }
.guess-form { display: flex; gap: 9px; }.guess-form input { min-width: 0; flex: 1; border: 1px solid var(--line); border-radius: 13px; padding: 11px 13px; background: white; }
.guess-history { display: flex; flex-wrap: wrap; gap: 6px; }.guess-history p { display: flex; align-items: center; gap: 5px; margin: 0; padding: 6px 9px; border-radius: 999px; background: #f5efec; color: var(--muted); font-size: 10px; }.guess-history p.correct { background: var(--sage-pale); color: #557050; }.guess-history strong { color: var(--ink); }
.round-complete { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 16px; border-radius: 15px; background: linear-gradient(135deg, #fff0f2, #fff8e7); color: var(--rose-dark); }.round-complete > div { display: flex; align-items: center; gap: 9px; }.round-complete span { display: flex; flex-direction: column; }.round-complete small { margin-top: 2px; color: var(--muted); }
.spin { animation: spin 1s linear infinite; }
@media (hover: none), (pointer: coarse) {
  .eraser-size { display: inline-flex; }
}
@media (max-width: 620px) {
  canvas { height: 330px; }.guess-form { align-items: stretch; }.guess-form .button { padding-left: 13px; padding-right: 13px; }.round-complete { align-items: stretch; flex-direction: column; }.round-complete .button { width: 100%; }
}
</style>
