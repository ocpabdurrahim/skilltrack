// Numbers derived from the whole center, and the edits that touch several
// records at once. Same rules as the Android app (docs/data-model.md).
import { attendanceByEnrollment, type AttendanceStats } from '../domain/attendance'
import { batchStatus } from '../domain/batches'
import { feeStatus, paidByEnrollment, type FeeStatus } from '../domain/fees'
import { nextNumber, yearOf } from '../domain/numbering'
import { batchResults, type Result } from '../domain/results'
import { newId } from './ids'
import { store, type CenterData } from './store'
import type {
  Batch, Certificate, Course, Enrollment, Installment, Method, Payment, Student,
} from './types'

// ---- Memoised views of a snapshot -------------------------------------------
// A snapshot object never changes, so anything worked out from it can be kept
// until the next snapshot replaces it.

const memoStore = new WeakMap<CenterData, Map<string, unknown>>()

function memo<T>(data: CenterData, key: string, make: () => T): T {
  let m = memoStore.get(data)
  if (!m) memoStore.set(data, (m = new Map()))
  if (!m.has(key)) m.set(key, make())
  return m.get(key) as T
}

export function byId<T extends { id: string }>(list: T[]): Map<string, T> {
  return new Map(list.map(x => [x.id, x]))
}

export const paidMap = (data: CenterData) => memo(data, 'paid', () => paidByEnrollment(data.payments))

export const attendanceMap = (data: CenterData): Map<string, AttendanceStats> =>
  memo(data, 'attendance', () => attendanceByEnrollment(data.attendance))

export interface FeeRow {
  enrollment: Enrollment
  student: Student | undefined
  batch: Batch | undefined
  course: Course | undefined
  fee: FeeStatus
}

/** Fee status of every enrollment that is not dropped. */
export function feeRows(data: CenterData, today: string): FeeRow[] {
  return memo(data, `fees:${today}`, () => {
    const paid = paidMap(data)
    return data.enrollments
      .filter(e => e.status !== 'DROPPED')
      .map(e => ({
        enrollment: e,
        student: data.idx.students.get(e.studentId),
        batch: data.idx.batches.get(e.batchId),
        course: data.idx.courses.get(e.courseId),
        fee: feeStatus(e, paid.get(e.id) ?? 0, today),
      }))
  })
}

export function feeOf(data: CenterData, e: Enrollment, today: string): FeeStatus {
  return feeStatus(e, paidMap(data).get(e.id) ?? 0, today)
}

/** Results of every batch, keyed by enrollment id. */
export function resultsMap(data: CenterData, today: string): Map<string, Result> {
  return memo(data, `results:${today}`, () => {
    const out = new Map<string, Result>()
    const examsByBatch = groupBy(data.exams, x => x.batchId)
    const enrollmentsByBatch = groupBy(data.enrollments, e => e.batchId)
    for (const [batchId, exams] of examsByBatch) {
      batchResults(enrollmentsByBatch.get(batchId) ?? [], exams, today).forEach((r, id) => out.set(id, r))
    }
    return out
  })
}

export function groupBy<T>(items: T[], key: (t: T) => string): Map<string, T[]> {
  const out = new Map<string, T[]>()
  for (const it of items) {
    const k = key(it)
    const list = out.get(k)
    if (list) list.push(it)
    else out.set(k, [it])
  }
  return out
}

/** Students with at least one ACTIVE enrollment. */
export function activeStudentIds(data: CenterData): Set<string> {
  return memo(data, 'active', () => new Set(data.enrollments.filter(e => e.status === 'ACTIVE').map(e => e.studentId)))
}

export function runningBatches(data: CenterData, today: string): Batch[] {
  return data.batches.filter(b => batchStatus(b, today) === 'RUNNING')
}

/** Enrollments in a batch that take a seat (not dropped). */
export function seatsTaken(data: CenterData, batchId: string): number {
  return data.enrollments.filter(e => e.batchId === batchId && e.status !== 'DROPPED').length
}

export const staffName = (data: CenterData) => data.me?.name || data.email

// ---- Numbers ------------------------------------------------------------------

export function nextRegNo(data: CenterData, date: string): string {
  return nextNumber(data.center?.studentPrefix || 'ST', yearOf(date), data.students.map(s => s.regNo))
}

