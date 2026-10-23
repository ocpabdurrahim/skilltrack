import {
  getRedirectResult, GoogleAuthProvider, onAuthStateChanged, signInWithCredential, signInWithPopup, signInWithRedirect,
  signOut, type User,
} from 'firebase/auth'
import { createContext, lazy, Suspense, useContext, useEffect, useState } from 'react'
import { NavLink, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { askToCreate, isAppOwner, watchAccess, watchRequests, type CenterRequest, type CreateAccess } from '../data/approvals'
import { createCenter, findCenters, lastCenter, rememberCenter, type CenterChoice } from '../data/centers'
import { store, useData } from '../data/store'
import { ROLE_LABELS, type Role } from '../data/types'
import { initFirebase, type FirebaseHandles } from '../firebase'
import { permissions, type Permissions } from '../domain/roles'
import { Field, Spinner, ToastProvider, useToast } from './common'
import { DialogHost } from './dialogs/host'
import { GoogleLogo, Icon, LogoMark, type IconName } from './icons'
import { useTheme } from './preferences'
import { HomePage } from './pages/Home'

const StudentsPage = lazy(() => import('./pages/Students'))
const StudentPage = lazy(() => import('./pages/StudentProfile'))
const BatchesPage = lazy(() => import('./pages/Batches'))
const BatchPage = lazy(() => import('./pages/BatchDetail'))
const CoursesPage = lazy(() => import('./pages/Courses'))
const AttendancePage = lazy(() => import('./pages/Attendance'))
const ExamMarksPage = lazy(() => import('./pages/ExamMarks'))
const FeesPage = lazy(() => import('./pages/Fees'))
const EnquiriesPage = lazy(() => import('./pages/Enquiries'))
const ExpensesPage = lazy(() => import('./pages/Expenses'))
const InstructorsPage = lazy(() => import('./pages/Instructors'))
const InstructorPage = lazy(() => import('./pages/InstructorDetail'))
const CertificatesPage = lazy(() => import('./pages/Certificates'))
const ReportsPage = lazy(() => import('./pages/Reports'))
const StaffPage = lazy(() => import('./pages/Staff'))
const SettingsPage = lazy(() => import('./pages/Settings'))
const MorePage = lazy(() => import('./pages/More'))
const PrintPage = lazy(() => import('./print/Print'))
const ApprovalsPage = lazy(() => import('./pages/Approvals'))

const FirebaseContext = createContext<FirebaseHandles | null>(null)

export function useFirebase(): FirebaseHandles {
  const value = useContext(FirebaseContext)
  if (!value) throw new Error('Firebase is not ready')
  return value
}

/** The signed-in person and how to leave the current center. */
const SessionContext = createContext<{ user: User; switchCenter: () => void } | null>(null)
export function useSession() {
  const value = useContext(SessionContext)
  if (!value) throw new Error('No session')
  return value
}

export function App() {
  useTheme() // applies the saved theme before anything renders
  const [firebase, setFirebase] = useState<FirebaseHandles | null>(null)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    initFirebase().then(setFirebase, e => setError(e instanceof Error ? e.message : String(e)))
  }, [])
  if (error) {
    return <div className="center-screen"><div className="signin"><h1>SkillTrack</h1><p>{error}</p></div></div>
  }
  if (!firebase) return <Spinner />
  return (
    <FirebaseContext.Provider value={firebase}>
      <ToastProvider>
        <AuthGate />
      </ToastProvider>
    </FirebaseContext.Provider>
  )
}

const emailOf = (user: User) => (user.email ?? '').toLowerCase()

function AuthGate() {
  const { auth } = useFirebase()
  const [user, setUser] = useState<User | null | undefined>(undefined)
  useEffect(() => onAuthStateChanged(auth, setUser), [auth])
  if (user === undefined) return <Spinner />
  if (!user) return <SignIn />
  return <CenterGate key={user.uid} user={user} />
}

