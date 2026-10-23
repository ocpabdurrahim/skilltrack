// Calendar helpers. Dates without a time are "YYYY-MM-DD" strings in the
// center's (the browser's) local calendar; a month is "YYYY-MM".

export type YearMonth = string
export type DateStr = string

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December']
const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']

const pad = (n: number) => String(n).padStart(2, '0')

export function dateStr(d: Date): DateStr {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export function today(now = new Date()): DateStr {
  return dateStr(now)
}

export function isDate(s: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(s)
}

/** Local midnight of a "YYYY-MM-DD" date. */
export function parseDate(s: DateStr): Date {
  const [y, m, d] = s.split('-').map(Number)
  return new Date(y, m - 1, d)
}

export function addDays(s: DateStr, n: number): DateStr {
  const d = parseDate(s)
  d.setDate(d.getDate() + n)
  return dateStr(d)
}

/** Same day n months later; the 31st becomes the month's last day when it is shorter. */
export function addMonthsToDate(s: DateStr, n: number): DateStr {
  const [y, m, d] = s.split('-').map(Number)
  const first = new Date(y, m - 1 + n, 1)
  const last = new Date(first.getFullYear(), first.getMonth() + 1, 0).getDate()
  return dateStr(new Date(first.getFullYear(), first.getMonth(), Math.min(d, last)))
}

export function ym(year: number, monthIndex: number): YearMonth {
  const d = new Date(year, monthIndex, 1)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}`
}

export function currentMonth(now = new Date()): YearMonth {
  return ym(now.getFullYear(), now.getMonth())
}

export function parseYm(value: YearMonth): { year: number; month: number } {
  const [y, m] = value.split('-').map(Number)
  return { year: y, month: m - 1 }
}

export function addMonths(value: YearMonth, n: number): YearMonth {
  const { year, month } = parseYm(value)
  return ym(year, month + n)
}

/** Months between two "YYYY-MM" values: b - a. */
export function monthDiff(a: YearMonth, b: YearMonth): number {
  const pa = parseYm(a)
  const pb = parseYm(b)
  return (pb.year - pa.year) * 12 + (pb.month - pa.month)
}

export const monthOfDate = (s: DateStr): YearMonth => s.slice(0, 7)

export function daysInMonth(value: YearMonth): number {
  const { year, month } = parseYm(value)
  return new Date(year, month + 1, 0).getDate()
}

export function monthDays(value: YearMonth): DateStr[] {
  return Array.from({ length: daysInMonth(value) }, (_, i) => `${value}-${pad(i + 1)}`)
}

export function monthLabel(value: YearMonth): string {
  const { year, month } = parseYm(value)
  return `${MONTHS[month]} ${year}`
}

export function shortMonthLabel(value: YearMonth): string {
  return MONTHS[parseYm(value).month].slice(0, 3)
}

/** "23 Oct 2026"; "" stays "". */
export function fullDate(s: DateStr): string {
  if (!isDate(s)) return ''
  const d = parseDate(s)
  return `${d.getDate()} ${MONTHS[d.getMonth()].slice(0, 3)} ${d.getFullYear()}`
}

export function shortDate(s: DateStr): string {
  if (!isDate(s)) return ''
  const d = parseDate(s)
  return `${d.getDate()} ${MONTHS[d.getMonth()].slice(0, 3)}`
}

export function weekdayLabel(s: DateStr): string {
  return DAYS[parseDate(s).getDay()]
}

/** "Today", "Yesterday", "Tomorrow", or a date. */
export function relativeDay(s: DateStr, now = today()): string {
  if (s === now) return 'Today'
  if (s === addDays(now, -1)) return 'Yesterday'
  if (s === addDays(now, 1)) return 'Tomorrow'
  return fullDate(s)
}

export function timeLabel(millis: number): string {
  const d = new Date(millis)
  const h = d.getHours() % 12 || 12
  return `${h}:${pad(d.getMinutes())} ${d.getHours() < 12 ? 'AM' : 'PM'}`
}

/** Whole years between a birthday and today, or null. */
export function age(dob: DateStr, now = today()): number | null {
  if (!isDate(dob)) return null
  const [y, m, d] = dob.split('-').map(Number)
  const [ty, tm, td] = now.split('-').map(Number)
  let years = ty - y
  if (tm < m || (tm === m && td < d)) years--
  return years >= 0 ? years : null
}

export function greeting(now = new Date()): string {
  const h = now.getHours()
  if (h >= 5 && h <= 11) return 'Good morning'
  if (h >= 12 && h <= 16) return 'Good afternoon'
  if (h >= 17 && h <= 21) return 'Good evening'
  return 'Good night'
}
