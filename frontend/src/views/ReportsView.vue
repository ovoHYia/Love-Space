<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  BarChart3, BookHeart, ChevronLeft, ChevronRight, Flame, HeartHandshake,
  Images, Mails as MailHeart, Printer, Sparkles, Target,
} from 'lucide-vue-next'
import { api } from '../api'
import { errorMessage } from '../api/client'
import BaseAvatar from '../components/BaseAvatar.vue'
import EmptyState from '../components/EmptyState.vue'
import LoadingState from '../components/LoadingState.vue'
import { authState } from '../stores/auth'
import type { MonthlyHighlight, MonthlyReport, UserProfile } from '../types'
import { formatDate, sameId, todayInput } from '../utils'

const router = useRouter()
const report = ref<MonthlyReport | null>(null)
const loading = ref(true)
const error = ref('')
const currentMonth = todayInput().slice(0, 7)
const month = ref(currentMonth)
const chartWidth = 760
const chartHeight = 240
const plotLeft = 48
const plotRight = 738
const plotTop = 24
const plotBottom = 194
const colors = ['#d85f74', '#5f8f96']

const monthLabel = computed(() => {
  const [year, monthValue] = month.value.split('-').map(Number)
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long' })
    .format(new Date(year, monthValue - 1, 1))
})
const canNext = computed(() => month.value < currentMonth)
const chartSeries = computed(() => (report.value?.people || []).map((person, index) => {
  const points = (report.value?.trend || [])
    .filter(item => sameId(item.userId, person.userId))
    .map(item => {
      const day = Number(item.date.slice(8, 10))
      const daySpan = Math.max(1, (report.value?.daysInScope || 1) - 1)
      return {
        ...item,
        x: plotLeft + ((day - 1) / daySpan) * (plotRight - plotLeft),
        y: plotTop + ((5 - item.score) / 4) * (plotBottom - plotTop),
      }
    })
  return {
    ...person,
    color: colors[index % colors.length],
    points,
    polyline: points.map(point => `${point.x},${point.y}`).join(' '),
  }
}))
const dayTicks = computed(() => {
  const last = report.value?.daysInScope || 1
  return [...new Set([1, 7, 14, 21, 28, last])].filter(day => day <= last).map(day => ({
    day,
    x: plotLeft + ((day - 1) / Math.max(1, last - 1)) * (plotRight - plotLeft),
  }))
})
const scoreTicks = [
  { score: 5, label: '明亮' },
  { score: 4, label: '舒展' },
  { score: 3, label: '平缓' },
  { score: 2, label: '疲惫' },
  { score: 1, label: '低落' },
]
const activityItems = computed(() => {
  const value = report.value?.activities
  return [
    { label: '共同回忆', value: value?.memories || 0, icon: Images, route: 'memories' },
    { label: '写下日记', value: value?.diaries || 0, icon: BookHeart, route: 'diaries' },
    { label: '彼此信笺', value: value?.letters || 0, icon: MailHeart, route: 'letters' },
    { label: '完成愿望', value: value?.completedWishes || 0, icon: Target, route: 'wishes' },
  ]
})

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    report.value = await api.monthlyReport(month.value)
  } catch (cause) {
    error.value = errorMessage(cause)
  } finally {
    loading.value = false
  }
}

async function changeMonth(offset: number) {
  const [year, monthValue] = month.value.split('-').map(Number)
  const next = new Date(year, monthValue - 1 + offset, 1)
  month.value = `${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, '0')}`
  await load()
}

function profileFor(userId: number | string, nickname: string): UserProfile {
  if (sameId(authState.user?.id, userId)) return authState.user || { id: userId, nickname }
  if (sameId(authState.partner?.id, userId)) return authState.partner || { id: userId, nickname }
  return { id: userId, nickname }
}

function highlightRoute(item: MonthlyHighlight) {
  return {
    MEMORY: 'memories',
    DIARY: 'diaries',
    LETTER: 'letters',
    WISH: 'wishes',
  }[item.type]
}

