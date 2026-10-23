// Attendance counts and rates (docs/data-model.md, "Attendance").
import type { Attendance, Batch, Day, Enrollment, Mark } from '../data/types'
import { parseDate, type DateStr } from './dates'

export interface AttendanceStats {
  present: number
  late: number
  absent: number
  excused: number
  /** Sessions with a mark for this student. */
  marked: number
  /** (P + L) / (P + L + A), or null when there is nothing to count. */
  rate: number | null
}

const empty = (): AttendanceStats => ({ present: 0, late: 0, absent: 0, excused: 0, marked: 0, rate: null })

function add(s: AttendanceStats, m: Mark) {
  s.marked++
  if (m === 'P') s.present++
  else if (m === 'L') s.late++
  else if (m === 'A') s.absent++
  else s.excused++
}

function finish(s: AttendanceStats): AttendanceStats {
  const counted = s.present + s.late + s.absent
  s.rate = counted > 0 ? (s.present + s.late) / counted : null
  return s
}

export function attendanceOf(enrollmentId: string, sessions: Attendance[]): AttendanceStats {
  const s = empty()
  for (const a of sessions) {
    const m = a.marks[enrollmentId]
    if (m) add(s, m)
  }
  return finish(s)
}

/** Stats for every enrollment that appears in [sessions]. */
export function attendanceByEnrollment(sessions: Attendance[]): Map<string, AttendanceStats> {
  const out = new Map<string, AttendanceStats>()
  for (const a of sessions) {
    for (const [id, m] of Object.entries(a.marks)) {
      let s = out.get(id)
      if (!s) out.set(id, (s = empty()))
      add(s, m)
    }
  }
  out.forEach(finish)
  return out
}

const CODES: Day[] = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT']

export function dayCode(date: DateStr): Day {
  return CODES[parseDate(date).getDay()]
}

/** The fixed id of a batch's attendance record for a day. */
export function attendanceId(batchId: string, date: DateStr): string {
  return `${batchId}_${date.replace(/-/g, '')}`
}

/** Who is on the register that day: ACTIVE and COMPLETED, admitted on or before it. */
export function studentsToMark(batchId: string, enrollments: Enrollment[], date: DateStr): Enrollment[] {
  return enrollments
    .filter(e => e.batchId === batchId && e.status !== 'DROPPED' && e.date <= date)
    .sort((a, b) => a.rollNo - b.rollNo)
}

/** Why marking this day is unusual, or null. Marking is still allowed (make-up classes). */
export function classDayWarning(batch: Batch, date: DateStr): string | null {
  if (batch.startDate && date < batch.startDate) return 'This is before the batch starts.'
  if (batch.endDate && date > batch.endDate) return 'This is after the batch ended.'
  if (batch.days.length > 0 && !batch.days.includes(dayCode(date))) return 'This is not one of the batch\'s class days.'
  return null
}

export function isClassDay(batch: Batch, date: DateStr): boolean {
  return batch.days.includes(dayCode(date)) &&
    (!batch.startDate || date >= batch.startDate) && (!batch.endDate || date <= batch.endDate)
}
