import { describe, expect, it } from 'vitest'
import type { Attendance, Batch, Enrollment, Exam, Instructor } from '../data/types'
import { attendanceByEnrollment, attendanceId, attendanceOf, classDayWarning, dayCode, studentsToMark } from './attendance'
import { batchStatus, daysLabel } from './batches'
import { addMonthsToDate, age, monthDiff } from './dates'
import { feeStatus, makePlan, paidByEnrollment } from './fees'
import { instructorPay } from './instructors'
import { formatMoney, parseAmount } from './money'
import { nextNumber } from './numbering'
import { monthSummary } from './reports'
import { batchResults, gradeFor, resultOf } from './results'
import { canWrite, permissions } from './roles'
import { amountInWords, numberInWords } from './words'

const enrollment = (over: Partial<Enrollment> = {}): Enrollment => ({
  id: 'e1', studentId: 's1', batchId: 'b1', courseId: 'c1', date: '2026-01-10', fee: 1_000_000, discount: 100_000,
  installments: [], status: 'ACTIVE', rollNo: 1, completedOn: '', note: '', ...over,
})

const batch = (over: Partial<Batch> = {}): Batch => ({
  id: 'b1', name: 'MSO-26-B01', courseId: 'c1', instructorId: 'i1', startDate: '2026-01-03', endDate: '2026-03-31',
  days: ['SAT', 'MON', 'WED'], time: '', room: '', capacity: 0, cancelled: false, note: '', ...over,
})

describe('money', () => {
  it('formats with South Asian grouping', () => {
    expect(formatMoney(12_345_678_90, '৳', 'SOUTH_ASIAN')).toBe('৳1,23,45,678.90')
    expect(formatMoney(150_000, '৳', 'INTERNATIONAL')).toBe('৳1,500')
    expect(parseAmount('1,250.5')).toBe(125_050)
  })
})

describe('dates', () => {
  it('adds months, keeping month ends sensible', () => {
    expect(addMonthsToDate('2026-01-31', 1)).toBe('2026-02-28')
    expect(addMonthsToDate('2026-11-15', 2)).toBe('2027-01-15')
    expect(monthDiff('2025-11', '2026-02')).toBe(3)
    expect(age('2000-10-24', '2026-10-23')).toBe(25)
    expect(age('2000-10-23', '2026-10-23')).toBe(26)
  })
})

describe('fees', () => {
  it('without a plan everything is due on the admission date', () => {
    const s = feeStatus(enrollment(), 300_000, '2026-02-01')
    expect(s.payable).toBe(900_000)
    expect(s.due).toBe(600_000)
    expect(s.overdue).toBe(600_000)
    expect(s.nextDue).toEqual({ date: '2026-01-10', amount: 600_000 })
  })

  it('counts only installments that have fallen due as overdue', () => {
    const e = enrollment({ installments: makePlan(900_000, 3, '2026-01-10') })
    expect(e.installments.map(i => i.amount)).toEqual([300_000, 300_000, 300_000])
    expect(e.installments.map(i => i.due)).toEqual(['2026-01-10', '2026-02-10', '2026-03-10'])
    const s = feeStatus(e, 200_000, '2026-02-15')
    expect(s.overdue).toBe(400_000)
    expect(s.nextDue).toEqual({ date: '2026-01-10', amount: 100_000 })
    const later = feeStatus(e, 300_000, '2026-02-01')
    expect(later.overdue).toBe(0)
    expect(later.nextDue).toEqual({ date: '2026-02-10', amount: 300_000 })
  })

  it('handles advance payments and odd splits', () => {
    const s = feeStatus(enrollment(), 1_000_000, '2026-02-01')
    expect(s.due).toBe(-100_000)
    expect(s.overdue).toBe(0)
    expect(s.nextDue).toBeNull()
    const plan = makePlan(1_000_050, 3, '2026-01-01')
    expect(plan.map(i => i.amount)).toEqual([333_300, 333_300, 333_450])
    expect(makePlan(1000, 1, '2026-01-01')).toEqual([])
  })

  it('sums payments per enrollment', () => {
    const paid = paidByEnrollment([
      { enrollmentId: 'a', amount: 5 }, { enrollmentId: 'a', amount: 7 }, { enrollmentId: 'b', amount: 1 },
    ] as never)
    expect(paid.get('a')).toBe(12)
    expect(paid.get('b')).toBe(1)
  })
})

