import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { attendanceMap, runningBatches, staffName } from '../../data/actions'
import { store, useData } from '../../data/store'
import { MARK_LABELS, MARKS, type Batch, type Mark } from '../../data/types'
import { attendanceId, classDayWarning, studentsToMark } from '../../domain/attendance'
import { batchStatus } from '../../domain/batches'
import { addDays, fullDate, weekdayLabel } from '../../domain/dates'
import { Avatar, Empty, Field, PageHead, pct, rateColor, useToast, useToday } from '../common'

export default function AttendancePage() {
  const data = useData()
  const today = useToday()
  const [params, setParams] = useSearchParams()
  const mine = data.me?.instructorId
  const batches = useMemo(() => {
    const open = data.batches.filter(b => batchStatus(b, today) !== 'CANCELLED')
    return open.sort((a, b) => Number(b.instructorId === mine && !!mine) - Number(a.instructorId === mine && !!mine) ||
      Number(batchStatus(b, today) === 'RUNNING') - Number(batchStatus(a, today) === 'RUNNING') || a.name.localeCompare(b.name))
  }, [data.batches, today, mine])
  const batchId = params.get('batch') ?? runningBatches(data, today)[0]?.id ?? batches[0]?.id ?? ''
  const date = params.get('date') ?? today
  const batch = data.idx.batches.get(batchId)
  const set = (k: 'batch' | 'date', v: string) => setParams({ batch: batchId, date, [k]: v }, { replace: true })

  return (
    <div className="page">
      <PageHead title="Attendance" sub={batch ? `${batch.name} · ${weekdayLabel(date)}, ${fullDate(date)}` : undefined} />
      {batches.length === 0 ? <div className="card"><Empty emoji="🗓️" title="No batches yet" body="Create a batch and admit students, then mark attendance here." /></div> : (
        <div className="grid-2">
          <div className="stack">
            <div className="card pad">
              <div className="form-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))' }}>
                <Field label="Batch">
                  <select className="input" value={batchId} onChange={e => set('batch', e.target.value)} data-testid="att-batch">
                    {batches.map(b => <option key={b.id} value={b.id}>{b.name} — {data.idx.courses.get(b.courseId)?.name ?? ''}</option>)}
                  </select>
                </Field>
                <Field label="Date">
                  <div className="row" style={{ gap: 4 }}>
                    <button className="icon-btn" aria-label="Previous day" onClick={() => set('date', addDays(date, -1))}>‹</button>
                    <input className="input grow" type="date" value={date} max={today} onChange={e => e.target.value && set('date', e.target.value)} data-testid="att-date" />
                    <button className="icon-btn" aria-label="Next day" disabled={date >= today} onClick={() => set('date', addDays(date, 1))}>›</button>
                  </div>
                </Field>
              </div>
            </div>
            {batch && <Register key={`${batch.id}_${date}`} batch={batch} date={date} />}
          </div>
          <LowAttendance />
        </div>
      )}
    </div>
  )
}

