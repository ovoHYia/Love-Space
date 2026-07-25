export function formatDate(value?: string, options: Intl.DateTimeFormatOptions = { year: 'numeric', month: 'long', day: 'numeric' }) {
  if (!value) return '未记录日期'
  const dateOnly = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  const date = dateOnly ? new Date(Number(dateOnly[1]), Number(dateOnly[2]) - 1, Number(dateOnly[3])) : new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('zh-CN', options).format(date)
}

export function formatDateTime(value?: string) {
  return formatDate(value, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

export function toLocalDateTimeInput(value?: string | Date) {
  const date = value ? new Date(value) : new Date()
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000)
  return local.toISOString().slice(0, 16)
}

export function todayInput() {
  const date = new Date()
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
}

export function sameId(a?: number | string | null, b?: number | string | null) {
  return a !== undefined && a !== null && b !== undefined && b !== null && String(a) === String(b)
}

export function daysUntilAnniversary(dateValue: string, yearly: boolean) {
  const source = new Date(`${dateValue}T00:00:00`)
  if (Number.isNaN(source.getTime())) return 0
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  let target = yearly ? new Date(now.getFullYear(), source.getMonth(), source.getDate()) : source
  if (yearly && target < today) target = new Date(now.getFullYear() + 1, source.getMonth(), source.getDate())
  return Math.ceil((target.getTime() - today.getTime()) / 86400000)
}
