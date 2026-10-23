import { useNavigate, useParams } from 'react-router-dom'
import { useData } from '../../data/store'
import { METHOD_LABELS, PAY_TYPE_LABELS } from '../../data/types'
import { fullDate, monthLabel } from '../../domain/dates'
import { instructorPay, unitLabel } from '../../domain/instructors'
import { BatchStatusPill, Empty, PageHead, Tile, useMoney, usePermissions, useToday } from '../common'
import { useDialogs } from '../dialogs/host'
import { Icon } from '../icons'

export default function InstructorPage() {
  const { id } = useParams()
  const data = useData()
  const money = useMoney()
  const can = usePermissions()
  const navigate = useNavigate()
  const { open } = useDialogs()
  const today = useToday()
  const i = id ? data.idx.instructors.get(id) : undefined
  if (!i) return <div className="page"><Empty emoji="🔎" title="Instructor not found" /></div>
  const pay = instructorPay(i, data.batches, data.attendance, data.instructorPayments, today)
  const batches = data.batches.filter(b => b.instructorId === i.id)
  const payments = data.instructorPayments.filter(p => p.instructorId === i.id)
  const classes = data.attendance.filter(a => batches.some(b => b.id === a.batchId)).length
  return (
    <div className="page">
      <PageHead title={i.name} back={() => navigate(-1)} sub={[i.subject, i.phone, i.email].filter(Boolean).join(' · ')}>
        {can.manageSetup && <button className="btn outline" onClick={() => open({ kind: 'instructor', id: i.id })}><Icon name="edit" size={18} />Edit</button>}
        {can.manageMoney && <button className="btn" onClick={() => open({ kind: 'instructorPayment', instructorId: i.id })} data-testid="pay-instructor"><Icon name="fees" size={18} />Record payment</button>}
      </PageHead>
      <div className="tiles" style={{ marginBottom: 16 }}>
        <Tile label="Pay" value={i.payType === 'NONE' ? PAY_TYPE_LABELS.NONE : money.format(i.payRate)} sub={i.payType === 'NONE' ? undefined : PAY_TYPE_LABELS[i.payType]} />
        <Tile label="Earned" value={i.payType === 'NONE' ? '—' : money.format(pay.earned)} sub={unitLabel(i, pay.units) || undefined} />
        <Tile label="Paid" value={money.format(pay.paid)} />
        <Tile label="Owed" value={i.payType === 'NONE' ? '—' : money.format(pay.owed)} className={pay.owed > 0 ? 'danger' : 'success'} />
      </div>
      <div className="grid-2">
        <div className="card">
          <div className="card-head"><h2>Batches</h2><span className="small muted">{classes} classes taken</span></div>
          {batches.length === 0 ? <Empty emoji="🗓️" title="No batches assigned" /> : (
            <div className="list">
              {batches.map(b => (
                <button key={b.id} className="item" onClick={() => navigate(`/batches/${b.id}`)}>
                  <div className="grow"><div className="title">{b.name}</div><div className="subtitle">{data.idx.courses.get(b.courseId)?.name} · {fullDate(b.startDate)}</div></div>
                  <BatchStatusPill batch={b} date={today} />
                </button>
              ))}
            </div>
          )}
        </div>
        <div className="card">
          <div className="card-head"><h2>Payments</h2></div>
          {payments.length === 0 ? <Empty emoji="🧾" title="No payments yet" /> : (
            <div className="list">
              {payments.map(p => (
                <button key={p.id} className="item" onClick={() => can.manageMoney && open({ kind: 'instructorPayment', id: p.id })}>
                  <div className="grow">
                    <div className="title">{p.month ? `Salary for ${monthLabel(p.month)}` : p.batchId ? data.idx.batches.get(p.batchId)?.name ?? 'Payment' : 'Payment'}</div>
                    <div className="subtitle">{fullDate(p.date)} · {METHOD_LABELS[p.method]}{p.note ? ` · ${p.note}` : ''}</div>
                  </div>
                  <div className="end bold num">{money.format(p.amount)}</div>
                </button>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
