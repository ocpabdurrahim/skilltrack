import { useState } from 'react'
import { removeCourse } from '../../data/actions'
import { newId } from '../../data/ids'
import { store, useData } from '../../data/store'
import {
  DAY_LABELS, DAYS, PAY_TYPE_LABELS, PAY_TYPES, ROLE_HELP, ROLE_LABELS,
  type Batch, type Course, type Day, type Instructor, type InstructorPayment, type Member, type Method, type PayType, type Role,
} from '../../data/types'
import { addMonths, currentMonth, monthLabel } from '../../domain/dates'
import { instructorPay } from '../../domain/instructors'
import {
  amountText, ConfirmDialog, Field, MethodSelect, Modal, MoneyInput, parseMoney, useMoney, useToast, useToday,
} from '../common'
import type { DialogRequest } from './host'

type SetupRequest = Extract<DialogRequest, { kind: 'course' | 'batch' | 'instructor' | 'instructorPayment' | 'member' }>

export default function SetupDialogs({ request, onClose }: { request: SetupRequest; onClose: () => void }) {
  switch (request.kind) {
    case 'course': return <CourseDialog id={request.id} onClose={onClose} />
    case 'batch': return <BatchDialog id={request.id} courseId={request.courseId} onClose={onClose} />
    case 'instructor': return <InstructorDialog id={request.id} onClose={onClose} />
    case 'instructorPayment': return <InstructorPaymentDialog id={request.id} instructorId={request.instructorId} onClose={onClose} />
    case 'member': return <MemberDialog id={request.id} onClose={onClose} />
  }
}

function CourseDialog({ id, onClose }: { id?: string; onClose: () => void }) {
  const data = useData()
  const toast = useToast()
  const existing = id ? data.idx.courses.get(id) : undefined
  const [c, setC] = useState<Course>(existing ?? { id: newId(), name: '', code: '', fee: 0, duration: '', description: '', archived: false })
  const [fee, setFee] = useState(amountText(c.fee))
  const [confirm, setConfirm] = useState(false)
  const save = () => {
    store.put('courses', { ...c, name: c.name.trim(), code: c.code.trim().toUpperCase(), fee: parseMoney(fee) })
    toast('Course saved')
    onClose()
  }
  return (
    <Modal onClose={onClose} label="Course">
      <h2>{existing ? 'Edit course' : 'New course'}</h2>
      <div className="form-grid">
        <Field label="Name *" full><input className="input" autoFocus value={c.name} placeholder="e.g. MS Office Application" onChange={e => setC({ ...c, name: e.target.value })} data-testid="course-name" /></Field>
        <Field label="Short code"><input className="input" value={c.code} placeholder="e.g. MSO" onChange={e => setC({ ...c, code: e.target.value })} /></Field>
        <Field label="Course fee"><MoneyInput value={fee} onChange={setFee} testId="course-fee" /></Field>
        <Field label="Duration"><input className="input" value={c.duration} placeholder="e.g. 3 months" onChange={e => setC({ ...c, duration: e.target.value })} /></Field>
        <Field label="Description" full><textarea className="input" value={c.description} onChange={e => setC({ ...c, description: e.target.value })} /></Field>
      </div>
      <div className="actions">
        {existing && (existing.archived
          ? <button className="btn text" style={{ marginRight: 'auto' }} onClick={() => { store.put('courses', { ...c, archived: false }); onClose() }}>Restore</button>
          : <button className="btn danger" style={{ marginRight: 'auto' }} onClick={() => setConfirm(true)}>Delete</button>)}
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!c.name.trim()} onClick={save} data-testid="course-save">Save</button>
      </div>
      {confirm && existing && (
        <ConfirmDialog title="Remove this course?" danger confirm="Remove"
          text="A course that already has batches, admissions or enquiries is archived instead, so its history stays intact."
          onConfirm={() => { toast(removeCourse(data, existing) === 'archived' ? 'Course archived' : 'Course deleted'); onClose() }}
          onClose={() => setConfirm(false)} />
      )}
    </Modal>
  )
}