export function nextReceiptNo(data: CenterData, date: string, alsoTaken: string[] = []): string {
  return nextNumber(data.center?.receiptPrefix || 'MR', yearOf(date), [...data.payments.map(p => p.receiptNo), ...alsoTaken])
}

export function nextCertNo(data: CenterData, date: string): string {
  return nextNumber(data.center?.certificatePrefix || 'CERT', yearOf(date), data.certificates.map(c => c.certNo))
}

// ---- Edits ------------------------------------------------------------------------

export interface FirstPayment {
  amount: number
  method: Method
  note: string
}

export interface AdmissionInput {
  student: Student
  /** True when the student is new and must be saved too. */
  newStudent: boolean
  batch: Batch
  date: string
  fee: number
  discount: number
  installments: Installment[]
  note: string
  firstPayment: FirstPayment | null
}

/** Saves the admission (and the new student, and a first payment) in one go. */
export function admit(data: CenterData, input: AdmissionInput): { enrollment: Enrollment; payment: Payment | null } {
  const rollNo = data.enrollments.filter(e => e.batchId === input.batch.id).reduce((m, e) => Math.max(m, e.rollNo), 0) + 1
  const enrollment: Enrollment = {
    id: newId(), studentId: input.student.id, batchId: input.batch.id, courseId: input.batch.courseId, date: input.date,
    fee: input.fee, discount: input.discount, installments: input.installments, status: 'ACTIVE', rollNo, completedOn: '',
    note: input.note,
  }
  const payment = input.firstPayment && input.firstPayment.amount > 0
    ? makePayment(data, enrollment, input.firstPayment.amount, input.date, input.firstPayment.method, input.firstPayment.note)
    : null
  store.putMany([
    ...(input.newStudent ? [['students', input.student] as const] : []),
    ['enrollments', enrollment] as const,
    ...(payment ? [['payments', payment] as const] : []),
  ])
  return { enrollment, payment }
}

function makePayment(data: CenterData, e: Enrollment, amount: number, date: string, method: Method, note: string): Payment {
  return {
    id: newId(), enrollmentId: e.id, studentId: e.studentId, amount, date, method, receiptNo: nextReceiptNo(data, date),
    note, receivedBy: staffName(data), createdAt: Date.now(),
  }
}

export function collectFee(data: CenterData, e: Enrollment, amount: number, date: string, method: Method, note: string): Payment {
  const payment = makePayment(data, e, amount, date, method, note)
  store.put('payments', payment)
  return payment
}

export function issueCertificate(data: CenterData, e: Enrollment, issueDate: string, grade: string, note: string): Certificate {
  const cert: Certificate = {
    id: newId(), enrollmentId: e.id, studentId: e.studentId, certNo: nextCertNo(data, issueDate), issueDate, grade, note,
  }
  store.put('certificates', cert)
  return cert
}

export type RemoveResult = 'deleted' | 'archived'

/** Records still referred to are archived, so old receipts and reports stay intact. */
export function removeCourse(data: CenterData, course: Course): RemoveResult {
  const used = data.batches.some(b => b.courseId === course.id) || data.enrollments.some(e => e.courseId === course.id) ||
    data.enquiries.some(q => q.courseId === course.id)
  if (used) {
    store.put('courses', { ...course, archived: true })
    return 'archived'
  }
  store.remove('courses', course.id)
  return 'deleted'
}

export function blankStudent(data: CenterData, date: string): Student {
  return {
    id: newId(), regNo: nextRegNo(data, date), name: '', fatherName: '', motherName: '', gender: '', dob: '', phone: '',
    guardianPhone: '', email: '', address: '', education: '', institution: '', idNo: '', joinDate: date, note: '',
    archived: false,
  }
}

/** Digits only, with Bangladesh's 0 prefix turned into 880 for wa.me links. */
export function whatsappNumber(phone: string): string {
  const digits = phone.replace(/\D/g, '')
  if (digits.startsWith('880')) return digits
  if (digits.startsWith('0') && digits.length === 11) return `88${digits}`
  if (digits.length === 10 && digits.startsWith('1')) return `880${digits}`
  return digits
}
