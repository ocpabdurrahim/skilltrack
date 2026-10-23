import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { issueCertificate, resultsMap } from '../../data/actions'
import { newId } from '../../data/ids'
import { store, useData } from '../../data/store'
import {
  ENQUIRY_SOURCES, SOURCE_LABELS,
  type Enquiry, type EnquiryStatus, type Exam, type Expense, type ExpenseHead, type Method,
} from '../../data/types'
import { addDays } from '../../domain/dates'
import { percentLabel } from '../../domain/results'
import {
  amountText, ConfirmDialog, Field, MethodSelect, Modal, MoneyInput, parseMoney, Segmented, useToast, useToday,
} from '../common'
import { Icon } from '../icons'
import type { DialogRequest } from './host'

type OtherRequest = Extract<DialogRequest, { kind: 'exam' | 'enquiry' | 'expense' | 'head' | 'certificate' }>

export default function OtherDialogs({ request, onClose }: { request: OtherRequest; onClose: () => void }) {
  switch (request.kind) {
    case 'exam': return <ExamDialog batchId={request.batchId} id={request.id} onClose={onClose} />
    case 'enquiry': return <EnquiryDialog id={request.id} onClose={onClose} />
    case 'expense': return <ExpenseDialog id={request.id} onClose={onClose} />
    case 'head': return <HeadDialog id={request.id} onClose={onClose} />
    case 'certificate': return <CertificateDialog enrollmentId={request.enrollmentId} onClose={onClose} />
  }
}

function ExamDialog({ batchId, id, onClose }: { batchId: string; id?: string; onClose: () => void }) {
  const data = useData()
  const toast = useToast()
  const today = useToday()
  const navigate = useNavigate()
  const existing = id ? data.exams.find(x => x.id === id) : undefined
  const [x, setX] = useState<Exam>(existing ?? { id: newId(), batchId, name: '', date: today, fullMarks: 100, passMarks: 33, marks: {} })
  const [confirm, setConfirm] = useState(false)
  const bad = x.fullMarks <= 0 || x.passMarks < 0 || x.passMarks > x.fullMarks
  const save = (thenMarks: boolean) => {
    store.put('exams', { ...x, name: x.name.trim() || 'Exam' })
    toast('Exam saved')
    onClose()
    if (thenMarks) navigate(`/exams/${x.id}`)
  }
  return (
    <Modal onClose={onClose} narrow label="Exam">
      <h2>{existing ? 'Edit exam' : 'New exam'}</h2>
      <div className="form-grid">
        <Field label="Name" full><input className="input" autoFocus value={x.name} placeholder="e.g. Final - Practical" onChange={e => setX({ ...x, name: e.target.value })} data-testid="exam-name" /></Field>
        <Field label="Date" full><input className="input" type="date" value={x.date} onChange={e => setX({ ...x, date: e.target.value })} /></Field>
        <Field label="Full marks"><input className="input" type="number" min={1} value={x.fullMarks} onChange={e => setX({ ...x, fullMarks: Number(e.target.value) || 0 })} /></Field>
        <Field label="Pass marks"><input className="input" type="number" min={0} value={x.passMarks} onChange={e => setX({ ...x, passMarks: Number(e.target.value) || 0 })} /></Field>
      </div>
      {bad && <p className="error-text small">Pass marks must be between 0 and the full marks.</p>}
      <div className="actions">
        {existing && <button className="btn danger" style={{ marginRight: 'auto' }} onClick={() => setConfirm(true)}>Delete</button>}
        <button className="btn text" onClick={onClose}>Cancel</button>
        {!existing && <button className="btn tonal" disabled={bad} onClick={() => save(true)} data-testid="exam-save-marks">Save & enter marks</button>}
        <button className="btn" disabled={bad} onClick={() => save(false)}>Save</button>
      </div>
      {confirm && existing && (
        <ConfirmDialog title="Delete this exam?" danger confirm="Delete" text="Its marks are deleted too, and results are worked out without it."
          onConfirm={() => { store.remove('exams', existing.id); onClose() }} onClose={() => setConfirm(false)} />
      )}
    </Modal>
  )
}