function BatchDialog({ id, courseId, onClose }: { id?: string; courseId?: string; onClose: () => void }) {
  const data = useData()
  const toast = useToast()
  const today = useToday()
  const existing = id ? data.idx.batches.get(id) : undefined
  const courses = data.courses.filter(c => !c.archived || c.id === existing?.courseId)
  const [b, setB] = useState<Batch>(existing ?? {
    id: newId(), name: '', courseId: courseId ?? courses[0]?.id ?? '', instructorId: null, startDate: today, endDate: '',
    days: ['SAT', 'MON', 'WED'], time: '', room: '', capacity: 0, cancelled: false, note: '',
  })
  const course = data.idx.courses.get(b.courseId)
  // Suggests a name like MSO-26-B03 for a new batch.
  const suggestion = (() => {
    const code = course?.code || course?.name.slice(0, 3).toUpperCase() || 'B'
    const yy = (b.startDate || today).slice(2, 4)
    const n = data.batches.filter(x => x.courseId === b.courseId && x.startDate.slice(2, 4) === yy).length + 1
    return `${code}-${yy}-B${String(n).padStart(2, '0')}`
  })()
  const toggle = (d: Day) => setB({ ...b, days: b.days.includes(d) ? b.days.filter(x => x !== d) : DAYS.filter(x => x === d || b.days.includes(x)) })
  const save = () => {
    store.put('batches', { ...b, name: (b.name.trim() || suggestion) })
    toast('Batch saved')
    onClose()
  }
  if (courses.length === 0) {
    return (
      <Modal onClose={onClose} narrow label="Batch">
        <h2>Add a course first</h2>
        <p className="muted">Every batch belongs to a course. Create the course under Courses, then add its batches.</p>
        <div className="actions"><button className="btn" onClick={onClose}>OK</button></div>
      </Modal>
    )
  }
  return (
    <Modal onClose={onClose} label="Batch">
      <h2>{existing ? 'Edit batch' : 'New batch'}</h2>
      <div className="form-grid">
        <Field label="Course *">
          <select className="input" value={b.courseId} onChange={e => setB({ ...b, courseId: e.target.value })} data-testid="batch-course">
            {courses.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </Field>
        <Field label="Batch name" hint={!b.name.trim() ? `Leave empty for ${suggestion}` : undefined}>
          <input className="input" value={b.name} placeholder={suggestion} onChange={e => setB({ ...b, name: e.target.value })} data-testid="batch-name" />
        </Field>
        <Field label="Starts"><input className="input" type="date" value={b.startDate} onChange={e => setB({ ...b, startDate: e.target.value })} /></Field>
        <Field label="Ends"><input className="input" type="date" value={b.endDate} onChange={e => setB({ ...b, endDate: e.target.value })} /></Field>
        <Field label="Class days" full>
          <div className="day-toggles">
            {DAYS.map(d => <button key={d} type="button" className={b.days.includes(d) ? 'on' : ''} onClick={() => toggle(d)}>{DAY_LABELS[d]}</button>)}
          </div>
        </Field>
        <Field label="Time"><input className="input" value={b.time} placeholder="e.g. 4:00 PM - 6:00 PM" onChange={e => setB({ ...b, time: e.target.value })} /></Field>
        <Field label="Room / lab"><input className="input" value={b.room} onChange={e => setB({ ...b, room: e.target.value })} /></Field>
        <Field label="Instructor">
          <select className="input" value={b.instructorId ?? ''} onChange={e => setB({ ...b, instructorId: e.target.value || null })}>
            <option value="">—</option>
            {data.instructors.filter(i => !i.archived || i.id === b.instructorId).map(i => <option key={i.id} value={i.id}>{i.name}</option>)}
          </select>
        </Field>
        <Field label="Seats" hint="0 = no limit">
          <input className="input" type="number" min={0} value={b.capacity} onChange={e => setB({ ...b, capacity: Math.max(0, Math.round(Number(e.target.value) || 0)) })} />
        </Field>
        <Field label="Note" full><input className="input" value={b.note} onChange={e => setB({ ...b, note: e.target.value })} /></Field>
        {existing && (
          <label className="row full"><input type="checkbox" checked={b.cancelled} onChange={e => setB({ ...b, cancelled: e.target.checked })} /> This batch is cancelled</label>
        )}
      </div>
      <div className="actions">
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!b.courseId} onClick={save} data-testid="batch-save">Save</button>
      </div>
    </Modal>
  )
}