describe('attendance', () => {
  const sessions: Attendance[] = [
    { id: 'x', batchId: 'b1', date: '2026-01-03', marks: { e1: 'P', e2: 'A' }, takenBy: '', note: '' },
    { id: 'y', batchId: 'b1', date: '2026-01-05', marks: { e1: 'L', e2: 'E' }, takenBy: '', note: '' },
    { id: 'z', batchId: 'b1', date: '2026-01-07', marks: { e1: 'A', e2: 'E' }, takenBy: '', note: '' },
  ]
  it('leaves excused days out of the rate', () => {
    expect(attendanceOf('e1', sessions).rate).toBeCloseTo(2 / 3)
    expect(attendanceOf('e2', sessions).rate).toBe(0)
    expect(attendanceOf('e3', sessions).rate).toBeNull()
    expect(attendanceByEnrollment(sessions).get('e1')?.present).toBe(1)
  })
  it('knows class days and the register', () => {
    expect(dayCode('2026-10-24')).toBe('SAT')
    expect(attendanceId('b1', '2026-10-24')).toBe('b1_20261024')
    expect(classDayWarning(batch(), '2026-01-05')).toBeNull()
    expect(classDayWarning(batch(), '2026-01-06')).toMatch(/not one of/)
    expect(classDayWarning(batch(), '2026-04-04')).toMatch(/after/)
    const list = studentsToMark('b1', [
      enrollment({ id: 'a', rollNo: 2 }), enrollment({ id: 'b', rollNo: 1, status: 'COMPLETED' }),
      enrollment({ id: 'c', status: 'DROPPED' }), enrollment({ id: 'd', date: '2026-02-01' }),
    ], '2026-01-20')
    expect(list.map(e => e.id)).toEqual(['b', 'a'])
  })
})

describe('results', () => {
  const exam = (over: Partial<Exam>): Exam => ({
    id: 'x', batchId: 'b1', name: 'Written', date: '2026-03-01', fullMarks: 50, passMarks: 17, marks: {}, ...over,
  })
  it('grades on the Bangladesh scale', () => {
    expect(gradeFor(80).grade).toBe('A+')
    expect(gradeFor(79.9).grade).toBe('A')
    expect(gradeFor(33).grade).toBe('D')
    expect(gradeFor(32.9).point).toBe(0)
    expect(gradeFor(95, true).grade).toBe('F')
  })
  it('fails a mark below pass, and a missing mark once the exam is past', () => {
    const exams = [exam({ id: 'w', marks: { a: 45, b: 40, c: 10, d: 30 } }), exam({ id: 'p', marks: { a: 45, b: 45, c: 50 } })]
    const r = batchResults(
      [enrollment({ id: 'a' }), enrollment({ id: 'b' }), enrollment({ id: 'c' }), enrollment({ id: 'd' })],
      exams, '2026-03-10',
    )
    expect(r.get('a')).toMatchObject({ obtained: 90, full: 100, percent: 90, grade: 'A+', position: 1 })
    expect(r.get('b')).toMatchObject({ percent: 85, position: 2 })
    expect(r.get('c')).toMatchObject({ failed: true, grade: 'F', position: null })
    // The practical's date has passed without a mark: it counts as 0 and fails.
    expect(r.get('d')).toMatchObject({ obtained: 30, full: 100, failed: true, complete: false })
    // Before the exam date a missing mark is only "not entered yet": that exam doesn't count.
    expect(resultOf('d', exams, '2026-02-01')).toMatchObject({ obtained: 30, full: 50, failed: false, complete: false, grade: 'A-' })
    // On the exam day itself it doesn't count yet either.
    expect(resultOf('d', exams, '2026-03-01')).toMatchObject({ full: 50, failed: false })
    // Nothing counted = no result.
    expect(resultOf('z', exams, '2026-02-01')).toBeNull()
  })

  it('gives dropped students no position', () => {
    const r = batchResults(
      [enrollment({ id: 'a', status: 'DROPPED' }), enrollment({ id: 'b' })],
      [exam({ marks: { a: 50, b: 40 } })], '2026-03-10',
    )
    expect(r.get('a')).toMatchObject({ percent: 100, position: null })
    expect(r.get('b')!.position).toBe(1)
  })
  it('lets ties share a position', () => {
    const r = batchResults(
      [enrollment({ id: 'a' }), enrollment({ id: 'b' }), enrollment({ id: 'c' })],
      [exam({ marks: { a: 40, b: 40, c: 30 } })], '2026-03-10',
    )
    expect([r.get('a')!.position, r.get('b')!.position, r.get('c')!.position]).toEqual([1, 1, 3])
  })
})