function printReport() {
  window.print()
}
</script>

<template>
  <div class="page-stack reports-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">MOOD & MONTHLY STORY</p>
        <h1>心情趋势与月度报告</h1>
        <p>回看彼此的情绪起伏，也收藏这个月一起完成的小事。</p>
      </div>
      <button class="button ghost print-button" type="button" :disabled="!report" @click="printReport">
        <Printer :size="17" />打印或保存
      </button>
    </header>

    <section class="report-month-switcher card">
      <button class="icon-button" type="button" aria-label="查看上个月" @click="changeMonth(-1)">
        <ChevronLeft :size="19" />
      </button>
      <div><p class="eyebrow">MONTHLY REPORT</p><h2>{{ monthLabel }}</h2></div>
      <button class="icon-button" type="button" aria-label="查看下个月" :disabled="!canNext" @click="changeMonth(1)">
        <ChevronRight :size="19" />
      </button>
    </section>

    <LoadingState v-if="loading" label="正在整理这个月的心情故事…" />
    <div v-else-if="error" class="error-panel" role="alert">
      <p>{{ error }}</p>
      <button class="button secondary" type="button" @click="load">重新加载</button>
    </div>

    <template v-else-if="report">
      <section class="report-hero">
        <div class="report-insight">
          <span><Sparkles :size="20" /></span>
          <div>
            <p class="eyebrow">THIS MONTH</p>
            <h2>{{ report.insight }}</h2>
            <p>{{ formatDate(report.from, { month: 'long', day: 'numeric' }) }} 至 {{ formatDate(report.to, { month: 'long', day: 'numeric' }) }}，共有 {{ report.totalMoodEntries }} 条心情被认真留下。</p>
          </div>
        </div>
        <div class="report-metrics">
          <article><HeartHandshake :size="20" /><strong>{{ report.sharedMoodDays }}</strong><span>共同打卡天</span></article>
          <article><Flame :size="20" /><strong>{{ report.longestStreak }}</strong><span>最长连续天</span></article>
          <article><Sparkles :size="20" /><strong>{{ report.resonanceRate }}%</strong><span>心情同频率</span></article>
          <article><BarChart3 :size="20" /><strong>{{ report.coverageRate }}%</strong><span>记录覆盖率</span></article>
        </div>
      </section>

      <section class="mood-chart-card card">
        <div class="report-section-heading">
          <div><p class="eyebrow">MOOD TREND</p><h2>这个月的心情曲线</h2></div>
          <div class="chart-legend">
            <span v-for="series in chartSeries" :key="String(series.userId)">
              <i :style="{ background: series.color }"></i>{{ series.nickname }}
            </span>
          </div>
        </div>
        <EmptyState v-if="!report.trend.length" title="还没有可以连成曲线的心情" description="每天记录一次，月底就能看到属于你们的心情轨迹。" />
        <div v-else class="mood-chart-scroll">
          <svg class="mood-chart" :viewBox="`0 0 ${chartWidth} ${chartHeight}`" role="img" :aria-label="`${monthLabel}两人的心情趋势图`">
            <g v-for="tick in scoreTicks" :key="tick.score">
              <line :x1="plotLeft" :x2="plotRight" :y1="plotTop + ((5 - tick.score) / 4) * (plotBottom - plotTop)" :y2="plotTop + ((5 - tick.score) / 4) * (plotBottom - plotTop)" class="chart-grid-line" />
              <text x="8" :y="plotTop + ((5 - tick.score) / 4) * (plotBottom - plotTop) + 4" class="chart-y-label">{{ tick.label }}</text>
            </g>
            <g v-for="tick in dayTicks" :key="tick.day">
              <line :x1="tick.x" :x2="tick.x" :y1="plotTop" :y2="plotBottom" class="chart-day-line" />
              <text :x="tick.x" y="218" class="chart-x-label">{{ tick.day }}日</text>
            </g>
            <g v-for="series in chartSeries" :key="String(series.userId)">
              <polyline v-if="series.points.length > 1" :points="series.polyline" fill="none" :stroke="series.color" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round" />
              <circle v-for="point in series.points" :key="`${point.date}-${point.userId}`" :cx="point.x" :cy="point.y" r="6" :fill="series.color" class="chart-point">
                <title>{{ point.date }} · {{ point.nickname }}：{{ point.emoji }} {{ point.label }}{{ point.note ? ` — ${point.note}` : '' }}</title>
              </circle>
            </g>
          </svg>
        </div>
        <p v-if="report.trend.length" class="chart-note">悬停圆点可查看当天记录；曲线仅连接已记录的日期，空白日期不会被补写。</p>
      </section>

      <section class="report-detail-grid">
        <div class="card person-summary-card">
          <div class="report-section-heading"><div><p class="eyebrow">TWO OF US</p><h2>两个人的本月小结</h2></div></div>
          <div class="person-summary-list">
            <article v-for="person in report.people" :key="String(person.userId)">
              <BaseAvatar :user="profileFor(person.userId, person.nickname)" size="md" />
              <div>
                <h3>{{ person.nickname }}</h3>
                <p v-if="person.recordedDays"><span>{{ person.dominantEmoji }} {{ person.dominantLabel }}</span> 是最常出现的心情</p>
                <p v-else>这个月还没有留下心情</p>
              </div>
              <dl>
                <div><dt>记录</dt><dd>{{ person.recordedDays }} 天</dd></div>
                <div><dt>平均能量</dt><dd>{{ person.averageScore || '—' }}<small v-if="person.averageScore"> / 5</small></dd></div>
              </dl>
            </article>
          </div>
        </div>

        <div class="card distribution-card">
          <div class="report-section-heading"><div><p class="eyebrow">MOOD MIX</p><h2>心情组成</h2></div></div>
          <EmptyState v-if="!report.distribution.length" title="等待第一枚心情" description="记录后，这里会展示本月各种心情所占的比例。" />
          <div v-else class="distribution-list">
            <div v-for="item in report.distribution" :key="item.label">
              <span class="distribution-emoji">{{ item.emoji }}</span>
              <div><p><strong>{{ item.label }}</strong><small>{{ item.count }} 次 · {{ item.percentage }}%</small></p><i><b :style="{ width: `${item.percentage}%` }"></b></i></div>
            </div>
          </div>
        </div>
      </section>

      <section class="report-detail-grid activity-grid">
        <div class="card activity-card">
          <div class="report-section-heading"><div><p class="eyebrow">TOGETHER</p><h2>这个月一起留下</h2></div></div>
          <div class="activity-list">
            <button v-for="item in activityItems" :key="item.label" type="button" @click="router.push({ name: item.route })">
              <span><component :is="item.icon" :size="20" /></span>
              <strong>{{ item.value }}</strong>
              <small>{{ item.label }}</small>
            </button>
          </div>
        </div>

        <div class="card highlight-card">
          <div class="report-section-heading"><div><p class="eyebrow">HIGHLIGHTS</p><h2>本月闪光片段</h2></div></div>
          <EmptyState v-if="!report.highlights.length" title="这个月还很轻盈" description="回忆、日记、信笺与完成的愿望，会在这里汇成月度片段。" />
          <div v-else class="highlight-list">
            <button v-for="item in report.highlights" :key="`${item.type}-${item.id}`" type="button" @click="router.push({ name: highlightRoute(item) })">
              <span>{{ formatDate(item.date, { month: 'short', day: 'numeric' }) }}</span>
              <strong>{{ item.title }}</strong>
              <ChevronRight :size="15" />
            </button>
          </div>
        </div>
      </section>

      <p class="report-method-note">同频率指双方同一天的心情能量分相差不超过 1；记录覆盖率按“已记录条数 ÷ 本月已过天数 ÷ 2 人”计算。</p>
    </template>
  </div>