function SignIn() {
  const { auth, emulator } = useFirebase()
  const [error, setError] = useState<string | null>(null)
  const [testEmail, setTestEmail] = useState('owner@skilltrack.test')
  useEffect(() => {
    // Finishes a redirect sign-in; failures would otherwise be silent.
    getRedirectResult(auth).catch(e => setError(e instanceof Error ? e.message : String(e)))
  }, [auth])
  const signIn = async () => {
    setError(null)
    const provider = new GoogleAuthProvider()
    provider.setCustomParameters({ prompt: 'select_account' })
    try {
      await signInWithPopup(auth, provider)
    } catch (e) {
      const code = (e as { code?: string }).code
      if (code === 'auth/popup-blocked' || code === 'auth/operation-not-supported-in-this-environment') {
        await signInWithRedirect(auth, provider)
      } else if (code !== 'auth/popup-closed-by-user' && code !== 'auth/cancelled-popup-request') {
        setError(e instanceof Error ? e.message : String(e))
      }
    }
  }
  const testSignIn = () => {
    const email = testEmail.trim().toLowerCase()
    // The auth emulator accepts an unsigned Google token, so tests can sign in without a popup.
    signInWithCredential(auth, GoogleAuthProvider.credential(JSON.stringify({
      sub: `test-${email}`, email, email_verified: true, name: email.split('@')[0],
    }))).catch(e => setError(String(e)))
  }
  return (
    <div className="center-screen">
      <div className="signin">
        <LogoMark size={72} />
        <h1>SkillTrack</h1>
        <p>Students, batches, fees, attendance and results for your training center — the same here as in the Android app.</p>
        <button className="google-btn" onClick={signIn}><GoogleLogo />Continue with Google</button>
        {emulator && (
          <div className="card pad" style={{ marginTop: 18, textAlign: 'left' }}>
            <div className="small bold" style={{ marginBottom: 8 }}>Test mode (local Firebase emulator)</div>
            <div className="row">
              <input className="input" value={testEmail} onChange={e => setTestEmail(e.target.value)} data-testid="test-email" />
              <button className="btn" onClick={testSignIn} data-testid="test-signin">Sign in</button>
            </div>
          </div>
        )}
        {error && <p className="error-text small" style={{ marginTop: 16 }}>{error}</p>}
        <p className="small" style={{ marginTop: 26 }}>
          Staff sign in with the Google account their center's owner added.
        </p>
      </div>
    </div>
  )
}