function Register({ batch, date }: { batch: Batch; date: string }) {
  const data = useData()
  const toast = useToast()
  const today = useToday()
  const id = attendanceId(batch.id, date)
  const existing = data.attendance.find(a => a.id === id)
  const roster = studentsToMark(batch.id, data.enrollments, date)
  const [marks, setMarks] = useState<Record<string, Mark>>(() => {
    const out: Record<string, Mark> = {}
    for (const e of roster) out[e.id] = existing?.marks[e.id] ?? 'P'
    return out
  })
  const [note, setNote] = useState(existing?.note ?? '')
  // Unsaved when new, or when someone admitted since is not on the saved register yet.
  const [dirty, setDirty] = useState(() => !existing || roster.some(e => !(e.id in existing.marks)))
  const warning = classDayWarning(batch, date)
  const counts = MARKS.map(m => [m, Object.values(marks).filter(x => x === m).length] as const)
  const setAll = (m: Mark) => {
    setMarks(Object.fromEntries(roster.map(e => [e.id, m])))
    setDirty(true)
  }
  const save = () => {
    store.put('attendance', { id, batchId: batch.id, date, marks, takenBy: staffName(data), note: note.trim() })
    setDirty(false)
    toast(`Attendance saved for ${batch.name}, ${fullDate(date)}`)
  }
  if (date > today) return <div className="card"><Empty emoji="⏳" title="That day hasn't come yet" /></div>
  if (roster.length === 0) return <div className="card"><Empty emoji="🧑‍🎓" title="No students on the register" body="Only students admitted on or before this day are listed." /></div>

  return (
    <div className="card">
      <div className="card-head">
        <h2>{existing ? 'Attendance taken' : 'Mark attendance'}</h2>
        {existing && <span className="pill green">by {existing.takenBy || 'staff'}</span>}
      </div>
      {warning && <div className="notice warn" style={{ margin: '4px 16px 8px' }}>{warning} You can still mark it, e.g. for a make-up class.</div>}
      <div className="row wrap" style={{ padding: '0 16px 8px' }}>
        <button className="btn tonal small" onClick={() => setAll('P')} data-testid="all-present">All present</button>
        <button className="btn outline small" onClick={() => setAll('A')}>All absent</button>
        <div className="grow" />
        <span className="small">{counts.map(([m, n]) => <span key={m} className={`m-${m}`} style={{ marginLeft: 10 }}>{m} {n}</span>)}</span>
      </div>
      <div className="list">
        {roster.map(e => {
          const s = data.idx.students.get(e.studentId)
          return (
            <div key={e.id} className="item" data-testid="att-row">
              <Avatar studentId={e.studentId} name={s?.name ?? '?'} size={34} />
              <div className="grow"><div className="title">{e.rollNo}. {s?.name ?? '?'}</div><div className="subtitle">{s?.regNo}</div></div>
              <div className="mark-toggle" role="radiogroup" aria-label={`Attendance for ${s?.name ?? ''}`}>
                {MARKS.map(m => (
                  <button key={m} title={MARK_LABELS[m]} aria-pressed={marks[e.id] === m} className={`${m} ${marks[e.id] === m ? 'on' : ''}`}
                    onClick={() => { setMarks({ ...marks, [e.id]: m }); setDirty(true) }} data-testid={`mark-${m}`}>{m}</button>
                ))}
              </div>
            </div>
          )
        })}
      </div>
      <div style={{ padding: 16 }} className="stack">
        <input className="input" placeholder="Note for this class (optional): topic covered, make-up class…" value={note} onChange={ev => { setNote(ev.target.value); setDirty(true) }} />
        <button className="btn big" disabled={!dirty} onClick={save} data-testid="att-save">{existing && !dirty ? 'Saved' : 'Save attendance'}</button>
      </div>
    </div>
  )
}

function LowAttendance() {
  const data = useData()
  const navigate = useNavigate()
  const today = useToday()
  const att = attendanceMap(data)
  const running = new Set(runningBatches(data, today).map(b => b.id))
  const low = data.enrollments
    .filter(e => e.status === 'ACTIVE' && running.has(e.batchId))
    .map(e => ({ e, a: att.get(e.id) }))
    .filter(x => x.a && x.a.rate !== null && x.a.rate < 0.75 && x.a.present + x.a.late + x.a.absent >= 3)
    .sort((x, y) => (x.a!.rate ?? 0) - (y.a!.rate ?? 0))
  return (
    <div className="card">
      <div className="card-head"><h2>Low attendance</h2><span className="small muted">below 75%, running batches</span></div>
      {low.length === 0 ? <Empty emoji="👏" title="Everyone is coming regularly" /> : (
        <div className="list">
          {low.slice(0, 30).map(({ e, a }) => {
            const s = data.idx.students.get(e.studentId)
            return (
              <button key={e.id} className="item" onClick={() => navigate(`/students/${e.studentId}`)}>
                <Avatar studentId={e.studentId} name={s?.name ?? '?'} size={34} />
                <div className="grow"><div className="title">{s?.name ?? '?'}</div>
                  <div className="subtitle">{data.idx.batches.get(e.batchId)?.name} · absent {a!.absent} · {s?.phone}</div></div>
                <b className="num" style={{ color: rateColor(a!.rate) }}>{pct(a!.rate)}</b>
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}
