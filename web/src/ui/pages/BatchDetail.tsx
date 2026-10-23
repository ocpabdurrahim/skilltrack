import { useMemo, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { attendanceMap, feeOf } from '../../data/actions'
import { useData } from '../../data/store'
import type { Attendance, Batch, Enrollment } from '../../data/types'
import { attendanceOf, isClassDay } from '../../domain/attendance'
import { daysLabel } from '../../domain/batches'
import { currentMonth, fullDate, monthDays, monthOfDate, shortDate, weekdayLabel, type YearMonth } from '../../domain/dates'
import { batchResults, percentLabel } from '../../domain/results'
import {
  BatchStatusPill, Empty, EnrollmentPill, MonthSelector, PageHead, pct, rateColor, Tabs, useMoney, usePermissions, useToday,
} from '../common'
import { useDialogs } from '../dialogs/host'
import { Icon } from '../icons'

type Tab = 'students' | 'attendance' | 'exams' | 'results'

export default function BatchPage() {
  const { id } = useParams()
  const data = useData()
  const can = usePermissions()
  const navigate = useNavigate()
  const { open } = useDialogs()
  const today = useToday()
  const [params, setParams] = useSearchParams()
  const tab = (params.get('tab') as Tab) || 'students'
  const b = id ? data.idx.batches.get(id) : undefined
  if (!b) return <div className="page"><Empty emoji="🔎" title="Batch not found" /></div>
  const c = data.idx.courses.get(b.courseId)
  const t = b.instructorId ? data.idx.instructors.get(b.instructorId) : undefined
  const enrollments = data.enrollments.filter(e => e.batchId === b.id)

  return (
    <div className="page">
      <PageHead title={b.name} back={() => navigate(-1)}
        sub={<>{c?.name ?? 'Course'} · <BatchStatusPill batch={b} date={today} /></>}>
        {can.manageSetup && <button className="btn outline" onClick={() => open({ kind: 'batch', id: b.id })}><Icon name="edit" size={18} />Edit</button>}
        {can.teach && <button className="btn tonal" onClick={() => navigate(`/attendance?batch=${b.id}&date=${today}`)} data-testid="batch-attendance"><Icon name="attendance" size={18} />Attendance</button>}
        {can.manageStudents && <button className="btn" onClick={() => open({ kind: 'admission', batchId: b.id })}><Icon name="plus" size={18} />Admit</button>}
      </PageHead>
      <div className="card pad" style={{ marginBottom: 16 }}>
        <dl className="kv" style={{ margin: 0, gridTemplateColumns: 'max-content 1fr max-content 1fr' }}>
          <dt>Dates</dt><dd>{fullDate(b.startDate)}{b.endDate ? ` – ${fullDate(b.endDate)}` : ''}</dd>
          <dt>Class days</dt><dd>{daysLabel(b.days) || '—'}{b.time ? ` · ${b.time}` : ''}</dd>
          <dt>Instructor</dt><dd>{t?.name ?? '—'}</dd>
          <dt>Room</dt><dd>{b.room || '—'}</dd>
          <dt>Students</dt><dd>{enrollments.filter(e => e.status !== 'DROPPED').length}{b.capacity ? ` of ${b.capacity} seats` : ''}</dd>
          <dt>Note</dt><dd>{b.note || '—'}</dd>
        </dl>
      </div>
      <Tabs options={[['students', 'Students'], ['attendance', 'Attendance'], ['exams', 'Exams'], ['results', 'Results']]} value={tab}
        onChange={v => setParams({ tab: v }, { replace: true })} />
      {tab === 'students' && <Roster batch={b} enrollments={enrollments} />}
      {tab === 'attendance' && <AttendanceSheet batch={b} enrollments={enrollments} />}
      {tab === 'exams' && <ExamList batch={b} />}
      {tab === 'results' && <ResultSheet batch={b} enrollments={enrollments} />}
    </div>
  )
}

function Roster({ batch, enrollments }: { batch: Batch; enrollments: Enrollment[] }) {
  const data = useData()
  const money = useMoney()
  const can = usePermissions()
  const navigate = useNavigate()
  const { open } = useDialogs()
  const today = useToday()
  const att = attendanceMap(data)
  const results = useMemo(() => batchResults(enrollments, data.exams.filter(x => x.batchId === batch.id), today), [enrollments, data.exams, batch.id, today])
  if (enrollments.length === 0) {
    return <div className="card"><Empty emoji="🧑‍🎓" title="No students in this batch yet">
      {can.manageStudents && <button className="btn" onClick={() => open({ kind: 'admission', batchId: batch.id })}>Admit a student</button>}
    </Empty></div>
  }
  return (
    <div className="card table-wrap">
      <div className="row" style={{ padding: '10px 12px 0', justifyContent: 'flex-end' }}>
        <button className="btn text small" onClick={() => navigate(`/print/idcards?batch=${batch.id}`)}><Icon name="card" size={16} />ID cards</button>
      </div>
      <table className="table">
        <thead><tr><th>Roll</th><th>Student</th><th>Phone</th><th>Status</th>{can.seeMoney && <th className="r">Due</th>}<th className="r">Attendance</th><th className="r">Result</th></tr></thead>
        <tbody>
          {enrollments.map(e => {
            const s = data.idx.students.get(e.studentId)
            const f = feeOf(data, e, today)
            const a = att.get(e.id)
            const r = results.get(e.id)
            return (
              <tr key={e.id} className="click" onClick={() => navigate(`/students/${e.studentId}`)} style={e.status === 'DROPPED' ? { opacity: 0.55 } : undefined}>
                <td className="num">{e.rollNo}</td>
                <td><b>{s?.name ?? '?'}</b><div className="small muted">{s?.regNo}</div></td>
                <td>{s?.phone}</td>
                <td><EnrollmentPill status={e.status} /></td>
                {can.seeMoney && <td className={`r num ${f.overdue > 0 ? 'danger bold' : ''}`}>{e.status === 'DROPPED' ? '—' : f.due > 0 ? money.format(f.due) : 'Paid'}</td>}
                <td className="r num" style={{ color: rateColor(a?.rate ?? null) }}>{pct(a?.rate ?? null)}</td>
                <td className="r">{r ? <><b>{r.grade}</b> <span className="small muted">{percentLabel(r.percent)}</span></> : '—'}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

/** Students × days for a month, like a paper register. */
export function AttendanceSheet({ batch, enrollments }: { batch: Batch; enrollments: Enrollment[] }) {
  const data = useData()
  const navigate = useNavigate()
  const can = usePermissions()
  const today = useToday()
  const [month, setMonth] = useState<YearMonth>(() => {
    const now = currentMonth()
    if (batch.endDate && monthOfDate(batch.endDate) < now) return monthOfDate(batch.endDate)
    if (batch.startDate && monthOfDate(batch.startDate) > now) return monthOfDate(batch.startDate)
    return now
  })
  const sessions = data.attendance.filter(a => a.batchId === batch.id)
  const byDate = new Map<string, Attendance>(sessions.filter(a => a.date.startsWith(month)).map(a => [a.date, a]))
  const days = monthDays(month).filter(d => byDate.has(d) || (isClassDay(batch, d) && d <= today))
  const roster = enrollments.filter(e => e.status !== 'DROPPED')
  const monthSessions = [...byDate.values()]
  return (
    <div className="stack">
      <div className="row wrap">
        <MonthSelector month={month} onChange={setMonth} />
        <div className="grow" />
        <button className="btn outline small" onClick={() => navigate(`/print/attendance/${batch.id}/${month}`)}><Icon name="printer" size={16} />Print register</button>
      </div>
      {days.length === 0 ? <div className="card"><Empty emoji="🗓️" title="No classes this month" /></div> : (
        <div className="card table-wrap" style={{ padding: 8 }}>
          <table className="grid-sheet">
            <thead>
              <tr>
                <th className="name">Student</th>
                {days.map(d => (
                  <th key={d} style={{ cursor: can.teach ? 'pointer' : undefined }} title={can.teach ? 'Open this day' : undefined}
                    onClick={() => can.teach && navigate(`/attendance?batch=${batch.id}&date=${d}`)}>
                    {Number(d.slice(8))}<br /><span className="muted" style={{ fontWeight: 400 }}>{weekdayLabel(d).slice(0, 2)}</span>
                  </th>
                ))}
                <th>Month</th><th>All</th>
              </tr>
            </thead>
            <tbody>
              {roster.map(e => {
                const s = data.idx.students.get(e.studentId)
                const m = attendanceOf(e.id, monthSessions)
                const all = attendanceOf(e.id, sessions)
                return (
                  <tr key={e.id}>
                    <td className="name">{e.rollNo}. {s?.name ?? '?'}</td>
                    {days.map(d => {
                      const mark = byDate.get(d)?.marks[e.id]
                      return <td key={d} className={mark ? `m-${mark}` : 'muted'}>{mark ?? (byDate.has(d) ? '·' : '')}</td>
                    })}
                    <td className="bold" style={{ color: rateColor(m.rate) }}>{pct(m.rate)}</td>
                    <td className="bold" style={{ color: rateColor(all.rate) }}>{pct(all.rate)}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
          <div className="tiny muted" style={{ padding: '8px 4px 0' }}>P present · A absent · L late · E excused. Days without a mark yet are empty; click a day to mark it.</div>
        </div>
      )}
    </div>
  )
}

function ExamList({ batch }: { batch: Batch }) {
  const data = useData()
  const can = usePermissions()
  const navigate = useNavigate()
  const { open } = useDialogs()
  const exams = data.exams.filter(x => x.batchId === batch.id)
  const roster = data.enrollments.filter(e => e.batchId === batch.id && e.status !== 'DROPPED')
  return (
    <div className="stack">
      {can.teach && <div className="row"><div className="grow" /><button className="btn" onClick={() => open({ kind: 'exam', batchId: batch.id })} data-testid="add-exam"><Icon name="plus" size={18} />Exam</button></div>}
      {exams.length === 0 ? <div className="card"><Empty emoji="📝" title="No exams yet" body="Add each exam (e.g. Written, Practical, Viva) with its full and pass marks, then enter marks." /></div> : (
        <div className="card list">
          {exams.map(x => {
            const entered = roster.filter(e => typeof x.marks[e.id] === 'number').length
            return (
              <div key={x.id} className="item">
                <Icon name="exams" />
                <div className="grow">
                  <div className="title">{x.name}</div>
                  <div className="subtitle">{fullDate(x.date) || 'No date'} · full {x.fullMarks} · pass {x.passMarks} · {entered}/{roster.length} marks entered</div>
                </div>
                {can.teach && <button className="btn outline small" onClick={() => open({ kind: 'exam', batchId: batch.id, id: x.id })}>Edit</button>}
                <button className="btn tonal small" onClick={() => navigate(`/exams/${x.id}`)} data-testid="exam-marks">{can.teach ? 'Marks' : 'View'}</button>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

function ResultSheet({ batch, enrollments }: { batch: Batch; enrollments: Enrollment[] }) {
  const data = useData()
  const navigate = useNavigate()
  const today = useToday()
  const exams = data.exams.filter(x => x.batchId === batch.id)
  const results = useMemo(() => batchResults(enrollments, exams, today), [enrollments, exams, today])
  if (exams.length === 0) return <div className="card"><Empty emoji="🏅" title="No results yet" body="Results appear once the batch has exams with marks." /></div>
  return (
    <div className="stack">
      <div className="row"><div className="grow small muted">Grades on the Bangladesh scale (A+ 80%+). A mark below the pass mark in any exam is F.</div>
        <button className="btn outline small" onClick={() => navigate(`/print/results/${batch.id}`)} data-testid="print-results"><Icon name="printer" size={16} />Print result sheet</button></div>
      <div className="card table-wrap">
        <table className="table">
          <thead>
            <tr><th>Roll</th><th>Student</th>{exams.map(x => <th key={x.id} className="r">{x.name}<div className="tiny muted">/{x.fullMarks}</div></th>)}
              <th className="r">Total</th><th className="r">%</th><th className="c">Grade</th><th className="r">GPA</th><th className="r">Position</th></tr>
          </thead>
          <tbody>
            {enrollments.filter(e => e.status !== 'DROPPED').map(e => {
              const s = data.idx.students.get(e.studentId)
              const r = results.get(e.id)
              return (
                <tr key={e.id} className="click" onClick={() => navigate(`/print/marksheet/${e.id}`)}>
                  <td className="num">{e.rollNo}</td>
                  <td><b>{s?.name ?? '?'}</b></td>
                  {exams.map(x => {
                    const m = x.marks[e.id]
                    return <td key={x.id} className={`r num ${typeof m === 'number' && m < x.passMarks ? 'danger bold' : ''}`}>{typeof m === 'number' ? m : '—'}</td>
                  })}
                  <td className="r num">{r ? `${r.obtained}/${r.full}` : '—'}</td>
                  <td className="r num">{r ? percentLabel(r.percent) : '—'}</td>
                  <td className={`c bold ${r?.failed ? 'danger' : ''}`}>{r?.grade ?? '—'}</td>
                  <td className="r num">{r ? r.point.toFixed(2) : '—'}</td>
                  <td className="r num">{r?.position ?? '—'}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      <div className="tiny muted">Click a row for the student's mark sheet. Dates: {exams.map(x => `${x.name} ${shortDate(x.date)}`).join(', ')}.</div>
    </div>
  )
}
