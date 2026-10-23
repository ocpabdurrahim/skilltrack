// Monthly income, spending and profit (docs/data-model.md, "Profit").
import type { Expense, InstructorPayment, Payment } from '../data/types'
import { addMonths, type YearMonth } from './dates'

export interface MonthSummary {
  month: YearMonth
  income: number
  expenses: number
  instructorPay: number
  spending: number
  profit: number
}

const inMonth = (date: string, month: YearMonth) => date.startsWith(month + '-')

export function monthSummary(
  month: YearMonth, payments: Payment[], expenses: Expense[], instructorPayments: InstructorPayment[],
): MonthSummary {
  const income = payments.filter(p => inMonth(p.date, month)).reduce((s, p) => s + p.amount, 0)
  const exp = expenses.filter(e => inMonth(e.date, month)).reduce((s, e) => s + e.amount, 0)
  const pay = instructorPayments.filter(p => inMonth(p.date, month)).reduce((s, p) => s + p.amount, 0)
  return { month, income, expenses: exp, instructorPay: pay, spending: exp + pay, profit: income - exp - pay }
}

/** The [count] months ending with [last], oldest first. */
export function lastMonths(last: YearMonth, count: number): YearMonth[] {
  return Array.from({ length: count }, (_, i) => addMonths(last, i - count + 1))
}

/** Sums [amount] per key, biggest first. */
export function sumBy<T>(items: T[], key: (t: T) => string, amount: (t: T) => number): [string, number][] {
  const m = new Map<string, number>()
  for (const it of items) m.set(key(it), (m.get(key(it)) ?? 0) + amount(it))
  return [...m.entries()].sort((a, b) => b[1] - a[1])
}

export function between(date: string, from: string, to: string): boolean {
  return (!from || date >= from) && (!to || date <= to)
}
