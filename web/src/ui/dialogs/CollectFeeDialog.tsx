import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { collectFee, feeOf, whatsappNumber } from '../../data/actions'
import { useData } from '../../data/store'
import type { Method, Payment } from '../../data/types'
import { fullDate } from '../../domain/dates'
import {
  amountText, EnrollmentPill, Field, matches, MethodSelect, Modal, MoneyInput, parseMoney, useMoney, useToast, useToday,
} from '../common'
import { Icon } from '../icons'
import type { DialogRequest } from './host'

export default function CollectFeeDialog({ request, onClose }: {
  request: Extract<DialogRequest, { kind: 'collect' }>
  onClose: () => void
}) {
  const data = useData()
  const money = useMoney()
  const toast = useToast()
  const navigate = useNavigate()
  const today = useToday()
  const preset = request.enrollmentId ? data.idx.enrollments.get(request.enrollmentId) : undefined
  const [studentId, setStudentId] = useState(preset?.studentId ?? request.studentId ?? '')
  const [search, setSearch] = useState('')
  const [enrollmentId, setEnrollmentId] = useState(preset?.id ?? '')
  const [amount, setAmount] = useState('')
  const [date, setDate] = useState(today)
  const [method, setMethod] = useState<Method>('CASH')
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<Payment | null>(null)

  const student = data.idx.students.get(studentId)
  const enrollments = useMemo(
    () => data.enrollments.filter(e => e.studentId === studentId).sort((a, b) => Number(a.status === 'DROPPED') - Number(b.status === 'DROPPED')),
    [data.enrollments, studentId],
  )
  const enrollment = data.idx.enrollments.get(enrollmentId) ?? (enrollments.length === 1 ? enrollments[0] : undefined)
  const fee = enrollment ? feeOf(data, enrollment, today) : null
  const suggested = fee ? (fee.overdue > 0 ? fee.overdue : fee.nextDue?.amount ?? Math.max(fee.due, 0)) : 0
  const results = search.trim() ? data.students.filter(s => matches(search, s.name, s.regNo, s.phone)).slice(0, 8) : []

  const save = () => {
    setError(null)
    if (!enrollment) return setError('Pick the admission this payment is for.')
    const value = parseMoney(amount || amountText(suggested))
    if (value <= 0) return setError('Enter the amount received.')
    const payment = collectFee(data, enrollment, value, date, method, note.trim())
    toast(`Receipt ${payment.receiptNo} saved`)
    setDone(payment)
  }

  if (done && enrollment && student) {
    const after = feeOf(data, enrollment, today)
    const remaining = Math.max(after.due, 0)
    const text = `Dear ${student.name}, we received ${money.format(done.amount)} (receipt ${done.receiptNo}) on ${fullDate(done.date)}.` +
      (remaining > 0 ? ` Remaining due: ${money.format(remaining)}.` : ' Your course fee is fully paid.') +
      ` Thank you. — ${data.center?.name ?? ''}`
    return (
      <Modal onClose={onClose} narrow label="Payment saved">
        <h2>✅ {money.format(done.amount)} received</h2>
        <p className="muted">Receipt <b>{done.receiptNo}</b> · {student.name}. {remaining > 0 ? `Still due: ${money.format(remaining)}.` : 'Fully paid.'}</p>
        <div className="stack" style={{ gap: 8 }}>
          <button className="btn" onClick={() => navigate(`/print/receipt/${done.id}`)} data-testid="print-receipt"><Icon name="printer" size={18} />Print receipt</button>
          {student.phone && (
            <a className="btn tonal" target="_blank" rel="noreferrer"
              href={`https://wa.me/${whatsappNumber(student.phone)}?text=${encodeURIComponent(text)}`}><Icon name="message" size={18} />Send on WhatsApp</a>
          )}
          <button className="btn text" onClick={onClose}>Done</button>
        </div>
      </Modal>
    )
  }

  return (
    <Modal onClose={onClose} label="Collect fee">
      <h2>Collect fee</h2>
      <div className="stack">
        {!student ? (
          <>
            <div className="search">
              <Icon name="search" size={18} />
              <input autoFocus placeholder="Find the student: name, reg. no. or phone" value={search} onChange={e => setSearch(e.target.value)} data-testid="collect-search" />
            </div>
            <div className="list card">
              {results.map(s => (
                <button key={s.id} className="item" onClick={() => { setStudentId(s.id); setEnrollmentId('') }}>
                  <div className="grow"><div className="title">{s.name}</div><div className="subtitle">{s.regNo}{s.phone ? ` · ${s.phone}` : ''}</div></div>
                </button>
              ))}
              {search.trim() && results.length === 0 && <div className="item muted">No student matches.</div>}
            </div>
          </>
        ) : (
          <>
            <div className="row">
              <div className="grow"><b>{student.name}</b> <span className="muted small">{student.regNo}</span></div>
              {!request.studentId && !request.enrollmentId && <button className="btn text small" onClick={() => setStudentId('')}>Change</button>}
            </div>
            {enrollments.length === 0 && <div className="notice warn">This student has no admission yet. Admit them to a batch first.</div>}
            <div className="list card">
              {enrollments.map(e => {
                const f = feeOf(data, e, today)
                const b = data.idx.batches.get(e.batchId)
                const c = data.idx.courses.get(e.courseId)
                const on = enrollment?.id === e.id
                return (
                  <button key={e.id} className="item" style={on ? { background: 'var(--secondary-container)' } : undefined} onClick={() => setEnrollmentId(e.id)}>
                    <input type="radio" readOnly checked={on} />
                    <div className="grow">
                      <div className="title">{c?.name ?? '?'} · {b?.name ?? '?'}</div>
                      <div className="subtitle">Payable {money.format(f.payable)} · paid {money.format(f.paid)}</div>
                    </div>
                    <div className="end">
                      <div className={`bold num ${f.overdue > 0 ? 'danger' : ''}`}>{f.due > 0 ? money.format(f.due) : 'Paid'}</div>
                      <EnrollmentPill status={e.status} />
                    </div>
                  </button>
                )
              })}
            </div>
            {fee && (
              <div className="small muted">
                {fee.overdue > 0 ? <span className="danger">Overdue now: {money.format(fee.overdue)}. </span> : null}
                {fee.nextDue ? `Next: ${money.format(fee.nextDue.amount)} due ${fullDate(fee.nextDue.date)}.` : 'Nothing more is due.'}
              </div>
            )}
            <div className="form-grid">
              <Field label="Amount received">
                <MoneyInput value={amount} placeholder={amountText(suggested)} onChange={setAmount} autoFocus testId="collect-amount" />
              </Field>
              <Field label="Date"><input className="input" type="date" value={date} onChange={e => setDate(e.target.value)} /></Field>
              <Field label="Method"><MethodSelect value={method} onChange={setMethod} /></Field>
              <Field label="Note"><input className="input" value={note} placeholder="e.g. 2nd installment, bKash TrxID" onChange={e => setNote(e.target.value)} /></Field>
            </div>
          </>
        )}
      </div>
      {error && <p className="error-text small">{error}</p>}
      <div className="actions">
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" onClick={save} disabled={!enrollment} data-testid="collect-save">Save receipt</button>
      </div>
    </Modal>
  )
}
