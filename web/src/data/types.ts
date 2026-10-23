// The records the web app works with, as described in docs/data-model.md.
// Ids are the Firestore document ids; sync fields (updatedAt, deleted) live
// only in Firestore.
import type { Grouping } from '../domain/money'

export type Role = 'OWNER' | 'ADMIN' | 'OFFICE' | 'TEACHER'
export const ROLES: Role[] = ['OWNER', 'ADMIN', 'OFFICE', 'TEACHER']

export type Day = 'SAT' | 'SUN' | 'MON' | 'TUE' | 'WED' | 'THU' | 'FRI'
export const DAYS: Day[] = ['SAT', 'SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI']

export type PayType = 'MONTHLY' | 'PER_BATCH' | 'PER_CLASS' | 'NONE'
export const PAY_TYPES: PayType[] = ['MONTHLY', 'PER_BATCH', 'PER_CLASS', 'NONE']

export type Gender = 'MALE' | 'FEMALE' | 'OTHER' | ''
export type EnrollmentStatus = 'ACTIVE' | 'COMPLETED' | 'DROPPED'
export const ENROLLMENT_STATUSES: EnrollmentStatus[] = ['ACTIVE', 'COMPLETED', 'DROPPED']

export type Method = 'CASH' | 'BKASH' | 'NAGAD' | 'ROCKET' | 'BANK' | 'CARD' | 'OTHER'
export const METHODS: Method[] = ['CASH', 'BKASH', 'NAGAD', 'ROCKET', 'BANK', 'CARD', 'OTHER']

export type Mark = 'P' | 'A' | 'L' | 'E'
export const MARKS: Mark[] = ['P', 'A', 'L', 'E']

export type EnquirySource = 'WALK_IN' | 'PHONE' | 'FACEBOOK' | 'REFERRAL' | 'OTHER'
export const ENQUIRY_SOURCES: EnquirySource[] = ['WALK_IN', 'PHONE', 'FACEBOOK', 'REFERRAL', 'OTHER']

export type EnquiryStatus = 'OPEN' | 'CONVERTED' | 'LOST'
export const ENQUIRY_STATUSES: EnquiryStatus[] = ['OPEN', 'CONVERTED', 'LOST']

export interface Center {
  id: string
  name: string
  tagline: string
  address: string
  phone: string
  email: string
  website: string
  logo: string
  signatory: string
  currencyCode: string
  currencySymbol: string
  grouping: Grouping
  studentPrefix: string
  receiptPrefix: string
  certificatePrefix: string
  ownerUid: string
  ownerEmail: string
  createdAt: number
}

export interface Member {
  /** The lower-case email, which is also the document id. */
  id: string
  email: string
  name: string
  role: Role
  active: boolean
  instructorId: string | null
  addedBy: string
  createdAt: number
}

export interface Course {
  id: string
  name: string
  code: string
  fee: number
  duration: string
  description: string
  archived: boolean
}

export interface Instructor {
  id: string
  name: string
  phone: string
  email: string
  subject: string
  payType: PayType
  payRate: number
  joinDate: string
  leaveDate: string
  note: string
  archived: boolean
}

export interface Batch {
  id: string
  name: string
  courseId: string
  instructorId: string | null
  startDate: string
  endDate: string
  days: Day[]
  time: string
  room: string
  capacity: number
  cancelled: boolean
  note: string
}

export interface Student {
  id: string
  regNo: string
  name: string
  fatherName: string
  motherName: string
  gender: Gender
  dob: string
  phone: string
  guardianPhone: string
  email: string
  address: string
  education: string
  institution: string
  idNo: string
  joinDate: string
  note: string
  archived: boolean
}

export interface Photo {
  id: string
  data: string
}

export interface Installment {
  due: string
  amount: number
}

export interface Enrollment {
  id: string
  studentId: string
  batchId: string
  courseId: string
  date: string
  fee: number
  discount: number
  installments: Installment[]
  status: EnrollmentStatus
  rollNo: number
  completedOn: string
  note: string
}

export interface Payment {
  id: string
  enrollmentId: string
  studentId: string
  amount: number
  date: string
  method: Method
  receiptNo: string
  note: string
  receivedBy: string
  createdAt: number
}

export interface Attendance {
  id: string
  batchId: string
  date: string
  marks: Record<string, Mark>
  takenBy: string
  note: string
}

