import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { activeStudentIds, feeRows, groupBy } from '../../data/actions'
import { buildCsv, download, plainAmount } from '../../data/export'
import { useData } from '../../data/store'
import { Avatar, Empty, matches, PageHead, Segmented, useMoney, usePermissions, useToday } from '../common'
import { useDialogs } from '../dialogs/host'
import { Icon } from '../icons'

type Show = 'active' | 'all' | 'archived'

export default function StudentsPage() {
  const data = useData()
  const money = useMoney()
  const can = usePermissions()
  const navigate = useNavigate()
  const { open } = useDialogs()
  const today = useToday()
  const [q, setQ] = useState('')
  const [show, setShow] = useState<Show>('active')
  const [courseId, setCourseId] = useState('')
  const [batchId, setBatchId] = useState('')

  const active = activeStudentIds(data)
  const byStudent = useMemo(() => groupBy(data.enrollments, e => e.studentId), [data.enrollments])
  const dueByStudent = useMemo(() => {
    const m = new Map<string, number>()
    if (can.seeMoney) for (const r of feeRows(data, today)) m.set(r.enrollment.studentId, (m.get(r.enrollment.studentId) ?? 0) + Math.max(r.fee.due, 0))
    return m
  }, [data, today, can.seeMoney])

  const list = data.students.filter(s => {
    if (show === 'active' && (s.archived || !active.has(s.id))) return false
    if (show === 'all' && s.archived) return false
    if (show === 'archived' && !s.archived) return false
    const es = byStudent.get(s.id) ?? []
    if (courseId && !es.some(e => e.courseId === courseId)) return false
    if (batchId && !es.some(e => e.batchId === batchId)) return false
    return matches(q, s.name, s.regNo, s.phone, s.guardianPhone, s.fatherName)
  })

  const exportCsv = () => {
    const rows = list.map(s => {
      const es = byStudent.get(s.id) ?? []
      return [
        s.regNo, s.name, s.fatherName, s.motherName, s.gender, s.dob, s.phone, s.guardianPhone, s.email, s.address, s.education,
        s.institution, s.joinDate, es.map(e => data.idx.batches.get(e.batchId)?.name ?? '').join('; '),
        ...(can.seeMoney ? [plainAmount(dueByStudent.get(s.id) ?? 0)] : []),
      ]
    })
    download(`students-${today}.csv`, buildCsv([
      'Reg no', 'Name', 'Father', 'Mother', 'Gender', 'Date of birth', 'Phone', 'Guardian phone', 'Email', 'Address', 'Education',
      'Institution', 'Joined', 'Batches', ...(can.seeMoney ? ['Due'] : []),
    ], rows))
  }

  return (
    <div className="page">
      <PageHead title="Students" sub={`${list.length} shown · ${active.size} active`}>
        <button className="btn outline hide-mobile" onClick={exportCsv}><Icon name="download" size={18} />CSV</button>
        {can.manageStudents && <button className="btn tonal" onClick={() => open({ kind: 'student', onSaved: s => navigate(`/students/${s.id}`) })}><Icon name="plus" size={18} />Student</button>}
        {can.manageStudents && <button className="btn" onClick={() => open({ kind: 'admission' })} data-testid="students-admit"><Icon name="plus" size={18} />Admission</button>}
      </PageHead>
      <div className="toolbar">
        <div className="search"><Icon name="search" size={18} /><input placeholder="Search name, reg. no., phone" value={q} onChange={e => setQ(e.target.value)} data-testid="students-search" /></div>
        <Segmented options={[['active', 'Active'], ['all', 'All'], ['archived', 'Archived']]} value={show} onChange={setShow} />
        <select className="input" value={courseId} onChange={e => { setCourseId(e.target.value); setBatchId('') }}>
          <option value="">All courses</option>
          {data.courses.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
        <select className="input" value={batchId} onChange={e => setBatchId(e.target.value)}>
          <option value="">All batches</option>
          {data.batches.filter(b => !courseId || b.courseId === courseId).map(b => <option key={b.id} value={b.id}>{b.name}</option>)}
        </select>
      </div>
      {list.length === 0 ? (
        <div className="card"><Empty emoji="🧑‍🎓" title={data.students.length === 0 ? 'No students yet' : 'No student matches'}
          body={data.students.length === 0 ? 'Admit your first student: pick a batch, set the fee and payment plan, and print the receipt.' : 'Try another search or filter.'}>
          {data.students.length === 0 && can.manageStudents && <button className="btn" onClick={() => open({ kind: 'admission' })}>New admission</button>}
        </Empty></div>
      ) : (
        <div className="card list">
          {list.slice(0, 500).map(s => {
            const es = byStudent.get(s.id) ?? []
            const due = dueByStudent.get(s.id) ?? 0
            return (
              <button key={s.id} className="item" onClick={() => navigate(`/students/${s.id}`)} data-testid="student-row">
                <Avatar studentId={s.id} name={s.name} />
                <div className="grow">
                  <div className="title">{s.name} {s.archived && <span className="pill">Archived</span>}</div>
                  <div className="subtitle">{s.regNo}{s.phone ? ` · ${s.phone}` : ''}{es.length ? ` · ${es.map(e => data.idx.batches.get(e.batchId)?.name ?? '').join(', ')}` : ''}</div>
                </div>
                {can.seeMoney && es.length > 0 && <div className={`end num bold ${due > 0 ? 'danger' : 'muted small'}`}>{due > 0 ? money.format(due) : 'Paid'}</div>}
              </button>
            )
          })}
          {list.length > 500 && <div className="item muted small">Showing the first 500. Search to narrow it down.</div>}
        </div>
      )}
    </div>
  )
}
