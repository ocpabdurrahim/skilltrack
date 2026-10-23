// What each role may do, matching firestore.rules. The UI hides the rest.
import type { Role, WritableName } from '../data/types'

export interface Permissions {
  /** Center profile, settings and staff. */
  manageCenter: boolean
  /** Courses, batches and instructors. */
  manageSetup: boolean
  /** Students, photos, admissions, enquiries, certificates. */
  manageStudents: boolean
  /** Fee payments, expenses, expense heads, instructor payments. */
  manageMoney: boolean
  /** Editing or deleting a receipt already issued. */
  editReceipts: boolean
  seeMoney: boolean
  /** Attendance and exams. */
  teach: boolean
}

export function permissions(role: Role | null): Permissions {
  const admin = role === 'OWNER' || role === 'ADMIN'
  const office = role === 'OFFICE'
  return {
    manageCenter: admin,
    manageSetup: admin,
    manageStudents: admin || office,
    manageMoney: admin || office,
    editReceipts: admin,
    seeMoney: admin || office,
    teach: admin || role === 'TEACHER',
  }
}

export function canWrite(role: Role | null, name: WritableName): boolean {
  const p = permissions(role)
  switch (name) {
    case 'members': return p.manageCenter
    case 'courses': case 'batches': case 'instructors': return p.manageSetup
    case 'students': case 'photos': case 'enrollments': case 'enquiries': case 'certificates': return p.manageStudents
    case 'payments': case 'expenses': case 'expenseHeads': case 'instructorPayments': return p.manageMoney
    case 'attendance': case 'exams': return p.teach
  }
}