export interface Exam {
  id: string
  batchId: string
  name: string
  date: string
  fullMarks: number
  passMarks: number
  marks: Record<string, number>
}

export interface Certificate {
  id: string
  enrollmentId: string
  studentId: string
  certNo: string
  issueDate: string
  grade: string
  note: string
}

export interface Enquiry {
  id: string
  name: string
  phone: string
  courseId: string | null
  source: EnquirySource
  date: string
  followUp: string
  status: EnquiryStatus
  note: string
  studentId: string | null
  createdAt: number
}

export interface ExpenseHead {
  id: string
  name: string
  emoji: string
  archived: boolean
}

export interface Expense {
  id: string
  headId: string
  amount: number
  date: string
  method: Method
  note: string
  paidTo: string
  createdAt: number
}

export interface InstructorPayment {
  id: string
  instructorId: string
  amount: number
  date: string
  method: Method
  batchId: string | null
  month: string
  note: string
  createdAt: number
}

/** The collections kept in memory (photos are fetched one at a time). */
export interface Collections {
  members: Member
  courses: Course
  instructors: Instructor
  batches: Batch
  students: Student
  enrollments: Enrollment
  payments: Payment
  attendance: Attendance
  exams: Exam
  certificates: Certificate
  enquiries: Enquiry
  expenseHeads: ExpenseHead
  expenses: Expense
  instructorPayments: InstructorPayment
}

export type CollectionName = keyof Collections

export const COLLECTIONS: CollectionName[] = [
  'members', 'courses', 'instructors', 'batches', 'students', 'enrollments', 'payments', 'attendance', 'exams',
  'certificates', 'enquiries', 'expenseHeads', 'expenses', 'instructorPayments',
]

/** What a TEACHER may not read. */
export const MONEY_COLLECTIONS: CollectionName[] = ['payments', 'expenses', 'expenseHeads', 'instructorPayments']

/** Any collection a record can be written to, photos included. */
export type WritableName = CollectionName | 'photos'

export const DEFAULT_EXPENSE_HEADS: ExpenseHead[] = [
  { id: 'head_rent', name: 'Rent', emoji: '🏠', archived: false },
  { id: 'head_bills', name: 'Electricity & bills', emoji: '💡', archived: false },
  { id: 'head_internet', name: 'Internet', emoji: '🌐', archived: false },
  { id: 'head_salary', name: 'Staff salary', emoji: '👥', archived: false },
  { id: 'head_marketing', name: 'Marketing', emoji: '📣', archived: false },
  { id: 'head_stationery', name: 'Stationery & printing', emoji: '📝', archived: false },
  { id: 'head_equipment', name: 'Computers & equipment', emoji: '💻', archived: false },
  { id: 'head_repairs', name: 'Repairs & maintenance', emoji: '🔧', archived: false },
  { id: 'head_other', name: 'Other', emoji: '📦', archived: false },
]

export const ROLE_LABELS: Record<Role, string> = {
  OWNER: 'Owner', ADMIN: 'Admin', OFFICE: 'Office', TEACHER: 'Teacher',
}

export const ROLE_HELP: Record<Role, string> = {
  OWNER: 'Everything; created the center',
  ADMIN: 'Everything except the owner',
  OFFICE: 'Students, admissions, fees, expenses, enquiries, certificates',
  TEACHER: 'Attendance and exams; sees no money',
}

export const METHOD_LABELS: Record<Method, string> = {
  CASH: 'Cash', BKASH: 'bKash', NAGAD: 'Nagad', ROCKET: 'Rocket', BANK: 'Bank', CARD: 'Card', OTHER: 'Other',
}

export const PAY_TYPE_LABELS: Record<PayType, string> = {
  MONTHLY: 'Monthly salary', PER_BATCH: 'Per batch', PER_CLASS: 'Per class', NONE: 'Not worked out',
}

export const SOURCE_LABELS: Record<EnquirySource, string> = {
  WALK_IN: 'Walk-in', PHONE: 'Phone', FACEBOOK: 'Facebook', REFERRAL: 'Referral', OTHER: 'Other',
}

export const DAY_LABELS: Record<Day, string> = {
  SAT: 'Sat', SUN: 'Sun', MON: 'Mon', TUE: 'Tue', WED: 'Wed', THU: 'Thu', FRI: 'Fri',
}

export const MARK_LABELS: Record<Mark, string> = { P: 'Present', A: 'Absent', L: 'Late', E: 'Excused' }