describe('instructor pay', () => {
  const instructor = (over: Partial<Instructor>): Instructor => ({
    id: 'i1', name: 'T', phone: '', email: '', subject: '', payType: 'MONTHLY', payRate: 1_000_000,
    joinDate: '2026-07-20', leaveDate: '', note: '', archived: false, ...over,
  })
  it('counts months up to last month, or up to leaving', () => {
    expect(instructorPay(instructor({}), [], [], [], '2026-10-23').units).toBe(3)
    expect(instructorPay(instructor({ leaveDate: '2026-08-05' }), [], [], [], '2026-10-23').units).toBe(2)
    expect(instructorPay(instructor({ joinDate: '' }), [], [], [], '2026-10-23').earned).toBe(0)
    expect(instructorPay(instructor({ joinDate: '2026-10-01' }), [], [], [], '2026-10-23').units).toBe(0)
  })
  it('counts started batches and classes taken', () => {
    const batches = [batch(), batch({ id: 'b2', startDate: '2027-01-01' }), batch({ id: 'b3', cancelled: true })]
    const att = [{ batchId: 'b1' }, { batchId: 'b1' }, { batchId: 'b9' }] as Attendance[]
    const payments = [{ instructorId: 'i1', amount: 500_000 }] as never
    const perBatch = instructorPay(instructor({ payType: 'PER_BATCH', payRate: 2_000_000 }), batches, att, payments, '2026-10-23')
    expect(perBatch).toEqual({ units: 1, earned: 2_000_000, paid: 500_000, owed: 1_500_000 })
    expect(instructorPay(instructor({ payType: 'PER_CLASS', payRate: 50_000 }), batches, att, [], '2026-10-23').earned).toBe(100_000)
    expect(instructorPay(instructor({ payType: 'NONE' }), batches, att, payments, '2026-10-23').owed).toBe(-500_000)
  })
})

describe('reports and numbers', () => {
  it('works out a month', () => {
    const s = monthSummary('2026-10',
      [{ date: '2026-10-01', amount: 1000 }, { date: '2026-09-30', amount: 999 }] as never,
      [{ date: '2026-10-05', amount: 300 }] as never,
      [{ date: '2026-10-31', amount: 200 }] as never)
    expect(s).toMatchObject({ income: 1000, expenses: 300, instructorPay: 200, spending: 500, profit: 500 })
  })
  it('numbers students, receipts and certificates per year', () => {
    expect(nextNumber('ST', 2026, [])).toBe('ST-2026-0001')
    expect(nextNumber('MR', 2026, ['MR-2026-0009', 'MR-2025-0100', 'MR-2026-0010', 'X-2026-0500'])).toBe('MR-2026-0011')
  })
  it('works out batch status', () => {
    expect(batchStatus(batch(), '2026-01-01')).toBe('UPCOMING')
    expect(batchStatus(batch(), '2026-02-01')).toBe('RUNNING')
    expect(batchStatus(batch(), '2026-04-01')).toBe('COMPLETED')
    expect(batchStatus(batch({ endDate: '' }), '2030-01-01')).toBe('RUNNING')
    expect(batchStatus(batch({ cancelled: true }), '2026-02-01')).toBe('CANCELLED')
    expect(daysLabel(['WED', 'SAT', 'MON'])).toBe('Sat, Mon, Wed')
  })
})

describe('roles', () => {
  it('matches the security rules', () => {
    expect(permissions('TEACHER').seeMoney).toBe(false)
    expect(canWrite('TEACHER', 'attendance')).toBe(true)
    expect(canWrite('TEACHER', 'students')).toBe(false)
    expect(canWrite('OFFICE', 'payments')).toBe(true)
    expect(canWrite('OFFICE', 'courses')).toBe(false)
    expect(canWrite('OFFICE', 'exams')).toBe(false)
    expect(canWrite('ADMIN', 'members')).toBe(true)
    expect(permissions('OFFICE').editReceipts).toBe(false)
  })
})

describe('words', () => {
  it('writes amounts in words', () => {
    expect(numberInWords(120500)).toBe('One Lakh Twenty Thousand Five Hundred')
    expect(numberInWords(12_000_015)).toBe('One Crore Twenty Lakh Fifteen')
    expect(numberInWords(1_250_000, 'INTERNATIONAL')).toBe('One Million Two Hundred Fifty Thousand')
    expect(amountInWords(125_050, 'SOUTH_ASIAN', 'Taka')).toBe('Taka One Thousand Two Hundred Fifty and 50/100 Only')
  })
})
