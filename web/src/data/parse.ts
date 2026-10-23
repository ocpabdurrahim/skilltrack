// Firestore documents -> records, tolerant of missing or odd fields: anything
// unexpected falls back to a safe default (docs/data-model.md, "Shared conventions").
import type { DocumentData } from 'firebase/firestore'
import type {
  Attendance, Batch, Center, Collections, CollectionName, Day, Enrollment, Installment, Mark, Role,
} from './types'
import { DAYS, ENQUIRY_SOURCES, ENQUIRY_STATUSES, ENROLLMENT_STATUSES, MARKS, METHODS, PAY_TYPES } from './types'

const num = (v: unknown, fallback = 0) => (typeof v === 'number' && Number.isFinite(v) ? v : fallback)
const int = (v: unknown, fallback = 0) => Math.round(num(v, fallback))
const str = (v: unknown, fallback = '') => (typeof v === 'string' ? v : fallback)
const strOrNull = (v: unknown) => (typeof v === 'string' && v !== '' ? v : null)
const bool = (v: unknown, fallback = false) => (typeof v === 'boolean' ? v : fallback)
/** One of [values], or the first of them. */
const oneOf = <T extends string>(v: unknown, values: readonly T[]): T => (values.includes(v as T) ? (v as T) : values[0])

function installments(v: unknown): Installment[] {
  if (!Array.isArray(v)) return []
  return v
    .filter((x): x is Record<string, unknown> => typeof x === 'object' && x !== null)
    .map(x => ({ due: str(x.due), amount: int(x.amount) }))
}

function marks(v: unknown): Record<string, Mark> {
  const out: Record<string, Mark> = {}
  if (typeof v === 'object' && v !== null) {
    for (const [k, m] of Object.entries(v)) if (MARKS.includes(m as Mark)) out[k] = m as Mark
  }
  return out
}

function scores(v: unknown): Record<string, number> {
  const out: Record<string, number> = {}
  if (typeof v === 'object' && v !== null) {
    for (const [k, m] of Object.entries(v)) if (typeof m === 'number' && Number.isFinite(m)) out[k] = m
  }
  return out
}

const days = (v: unknown): Day[] => (Array.isArray(v) ? DAYS.filter(d => v.includes(d)) : [])

// Unknown roles get the least access.
const role = (v: unknown): Role => (['OWNER', 'ADMIN', 'OFFICE', 'TEACHER'].includes(v as string) ? (v as Role) : 'TEACHER')

export function parseCenter(id: string, d: DocumentData): Center {
  return {
    id, name: str(d.name, 'Training center'), tagline: str(d.tagline), address: str(d.address), phone: str(d.phone),
    email: str(d.email), website: str(d.website), logo: str(d.logo), signatory: str(d.signatory, 'Director'),
    currencyCode: str(d.currencyCode, 'BDT'), currencySymbol: str(d.currencySymbol, '৳'),
    grouping: d.grouping === 'INTERNATIONAL' ? 'INTERNATIONAL' : 'SOUTH_ASIAN',
    studentPrefix: str(d.studentPrefix, 'ST') || 'ST', receiptPrefix: str(d.receiptPrefix, 'MR') || 'MR',
    certificatePrefix: str(d.certificatePrefix, 'CERT') || 'CERT',
    ownerUid: str(d.ownerUid), ownerEmail: str(d.ownerEmail), createdAt: num(d.createdAt),
  }
}

