// Fees and dues per enrollment (docs/data-model.md, "Fees and dues").
import type { Enrollment, Installment, Payment } from '../data/types'
import { addMonthsToDate, type DateStr } from './dates'

export interface FeeStatus {
  payable: number
  paid: number
  /** payable - paid; negative = paid in advance. */
  due: number
  overdue: number
  nextDue: { date: DateStr; amount: number } | null
  plan: Installment[]
}

export function payableOf(e: Pick<Enrollment, 'fee' | 'discount'>): number {
  return Math.max(e.fee - e.discount, 0)
}

/** The installments in date order, or the whole payable on the admission date. */
export function planOf(e: Enrollment): Installment[] {
  if (e.installments.length === 0) return [{ due: e.date, amount: payableOf(e) }]
  return [...e.installments].sort((a, b) => a.due.localeCompare(b.due))
}

export function feeStatus(e: Enrollment, paid: number, today: DateStr): FeeStatus {
  const payable = payableOf(e)
  const due = payable - paid
  const plan = planOf(e)
  const dueByToday = plan.filter(i => i.due !== '' && i.due <= today).reduce((s, i) => s + i.amount, 0)
  const overdue = Math.min(Math.max(dueByToday - paid, 0), Math.max(due, 0))
  let running = 0
  let nextDue: FeeStatus['nextDue'] = null
  for (const i of plan) {
    running += i.amount
    if (running > paid) {
      nextDue = { date: i.due, amount: running - paid }
      break
    }
  }
  return { payable, paid, due, overdue, nextDue, plan }
}

/** Total paid per enrollment id. */
export function paidByEnrollment(payments: Payment[]): Map<string, number> {
  const out = new Map<string, number>()
  for (const p of payments) out.set(p.enrollmentId, (out.get(p.enrollmentId) ?? 0) + p.amount)
  return out
}

/**
 * Splits [total] into [count] monthly installments starting on [firstDue].
 * Every part is a whole currency unit except possibly the last, which takes the rest.
 */
export function makePlan(total: number, count: number, firstDue: DateStr): Installment[] {
  if (count <= 1 || total <= 0) return []
  const base = Math.floor(total / count / 100) * 100
  return Array.from({ length: count }, (_, i) => ({
    due: addMonthsToDate(firstDue, i),
    amount: i === count - 1 ? total - base * (count - 1) : base,
  }))
}
