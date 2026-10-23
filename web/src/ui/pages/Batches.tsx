import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { seatsTaken } from '../../data/actions'
import { useData } from '../../data/store'
import { batchStatus, daysLabel, STATUS_LABELS, type BatchStatus } from '../../domain/batches'
import { fullDate } from '../../domain/dates'
import { BatchStatusPill, Empty, matches, PageHead, usePermissions, useToday } from '../common'
import { useDialogs } from '../dialogs/host'
import { Icon } from '../icons'

type Filter = BatchStatus | 'ALL'

export default function BatchesPage() {
  const data = useData()
  const can = usePermissions()
  const navigate = useNavigate()
  const { open } = useDialogs()
  const today = useToday()
  const [params, setParams] = useSearchParams()
  const courseId = params.get('course') ?? ''
  const [filter, setFilter] = useState<Filter>('RUNNING')
  const [q, setQ] = useState('')
  const mine = data.me?.instructorId

  const counts = new Map<Filter, number>()
  for (const b of data.batches) {
    if (courseId && b.courseId !== courseId) continue
    const s = batchStatus(b, today)
    counts.set(s, (counts.get(s) ?? 0) + 1)
    counts.set('ALL', (counts.get('ALL') ?? 0) + 1)
  }
  const list = data.batches.filter(b => (filter === 'ALL' || batchStatus(b, today) === filter) && (!courseId || b.courseId === courseId) &&
    matches(q, b.name, data.idx.courses.get(b.courseId)?.name, b.room))
    .sort((a, b) => Number(b.instructorId === mine && !!mine) - Number(a.instructorId === mine && !!mine))

  return (
    <div className="page">
      <PageHead title="Batches" sub={courseId ? data.idx.courses.get(courseId)?.name : undefined}>
        {can.manageSetup && <button className="btn" onClick={() => open({ kind: 'batch', courseId: courseId || undefined })} data-testid="add-batch"><Icon name="plus" size={18} />Batch</button>}
      </PageHead>
      <div className="toolbar">
        <div className="chips">
          {(['RUNNING', 'UPCOMING', 'COMPLETED', 'CANCELLED', 'ALL'] as Filter[]).map(f => (
            <button key={f} className={`chip ${filter === f ? 'on' : ''}`} onClick={() => setFilter(f)}>
              {f === 'ALL' ? 'All' : STATUS_LABELS[f]} {counts.get(f) ? <span className="muted">· {counts.get(f)}</span> : null}
            </button>
          ))}
        </div>
        <div className="search"><Icon name="search" size={18} /><input placeholder="Search batches" value={q} onChange={e => setQ(e.target.value)} /></div>
        <select className="input" value={courseId} onChange={e => setParams(e.target.value ? { course: e.target.value } : {})}>
          <option value="">All courses</option>
          {data.courses.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
      </div>
      {list.length === 0 ? (
        <div className="card"><Empty emoji="🗓️" title={data.batches.length === 0 ? 'No batches yet' : 'No batch here'}
          body={data.batches.length === 0 ? 'A batch is a group of students taking a course together: its class days, time, room and instructor.' : 'Try another filter.'}>
          {data.batches.length === 0 && can.manageSetup && <button className="btn" onClick={() => open({ kind: 'batch' })}>Add a batch</button>}
        </Empty></div>
      ) : (
        <div className="grid-3">
          {list.map(b => {
            const c = data.idx.courses.get(b.courseId)
            const t = b.instructorId ? data.idx.instructors.get(b.instructorId) : undefined
            const taken = seatsTaken(data, b.id)
            return (
              <button key={b.id} className="card pad" style={{ textAlign: 'left' }} onClick={() => navigate(`/batches/${b.id}`)} data-testid="batch-card">
                <div className="row"><b className="grow ellipsis">{b.name}</b><BatchStatusPill batch={b} date={today} /></div>
                <div className="muted small ellipsis">{c?.name ?? 'Course'}</div>
                <div className="small" style={{ marginTop: 10 }}>📅 {fullDate(b.startDate)}{b.endDate ? ` – ${fullDate(b.endDate)}` : ''}</div>
                <div className="small">🕑 {[daysLabel(b.days), b.time].filter(Boolean).join(' · ') || '—'}</div>
                <div className="small">👩‍🏫 {t?.name ?? 'No instructor'}{b.room ? ` · ${b.room}` : ''}</div>
                <div className="small bold" style={{ marginTop: 8 }}>{taken}{b.capacity ? ` / ${b.capacity}` : ''} students</div>
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}