function InstructorDialog({ id, onClose }: { id?: string; onClose: () => void }) {
  const data = useData()
  const toast = useToast()
  const today = useToday()
  const existing = id ? data.idx.instructors.get(id) : undefined
  const [x, setX] = useState<Instructor>(existing ?? {
    id: newId(), name: '', phone: '', email: '', subject: '', payType: 'MONTHLY', payRate: 0, joinDate: today, leaveDate: '',
    note: '', archived: false,
  })
  const [rate, setRate] = useState(amountText(x.payRate))
  const save = () => {
    store.put('instructors', { ...x, name: x.name.trim(), email: x.email.trim().toLowerCase(), payRate: parseMoney(rate) })
    toast('Instructor saved')
    onClose()
  }
  const rateLabel = x.payType === 'MONTHLY' ? 'Salary per month' : x.payType === 'PER_BATCH' ? 'Pay per batch' : x.payType === 'PER_CLASS' ? 'Pay per class' : 'Rate'
  return (
    <Modal onClose={onClose} label="Instructor">
      <h2>{existing ? 'Edit instructor' : 'New instructor'}</h2>
      <div className="form-grid">
        <Field label="Name *" full><input className="input" autoFocus value={x.name} onChange={e => setX({ ...x, name: e.target.value })} data-testid="instructor-name" /></Field>
        <Field label="Phone"><input className="input" type="tel" value={x.phone} onChange={e => setX({ ...x, phone: e.target.value })} /></Field>
        <Field label="Email"><input className="input" type="email" value={x.email} onChange={e => setX({ ...x, email: e.target.value })} /></Field>
        <Field label="Teaches" full><input className="input" value={x.subject} placeholder="e.g. Graphic design, Office" onChange={e => setX({ ...x, subject: e.target.value })} /></Field>
        <Field label="How they are paid">
          <select className="input" value={x.payType} onChange={e => setX({ ...x, payType: e.target.value as PayType })}>
            {PAY_TYPES.map(p => <option key={p} value={p}>{PAY_TYPE_LABELS[p]}</option>)}
          </select>
        </Field>
        {x.payType !== 'NONE' && <Field label={rateLabel}><MoneyInput value={rate} onChange={setRate} /></Field>}
        <Field label="Joined"><input className="input" type="date" value={x.joinDate} onChange={e => setX({ ...x, joinDate: e.target.value })} /></Field>
        <Field label="Left" hint="Empty while still working"><input className="input" type="date" value={x.leaveDate} onChange={e => setX({ ...x, leaveDate: e.target.value })} /></Field>
        <Field label="Note" full><input className="input" value={x.note} onChange={e => setX({ ...x, note: e.target.value })} /></Field>
        {existing && <label className="row full"><input type="checkbox" checked={x.archived} onChange={e => setX({ ...x, archived: e.target.checked })} /> Archived (no longer listed for new batches)</label>}
      </div>
      <div className="actions">
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!x.name.trim()} onClick={save} data-testid="instructor-save">Save</button>
      </div>
    </Modal>
  )
}

function InstructorPaymentDialog({ id, instructorId, onClose }: { id?: string; instructorId?: string; onClose: () => void }) {
  const data = useData()
  const money = useMoney()
  const toast = useToast()
  const today = useToday()
  const existing = id ? data.instructorPayments.find(p => p.id === id) : undefined
  const [p, setP] = useState<InstructorPayment>(existing ?? {
    id: newId(), instructorId: instructorId ?? data.instructors.find(i => !i.archived)?.id ?? '', amount: 0, date: today,
    method: 'CASH', batchId: null, month: '', note: '', createdAt: Date.now(),
  })
  const instructor = data.idx.instructors.get(p.instructorId)
  const pay = instructor ? instructorPay(instructor, data.batches, data.attendance, data.instructorPayments.filter(x => x.id !== p.id), today) : null
  const [amount, setAmount] = useState(amountText(existing?.amount ?? 0))
  const [confirm, setConfirm] = useState(false)
  const months = Array.from({ length: 13 }, (_, i) => addMonths(currentMonth(), 1 - i))
  const save = () => {
    store.put('instructorPayments', { ...p, amount: parseMoney(amount || amountText(Math.max(pay?.owed ?? 0, 0))) })
    toast('Payment saved')
    onClose()
  }
  return (
    <Modal onClose={onClose} label="Instructor payment">
      <h2>{existing ? 'Edit payment' : 'Pay an instructor'}</h2>
      <div className="form-grid">
        <Field label="Instructor *" full>
          <select className="input" value={p.instructorId} onChange={e => setP({ ...p, instructorId: e.target.value })}>
            <option value="">Choose…</option>
            {data.instructors.filter(i => !i.archived || i.id === p.instructorId).map(i => <option key={i.id} value={i.id}>{i.name}</option>)}
          </select>
        </Field>
        {pay && instructor?.payType !== 'NONE' && (
          <div className="full small muted">Earned {money.format(pay.earned)} · paid {money.format(pay.paid)} · <b className={pay.owed > 0 ? 'danger' : ''}>owed {money.format(pay.owed)}</b></div>
        )}
        <Field label="Amount"><MoneyInput value={amount} placeholder={amountText(Math.max(pay?.owed ?? 0, 0))} onChange={setAmount} /></Field>
        <Field label="Date"><input className="input" type="date" value={p.date} onChange={e => setP({ ...p, date: e.target.value })} /></Field>
        <Field label="Method"><MethodSelect value={p.method} onChange={(m: Method) => setP({ ...p, method: m })} /></Field>
        {instructor?.payType === 'MONTHLY' ? (
          <Field label="Salary for">
            <select className="input" value={p.month} onChange={e => setP({ ...p, month: e.target.value })}>
              <option value="">—</option>
              {months.map(m => <option key={m} value={m}>{monthLabel(m)}</option>)}
            </select>
          </Field>
        ) : (
          <Field label="For batch">
            <select className="input" value={p.batchId ?? ''} onChange={e => setP({ ...p, batchId: e.target.value || null })}>
              <option value="">—</option>
              {data.batches.filter(b => b.instructorId === p.instructorId).map(b => <option key={b.id} value={b.id}>{b.name}</option>)}
            </select>
          </Field>
        )}
        <Field label="Note" full><input className="input" value={p.note} onChange={e => setP({ ...p, note: e.target.value })} /></Field>
      </div>
      <div className="actions">
        {existing && <button className="btn danger" style={{ marginRight: 'auto' }} onClick={() => setConfirm(true)}>Delete</button>}
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!p.instructorId} onClick={save}>Save</button>
      </div>
      {confirm && existing && (
        <ConfirmDialog title="Delete this payment?" danger confirm="Delete" text="It is taken out of the instructor's account and the reports."
          onConfirm={() => { store.remove('instructorPayments', existing.id); onClose() }} onClose={() => setConfirm(false)} />
      )}
    </Modal>
  )
}

