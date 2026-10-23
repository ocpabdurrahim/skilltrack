import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { admit, blankStudent, seatsTaken } from '../../data/actions'
import { store, useData } from '../../data/store'
import type { Installment, Method, Payment, Student } from '../../data/types'
import { batchStatus } from '../../domain/batches'
import { fullDate } from '../../domain/dates'
import { makePlan, payableOf } from '../../domain/fees'
import {
  amountText, Field, matches, MethodSelect, Modal, MoneyInput, parseMoney, Segmented, useMoney, useToast, useToday,
} from '../common'
import { Icon } from '../icons'
import type { DialogRequest } from './host'

type PlanChoice = '1' | '2' | '3' | '4' | '6'

export default function AdmissionDialog({ request, onClose }: {
  request: Extract<DialogRequest, { kind: 'admission' }>
  onClose: () => void
}) {
  const data = useData()
  const money = useMoney()
  const toast = useToast()
  const navigate = useNavigate()
  const date0 = useToday()
  const enquiry = request.enquiryId ? data.enquiries.find(q => q.id === request.enquiryId) : undefined

  const [mode, setMode] = useState<'existing' | 'new'>(request.studentId ? 'existing' : 'new')
  const [studentId, setStudentId] = useState(request.studentId ?? '')
  const [studentSearch, setStudentSearch] = useState('')
  const [newStudent, setNewStudent] = useState<Student>(() => ({
    ...blankStudent(data, date0), name: enquiry?.name ?? '', phone: enquiry?.phone ?? '',
  }))
  const openBatches = useMemo(
    () => data.batches.filter(b => {
      const s = batchStatus(b, date0)
      return s === 'RUNNING' || s === 'UPCOMING' || b.id === request.batchId
    }),
    [data.batches, date0, request.batchId],
  )
  const initialBatch = request.batchId ??
    (enquiry?.courseId ? openBatches.find(b => b.courseId === enquiry.courseId)?.id : undefined) ?? ''
  const [batchId, setBatchId] = useState(initialBatch)
  const batch = data.idx.batches.get(batchId)
  const course = batch ? data.idx.courses.get(batch.courseId) : undefined
  const [date, setDate] = useState(date0)
  const [fee, setFee] = useState(() => amountText(course?.fee ?? 0))
  const [discount, setDiscount] = useState('')
  const [planChoice, setPlanChoice] = useState<PlanChoice>('1')
  const [plan, setPlan] = useState<Installment[]>([])
  const [note, setNote] = useState('')
  const [collectNow, setCollectNow] = useState(true)
  const [payAmount, setPayAmount] = useState('')
  const [method, setMethod] = useState<Method>('CASH')
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<{ student: Student; payment: Payment | null } | null>(null)

  const payable = payableOf({ fee: parseMoney(fee), discount: parseMoney(discount) })
  const planTotal = plan.reduce((s, i) => s + i.amount, 0)

  const choosePlan = (choice: PlanChoice, total = payable, start = date) => {
    setPlanChoice(choice)
    setPlan(makePlan(total, Number(choice), start))
  }

  const chooseBatch = (id: string) => {
    setBatchId(id)
    const b = data.idx.batches.get(id)
    const c = b ? data.idx.courses.get(b.courseId) : undefined
    if (c) {
      setFee(amountText(c.fee))
      choosePlan(planChoice, Math.max(c.fee - parseMoney(discount), 0))
    }
  }

  const firstDue = plan.length > 0 ? plan[0].amount : payable
  const studentChoices = data.students.filter(s => !s.archived && matches(studentSearch, s.name, s.regNo, s.phone)).slice(0, 50)
  const taken = batch ? seatsTaken(data, batch.id) : 0
  const full = batch !== undefined && batch.capacity > 0 && taken >= batch.capacity
  const student = mode === 'existing' ? data.idx.students.get(studentId) : newStudent
  const already = student && batch && data.enrollments.some(e => e.studentId === student.id && e.batchId === batch.id)

  const save = () => {
    setError(null)
    if (!student || !student.name.trim()) return setError(mode === 'new' ? 'Enter the student\'s name.' : 'Pick a student.')
    if (!batch) return setError('Pick a batch.')
    if (already) return setError('This student is already in that batch.')
    if (plan.length > 0 && planTotal !== payable) return setError(`The installments add up to ${money.format(planTotal)}, not ${money.format(payable)}.`)
    const amount = collectNow ? parseMoney(payAmount || amountText(firstDue)) : 0
    const { payment } = admit(data, {
      student: { ...student, name: student.name.trim() },
      newStudent: mode === 'new',
      batch, date, fee: parseMoney(fee), discount: parseMoney(discount), installments: plan, note,
      firstPayment: amount > 0 ? { amount, method, note: 'Admission' } : null,
    })
    if (enquiry) store.put('enquiries', { ...enquiry, status: 'CONVERTED', studentId: student.id })
    toast(`${student.name} admitted to ${batch.name}`)
    setDone({ student, payment })
  }

  if (done) {
    return (
      <Modal onClose={onClose} narrow label="Admitted">
        <h2>✅ Admitted</h2>
        <p className="muted">{done.student.name} ({done.student.regNo}) is now in {batch?.name}.
          {done.payment && <> Receipt <b>{done.payment.receiptNo}</b> for {money.format(done.payment.amount)}.</>}</p>
        <div className="stack" style={{ gap: 8 }}>
          {done.payment && <button className="btn" onClick={() => navigate(`/print/receipt/${done.payment!.id}`)}><Icon name="printer" size={18} />Print receipt</button>}
          <button className="btn tonal" onClick={() => navigate(`/print/admission/${done.student.id}`)}><Icon name="printer" size={18} />Print admission form</button>
          <button className="btn tonal" onClick={() => { onClose(); navigate(`/students/${done.student.id}`) }}>Open student</button>
          <button className="btn text" onClick={onClose}>Done</button>
        </div>
      </Modal>
    )
  }

  return (
    <Modal onClose={onClose} wide label="New admission">
      <h2>New admission{enquiry ? ` · from enquiry` : ''}</h2>
      <div className="stack">
        <div className="row wrap">
          <b className="grow">Student</b>
          {!request.studentId && (
            <Segmented options={[['new', 'New student'], ['existing', 'Existing student']]} value={mode} onChange={setMode} />
          )}
        </div>
        {mode === 'existing' ? (
          <div className="form-grid">
            {!request.studentId && (
              <Field label="Search">
                <input className="input" placeholder="Name, reg. no. or phone" value={studentSearch} onChange={e => setStudentSearch(e.target.value)} />
              </Field>
            )}
            <Field label="Student" full={!!request.studentId}>
              <select className="input" value={studentId} onChange={e => setStudentId(e.target.value)} data-testid="admission-student">
                <option value="">Choose…</option>
                {(request.studentId ? data.students.filter(s => s.id === request.studentId) : studentChoices).map(s => (
                  <option key={s.id} value={s.id}>{s.name} · {s.regNo}{s.phone ? ` · ${s.phone}` : ''}</option>
                ))}
              </select>
            </Field>
          </div>
        ) : (
          <div className="form-grid">
            <Field label="Name *">
              <input className="input" value={newStudent.name} autoFocus onChange={e => setNewStudent({ ...newStudent, name: e.target.value })} data-testid="admission-name" />
            </Field>
            <Field label="Phone">
              <input className="input" type="tel" value={newStudent.phone} onChange={e => setNewStudent({ ...newStudent, phone: e.target.value })} />
            </Field>
            <Field label="Father's name">
              <input className="input" value={newStudent.fatherName} onChange={e => setNewStudent({ ...newStudent, fatherName: e.target.value })} />
            </Field>
            <Field label="Guardian's phone">
              <input className="input" type="tel" value={newStudent.guardianPhone} onChange={e => setNewStudent({ ...newStudent, guardianPhone: e.target.value })} />
            </Field>
            <div className="full small muted">Reg. no. {newStudent.regNo}. Add the photo and other details from the student's page later.</div>
          </div>
        )}

        <b>Course and batch</b>
        <div className="form-grid">
          <Field label="Batch *" full>
            <select className="input" value={batchId} onChange={e => chooseBatch(e.target.value)} data-testid="admission-batch">
              <option value="">Choose…</option>
              {openBatches.map(b => {
                const c = data.idx.courses.get(b.courseId)
                const left = b.capacity > 0 ? ` · ${Math.max(b.capacity - seatsTaken(data, b.id), 0)} seats left` : ''
                return <option key={b.id} value={b.id}>{b.name} — {c?.name ?? '?'} · starts {fullDate(b.startDate)}{left}</option>
              })}
            </select>
          </Field>
          {full && <div className="full notice warn">This batch is full ({taken}/{batch!.capacity}). You can still admit, but check the room first.</div>}
          <Field label="Admission date">
            <input className="input" type="date" value={date} onChange={e => { setDate(e.target.value); choosePlan(planChoice, payable, e.target.value) }} />
          </Field>
          <Field label="Course fee">
            <MoneyInput value={fee} onChange={v => { setFee(v); choosePlan(planChoice, Math.max(parseMoney(v) - parseMoney(discount), 0)) }} />
          </Field>
          <Field label="Discount">
            <MoneyInput value={discount} onChange={v => { setDiscount(v); choosePlan(planChoice, Math.max(parseMoney(fee) - parseMoney(v), 0)) }} />
          </Field>
          <Field label="Payable">
            <div className="input num bold" style={{ background: 'var(--card-2)' }}>{money.format(payable)}</div>
          </Field>
        </div>

        <b>Payment plan</b>
        <Segmented options={[['1', 'Full'], ['2', '2'], ['3', '3'], ['4', '4'], ['6', '6 months']]} value={planChoice} onChange={c => choosePlan(c)} />
        {plan.length > 0 && (
          <div className="table-wrap">
            <table className="table">
              <thead><tr><th>#</th><th>Due date</th><th className="r">Amount</th></tr></thead>
              <tbody>
                {plan.map((i, n) => (
                  <tr key={n}>
                    <td>{n + 1}</td>
                    <td><input className="input compact" style={{ width: 160 }} type="date" value={i.due}
                      onChange={e => setPlan(plan.map((x, k) => (k === n ? { ...x, due: e.target.value } : x)))} /></td>
                    <td className="r"><input className="input compact num" value={amountText(i.amount)}
                      onChange={e => setPlan(plan.map((x, k) => (k === n ? { ...x, amount: parseMoney(e.target.value) } : x)))} /></td>
                  </tr>
                ))}
              </tbody>
              <tfoot><tr><td colSpan={2}>Total</td><td className={`r num ${planTotal !== payable ? 'error-text' : ''}`}>{money.format(planTotal)}</td></tr></tfoot>
            </table>
          </div>
        )}

        <label className="row"><input type="checkbox" checked={collectNow} onChange={e => setCollectNow(e.target.checked)} /> Collect a payment now</label>
        {collectNow && (
          <div className="form-grid">
            <Field label="Amount received">
              <MoneyInput value={payAmount} placeholder={amountText(firstDue)} onChange={setPayAmount} testId="admission-pay" />
            </Field>
            <Field label="Method"><MethodSelect value={method} onChange={setMethod} /></Field>
          </div>
        )}
        <Field label="Note"><input className="input" value={note} onChange={e => setNote(e.target.value)} /></Field>
      </div>
      {error && <p className="error-text small">{error}</p>}
      <div className="actions">
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" onClick={save} data-testid="admission-save">Admit</button>
      </div>
    </Modal>
  )
}
