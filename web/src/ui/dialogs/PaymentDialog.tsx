import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { store, useData } from '../../data/store'
import type { Method } from '../../data/types'
import { amountText, ConfirmDialog, Field, MethodSelect, Modal, MoneyInput, parseMoney, usePermissions, useToast } from '../common'
import { Icon } from '../icons'

/** A receipt already issued: owners and admins may correct or delete it. */
export default function PaymentDialog({ id, onClose }: { id: string; onClose: () => void }) {
  const data = useData()
  const toast = useToast()
  const navigate = useNavigate()
  const can = usePermissions()
  const p = data.payments.find(x => x.id === id)
  const [amount, setAmount] = useState(amountText(p?.amount ?? 0))
  const [date, setDate] = useState(p?.date ?? '')
  const [method, setMethod] = useState<Method>(p?.method ?? 'CASH')
  const [note, setNote] = useState(p?.note ?? '')
  const [confirm, setConfirm] = useState(false)
  if (!p) return null
  const student = data.idx.students.get(p.studentId)
  const edit = can.editReceipts

  return (
    <Modal onClose={onClose} narrow label="Receipt">
      <h2>Receipt {p.receiptNo}</h2>
      <p className="muted small">{student?.name ?? 'Student'} · received by {p.receivedBy || '—'}</p>
      <div className="form-grid">
        <Field label="Amount">{edit ? <MoneyInput value={amount} onChange={setAmount} /> : <div className="input">{amount}</div>}</Field>
        <Field label="Date"><input className="input" type="date" value={date} disabled={!edit} onChange={e => setDate(e.target.value)} /></Field>
        <Field label="Method">{edit ? <MethodSelect value={method} onChange={setMethod} /> : <div className="input">{method}</div>}</Field>
        <Field label="Note"><input className="input" value={note} disabled={!edit} onChange={e => setNote(e.target.value)} /></Field>
      </div>
      {!edit && <p className="small muted">Only an owner or admin can change a receipt once it is issued.</p>}
      <div className="actions">
        {edit && <button className="btn danger" style={{ marginRight: 'auto' }} onClick={() => setConfirm(true)}>Delete</button>}
        <button className="btn tonal" onClick={() => navigate(`/print/receipt/${p.id}`)}><Icon name="printer" size={18} />Print</button>
        {edit && (
          <button className="btn" disabled={parseMoney(amount) <= 0}
            onClick={() => { store.put('payments', { ...p, amount: parseMoney(amount), date, method, note }); toast('Receipt saved'); onClose() }}>
            Save
          </button>
        )}
        {!edit && <button className="btn text" onClick={onClose}>Close</button>}
      </div>
      {confirm && (
        <ConfirmDialog title="Delete this receipt?" danger confirm="Delete"
          text="The payment is taken off the student's account and out of the reports. The receipt number is not reused."
          onConfirm={() => { store.remove('payments', p.id); toast('Receipt deleted'); onClose() }} onClose={() => setConfirm(false)} />
      )}
    </Modal>
  )
}
