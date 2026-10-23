// Dialogs can be opened from anywhere: useDialogs().open({ kind: 'admission', ... }).
import { createContext, lazy, Suspense, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { useLocation } from 'react-router-dom'
import type { Student } from '../../data/types'

export type DialogRequest =
  | { kind: 'student'; id?: string; prefill?: Partial<Student>; onSaved?: (s: Student) => void }
  | { kind: 'admission'; studentId?: string; batchId?: string; enquiryId?: string }
  | { kind: 'enrollment'; id: string }
  | { kind: 'collect'; studentId?: string; enrollmentId?: string }
  | { kind: 'payment'; id: string }
  | { kind: 'course'; id?: string }
  | { kind: 'batch'; id?: string; courseId?: string }
  | { kind: 'instructor'; id?: string }
  | { kind: 'instructorPayment'; instructorId?: string; id?: string }
  | { kind: 'exam'; batchId: string; id?: string }
  | { kind: 'enquiry'; id?: string }
  | { kind: 'expense'; id?: string }
  | { kind: 'head'; id?: string }
  | { kind: 'member'; id?: string }
  | { kind: 'certificate'; enrollmentId?: string }

const DialogContext = createContext<{ open: (r: DialogRequest) => void }>({ open: () => {} })
export const useDialogs = () => useContext(DialogContext)

const StudentDialog = lazy(() => import('./StudentDialog'))
const AdmissionDialog = lazy(() => import('./AdmissionDialog'))
const EnrollmentDialog = lazy(() => import('./EnrollmentDialog'))
const CollectFeeDialog = lazy(() => import('./CollectFeeDialog'))
const PaymentDialog = lazy(() => import('./PaymentDialog'))
const SetupDialogs = lazy(() => import('./SetupDialogs'))
const OtherDialogs = lazy(() => import('./OtherDialogs'))

export function DialogHost({ children }: { children: ReactNode }) {
  const [request, setRequest] = useState<DialogRequest | null>(null)
  const open = useCallback((r: DialogRequest) => setRequest(r), [])
  const close = useCallback(() => setRequest(null), [])
  // Going to another page (e.g. "Print receipt") closes whatever dialog was open.
  const { pathname } = useLocation()
  useEffect(() => setRequest(null), [pathname])
  let dialog: ReactNode = null
  if (request) {
    switch (request.kind) {
      case 'student': dialog = <StudentDialog request={request} onClose={close} />; break
      case 'admission': dialog = <AdmissionDialog request={request} onClose={close} />; break
      case 'enrollment': dialog = <EnrollmentDialog id={request.id} onClose={close} />; break
      case 'collect': dialog = <CollectFeeDialog request={request} onClose={close} />; break
      case 'payment': dialog = <PaymentDialog id={request.id} onClose={close} />; break
      case 'course': case 'batch': case 'instructor': case 'instructorPayment': case 'member':
        dialog = <SetupDialogs request={request} onClose={close} />; break
      default: dialog = <OtherDialogs request={request} onClose={close} />
    }
  }
  return (
    <DialogContext.Provider value={{ open }}>
      {children}
      {dialog && <Suspense fallback={null}>{dialog}</Suspense>}
    </DialogContext.Provider>
  )
}