function MemberDialog({ id, onClose }: { id?: string; onClose: () => void }) {
  const data = useData()
  const toast = useToast()
  const existing = id ? data.idx.members.get(id) : undefined
  const [email, setEmail] = useState(existing?.email ?? '')
  const [name, setName] = useState(existing?.name ?? '')
  const [role, setRole] = useState<Role>(existing?.role ?? 'OFFICE')
  const [instructorId, setInstructorId] = useState(existing?.instructorId ?? '')
  const [active, setActive] = useState(existing?.active ?? true)
  const [error, setError] = useState<string | null>(null)
  const owner = existing?.role === 'OWNER'
  const save = () => {
    const e = email.trim().toLowerCase()
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e)) return setError('Enter their Google account email address.')
    if (!existing && data.idx.members.has(e)) return setError('This person is already on the staff list.')
    const member: Member = {
      id: e, email: e, name: name.trim() || e.split('@')[0], role: owner ? 'OWNER' : role, active: owner ? true : active,
      instructorId: role === 'TEACHER' && instructorId ? instructorId : null, addedBy: existing?.addedBy ?? data.email,
      createdAt: existing?.createdAt ?? Date.now(),
    }
    store.put('members', member)
    toast(existing ? 'Staff saved' : `${member.name} can now sign in with ${e}`)
    onClose()
  }
  const roles: Role[] = ['ADMIN', 'OFFICE', 'TEACHER']
  return (
    <Modal onClose={onClose} narrow label="Staff">
      <h2>{existing ? 'Edit staff' : 'Add staff'}</h2>
      <div className="stack">
        <Field label="Google email *" hint={existing ? undefined : 'They sign in to SkillTrack with this Google account.'}>
          <input className="input" type="email" autoFocus={!existing} disabled={!!existing} value={email} onChange={e => setEmail(e.target.value)} data-testid="member-email" />
        </Field>
        <Field label="Name"><input className="input" value={name} onChange={e => setName(e.target.value)} data-testid="member-name" /></Field>
        {owner ? <div className="notice">This is the center's owner. The role can't be changed.</div> : (
          <Field label="Role">
            <div className="stack" style={{ gap: 6 }}>
              {roles.map(r => (
                <label key={r} className="row" style={{ alignItems: 'flex-start' }}>
                  <input type="radio" name="role" checked={role === r} onChange={() => setRole(r)} data-testid={`role-${r}`} />
                  <span><b>{ROLE_LABELS[r]}</b><br /><span className="small muted">{ROLE_HELP[r]}</span></span>
                </label>
              ))}
            </div>
          </Field>
        )}
        {!owner && role === 'TEACHER' && (
          <Field label="Instructor record" hint="Their own classes are shown first.">
            <select className="input" value={instructorId} onChange={e => setInstructorId(e.target.value)}>
              <option value="">—</option>
              {data.instructors.filter(i => !i.archived).map(i => <option key={i.id} value={i.id}>{i.name}</option>)}
            </select>
          </Field>
        )}
        {existing && !owner && (
          <label className="row"><input type="checkbox" checked={active} onChange={e => setActive(e.target.checked)} /> Active (can sign in and work)</label>
        )}
      </div>
      {error && <p className="error-text small">{error}</p>}
      <div className="actions">
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" onClick={save} data-testid="member-save">Save</button>
      </div>
    </Modal>
  )
}
