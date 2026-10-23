import { useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { activeStudentIds, feeRows, runningBatches } from '../../data/actions'
import { useData } from '../../data/store'
import { attendanceId, isClassDay } from '../../domain/attendance'
import { currentMonth, fullDate, greeting, monthLabel, relativeDay, timeLabel } from '../../domain/dates'
import { monthSummary } from '../../domain/reports'
import { Avatar, Empty, PageHead, Tile, useMoney, usePermissions, useToday } from '../common'
import { useDialogs } from '../dialogs/host'
import { Icon } from '../icons'

export function HomePage() {
  const can = usePermissions()
  return can.seeMoney ? <OfficeHome /> : <TeacherHome />
}

function TodaysClasses() {
  const data = useData()
  const navigate = useNavigate()
  const today = useToday()
  const mine = data.me?.instructorId
  const classes = useMemo(() => runningBatches(data, today)
    .filter(b => isClassDay(b, today))
    .sort((a, b) => Number(b.instructorId === mine && !!mine) - Number(a.instructorId === mine && !!mine) || a.time.localeCompare(b.time)),
  [data, today, mine])
  const taken = new Set(data.attendance.filter(a => a.date === today).map(a => a.id))
  return (
    <div className="card">
      <div className="card-head"><h2>Today's classes</h2><span className="muted small">{fullDate(today)}</span></div>
      {classes.length === 0 ? <Empty emoji="🗓️" title="No classes today" body="Batches show here on their class days." /> : (
        <div className="list">
          {classes.map(b => {
            const done = taken.has(attendanceId(b.id, today))
            const course = data.idx.courses.get(b.courseId)
            const teacher = b.instructorId ? data.idx.instructors.get(b.instructorId)?.name : undefined
            return (
              <button key={b.id} className="item" onClick={() => navigate(`/attendance?batch=${b.id}&date=${today}`)} data-testid="today-class">
                <span className="avatar" style={{ width: 40, height: 40, background: done ? 'color-mix(in srgb, var(--success) 20%, transparent)' : 'var(--chip)' }}>
                  {done ? '✓' : '🕑'}
                </span>
                <div className="grow">
                  <div className="title">{b.name} · {course?.name ?? ''}</div>
                  <div className="subtitle">{[b.time, b.room, teacher].filter(Boolean).join(' · ') || 'No time set'}</div>
                </div>
                <span className={`pill ${done ? 'green' : 'amber'}`}>{done ? 'Taken' : 'Mark attendance'}</span>
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}

function OfficeHome() {
  const data = useData()
  const money = useMoney()
  const navigate = useNavigate()
  const { open } = useDialogs()
  const today = useToday()
  const month = currentMonth()
  const summary = useMemo(() => monthSummary(month, data.payments, data.expenses, data.instructorPayments), [data, month])
  const todayIncome = data.payments.filter(p => p.date === today).reduce((s, p) => s + p.amount, 0)
  const rows = feeRows(data, today)
  const overdue = rows.filter(r => r.fee.overdue > 0).sort((a, b) => b.fee.overdue - a.fee.overdue)
  const totalOverdue = overdue.reduce((s, r) => s + r.fee.overdue, 0)
  const followUps = data.enquiries.filter(q => q.status === 'OPEN' && q.followUp !== '' && q.followUp <= today)
    .sort((a, b) => a.followUp.localeCompare(b.followUp))
  const openEnquiries = data.enquiries.filter(q => q.status === 'OPEN').length

  return (
    <div className="page">
      <PageHead title={data.center?.name ?? 'SkillTrack'} sub={greeting()}>
        <button className="btn" onClick={() => open({ kind: 'admission' })} data-testid="home-admission"><Icon name="plus" size={18} />New admission</button>
        <button className="btn tonal" onClick={() => open({ kind: 'collect' })} data-testid="home-collect"><Icon name="fees" size={18} />Collect fee</button>
      </PageHead>

      <div className="grid-2">
        <div className="stack">
          <div className="hero">
            <div className="label">Collected in {monthLabel(month)}</div>
            <div className="big num" data-testid="month-income">{money.format(summary.income)}</div>
            <div className="stats">
              <div><div className="k">Today</div><div className="v num">{money.format(todayIncome)}</div></div>
              <div><div className="k">Spent</div><div className="v num">{money.format(summary.spending)}</div></div>
              <div><div className="k">Profit</div><div className="v num">{money.format(summary.profit)}</div></div>
            </div>
          </div>
          <div className="tiles">
            <Tile label="Active students" value={String(activeStudentIds(data).size)} onClick={() => navigate('/students')} />
            <Tile label="Running batches" value={String(runningBatches(data, today).length)} onClick={() => navigate('/batches')} />
            <Tile label="Overdue fees" value={money.format(totalOverdue)} className={totalOverdue > 0 ? 'danger' : ''}
              sub={`${overdue.length} student${overdue.length === 1 ? '' : 's'}`} onClick={() => navigate('/fees?tab=dues')} />
            <Tile label="Open enquiries" value={String(openEnquiries)} sub={followUps.length ? `${followUps.length} to call` : undefined}
              onClick={() => navigate('/enquiries')} />
          </div>
          <TodaysClasses />
        </div>

        <div className="stack">
          {followUps.length > 0 && (
            <div className="card">
              <div className="card-head"><h2>Follow up today</h2><button className="btn text small" onClick={() => navigate('/enquiries')}>All</button></div>
              <div className="list">
                {followUps.slice(0, 5).map(q => (
                  <button key={q.id} className="item" onClick={() => open({ kind: 'enquiry', id: q.id })}>
                    <Icon name="phone" size={18} />
                    <div className="grow">
                      <div className="title">{q.name}</div>
                      <div className="subtitle">{q.phone} · {(q.courseId && data.idx.courses.get(q.courseId)?.name) || 'Any course'}</div>
                    </div>
                    <span className={`pill ${q.followUp < today ? 'red' : 'amber'}`}>{relativeDay(q.followUp, today)}</span>
                  </button>
                ))}
              </div>
            </div>
          )}

          <div className="card">
            <div className="card-head"><h2>Recent payments</h2><button className="btn text small" onClick={() => navigate('/fees?tab=receipts')}>All</button></div>
            {data.payments.length === 0 ? <Empty emoji="🧾" title="No payments yet" body="Collect a fee and the receipt shows here." /> : (
              <div className="list">
                {data.payments.slice(0, 6).map(p => {
                  const s = data.idx.students.get(p.studentId)
                  return (
                    <button key={p.id} className="item" onClick={() => open({ kind: 'payment', id: p.id })}>
                      <Avatar studentId={p.studentId} name={s?.name ?? '?'} size={36} />
                      <div className="grow">
                        <div className="title">{s?.name ?? 'Student'}</div>
                        <div className="subtitle">{p.receiptNo} · {relativeDay(p.date, today)}{p.date === today ? ` · ${timeLabel(p.createdAt)}` : ''}</div>
                      </div>
                      <div className="end bold num success">{money.format(p.amount)}</div>
                    </button>
                  )
                })}
              </div>
            )}
          </div>

          {overdue.length > 0 && (
            <div className="card">
              <div className="card-head"><h2>Most overdue</h2><button className="btn text small" onClick={() => navigate('/fees?tab=dues')}>All dues</button></div>
              <div className="list">
                {overdue.slice(0, 5).map(r => (
                  <button key={r.enrollment.id} className="item" onClick={() => open({ kind: 'collect', enrollmentId: r.enrollment.id })}>
                    <Avatar studentId={r.student?.id} name={r.student?.name ?? '?'} size={36} />
                    <div className="grow">
                      <div className="title">{r.student?.name ?? '?'}</div>
                      <div className="subtitle">{r.batch?.name ?? ''} · {r.student?.phone ?? ''}</div>
                    </div>
                    <div className="end bold num danger">{money.format(r.fee.overdue)}</div>
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function TeacherHome() {
  const data = useData()
  const navigate = useNavigate()
  const today = useToday()
  const upcoming = data.exams.filter(x => x.date >= today).slice(0, 6)
  return (
    <div className="page">
      <PageHead title={data.center?.name ?? 'SkillTrack'} sub={`${greeting()}, ${data.me?.name || data.email}`}>
        <button className="btn" onClick={() => navigate('/attendance')}><Icon name="attendance" size={18} />Mark attendance</button>
      </PageHead>
      <div className="grid-2">
        <TodaysClasses />
        <div className="card">
          <div className="card-head"><h2>Coming exams</h2></div>
          {upcoming.length === 0 ? <Empty emoji="📝" title="No exams planned" body="Add exams from a batch's page." /> : (
            <div className="list">
              {upcoming.map(x => (
                <button key={x.id} className="item" onClick={() => navigate(`/exams/${x.id}`)}>
                  <Icon name="exams" size={18} />
                  <div className="grow">
                    <div className="title">{x.name}</div>
                    <div className="subtitle">{data.idx.batches.get(x.batchId)?.name ?? ''} · {x.fullMarks} marks</div>
                  </div>
                  <span className="pill blue">{relativeDay(x.date, today)}</span>
                </button>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
