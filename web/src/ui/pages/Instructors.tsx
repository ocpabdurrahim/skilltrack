import { useNavigate } from 'react-router-dom'
import { useData } from '../../data/store'
import { PAY_TYPE_LABELS } from '../../data/types'
import { batchStatus } from '../../domain/batches'
import { instructorPay } from '../../domain/instructors'
import { Empty, PageHead, useMoney, usePermissions, useToday } from '../common'
import { useDialogs } from '../dialogs/host'
import { Icon } from '../icons'

export default function InstructorsPage() {
  const data = useData()
  const money = useMoney()
  const can = usePermissions()
  const navigate = useNavigate()
  const { open } = useDialogs()
  const today = useToday()
  return (
    <div className="page">
      <PageHead title="Instructors">
        {can.manageMoney && <button className="btn tonal" onClick={() => open({ kind: 'instructorPayment' })}><Icon name="fees" size={18} />Pay</button>}
        {can.manageSetup && <button className="btn" onClick={() => open({ kind: 'instructor' })} data-testid="add-instructor"><Icon name="plus" size={18} />Instructor</button>}
      </PageHead>
      {data.instructors.length === 0 ? (
        <div className="card"><Empty emoji="👩‍🏫" title="No instructors yet" body="Add your teachers with how they are paid (monthly, per batch or per class) to see what each is owed.">
          {can.manageSetup && <button className="btn" onClick={() => open({ kind: 'instructor' })}>Add an instructor</button>}
        </Empty></div>
      ) : (
        <div className="card table-wrap">
          <table className="table">
            <thead><tr><th>Instructor</th><th>Pay</th><th className="r">Running batches</th><th className="r">Earned</th><th className="r">Paid</th><th className="r">Owed</th></tr></thead>
            <tbody>
              {data.instructors.map(i => {
                const p = instructorPay(i, data.batches, data.attendance, data.instructorPayments, today)
                const running = data.batches.filter(b => b.instructorId === i.id && batchStatus(b, today) === 'RUNNING').length
                return (
                  <tr key={i.id} className="click" onClick={() => navigate(`/instructors/${i.id}`)} style={i.archived ? { opacity: 0.55 } : undefined}>
                    <td><b>{i.name}</b><div className="small muted">{[i.subject, i.phone].filter(Boolean).join(' · ')}</div></td>
                    <td>{PAY_TYPE_LABELS[i.payType]}{i.payType !== 'NONE' && <div className="small muted">{money.format(i.payRate)}</div>}</td>
                    <td className="r num">{running}</td>
                    <td className="r num">{i.payType === 'NONE' ? '—' : money.format(p.earned)}</td>
                    <td className="r num">{money.format(p.paid)}</td>
                    <td className={`r num bold ${p.owed > 0 ? 'danger' : ''}`}>{i.payType === 'NONE' ? '—' : money.format(p.owed)}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
