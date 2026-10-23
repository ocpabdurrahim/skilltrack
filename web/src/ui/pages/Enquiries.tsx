import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { whatsappNumber } from '../../data/actions'
import { store, useData } from '../../data/store'
import { SOURCE_LABELS, type EnquiryStatus } from '../../data/types'
import { addDays, fullDate, relativeDay } from '../../domain/dates'
import { Empty, matches, PageHead, Tile, useToday } from '../common'
import { useDialogs } from '../dialogs/host'
import { Icon } from '../icons'

export default function EnquiriesPage() {
  const data = useData()
  const navigate = useNavigate()
  const { open } = useDialogs()
  const today = useToday()
  const [status, setStatus] = useState<EnquiryStatus>('OPEN')
  const [q, setQ] = useState('')
  const counts = new Map<EnquiryStatus, number>()
  for (const x of data.enquiries) counts.set(x.status, (counts.get(x.status) ?? 0) + 1)
  const list = data.enquiries
    .filter(x => x.status === status && matches(q, x.name, x.phone, x.note, x.courseId ? data.idx.courses.get(x.courseId)?.name : ''))
    .sort((a, b) => status === 'OPEN'
      ? (a.followUp || '9999').localeCompare(b.followUp || '9999') || b.date.localeCompare(a.date)
      : b.date.localeCompare(a.date))
  const due = data.enquiries.filter(x => x.status === 'OPEN' && x.followUp && x.followUp <= today).length
  const decided = (counts.get('CONVERTED') ?? 0) + (counts.get('LOST') ?? 0)
  const rate = decided ? Math.round(((counts.get('CONVERTED') ?? 0) / decided) * 100) : null

  return (
    <div className="page">
      <PageHead title="Enquiries" sub="Walk-ins, calls and messages from people interested in a course">
        <button className="btn" onClick={() => open({ kind: 'enquiry' })} data-testid="add-enquiry"><Icon name="plus" size={18} />Enquiry</button>
      </PageHead>
      <div className="tiles" style={{ marginBottom: 14 }}>
        <Tile label="Open" value={String(counts.get('OPEN') ?? 0)} />
        <Tile label="To call today" value={String(due)} className={due ? 'warning' : ''} />
        <Tile label="Admitted" value={String(counts.get('CONVERTED') ?? 0)} className="success" />
        <Tile label="Conversion" value={rate === null ? '—' : `${rate}%`} sub="admitted ÷ (admitted + lost)" />
      </div>
      <div className="toolbar">
        <div className="chips">
          {([['OPEN', 'Open'], ['CONVERTED', 'Admitted'], ['LOST', 'Lost']] as [EnquiryStatus, string][]).map(([s, l]) => (
            <button key={s} className={`chip ${status === s ? 'on' : ''}`} onClick={() => setStatus(s)}>{l} <span className="muted">· {counts.get(s) ?? 0}</span></button>
          ))}
        </div>
        <div className="search"><Icon name="search" size={18} /><input placeholder="Name, phone, note" value={q} onChange={e => setQ(e.target.value)} /></div>
      </div>
      {list.length === 0 ? <div className="card"><Empty emoji="📞" title="Nothing here" body="Record every enquiry with a follow-up date, so nobody is forgotten." /></div> : (
        <div className="card list">
          {list.map(x => {
            const course = x.courseId ? data.idx.courses.get(x.courseId) : undefined
            const late = x.status === 'OPEN' && x.followUp && x.followUp <= today
            return (
              <div key={x.id} className="item" style={{ flexWrap: 'wrap' }} data-testid="enquiry-row">
                <button className="grow" style={{ border: 0, background: 'transparent', textAlign: 'left', padding: 0, minWidth: 200 }} onClick={() => open({ kind: 'enquiry', id: x.id })}>
                  <div className="title">{x.name} <span className="muted small">· {SOURCE_LABELS[x.source]}</span></div>
                  <div className="subtitle">{x.phone}{course ? ` · ${course.name}` : ''} · {fullDate(x.date)}{x.note ? ` · ${x.note}` : ''}</div>
                </button>
                {x.status === 'OPEN' && x.followUp && <span className={`pill ${late ? (x.followUp < today ? 'red' : 'amber') : 'blue'}`}>📞 {relativeDay(x.followUp, today)}</span>}
                {x.status === 'CONVERTED' && x.studentId && <button className="btn text small" onClick={() => navigate(`/students/${x.studentId}`)}>Student</button>}
                {x.status === 'OPEN' && (
                  <div className="row" style={{ gap: 4 }}>
                    {x.phone && <a className="icon-btn" title="Call" href={`tel:${x.phone}`}><Icon name="phone" size={18} /></a>}
                    {x.phone && <a className="icon-btn" title="WhatsApp" target="_blank" rel="noreferrer" href={`https://wa.me/${whatsappNumber(x.phone)}`}><Icon name="message" size={18} /></a>}
                    <button className="btn outline small" title="Called, follow up again in 3 days"
                      onClick={() => store.put('enquiries', { ...x, followUp: addDays(today, 3) })}>+3 days</button>
                    <button className="btn small" onClick={() => open({ kind: 'admission', enquiryId: x.id })} data-testid="convert">Admit</button>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