function EnquiryDialog({ id, onClose }: { id?: string; onClose: () => void }) {
  const data = useData()
  const toast = useToast()
  const today = useToday()
  const existing = id ? data.enquiries.find(q => q.id === id) : undefined
  const [q, setQ] = useState<Enquiry>(existing ?? {
    id: newId(), name: '', phone: '', courseId: null, source: 'WALK_IN', date: today, followUp: addDays(today, 2), status: 'OPEN',
    note: '', studentId: null, createdAt: Date.now(),
  })
  const [confirm, setConfirm] = useState(false)
  const save = () => {
    store.put('enquiries', { ...q, name: q.name.trim(), phone: q.phone.trim() })
    toast('Enquiry saved')
    onClose()
  }
  return (
    <Modal onClose={onClose} label="Enquiry">
      <h2>{existing ? 'Edit enquiry' : 'New enquiry'}</h2>
      <div className="form-grid">
        <Field label="Name *"><input className="input" autoFocus value={q.name} onChange={e => setQ({ ...q, name: e.target.value })} data-testid="enquiry-name" /></Field>
        <Field label="Phone"><input className="input" type="tel" value={q.phone} onChange={e => setQ({ ...q, phone: e.target.value })} /></Field>
        <Field label="Interested in">
          <select className="input" value={q.courseId ?? ''} onChange={e => setQ({ ...q, courseId: e.target.value || null })}>
            <option value="">—</option>
            {data.courses.filter(c => !c.archived || c.id === q.courseId).map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </Field>
        <Field label="Came from">
          <select className="input" value={q.source} onChange={e => setQ({ ...q, source: e.target.value as Enquiry['source'] })}>
            {ENQUIRY_SOURCES.map(s => <option key={s} value={s}>{SOURCE_LABELS[s]}</option>)}
          </select>
        </Field>
        <Field label="Enquiry date"><input className="input" type="date" value={q.date} onChange={e => setQ({ ...q, date: e.target.value })} /></Field>
        <Field label="Follow up on" hint="Empty = no follow-up"><input className="input" type="date" value={q.followUp} onChange={e => setQ({ ...q, followUp: e.target.value })} /></Field>
        <Field label="Status" full>
          <Segmented options={[['OPEN', 'Open'], ['CONVERTED', 'Admitted'], ['LOST', 'Lost']]} value={q.status} onChange={(s: EnquiryStatus) => setQ({ ...q, status: s })} />
        </Field>
        <Field label="Notes" full><textarea className="input" value={q.note} placeholder="What they asked, what you offered, when to call…" onChange={e => setQ({ ...q, note: e.target.value })} /></Field>
      </div>
      <div className="actions">
        {existing && <button className="btn danger" style={{ marginRight: 'auto' }} onClick={() => setConfirm(true)}>Delete</button>}
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!q.name.trim()} onClick={save} data-testid="enquiry-save">Save</button>
      </div>
      {confirm && existing && (
        <ConfirmDialog title="Delete this enquiry?" danger confirm="Delete" text="It is removed from the list and the conversion report."
          onConfirm={() => { store.remove('enquiries', existing.id); onClose() }} onClose={() => setConfirm(false)} />
      )}
    </Modal>
  )
}

function ExpenseDialog({ id, onClose }: { id?: string; onClose: () => void }) {
  const data = useData()
  const toast = useToast()
  const today = useToday()
  const existing = id ? data.expenses.find(x => x.id === id) : undefined
  const heads = data.expenseHeads.filter(h => !h.archived || h.id === existing?.headId)
  const [x, setX] = useState<Expense>(existing ?? {
    id: newId(), headId: heads[0]?.id ?? '', amount: 0, date: today, method: 'CASH', note: '', paidTo: '', createdAt: Date.now(),
  })
  const [amount, setAmount] = useState(amountText(x.amount))
  const [confirm, setConfirm] = useState(false)
  const value = parseMoney(amount)
  return (
    <Modal onClose={onClose} label="Expense">
      <h2>{existing ? 'Edit expense' : 'New expense'}</h2>
      <div className="form-grid">
        <Field label="Amount *"><MoneyInput value={amount} onChange={setAmount} autoFocus testId="expense-amount" /></Field>
        <Field label="Date"><input className="input" type="date" value={x.date} onChange={e => setX({ ...x, date: e.target.value })} /></Field>
        <Field label="Head" full>
          <div className="emoji-grid" style={{ gap: 8 }}>
            {heads.map(h => (
              <button key={h.id} type="button" className={`chip ${x.headId === h.id ? 'on' : ''}`} style={{ width: 'auto', height: 'auto', fontSize: 14 }}
                onClick={() => setX({ ...x, headId: h.id })}>{h.emoji} {h.name}</button>
            ))}
          </div>
        </Field>
        <Field label="Method"><MethodSelect value={x.method} onChange={(m: Method) => setX({ ...x, method: m })} /></Field>
        <Field label="Paid to"><input className="input" value={x.paidTo} onChange={e => setX({ ...x, paidTo: e.target.value })} /></Field>
        <Field label="Note" full><input className="input" value={x.note} onChange={e => setX({ ...x, note: e.target.value })} /></Field>
      </div>
      <div className="actions">
        {existing && <button className="btn danger" style={{ marginRight: 'auto' }} onClick={() => setConfirm(true)}>Delete</button>}
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={value <= 0 || !x.headId} data-testid="expense-save"
          onClick={() => { store.put('expenses', { ...x, amount: value, paidTo: x.paidTo.trim(), note: x.note.trim() }); toast('Expense saved'); onClose() }}>
          Save
        </button>
      </div>
      {confirm && existing && (
        <ConfirmDialog title="Delete this expense?" danger confirm="Delete" text="It is removed from the reports."
          onConfirm={() => { store.remove('expenses', existing.id); onClose() }} onClose={() => setConfirm(false)} />
      )}
    </Modal>
  )
}

