import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { resultsMap } from '../../data/actions'
import { useData } from '../../data/store'
import { fullDate } from '../../domain/dates'
import { Empty, matches, PageHead, useToday } from '../common'
import { useDialogs } from '../dialogs/host'
import { Icon } from '../icons'

export default function CertificatesPage() {
  const data = useData()
  const navigate = useNavigate()
  const { open } = useDialogs()
  const today = useToday()
  const [q, setQ] = useState('')
  const results = resultsMap(data, today)
  const issued = new Set(data.certificates.map(c => c.enrollmentId))
  // Ready for a certificate: completed, or passed every exam, and none issued yet.
  const ready = data.enrollments.filter(e => !issued.has(e.id) && e.status !== 'DROPPED' &&
    (e.status === 'COMPLETED' || (() => { const r = results.get(e.id); return !!r && r.complete && !r.failed })()))
  const list = data.certificates.filter(c => {
    const s = data.idx.students.get(c.studentId)
    return matches(q, c.certNo, s?.name, s?.regNo)
  })
  return (
    <div className="page">
      <PageHead title="Certificates" sub={`${data.certificates.length} issued`}>
        <button className="btn outline" onClick={() => navigate('/print/idcards')}><Icon name="card" size={18} />ID cards</button>
        <button className="btn" onClick={() => open({ kind: 'certificate' })} data-testid="issue-cert"><Icon name="plus" size={18} />Issue</button>
      </PageHead>
      <div className="grid-2">
        <div className="stack">
          <div className="search"><Icon name="search" size={18} /><input placeholder="Certificate no., student" value={q} onChange={e => setQ(e.target.value)} /></div>
          {list.length === 0 ? <div className="card"><Empty emoji="🎓" title="No certificates issued yet" body="Issue one when a student completes their course; it gets a number and prints on A4." /></div> : (
            <div className="card list">
              {list.map(c => {
                const s = data.idx.students.get(c.studentId)
                const e = data.idx.enrollments.get(c.enrollmentId)
                return (
                  <button key={c.id} className="item" onClick={() => navigate(`/print/certificate/${c.id}`)} data-testid="cert-row">
                    <span style={{ fontSize: 22 }}>🎓</span>
                    <div className="grow">
                      <div className="title">{s?.name ?? '?'} {c.grade && <span className="pill indigo">{c.grade}</span>}</div>
                      <div className="subtitle">{c.certNo} · {e ? data.idx.courses.get(e.courseId)?.name : ''} · {fullDate(c.issueDate)}</div>
                    </div>
                    <Icon name="printer" size={18} />
                  </button>
                )
              })}
            </div>
          )}
        </div>
        <div className="card">
          <div className="card-head"><h2>Ready for a certificate</h2><span className="small muted">{ready.length}</span></div>
          {ready.length === 0 ? <Empty emoji="⏳" title="Nobody yet" body="Students show here when their admission is marked completed, or they have passed every exam." /> : (
            <div className="list">
              {ready.map(e => {
                const s = data.idx.students.get(e.studentId)
                const r = results.get(e.id)
                return (
                  <div key={e.id} className="item">
                    <div className="grow"><div className="title">{s?.name ?? '?'}</div>
                      <div className="subtitle">{data.idx.batches.get(e.batchId)?.name}{r ? ` · ${r.grade}` : ''}</div></div>
                    <button className="btn tonal small" onClick={() => open({ kind: 'certificate', enrollmentId: e.id })}>Issue</button>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
