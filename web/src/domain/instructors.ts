// What an instructor has earned and is still owed (docs/data-model.md, "Instructor pay").
import type { Attendance, Batch, Instructor, InstructorPayment } from '../data/types'
import { addMonths, monthDiff, monthOfDate, type DateStr } from './dates'

export interface InstructorPay {
  /** Units the rate is multiplied by: months, batches or classes. */
  units: number
  earned: number
  paid: number
  owed: number
}

export function earnedUnits(
  instructor: Instructor, batches: Batch[], attendance: Attendance[], today: DateStr,
): number {
  switch (instructor.payType) {
    case 'MONTHLY': {
      if (!instructor.joinDate) return 0
      let last = addMonths(monthOfDate(today), -1)
      if (instructor.leaveDate) {
        const leave = monthOfDate(instructor.leaveDate)
        if (leave < last) last = leave
      }
      return Math.max(monthDiff(monthOfDate(instructor.joinDate), last) + 1, 0)
    }
    case 'PER_BATCH':
      return batches.filter(b => b.instructorId === instructor.id && !b.cancelled && b.startDate !== '' && b.startDate <= today).length
    case 'PER_CLASS': {
      const mine = new Set(batches.filter(b => b.instructorId === instructor.id).map(b => b.id))
      return attendance.filter(a => mine.has(a.batchId)).length
    }
    default:
      return 0
  }
}

export function instructorPay(
  instructor: Instructor, batches: Batch[], attendance: Attendance[], payments: InstructorPayment[], today: DateStr,
): InstructorPay {
  const units = earnedUnits(instructor, batches, attendance, today)
  const earned = instructor.payType === 'NONE' ? 0 : units * instructor.payRate
  const paid = payments.filter(p => p.instructorId === instructor.id).reduce((s, p) => s + p.amount, 0)
  return { units, earned, paid, owed: earned - paid }
}

export function unitLabel(instructor: Instructor, units: number): string {
  const plural = (w: string) => `${units} ${w}${units === 1 ? '' : 's'}`
  switch (instructor.payType) {
    case 'MONTHLY': return plural('month')
    case 'PER_BATCH': return plural('batch').replace('batchs', 'batches')
    case 'PER_CLASS': return plural('class').replace('classs', 'classes')
    default: return ''
  }
}
