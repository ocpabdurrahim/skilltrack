import { useState } from 'react'
import { feeOf } from '../../data/actions'
import { store, useData } from '../../data/store'
import type { EnrollmentStatus, Installment } from '../../data/types'
import { makePlan, payableOf } from '../../domain/fees'
import { amountText, Field, Modal, MoneyInput, parseMoney, Segmented, useMoney, useToast, useToday } from '../common'

/** Change an admission: status, discount, payment plan, note. */
export default function EnrollmentDialog({ id, onClose }: { id: string; onClose: () => void }) {
  const data = useData()
  const money = useMoney()
  const toast = useToast()
  const today = useToday()
  const e = data.idx.enrollments.get(id)
  const [status, setStatus] = useState<EnrollmentStatus>(e?.status ?? 'ACTIVE')
  const [completedOn, setCompletedOn] = useState(e?.completedOn || today)
  const [fee, setFee] = useState(amountText(e?.fee ?? 0))
  const [discount, setDiscount] = useState(amountText(e?.discount ?? 0))
  const [plan, setPlan] = useState<Installment[]>(e?.installments ?? [])
  const [note, setNote] = useState(e?.note ?? '')
  const [error, setError] = useState<string | null>(null)
  if (!e) return null
  const student = data.idx.students.get(e.studentId)
  const batch = data.idx.batches.get(e.batchId)
  const payable = payableOf({ fee: parseMoney(fee), discount: parseMoney(discount) })
  const planTotal = plan.reduce((s, i) => s + i.amount, 0)
  const fs = feeOf(data, e, today)

  const save = () => {
    if (plan.length > 0 && planTotal !== payable) return setError(`The installments add up to ${money.format(planTotal)}, not ${money.format(payable)}.`)
    store.put('enrollments', {
      ...e, status, completedOn: status === 'COMPLETED' ? completedOn : '', fee: parseMoney(fee), discount: parseMoney(discount),
      installments: plan, note,
    })
    toast('Admission saved')
    onClose()
  }

  return (
    <Modal onClose={onClose} label="Edit admission">
      <h2>{student?.name ?? 'Student'} · {batch?.name ?? 'Batch'}</h2>
      <div className="stack">
        <Field label="Status">
          <Segmented options={[['ACTIVE', 'Active'], ['COMPLETED', 'Completed'], ['DROPPED', 'Dropped']]} value={status} onChange={setStatus} />
        </Field>
        {status === 'COMPLETED' && (
          <Field label="Completed on"><input className="input" type="date" value={completedOn} onChange={ev => setCompletedOn(ev.target.value)} /></Field>
        )}
        {status === 'DROPPED' && <div className="notice warn">Dropped admissions are left out of dues and attendance registers.</div>}
        <div className="form-grid">
          <Field label="Course fee"><MoneyInput value={fee} onChange={setFee} /></Field>
          <Field label="Discount"><MoneyInput value={discount} onChange={setDiscount} /></Field>
        </div>
        <div className="small muted">Payable {money.format(payable)} · paid {money.format(fs.paid)} · due {money.format(Math.max(payable - fs.paid, 0))}</div>
        <div className="row wrap">
          <b className="grow">Payment plan</b>
          <button className="btn text small" onClick={() => setPlan([])}>Pay in full</button>
          {[2, 3, 4, 6].map(n => (
            <button key={n} className="btn text small" onClick={() => setPlan(makePlan(payable, n, plan[0]?.due || e.date))}>{n} parts</button>
          ))}
        </div>
        {plan.length === 0 ? <div className="small muted">Everything is due on the admission date.</div> : (
          <table className="table">
            <thead><tr><th>#</th><th>Due date</th><th className="r">Amount</th><th /></tr></thead>
            <tbody>
              {plan.map((i, n) => (
                <tr key={n}>
                  <td>{n + 1}</td>
                  <td><input className="input compact" style={{ width: 160 }} type="date" value={i.due}
                    onChange={ev => setPlan(plan.map((x, k) => (k === n ? { ...x, due: ev.target.value } : x)))} /></td>
                  <td className="r"><input className="input compact num" value={amountText(i.amount)}
                    onChange={ev => setPlan(plan.map((x, k) => (k === n ? { ...x, amount: parseMoney(ev.target.value) } : x)))} /></td>
                  <td><button className="btn text small" onClick={() => setPlan(plan.filter((_, k) => k !== n))}>✕</button></td>
                </tr>
              ))}
            </tbody>
            <tfoot><tr><td colSpan={2}>Total</td><td className={`r num ${planTotal !== payable ? 'error-text' : ''}`}>{money.format(planTotal)}</td><td /></tr></tfoot>
          </table>
        )}
        <Field label="Note"><input className="input" value={note} onChange={ev => setNote(ev.target.value)} /></Field>
      </div>
      {error && <p className="error-text small">{error}</p>}
      <div className="actions">
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" onClick={save}>Save</button>
      </div>
    </Modal>
  )
}