function CenterGate({ user }: { user: User }) {
  const { db, auth } = useFirebase()
  const email = emailOf(user)
  const [choices, setChoices] = useState<CenterChoice[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [current, setCurrent] = useState<{ id: string; role: Role } | null>(null)
  const [creating, setCreating] = useState(false)
  const [reviewing, setReviewing] = useState(false)
  const [reload, setReload] = useState(0)
  const appOwner = isAppOwner(email)
  const waiting = useWaitingRequests(appOwner)

  useEffect(() => {
    let live = true
    setError(null)
    findCenters(db, email).then(list => {
      if (!live) return
      setChoices(list)
      const last = lastCenter(email)
      const pick = list.find(c => c.id === last) ?? (list.length === 1 ? list[0] : undefined)
      if (pick && reload === 0) setCurrent({ id: pick.id, role: pick.role })
    }, e => live && setError(e instanceof Error ? e.message : String(e)))
    return () => { live = false }
  }, [db, email, reload])

  useEffect(() => {
    if (!current) return
    store.start(db, current.id, email, current.role)
    rememberCenter(email, current.id)
    return () => store.stop()
  }, [db, email, current])

  const switchCenter = () => {
    rememberCenter(email, null)
    setCurrent(null)
    setReload(n => n + 1)
  }

  if (current) {
    return (
      <SessionContext.Provider value={{ user, switchCenter }}>
        <DialogHost>
          <Shell />
        </DialogHost>
      </SessionContext.Provider>
    )
  }
  if (error) {
    return (
      <div className="center-screen"><div className="signin">
        <h1>Can't reach SkillTrack</h1><p>{error}</p>
        <button className="btn" onClick={() => setReload(n => n + 1)}>Try again</button>{' '}
        <button className="btn text" onClick={() => signOut(auth)}>Sign out</button>
      </div></div>
    )
  }
  if (!choices) return <Spinner />
  if (reviewing) {
    return (
      <div style={{ maxWidth: 980, margin: '0 auto' }}>
        <Suspense fallback={<Spinner />}><ApprovalsPage me={email} onBack={() => setReviewing(false)} /></Suspense>
      </div>
    )
  }
  return (
    <div className="center-screen">
      <div className="picker stack">
        <div className="row"><LogoMark /><h1 style={{ margin: 0, fontSize: 24 }} className="grow">SkillTrack</h1>
          <button className="btn text" onClick={() => signOut(auth)}><Icon name="logout" size={18} />Sign out</button></div>
        <div className="muted small">Signed in as {email}</div>
        {appOwner && !creating && (
          <button className="btn tonal" onClick={() => setReviewing(true)} data-testid="open-approvals">
            <Icon name="check" size={18} />Center requests{waiting > 0 ? ` (${waiting} waiting)` : ''}
          </button>
        )}
        {choices.length > 0 && !creating && (
          <div className="card list">
            <div className="card-head"><h2>Your centers</h2></div>
            {choices.map(c => (
              <button key={c.id} className="item" onClick={() => setCurrent({ id: c.id, role: c.role })} data-testid="center-choice">
                <LogoMark size={36} />
                <div className="grow"><div className="title">{c.name}</div><div className="subtitle">{c.tagline || 'Training center'}</div></div>
                <span className="pill indigo">{ROLE_LABELS[c.role]}</span>
              </button>
            ))}
          </div>
        )}
        {choices.length === 0 && !creating && (
          <div className="card pad">
            <h2>Welcome</h2>
            <p className="muted">If your center already uses SkillTrack, ask its owner to add <b>{email}</b> under Staff, then
              <button className="btn text small" onClick={() => setReload(n => n + 1)}>check again</button>.</p>
            <p className="muted">Or start your own center: you'll be its owner{appOwner ? '' : ' once the SkillTrack owner approves it'}.</p>
            <button className="btn" onClick={() => setCreating(true)} data-testid="create-center">Create a center</button>
          </div>
        )}
        {choices.length > 0 && !creating && (
          <button className="btn tonal" onClick={() => setCreating(true)}><Icon name="plus" size={18} />Create another center</button>
        )}
        {creating && (
          <NewCenter user={user} onCancel={() => setCreating(false)} onCreated={id => { setCreating(false); setCurrent({ id, role: 'OWNER' }) }} />
        )}
      </div>
    </div>
  )
}

/** How many center requests wait for an answer (the app owner's picker). */
function useWaitingRequests(appOwner: boolean): number {
  const { db } = useFirebase()
  const [waiting, setWaiting] = useState(0)
  useEffect(() => (appOwner ? watchRequests(db, list => setWaiting(list.filter(r => r.status === 'PENDING').length)) : undefined), [db, appOwner])
  return waiting
}

/**
 * "Create a center": the form for the app owner and approved people; everyone
 * else asks first, and the form appears by itself once they are approved.
 */
function NewCenter({ user, onCancel, onCreated }: { user: User; onCancel: () => void; onCreated: (id: string) => void }) {
  const { db } = useFirebase()
  const email = emailOf(user)
  const [access, setAccess] = useState<CreateAccess>({ kind: 'loading' })
  const [editing, setEditing] = useState(false)
  useEffect(() => watchAccess(db, email, setAccess), [db, email])
  if (access.kind === 'loading') return <div className="card pad"><p className="muted">Checking whether you can create a center…</p></div>
  if (access.kind === 'allowed') return <CreateCenter user={user} onCancel={onCancel} onCreated={onCreated} />
  const r = access.request
  if (!r || editing) return <RequestCenter user={user} previous={r} onCancel={onCancel} onSent={() => setEditing(false)} />
  return (
    <div className="card pad stack" data-testid="request-status">
      {r.status === 'DECLINED' ? (
        <>
          <h2>Not approved</h2>
          <p className="muted">Your request to create “{r.centerName}” was not approved. You can ask again with more details.</p>
        </>
      ) : (
        <>
          <h2>Request sent</h2>
          <p className="muted">You asked on {new Date(r.createdAt).toLocaleString('en-GB')} to create “{r.centerName}”. As soon as the
            SkillTrack owner approves it, this turns into the form to create your center. You can close the page and come back.</p>
        </>
      )}
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <button className="btn text" onClick={onCancel}>Back</button>
        <button className="btn tonal" onClick={() => setEditing(true)}>{r.status === 'DECLINED' ? 'Ask again' : 'Change my request'}</button>
      </div>
    </div>
  )
}

function RequestCenter({ user, previous, onCancel, onSent }: {
  user: User; previous: CenterRequest | null; onCancel: () => void; onSent: () => void
}) {
  const { db } = useFirebase()
  const toast = useToast()
  const email = emailOf(user)
  const [f, setF] = useState({
    name: previous?.name ?? user.displayName ?? '', centerName: previous?.centerName ?? '', phone: previous?.phone ?? '',
    message: previous?.message ?? '',
  })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const send = async () => {
    setBusy(true)
    setError(null)
    try {
      await askToCreate(db, { email, name: f.name.trim(), centerName: f.centerName.trim(), phone: f.phone.trim(), message: f.message.trim() })
      toast('Request sent')
      onSent()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
    setBusy(false)
  }
  return (
    <div className="card pad stack">
      <h2>Ask to create a center</h2>
      <p className="muted">New centers on SkillTrack are approved by its owner. Tell them who you are; you'll be able to create your
        center here as soon as they approve.</p>
      <div className="form-grid">
        <Field label="Your name *"><input className="input" value={f.name} onChange={e => setF({ ...f, name: e.target.value })} /></Field>
        <Field label="Phone *"><input className="input" type="tel" value={f.phone} onChange={e => setF({ ...f, phone: e.target.value })} data-testid="request-phone" /></Field>
        <Field label="Center name *" full><input className="input" autoFocus value={f.centerName} onChange={e => setF({ ...f, centerName: e.target.value })} data-testid="request-center-name" /></Field>
        <Field label="Message (optional)" full><textarea className="input" rows={3} value={f.message} maxLength={1000}
          placeholder="Where is your center, how many students, how you heard of SkillTrack…" onChange={e => setF({ ...f, message: e.target.value })} /></Field>
      </div>
      <div className="small muted">Signed in as {email}</div>
      {error && <div className="error-text small">{error}</div>}
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <button className="btn text" onClick={onCancel}>Cancel</button>
        <button className="btn" disabled={busy || !f.name.trim() || !f.centerName.trim() || !f.phone.trim()} onClick={send} data-testid="send-request">
          {busy ? 'Sending…' : 'Send request'}
        </button>
      </div>
    </div>
  )
}

function CreateCenter({ user, onCancel, onCreated }: { user: User; onCancel: () => void; onCreated: (id: string) => void }) {
  const { db } = useFirebase()
  const toast = useToast()
  const [f, setF] = useState({ name: '', tagline: 'Computer Training Center', address: '', phone: '', email: user.email ?? '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const create = async () => {
    setBusy(true)
    setError(null)
    try {
      const id = await createCenter(db, { uid: user.uid, email: emailOf(user), name: user.displayName ?? '' },
        { ...f, name: f.name.trim(), tagline: f.tagline.trim(), address: f.address.trim(), phone: f.phone.trim(), email: f.email.trim() })
      toast('Center created')
      onCreated(id)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
      setBusy(false)
    }
  }
  return (
    <div className="card pad stack">
      <h2>New center</h2>
      <div className="form-grid">
        <Field label="Center name *" full><input className="input" autoFocus value={f.name} placeholder="e.g. Bright Future IT Institute"
          onChange={e => setF({ ...f, name: e.target.value })} data-testid="center-name" /></Field>
        <Field label="Tagline" full><input className="input" value={f.tagline} onChange={e => setF({ ...f, tagline: e.target.value })} /></Field>
        <Field label="Address" full><input className="input" value={f.address} onChange={e => setF({ ...f, address: e.target.value })} /></Field>
        <Field label="Phone"><input className="input" type="tel" value={f.phone} onChange={e => setF({ ...f, phone: e.target.value })} /></Field>
        <Field label="Email"><input className="input" type="email" value={f.email} onChange={e => setF({ ...f, email: e.target.value })} /></Field>
      </div>
      <div className="small muted">Currency ৳ (Taka). You can change it, add a logo and set number prefixes under Settings.</div>
      {error && <div className="error-text small">{error}</div>}
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <button className="btn text" onClick={onCancel}>Cancel</button>
        <button className="btn" disabled={!f.name.trim() || busy} onClick={create} data-testid="center-create">{busy ? 'Creating…' : 'Create center'}</button>
      </div>
    </div>
  )
}

interface NavItem { to: string; label: string; icon: IconName; show: (p: Permissions, appOwner: boolean) => boolean; group?: string }

const NAV: NavItem[] = [
  { to: '/', label: 'Home', icon: 'home', show: () => true },
  { to: '/students', label: 'Students', icon: 'students', show: () => true },
  { to: '/batches', label: 'Batches', icon: 'batches', show: () => true },
  { to: '/attendance', label: 'Attendance', icon: 'attendance', show: p => p.teach },
  { to: '/fees', label: 'Fees', icon: 'fees', show: p => p.seeMoney },
  { to: '/enquiries', label: 'Enquiries', icon: 'enquiries', show: p => p.manageStudents },
  { to: '/certificates', label: 'Certificates', icon: 'certificate', show: p => p.manageStudents },
  { to: '/courses', label: 'Courses', icon: 'courses', show: () => true, group: 'Setup' },
  { to: '/instructors', label: 'Instructors', icon: 'instructors', show: p => p.seeMoney },
  { to: '/expenses', label: 'Expenses', icon: 'expenses', show: p => p.seeMoney },
  { to: '/reports', label: 'Reports', icon: 'reports', show: p => p.seeMoney },
  { to: '/staff', label: 'Staff', icon: 'staff', show: p => p.manageCenter },
  { to: '/settings', label: 'Settings', icon: 'settings', show: () => true },
  { to: '/approvals', label: 'Center requests', icon: 'check', show: (_p, appOwner) => appOwner, group: 'SkillTrack' },
]

function Shell() {
  const data = useData()
  const { user, switchCenter } = useSession()
  const location = useLocation()
  const can = permissions(data.role)
  const appOwner = isAppOwner(user.email)
  const nav = NAV.filter(n => n.show(can, appOwner))
  const bottom: NavItem[] = [
    NAV[0], NAV[1], NAV[2],
    can.seeMoney ? NAV.find(n => n.to === '/fees')! : NAV.find(n => n.to === '/attendance')!,
    { to: '/more', label: 'More', icon: 'more', show: () => true },
  ]
  const syncLabel = data.error && !data.revoked ? 'Sync problem' : data.pending > 0 ? 'Saving…' : data.synced ? 'Synced' : 'Connecting…'
  const syncColor = data.error ? 'var(--error)' : data.pending > 0 || !data.synced ? 'var(--warning)' : 'var(--success)'

  if (data.revoked) {
    return (
      <div className="center-screen"><div className="signin">
        <LogoMark size={64} />
        <h1>No access</h1>
        <p>{user.email} is no longer active staff at this center. Ask the owner to add you again.</p>
        <button className="btn" onClick={switchCenter}>Choose another center</button>
      </div></div>
    )
  }
  if (location.pathname.startsWith('/print/')) {
    return !data.ready || !data.center ? <Spinner /> : (
      <Suspense fallback={<Spinner />}><Routes><Route path="/print/*" element={<PrintPage />} /></Routes></Suspense>
    )
  }

  return (
    <div className="app">
      <nav className="sidebar" aria-label="Main">
        <div className="brand"><LogoMark />SkillTrack</div>
        <div className="brand-center" title={data.center?.name}>{data.center?.name ?? '…'} · {ROLE_LABELS[data.role]}</div>
        {nav.map((n, i) => (
          <div key={n.to}>
            {n.group && nav.findIndex(x => x.group === n.group) === i && <div className="nav-group">{n.group}</div>}
            <NavLink to={n.to} end={n.to === '/'} className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
              <Icon name={n.icon} size={20} />{n.label}
            </NavLink>
          </div>
        ))}
        <div className="sidebar-foot">
          <span className="sync-dot" style={{ background: syncColor }} /> {syncLabel}
          <div className="ellipsis">{user.email}</div>
        </div>
      </nav>

      <main className="main">
        {!data.ready || !data.center ? <Spinner /> : (
          <Suspense fallback={<Spinner />}>
            <Routes>
              <Route path="/" element={<HomePage />} />
              <Route path="/students" element={<StudentsPage />} />
              <Route path="/students/:id" element={<StudentPage />} />
              <Route path="/batches" element={<BatchesPage />} />
              <Route path="/batches/:id" element={<BatchPage />} />
              <Route path="/courses" element={<CoursesPage />} />
              <Route path="/settings" element={<SettingsPage />} />
              <Route path="/more" element={<MorePage />} />
              {can.teach && <Route path="/attendance" element={<AttendancePage />} />}
              {can.teach && <Route path="/exams/:id" element={<ExamMarksPage />} />}
              {can.seeMoney && <Route path="/fees" element={<FeesPage />} />}
              {can.seeMoney && <Route path="/expenses" element={<ExpensesPage />} />}
              {can.seeMoney && <Route path="/instructors" element={<InstructorsPage />} />}
              {can.seeMoney && <Route path="/instructors/:id" element={<InstructorPage />} />}
              {can.seeMoney && <Route path="/reports" element={<ReportsPage />} />}
              {can.manageStudents && <Route path="/enquiries" element={<EnquiriesPage />} />}
              {can.manageStudents && <Route path="/certificates" element={<CertificatesPage />} />}
              {can.manageCenter && <Route path="/staff" element={<StaffPage />} />}
              {appOwner && <Route path="/approvals" element={<ApprovalsPage me={(user.email ?? '').toLowerCase()} />} />}
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </Suspense>
        )}
      </main>

      <nav className="bottom-nav" aria-label="Main">
        {bottom.map(n => (
          <NavLink key={n.to} to={n.to} end={n.to === '/'} className={({ isActive }) => (isActive ? 'active' : '')}>
            <span className="navpill"><Icon name={n.icon} /></span>{n.label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}

export { NAV }
