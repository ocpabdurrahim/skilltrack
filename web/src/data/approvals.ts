// Who may create a center (docs/data-model.md, "Who may create a center"):
// the app owner, and people the app owner approved. Everyone else asks first.
import {
  collection, deleteDoc, doc, onSnapshot, serverTimestamp, setDoc, writeBatch, type Firestore, type Unsubscribe,
} from 'firebase/firestore'

/** Must match isAppAdmin() in firebase/firestore.rules and AppOwner.EMAILS in the Android app. */
export const APP_OWNERS = ['ocpabdurrahim@gmail.com']

export const isAppOwner = (email: string | null | undefined) => !!email && APP_OWNERS.includes(email.toLowerCase())

export type RequestStatus = 'PENDING' | 'APPROVED' | 'DECLINED'

export interface CenterRequest {
  email: string
  name: string
  centerName: string
  phone: string
  message: string
  status: RequestStatus
  createdAt: number
  answeredBy: string
}

export interface Creator {
  email: string
  name: string
  approvedBy: string
  approvedAt: number
}

export type CreateAccess =
  | { kind: 'loading' }
  | { kind: 'allowed' }
  | { kind: 'notAllowed'; request: CenterRequest | null }

const str = (v: unknown) => (typeof v === 'string' ? v : '')
const num = (v: unknown) => (typeof v === 'number' && Number.isFinite(v) ? v : 0)

function parseRequest(id: string, d: Record<string, unknown>): CenterRequest {
  const status = d.status === 'APPROVED' || d.status === 'DECLINED' ? d.status : 'PENDING'
  return {
    email: str(d.email) || id, name: str(d.name), centerName: str(d.centerName), phone: str(d.phone),
    message: str(d.message), status, createdAt: num(d.createdAt), answeredBy: str(d.answeredBy),
  }
}

function parseCreator(id: string, d: Record<string, unknown>): Creator {
  return { email: str(d.email) || id, name: str(d.name), approvedBy: str(d.approvedBy), approvedAt: num(d.approvedAt) }
}

/**
 * Live: may [email] create a center? Answers only once the server has spoken
 * (a cache miss may just mean "never downloaded"); a refused read counts as "no".
 */
export function watchAccess(db: Firestore, email: string, onChange: (a: CreateAccess) => void): Unsubscribe {
  if (isAppOwner(email)) {
    onChange({ kind: 'allowed' })
    return () => {}
  }
  type Doc = { state: 'loading' } | { state: 'missing' } | { state: 'present'; data: Record<string, unknown> }
  let creator: Doc = { state: 'loading' }
  let request: Doc = { state: 'loading' }
  const emit = () => {
    if (creator.state === 'loading' || request.state === 'loading') onChange({ kind: 'loading' })
    else if (creator.state === 'present') onChange({ kind: 'allowed' })
    else onChange({ kind: 'notAllowed', request: request.state === 'present' ? parseRequest(email, request.data) : null })
  }
  const watch = (path: string, set: (d: Doc) => void) =>
    onSnapshot(doc(db, path), { includeMetadataChanges: true }, snap => {
      if (snap.exists()) set({ state: 'present', data: snap.data() })
      else if (!snap.metadata.fromCache) set({ state: 'missing' })
      emit()
    }, () => {
      set({ state: 'missing' })
      emit()
    })
  const stops = [
    watch(`creators/${email}`, d => { creator = d }),
    watch(`centerRequests/${email}`, d => { request = d }),
  ]
  emit()
  return () => stops.forEach(s => s())
}

/** Sends (or re-sends) a request; it always goes in as waiting. */
export function askToCreate(db: Firestore, r: Omit<CenterRequest, 'status' | 'createdAt' | 'answeredBy'>): Promise<void> {
  return setDoc(doc(db, 'centerRequests', r.email), {
    ...r, status: 'PENDING', createdAt: Date.now(), answeredBy: '', updatedAt: serverTimestamp(),
  })
}

// ---- The app owner's side ----------------------------------------------------

/** Every request, newest first. Only the app owner may read them; anyone else gets nothing. */
export function watchRequests(db: Firestore, onChange: (list: CenterRequest[]) => void): Unsubscribe {
  return onSnapshot(collection(db, 'centerRequests'), snap => {
    onChange(snap.docs.map(d => parseRequest(d.id, d.data())).sort((a, b) => b.createdAt - a.createdAt))
  }, () => onChange([]))
}

export function watchCreators(db: Firestore, onChange: (list: Creator[]) => void): Unsubscribe {
  return onSnapshot(collection(db, 'creators'), snap => {
    onChange(snap.docs.map(d => parseCreator(d.id, d.data())).sort((a, b) => b.approvedAt - a.approvedAt))
  }, () => onChange([]))
}

/** Lets [email] create centers, and marks their request (if any) approved, in one batch. */
export function approve(db: Firestore, email: string, name: string, by: string, hasRequest: boolean): Promise<void> {
  const e = email.trim().toLowerCase()
  const batch = writeBatch(db)
  batch.set(doc(db, 'creators', e), { email: e, name: name.trim(), approvedBy: by, approvedAt: Date.now(), updatedAt: serverTimestamp() })
  if (hasRequest) batch.set(doc(db, 'centerRequests', e), { status: 'APPROVED', answeredBy: by, updatedAt: serverTimestamp() }, { merge: true })
  return batch.commit()
}

export function decline(db: Firestore, email: string, by: string): Promise<void> {
  return setDoc(doc(db, 'centerRequests', email), { status: 'DECLINED', answeredBy: by, updatedAt: serverTimestamp() }, { merge: true })
}

/** Takes the approval away: no new centers; centers already made stay. */
export function removeCreator(db: Firestore, email: string): Promise<void> {
  return deleteDoc(doc(db, 'creators', email))
}
