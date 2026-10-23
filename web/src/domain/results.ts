// Exam results, grades and positions (docs/data-model.md, "Results").
import type { Enrollment, Exam } from '../data/types'
import type { DateStr } from './dates'

export const GRADE_SCALE: { min: number; grade: string; point: number }[] = [
  { min: 80, grade: 'A+', point: 5 },
  { min: 70, grade: 'A', point: 4 },
  { min: 60, grade: 'A-', point: 3.5 },
  { min: 50, grade: 'B', point: 3 },
  { min: 40, grade: 'C', point: 2 },
  { min: 33, grade: 'D', point: 1 },
  { min: 0, grade: 'F', point: 0 },
]

export function gradeFor(percent: number, failed = false): { grade: string; point: number } {
  if (failed) return { grade: 'F', point: 0 }
  const row = GRADE_SCALE.find(g => percent >= g.min) ?? GRADE_SCALE[GRADE_SCALE.length - 1]
  return { grade: row.grade, point: row.point }
}

export interface Result {
  enrollmentId: string
  obtained: number
  full: number
  /** Null when the exams have no full marks at all. */
  percent: number | null
  failed: boolean
  grade: string
  point: number
  /** Every exam has a mark for this student. */
  complete: boolean
  /** 1 = top; null when failed or there is no percent. */
  position: number | null
}

/**
 * One student's result over the given exams (all of their batch's). An exam
 * counts once it has this student's mark or its date is before today; a
 * counted exam without a mark scores 0 and fails. No counted exam = no result.
 */
export function resultOf(enrollmentId: string, exams: Exam[], today: DateStr): Result | null {
  let obtained = 0
  let full = 0
  let failed = false
  let counted = 0
  let complete = true
  for (const x of exams) {
    const mark = x.marks[enrollmentId]
    const hasMark = typeof mark === 'number'
    if (!hasMark) complete = false
    if (!hasMark && !(x.date !== '' && x.date < today)) continue
    counted++
    full += x.fullMarks
    if (hasMark) {
      obtained += mark
      if (mark < x.passMarks) failed = true
    } else {
      failed = true
    }
  }
  if (counted === 0) return null
  const percent = full > 0 ? (obtained / full) * 100 : null
  const { grade, point } = percent === null ? { grade: '', point: 0 } : gradeFor(percent, failed)
  return { enrollmentId, obtained, full, percent, failed, grade, point, complete, position: null }
}

/**
 * Results for a batch's enrollments, with positions among the non-dropped ones
 * who did not fail. Dropped students still get a result, never a position.
 */
export function batchResults(enrollments: Enrollment[], exams: Exam[], today: DateStr): Map<string, Result> {
  const out = new Map<string, Result>()
  const dropped = new Set(enrollments.filter(e => e.status === 'DROPPED').map(e => e.id))
  for (const e of enrollments) {
    const r = resultOf(e.id, exams, today)
    if (r) out.set(e.id, r)
  }
  const ranked = [...out.values()]
    .filter(r => !r.failed && r.percent !== null && !dropped.has(r.enrollmentId))
    .sort((a, b) => b.percent! - a.percent!)
  let position = 0
  let previous: number | null = null
  ranked.forEach((r, i) => {
    // Ties share a position; the next one skips (1, 2, 2, 4).
    if (previous === null || Math.abs(r.percent! - previous) > 1e-9) position = i + 1
    previous = r.percent
    r.position = position
  })
  return out
}

export function percentLabel(p: number | null): string {
  if (p === null) return '—'
  return `${(Math.round(p * 10) / 10).toFixed(1).replace(/\.0$/, '')}%`
}

export function ordinal(n: number): string {
  const s = ['th', 'st', 'nd', 'rd']
  const v = n % 100
  return n + (s[(v - 20) % 10] || s[v] || s[0])
}
