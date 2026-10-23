import { useNavigate, useParams } from 'react-router-dom'
import { attendanceMap, feeOf, resultsMap, whatsappNumber } from '../../data/actions'
import { useData, usePhoto } from '../../data/store'
import { METHOD_LABELS } from '../../data/types'
import { age, fullDate } from '../../domain/dates'
import { percentLabel } from '../../domain/results'
import {
  BatchStatusPill, Empty, EnrollmentPill, PageHead, pct, rateColor, useMoney, usePermissions, useToday,
} from '../common'
import { useDialogs } from '../dialogs/host'
import { Icon } from '../icons'

export default function StudentPage() {
  const { id } = useParams()
  const data = useData()
  const money = useMoney()
  const can = usePermissions()
  const navigate = useNavigate()
  const { open } = useDialogs()
  const today = useToday()
  const s = id ? data.idx.students.get(id) : undefined
  const photo = usePhoto(s?.id, true)
  if (!s) return <div className="page"><Empty emoji="🔎" title="Student not found" body="They may have been removed on another device." /></div>

  const enrollments = data.enrollments.filter(e => e.studentId === s.id)
  const payments = data.payments.filter(p => p.studentId === s.id)
  const att = attendanceMap(data)
  const results = resultsMap(data, today)
  const certs = data.certificates.filter(c => c.studentId === s.id)
  const totalDue = enrollments.filter(e => e.status !== 'DROPPED').reduce((sum, e) => sum + Math.max(feeOf(data, e, today).due, 0), 0)
  const years = age(s.dob, today)

  const details: [string, string][] = [
    ['Reg. no.', s.regNo], ['Phone', s.phone], ['Guardian\'s phone', s.guardianPhone], ['Father', s.fatherName],
    ['Mother', s.motherName], ['Gender', s.gender ? s.gender.charAt(0) + s.gender.slice(1).toLowerCase() : ''],
    ['Date of birth', s.dob ? `${fullDate(s.dob)}${years !== null ? ` (${years} years)` : ''}` : ''], ['Email', s.email],
    ['NID / birth reg.', s.idNo], ['Education', [s.education, s.institution].filter(Boolean).join(', ')], ['Address', s.address],
    ['Joined', fullDate(s.joinDate)], ['Note', s.note],
  ]

  return (
    <div className="page">
      <PageHead title={s.name} sub={`${s.regNo}${s.archived ? ' · archived' : ''}`} back={() => navigate(-1)}>
        {can.manageStudents && <button className="btn outline" onClick={() => open({ kind: 'student', id: s.id })}><Icon name="edit" size={18} />Edit</button>}
        {can.manageStudents && <button className="btn tonal" onClick={() => open({ kind: 'admission', studentId: s.id })}><Icon name="plus" size={18} />Admission</button>}
        {can.manageMoney && enrollments.length > 0 && <button className="btn" onClick={() => open({ kind: 'collect', studentId: s.id })} data-testid="profile-collect"><Icon name="fees" size={18} />Collect fee</button>}
      </PageHead>

      <div className="grid-2">
        <div className="stack">
          <div className="card pad">
            <div className="row" style={{ alignItems: 'flex-start', gap: 16, flexWrap: 'wrap' }}>
              <div className="photo-box">{photo ? <img src={photo} alt="" /> : '🧑‍🎓'}</div>
              <dl className="kv grow" style={{ margin: 0, minWidth: 220 }}>
                {details.filter(([, v]) => v).map(([k, v]) => <div key={k} style={{ display: 'contents' }}><dt>{k}</dt><dd>{v}</dd></div>)}
              </dl>
            </div>
            <div className="row wrap" style={{ marginTop: 14 }}>
              {s.phone && <a className="btn tonal small" href={`tel:${s.phone}`}><Icon name="phone" size={16} />Call</a>}
              {s.phone && <a className="btn tonal small" target="_blank" rel="noreferrer" href={`https://wa.me/${whatsappNumber(s.phone)}`}><Icon name="message" size={16} />WhatsApp</a>}
              <button className="btn outline small" onClick={() => navigate(`/print/idcards?ids=${s.id}`)}><Icon name="card" size={16} />ID card</button>
              <button className="btn outline small" onClick={() => navigate(`/print/admission/${s.id}`)}><Icon name="printer" size={16} />Admission form</button>
            </div>
          </div>

          {can.seeMoney && (
            <div className="card">
              <div className="card-head"><h2>Payments</h2><span className={`bold num ${totalDue > 0 ? 'danger' : 'success'}`}>{totalDue > 0 ? `${money.format(totalDue)} due` : 'Nothing due'}</span></div>
              {payments.length === 0 ? <Empty emoji="🧾" title="No payments yet" /> : (
                <div className="list">
                  {payments.map(p => (
                    <button key={p.id} className="item" onClick={() => open({ kind: 'payment', id: p.id })}>
                      <div className="grow">
                        <div className="title">{p.receiptNo}</div>
                        <div className="subtitle">{fullDate(p.date)} · {METHOD_LABELS[p.method]}{p.note ? ` · ${p.note}` : ''}</div>
                      </div>
                      <div className="end bold num success">{money.format(p.amount)}</div>
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>

        <div className="stack">
          <div className="section-title">Courses</div>
          {enrollments.length === 0 && (
            <div className="card"><Empty emoji="📚" title="Not admitted to a batch yet">
              {can.manageStudents && <button className="btn" onClick={() => open({ kind: 'admission', studentId: s.id })}>New admission</button>}
            </Empty></div>
          )}
          {enrollments.map(e => {
            const b = data.idx.batches.get(e.batchId)
            const c = data.idx.courses.get(e.courseId)
            const f = feeOf(data, e, today)
            const a = att.get(e.id)
            const r = results.get(e.id)
            const cert = certs.find(x => x.enrollmentId === e.id)
            return (
              <div key={e.id} className="card pad stack" style={{ gap: 10 }}>
                <div className="row">
                  <div className="grow">
                    <div className="bold">{c?.name ?? 'Course'}</div>
                    <button className="btn text small" style={{ padding: 0 }} onClick={() => navigate(`/batches/${e.batchId}`)}>{b?.name ?? 'Batch'}</button>
                    <span className="small muted"> · roll {e.rollNo} · admitted {fullDate(e.date)}</span>
                  </div>
                  <div className="stack" style={{ gap: 4, alignItems: 'flex-end' }}>
                    <EnrollmentPill status={e.status} />
                    {b && <BatchStatusPill batch={b} date={today} />}
                  </div>
                </div>
                <div className="tiles">
                  {can.seeMoney && <div className="tile"><div className="k">Fee</div><div className="v num">{money.format(f.payable)}</div><div className="s">paid {money.format(f.paid)}</div></div>}
                  {can.seeMoney && (
                    <div className="tile"><div className="k">Due</div>
                      <div className={`v num ${f.overdue > 0 ? 'danger' : ''}`}>{f.due > 0 ? money.format(f.due) : 'Paid'}</div>
                      <div className="s">{f.overdue > 0 ? `${money.format(f.overdue)} overdue` : f.nextDue ? `next ${fullDate(f.nextDue.date)}` : '—'}</div></div>
                  )}
                  <div className="tile"><div className="k">Attendance</div><div className="v num" style={{ color: rateColor(a?.rate ?? null) }}>{pct(a?.rate ?? null)}</div>
                    <div className="s">{a ? `${a.present + a.late} of ${a.present + a.late + a.absent}` : 'not marked'}</div></div>
                  <div className="tile"><div className="k">Result</div><div className="v num">{r ? r.grade : '—'}</div>
                    <div className="s">{r ? `${percentLabel(r.percent)}${r.position ? ` · position ${r.position}` : ''}` : 'no exams yet'}</div></div>
                </div>
                <div className="row wrap">
                  {can.manageStudents && <button className="btn outline small" onClick={() => open({ kind: 'enrollment', id: e.id })}><Icon name="edit" size={16} />Admission</button>}
                  {can.manageMoney && f.due > 0 && <button className="btn tonal small" onClick={() => open({ kind: 'collect', enrollmentId: e.id })}>Collect {money.format(f.overdue || f.nextDue?.amount || f.due)}</button>}
                  {r && <button className="btn outline small" onClick={() => navigate(`/print/marksheet/${e.id}`)}><Icon name="printer" size={16} />Mark sheet</button>}
                  {cert
                    ? <button className="btn outline small" onClick={() => navigate(`/print/certificate/${cert.id}`)}><Icon name="certificate" size={16} />{cert.certNo}</button>
                    : can.manageStudents && (e.status === 'COMPLETED' || (r && !r.failed && r.complete)) &&
                      <button className="btn tonal small" onClick={() => open({ kind: 'certificate', enrollmentId: e.id })}><Icon name="certificate" size={16} />Issue certificate</button>}
                </div>
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}