const HEAD_EMOJIS = ['🏠', '💡', '🌐', '👥', '📣', '📝', '💻', '🔧', '📦', '🚗', '☕', '🧹', '📱', '🏦', '🧾', '🎓', '🖨️', '⚡', '💧', '🎉']

function HeadDialog({ id, onClose }: { id?: string; onClose: () => void }) {
  const data = useData()
  const toast = useToast()
  const existing = id ? data.expenseHeads.find(h => h.id === id) : undefined
  const [h, setH] = useState<ExpenseHead>(existing ?? { id: newId(), name: '', emoji: '📦', archived: false })
  const used = existing ? data.expenses.some(x => x.headId === existing.id) : false
  return (
    <Modal onClose={onClose} narrow label="Expense head">
      <h2>{existing ? 'Edit head' : 'New expense head'}</h2>
      <div className="stack">
        <Field label="Name"><input className="input" autoFocus value={h.name} onChange={e => setH({ ...h, name: e.target.value })} /></Field>
        <div className="emoji-grid">
          {HEAD_EMOJIS.map(e => <button key={e} type="button" className={h.emoji === e ? 'on' : ''} onClick={() => setH({ ...h, emoji: e })}>{e}</button>)}
        </div>
      </div>
      <div className="actions">
        {existing && (
          <button className="btn danger" style={{ marginRight: 'auto' }} onClick={() => {
            if (existing.archived) store.put('expenseHeads', { ...h, archived: false })
            else if (used) store.put('expenseHeads', { ...h, archived: true })
            else store.remove('expenseHeads', existing.id)
            toast(existing.archived ? 'Head restored' : used ? 'Head archived (it has expenses)' : 'Head deleted')
            onClose()
          }}>{existing.archived ? 'Restore' : used ? 'Archive' : 'Delete'}</button>
        )}
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!h.name.trim()} onClick={() => { store.put('expenseHeads', { ...h, name: h.name.trim() }); onClose() }}>Save</button>
      </div>
    </Modal>
  )
}

function CertificateDialog({ enrollmentId, onClose }: { enrollmentId?: string; onClose: () => void }) {
  const data = useData()
  const toast = useToast()
  const today = useToday()
  const navigate = useNavigate()
  const results = resultsMap(data, today)
  const issued = new Set(data.certificates.map(c => c.enrollmentId))
  const eligible = data.enrollments.filter(e => e.status !== 'DROPPED' && (!issued.has(e.id) || e.id === enrollmentId))
  const [eid, setEid] = useState(enrollmentId ?? '')
  const e = data.idx.enrollments.get(eid)
  const result = e ? results.get(e.id) : undefined
  const [date, setDate] = useState(today)
  const [grade, setGrade] = useState(result?.grade ?? '')
  const [note, setNote] = useState('')
  const pick = (id: string) => {
    setEid(id)
    setGrade(results.get(id)?.grade ?? '')
  }
  const already = e ? data.certificates.find(c => c.enrollmentId === e.id) : undefined
  const warn = e && e.status !== 'COMPLETED' && !(result && !result.failed)
    ? 'This admission is not marked completed and has no pass result yet.' : null
  return (
    <Modal onClose={onClose} label="Issue certificate">
      <h2>Issue certificate</h2>
      <div className="stack">
        <Field label="Student and batch">
          <select className="input" value={eid} onChange={ev => pick(ev.target.value)} data-testid="cert-enrollment">
            <option value="">Choose…</option>
            {eligible.map(x => {
              const s = data.idx.students.get(x.studentId)
              const b = data.idx.batches.get(x.batchId)
              return <option key={x.id} value={x.id}>{s?.name ?? '?'} · {b?.name ?? '?'}{x.status === 'COMPLETED' ? ' · completed' : ''}</option>
            })}
          </select>
        </Field>
        {already && <div className="notice warn">Certificate {already.certNo} was already issued for this admission.</div>}
        {warn && <div className="notice warn">{warn}</div>}
        {result && <div className="small muted">Result: {percentLabel(result.percent)} · {result.grade}{result.complete ? '' : ' (not all marks entered)'}</div>}
        <div className="form-grid">
          <Field label="Issue date"><input className="input" type="date" value={date} onChange={ev => setDate(ev.target.value)} /></Field>
          <Field label="Grade on the certificate"><input className="input" value={grade} placeholder="e.g. A+" onChange={ev => setGrade(ev.target.value)} /></Field>
          <Field label="Note" full><input className="input" value={note} onChange={ev => setNote(ev.target.value)} /></Field>
        </div>
      </div>
      <div className="actions">
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!e || !!already} data-testid="cert-save" onClick={() => {
          const cert = issueCertificate(data, e!, date, grade.trim(), note.trim())
          toast(`Certificate ${cert.certNo} issued`)
          onClose()
          navigate(`/print/certificate/${cert.id}`)
        }}><Icon name="certificate" size={18} />Issue & print</button>
      </div>
    </Modal>
  )
}