</template>

<style scoped>
.reports-page { --report-blue: #5f8f96; }
.report-month-switcher { align-self: center; display: flex; align-items: center; gap: 18px; padding: 13px 16px; }
.report-month-switcher > div { min-width: 150px; text-align: center; }
.report-month-switcher .eyebrow { justify-content: center; margin-bottom: 2px; font-size: 8px; }
.report-month-switcher h2 { margin: 0; font-size: 21px; }
.report-month-switcher .icon-button:disabled { cursor: not-allowed; opacity: .35; }
.report-hero { overflow: hidden; border-radius: 26px; background: linear-gradient(135deg, #d95f74, #e98287 52%, #efae96); color: white; box-shadow: 0 18px 45px rgba(185,73,91,.2); }
.report-insight { display: flex; align-items: flex-start; gap: 15px; padding: 28px 30px 23px; }
.report-insight > span { width: 46px; height: 46px; flex: 0 0 auto; display: grid; place-items: center; border: 1px solid rgba(255,255,255,.28); border-radius: 15px; background: rgba(255,255,255,.15); }
.report-insight .eyebrow, .report-insight h2 { color: white; }
.report-insight h2 { max-width: 800px; margin: 0 0 7px; font-size: 27px; line-height: 1.35; }
.report-insight p:last-child { margin: 0; color: rgba(255,255,255,.82); font-size: 11px; }
.report-metrics { display: grid; grid-template-columns: repeat(4, 1fr); border-top: 1px solid rgba(255,255,255,.2); background: rgba(115,35,53,.08); }
.report-metrics article { min-height: 105px; display: grid; grid-template-columns: auto 1fr; align-content: center; column-gap: 9px; padding: 19px 24px; border-right: 1px solid rgba(255,255,255,.18); }
.report-metrics article:last-child { border-right: 0; }
.report-metrics svg { grid-row: 1 / 3; align-self: center; opacity: .8; }
.report-metrics strong { font: 700 25px Georgia, serif; }
.report-metrics span { color: rgba(255,255,255,.78); font-size: 9px; }
.mood-chart-card, .person-summary-card, .distribution-card, .activity-card, .highlight-card { padding: 22px; }
.report-section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; margin-bottom: 18px; }
.report-section-heading h2 { margin: 0; font-size: 22px; }
.chart-legend { display: flex; gap: 13px; padding-top: 6px; }
.chart-legend span { display: inline-flex; align-items: center; gap: 5px; color: var(--muted); font-size: 10px; font-weight: 700; }
.chart-legend i { width: 9px; height: 9px; border-radius: 50%; }
.mood-chart-scroll { overflow-x: auto; }
.mood-chart { width: 100%; min-width: 680px; height: auto; display: block; }
.chart-grid-line { stroke: #eadfe0; stroke-width: 1; stroke-dasharray: 4 5; }
.chart-day-line { stroke: #f2e8e7; stroke-width: 1; }
.chart-y-label, .chart-x-label { fill: #9a8388; font: 9px "Microsoft YaHei", sans-serif; }
.chart-x-label { text-anchor: middle; }
.chart-point { stroke: white; stroke-width: 3; cursor: help; transition: r .15s ease; }
.chart-point:hover { r: 8; }
.chart-note { margin: 5px 0 0; color: var(--muted); font-size: 9px; text-align: right; }
.report-detail-grid { display: grid; grid-template-columns: 1fr 1fr; align-items: stretch; gap: 18px; }
.person-summary-list { display: grid; gap: 10px; }
.person-summary-list article { display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 11px; padding: 13px; border: 1px solid var(--line); border-radius: 15px; background: #fff9f7; }
.person-summary-list h3 { margin: 0 0 4px; font-size: 15px; }
.person-summary-list p { margin: 0; color: var(--muted); font-size: 9px; }
.person-summary-list p span { color: var(--rose-dark); font-weight: 800; }
.person-summary-list dl { display: flex; gap: 12px; margin: 0; text-align: right; }
.person-summary-list dl div { display: flex; flex-direction: column; gap: 3px; }
.person-summary-list dt { color: var(--muted); font-size: 8px; }
.person-summary-list dd { margin: 0; font-size: 12px; font-weight: 800; }
.person-summary-list dd small { color: var(--muted); font-size: 8px; }
.distribution-card .empty-state, .highlight-card .empty-state { min-height: 150px; }
.distribution-list { display: grid; gap: 12px; }
.distribution-list > div { display: grid; grid-template-columns: 35px 1fr; align-items: center; gap: 9px; }
.distribution-emoji { font-size: 23px; text-align: center; }
.distribution-list p { display: flex; justify-content: space-between; margin: 0 0 6px; }
.distribution-list strong { font-size: 11px; }
.distribution-list small { color: var(--muted); font-size: 9px; }
.distribution-list i { height: 7px; display: block; overflow: hidden; border-radius: 999px; background: #f1e8e6; }
.distribution-list b { height: 100%; display: block; min-width: 4px; border-radius: inherit; background: linear-gradient(90deg, #ef9da6, var(--rose)); }
.activity-list { display: grid; grid-template-columns: repeat(4, 1fr); gap: 9px; }
.activity-list button { display: flex; flex-direction: column; align-items: center; gap: 5px; padding: 13px 6px; border: 1px solid var(--line); border-radius: 14px; background: #fffaf8; cursor: pointer; }
.activity-list button:hover { border-color: #e7b8bf; background: var(--rose-pale); }
.activity-list button > span { width: 35px; height: 35px; display: grid; place-items: center; border-radius: 11px; background: white; color: var(--rose); }
.activity-list strong { font: 700 22px Georgia, serif; }
.activity-list small { color: var(--muted); font-size: 8px; }
.highlight-list { display: grid; }
.highlight-list button { display: grid; grid-template-columns: 66px 1fr auto; align-items: center; gap: 10px; padding: 11px 3px; border: 0; border-bottom: 1px dashed var(--line); background: transparent; text-align: left; cursor: pointer; }
.highlight-list button:last-child { border-bottom: 0; }
.highlight-list button span { color: var(--rose-dark); font-size: 9px; font-weight: 800; }
.highlight-list button strong { overflow: hidden; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.highlight-list button svg { color: var(--muted); }
.report-method-note { margin: -7px 0 0; color: var(--muted); font-size: 9px; line-height: 1.6; text-align: center; }
@media (max-width: 960px) {
  .report-detail-grid { grid-template-columns: 1fr; }
}
@media (max-width: 700px) {
  .report-metrics { grid-template-columns: repeat(2, 1fr); }
  .report-metrics article:nth-child(2) { border-right: 0; }
  .report-metrics article:nth-child(-n+2) { border-bottom: 1px solid rgba(255,255,255,.18); }
  .report-insight { padding: 23px 19px 19px; }
  .report-insight h2 { font-size: 23px; }
  .mood-chart-card, .person-summary-card, .distribution-card, .activity-card, .highlight-card { padding: 18px 14px; }
  .person-summary-list article { grid-template-columns: auto 1fr; }
  .person-summary-list dl { grid-column: 1 / -1; justify-content: flex-end; padding-top: 9px; border-top: 1px dashed var(--line); }
  .activity-list { grid-template-columns: repeat(2, 1fr); }
}
@media print {
  .reports-page .page-header .button, .report-month-switcher .icon-button { display: none; }
  .report-month-switcher { border: 0; box-shadow: none; }
  .reports-page { gap: 12px; }
  .report-hero, .card { box-shadow: none; break-inside: avoid; }
}
</style>