export const parsers: { [K in CollectionName]: (id: string, d: DocumentData) => Collections[K] } = {
  members: (id, d) => ({
    id, email: str(d.email, id), name: str(d.name), role: role(d.role), active: bool(d.active),
    instructorId: strOrNull(d.instructorId), addedBy: str(d.addedBy), createdAt: num(d.createdAt),
  }),
  courses: (id, d) => ({
    id, name: str(d.name), code: str(d.code), fee: int(d.fee), duration: str(d.duration), description: str(d.description),
    archived: bool(d.archived),
  }),
  instructors: (id, d) => ({
    id, name: str(d.name), phone: str(d.phone), email: str(d.email), subject: str(d.subject),
    payType: oneOf(d.payType, PAY_TYPES), payRate: int(d.payRate), joinDate: str(d.joinDate), leaveDate: str(d.leaveDate),
    note: str(d.note), archived: bool(d.archived),
  }),
  batches: (id, d): Batch => ({
    id, name: str(d.name), courseId: str(d.courseId), instructorId: strOrNull(d.instructorId), startDate: str(d.startDate),
    endDate: str(d.endDate), days: days(d.days), time: str(d.time), room: str(d.room), capacity: int(d.capacity),
    cancelled: bool(d.cancelled), note: str(d.note),
  }),
  students: (id, d) => ({
    id, regNo: str(d.regNo), name: str(d.name), fatherName: str(d.fatherName), motherName: str(d.motherName),
    gender: d.gender === 'MALE' || d.gender === 'FEMALE' || d.gender === 'OTHER' ? d.gender : '',
    dob: str(d.dob), phone: str(d.phone), guardianPhone: str(d.guardianPhone), email: str(d.email), address: str(d.address),
    education: str(d.education), institution: str(d.institution), idNo: str(d.idNo), joinDate: str(d.joinDate),
    note: str(d.note), archived: bool(d.archived),
  }),
  enrollments: (id, d): Enrollment => ({
    id, studentId: str(d.studentId), batchId: str(d.batchId), courseId: str(d.courseId), date: str(d.date),
    fee: int(d.fee), discount: int(d.discount), installments: installments(d.installments),
    status: oneOf(d.status, ENROLLMENT_STATUSES), rollNo: int(d.rollNo), completedOn: str(d.completedOn), note: str(d.note),
  }),
  payments: (id, d) => ({
    id, enrollmentId: str(d.enrollmentId), studentId: str(d.studentId), amount: int(d.amount), date: str(d.date),
    method: oneOf(d.method, METHODS), receiptNo: str(d.receiptNo), note: str(d.note), receivedBy: str(d.receivedBy),
    createdAt: num(d.createdAt),
  }),
  attendance: (id, d): Attendance => ({
    id, batchId: str(d.batchId), date: str(d.date), marks: marks(d.marks), takenBy: str(d.takenBy), note: str(d.note),
  }),
  exams: (id, d) => ({
    id, batchId: str(d.batchId), name: str(d.name), date: str(d.date), fullMarks: num(d.fullMarks, 100),
    passMarks: num(d.passMarks), marks: scores(d.marks),
  }),
  certificates: (id, d) => ({
    id, enrollmentId: str(d.enrollmentId), studentId: str(d.studentId), certNo: str(d.certNo), issueDate: str(d.issueDate),
    grade: str(d.grade), note: str(d.note),
  }),
  enquiries: (id, d) => ({
    id, name: str(d.name), phone: str(d.phone), courseId: strOrNull(d.courseId), source: oneOf(d.source, ENQUIRY_SOURCES),
    date: str(d.date), followUp: str(d.followUp), status: oneOf(d.status, ENQUIRY_STATUSES), note: str(d.note),
    studentId: strOrNull(d.studentId), createdAt: num(d.createdAt),
  }),
  expenseHeads: (id, d) => ({ id, name: str(d.name), emoji: str(d.emoji, '📦'), archived: bool(d.archived) }),
  expenses: (id, d) => ({
    id, headId: str(d.headId), amount: int(d.amount), date: str(d.date), method: oneOf(d.method, METHODS),
    note: str(d.note), paidTo: str(d.paidTo), createdAt: num(d.createdAt),
  }),
  instructorPayments: (id, d) => ({
    id, instructorId: str(d.instructorId), amount: int(d.amount), date: str(d.date), method: oneOf(d.method, METHODS),
    batchId: strOrNull(d.batchId), month: str(d.month), note: str(d.note), createdAt: num(d.createdAt),
  }),
}
