const BEIJING_TIME_ZONE = 'Asia/Shanghai'
const BEIJING_OFFSET = '+08:00'

type DateParts = { year: number; month: number; day: number }

function dateOnlyParts(value: string): DateParts | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return null
  const parts = { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]) }
  const check = new Date(Date.UTC(parts.year, parts.month - 1, parts.day, 12))
  return check.getUTCFullYear() === parts.year && check.getUTCMonth() + 1 === parts.month && check.getUTCDate() === parts.day
    ? parts
    : null
}

function localDateTimeParts(value: string) {
  const match = /^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(value)
  if (!match) return null
  const date = dateOnlyParts(match[1])
  const hour = Number(match[2])
  const minute = Number(match[3])
  const second = Number(match[4] || 0)
  if (!date || hour > 23 || minute > 59 || second > 59) return null
  return { ...date, hour, minute, second }
}

function parseMoment(value?: string | Date) {
  if (value instanceof Date) {
    const copy = new Date(value.getTime())
    return Number.isNaN(copy.getTime()) ? null : copy
  }
  if (!value) return null
  if (/Z$|[+-]\d{2}:?\d{2}$/.test(value)) {
    const parsed = new Date(value)
    return Number.isNaN(parsed.getTime()) ? null : parsed
  }
  const parts = localDateTimeParts(value)
  if (!parts) return null
  // A legacy local datetime is explicitly interpreted as Beijing time, independent of
  // the browser's local timezone.
  return new Date(Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour - 8, parts.minute, parts.second))
}

function beijingParts(value: Date): Record<string, string> {
  return Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
    timeZone: BEIJING_TIME_ZONE,
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }).formatToParts(value).filter(part => part.type !== 'literal').map(part => [part.type, part.value]))
}

function dateForFormatting(parts: DateParts) {
  return new Date(Date.UTC(parts.year, parts.month - 1, parts.day, 12))
}

export function formatDate(value?: string, options: Intl.DateTimeFormatOptions = { year: 'numeric', month: 'long', day: 'numeric' }) {
  if (!value) return '未记录日期'
  const dateOnly = dateOnlyParts(value)
  if (dateOnly) {
    return new Intl.DateTimeFormat('zh-CN', { ...options, timeZone: 'UTC' }).format(dateForFormatting(dateOnly))
  }
  const date = parseMoment(value)
  return date ? new Intl.DateTimeFormat('zh-CN', { ...options, timeZone: BEIJING_TIME_ZONE }).format(date) : value
}

export function formatDateTime(value?: string) {
  return formatDate(value, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

export function toLocalDateTimeInput(value?: string | Date) {
  const parts = beijingParts(parseMoment(value) || new Date())
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`
}

export function toBeijingOffsetDateTime(value: string) {
  const parts = localDateTimeParts(value)
  if (parts) {
    return `${value.slice(0, 16)}:${String(parts.second).padStart(2, '0')}${BEIJING_OFFSET}`
  }
  const date = parseMoment(value)
  if (!date) return value
  const next = beijingParts(date)
  return `${next.year}-${next.month}-${next.day}T${next.hour}:${next.minute}:${next.second}${BEIJING_OFFSET}`
}

export function dateDay(value?: string) {
  return value ? dateOnlyParts(value)?.day ?? '' : ''
}

export function todayInput() {
  const parts = beijingParts(new Date())
  return `${parts.year}-${parts.month}-${parts.day}`
}

export function sameId(a?: number | string | null, b?: number | string | null) {
  return a !== undefined && a !== null && b !== undefined && b !== null && String(a) === String(b)
}

export function daysUntilAnniversary(dateValue: string, yearly: boolean) {
  const source = dateOnlyParts(dateValue)
  if (!source) return 0
  const today = dateOnlyParts(todayInput())!
  const targetYear = yearly ? today.year : source.year
  const targetMonth = source.month
  const targetDay = Math.min(source.day, new Date(Date.UTC(targetYear, targetMonth, 0)).getUTCDate())
  let target = Date.UTC(targetYear, targetMonth - 1, targetDay)
  const todayValue = Date.UTC(today.year, today.month - 1, today.day)
  if (yearly && target < todayValue) {
    const nextYear = targetYear + 1
    const nextDay = Math.min(source.day, new Date(Date.UTC(nextYear, targetMonth, 0)).getUTCDate())
    target = Date.UTC(nextYear, targetMonth - 1, nextDay)
  }
  return Math.round((target - todayValue) / 86400000)
}
