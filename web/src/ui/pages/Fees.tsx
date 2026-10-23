import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { feeRows, whatsappNumber, type FeeRow } from '../../data/actions'
import { buildCsv, download, plainAmount } from '../../data/export'
import { useData } from '../../data/store'
import { METHOD_LABELS, METHODS, type Method } from '../../data/types'
import { currentMonth, daysInMonth, fullDate } from '../../domain/dates'
import { between, sumBy } from '../../domain/reports'
import { Empty, matches, PageHead, Segmented, Tabs, Tile, useMoney, usePermissions, useToday } from '../common'
import { useDialogs } from '../dialogs/host'
import { Icon } from '../icons'

type Tab = 'receipts' | 'dues'

export default function FeesPage() {
  const [params, setParams] = useSearchParams()
  const tab = (params.get('tab') as Tab) || 'dues'
  const can = usePermissions()
  const { open } = useDialogs()
  return (
    <div className="page">
      <PageHead title="Fees">
        {can.manageMoney && <button className="btn" onClick={() => open({ kind: 'collect' })} data-testid="fees-collect"><Icon name="plus" size={18} />Collect fee</button>}
      </PageHead>
      <Tabs options={[['dues', 'Dues'], ['receipts', 'Receipts']]} value={tab} onChange={v => setParams({ tab: v }, { replace: true })} />
      {tab === 'dues' ? <Dues /> : <Receipts />}
    </div>
  )
}

function Receipts() {
  const data = useData()
  const money = useMoney()
  const { open } = useDialogs()
  const month = currentMonth()
  const [from, setFrom] = useState(`${month}-01`)
  const [to, setTo] = useState(`${month}-${String(daysInMonth(month)).padStart(2, '0')}`)
  const [method, setMethod] = useState<Method | ''>('')
  const [q, setQ] = useState('')
  const list = data.payments.filter(p => {
    if (!between(p.date, from, to) || (method && p.method !== method)) return false
    const s = data.idx.students.get(p.studentId)
    return matches(q, p.receiptNo, s?.name, s?.regNo, s?.phone, p.note)
  })
  const total = list.reduce((s, p) => s + p.amount, 0)
  const byMethod = sumBy(list, p => p.method, p => p.amount)
  const exportCsv = () => download(`receipts-${from}-to-${to}.csv`, buildCsv(
    ['Receipt', 'Date', 'Reg no', 'Student', 'Course', 'Batch', 'Method', 'Amount', 'Received by', 'Note'],
    [...list].reverse().map(p => {
      const s = data.idx.students.get(p.studentId)
      const e = data.idx.enrollments.get(p.enrollmentId)
      return [p.receiptNo, p.date, s?.regNo ?? '', s?.name ?? '', e ? data.idx.courses.get(e.courseId)?.name ?? '' : '',
        e ? data.idx.batches.get(e.batchId)?.name ?? '' : '', METHOD_LABELS[p.method], plainAmount(p.amount), p.receivedBy, p.note]
    }),
  ))
  return (
    <div className="stack">
      <div className="toolbar" style={{ marginBottom: 0 }}>
        <div className="search"><Icon name="search" size={18} /><input placeholder="Receipt no., student, phone" value={q} onChange={e => setQ(e.target.value)} /></div>
        <input className="input" type="date" value={from} onChange={e => setFrom(e.target.value)} aria-label="From" />
        <input className="input" type="date" value={to} onChange={e => setTo(e.target.value)} aria-label="To" />
        <select className="input" value={method} onChange={e => setMethod(e.target.value as Method | '')}>
          <option value="">All methods</option>
          {METHODS.map(m => <option key={m} value={m}>{METHOD_LABELS[m]}</option>)}
        </select>
        <button className="btn outline" onClick={exportCsv}><Icon name="download" size={18} />CSV</button>
      </div>
      <div className="tiles">
        <Tile label="Collected" value={money.format(total)} sub={`${list.length} receipt${list.length === 1 ? '' : 's'}`} className="success" />
        {byMethod.slice(0, 3).map(([m, v]) => <Tile key={m} label={METHOD_LABELS[m as Method]} value={money.format(v)} />)}
      </div>
      {list.length === 0 ? <div className="card"><Empty emoji="🧾" title="No receipts in this range" /></div> : (
        <div className="card table-wrap">
          <table className="table">
            <thead><tr><th>Receipt</th><th>Date</th><th>Student</th><th className="hide-mobile">Batch</th><th>Method</th><th className="r">Amount</th></tr></thead>
            <tbody>
              {list.map(p => {
                const s = data.idx.students.get(p.studentId)
                const e = data.idx.enrollments.get(p.enrollmentId)
                return (
                  <tr key={p.id} className="click" onClick={() => open({ kind: 'payment', id: p.id })} data-testid="receipt-row">
                    <td className="num">{p.receiptNo}</td>
                    <td>{fullDate(p.date)}</td>
                    <td><b>{s?.name ?? '?'}</b><div className="small muted">{s?.regNo}</div></td>
                    <td className="hide-mobile">{e ? data.idx.batches.get(e.batchId)?.name : ''}</td>
                    <td>{METHOD_LABELS[p.method]}</td>
                    <td className="r num bold">{money.format(p.amount)}</td>
                  </tr>
                )
              })}
            </tbody>
            <tfoot><tr><td colSpan={5}>Total</td><td className="r num">{money.format(total)}</td></tr></tfoot>
          </table>
        </div>
      )}
    </div>
  )
}

const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? '' : 's'}`

function reminderText(r: FeeRow, amount: string, center: string): string {
  const name = r.student?.name ?? ''
  const course = r.course?.name ?? 'your course'
  return `Dear ${name}, this is a friendly reminder that ${amount} of the fee for ${course} (${r.batch?.name ?? ''}) is due. ` +
    `Please pay at your earliest convenience. Thank you. — ${center}`
}

function Dues() {
  const data = useData()
  const money = useMoney()
  const can = usePermissions()
  const navigate = useNavigate()
  const { open } = useDialogs()
  const today = useToday()
  const [show, setShow] = useState<'overdue' | 'all'>('overdue')
  const [courseId, setCourseId] = useState('')
  const [batchId, setBatchId] = useState('')
  const [q, setQ] = useState('')
  const rows = useMemo(() => feeRows(data, today)
    .filter(r => (show === 'overdue' ? r.fee.overdue > 0 : r.fee.due > 0))
    .filter(r => (!courseId || r.enrollment.courseId === courseId) && (!batchId || r.enrollment.batchId === batchId))
    .filter(r => matches(q, r.student?.name, r.student?.regNo, r.student?.phone))
    .sort((a, b) => b.fee.overdue - a.fee.overdue || b.fee.due - a.fee.due),
  [data, today, show, courseId, batchId, q])
  const totalDue = rows.reduce((s, r) => s + r.fee.due, 0)
  const totalOverdue = rows.reduce((s, r) => s + r.fee.overdue, 0)
  const center = data.center?.name ?? ''
  const exportCsv = () => download(`dues-${today}.csv`, buildCsv(
    ['Reg no', 'Student', 'Phone', 'Course', 'Batch', 'Payable', 'Paid', 'Due', 'Overdue', 'Next due date'],
    rows.map(r => [r.student?.regNo ?? '', r.student?.name ?? '', r.student?.phone ?? '', r.course?.name ?? '', r.batch?.name ?? '',
      plainAmount(r.fee.payable), plainAmount(r.fee.paid), plainAmount(r.fee.due), plainAmount(r.fee.overdue), r.fee.nextDue?.date ?? '']),
  ))
  return (
    <div className="stack">
      <div className="toolbar" style={{ marginBottom: 0 }}>
        <Segmented options={[['overdue', 'Overdue now'], ['all', 'All dues']]} value={show} onChange={setShow} />
        <div className="search"><Icon name="search" size={18} /><input placeholder="Student, phone" value={q} onChange={e => setQ(e.target.value)} /></div>
        <select className="input" value={courseId} onChange={e => { setCourseId(e.target.value); setBatchId('') }}>
          <option value="">All courses</option>
          {data.courses.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
        <select className="input" value={batchId} onChange={e => setBatchId(e.target.value)}>
          <option value="">All batches</option>
          {data.batches.filter(b => !courseId || b.courseId === courseId).map(b => <option key={b.id} value={b.id}>{b.name}</option>)}
        </select>
        <button className="btn outline" onClick={exportCsv}><Icon name="download" size={18} />CSV</button>
        <button className="btn outline" onClick={() => navigate(`/print/dues?show=${show}&course=${courseId}&batch=${batchId}`)}><Icon name="printer" size={18} />Print</button>
      </div>
      <div className="tiles">
        <Tile label="Overdue now" value={money.format(totalOverdue)} className="danger" sub={plural(rows.filter(r => r.fee.overdue > 0).length, 'admission')} />
        <Tile label="Total still due" value={money.format(totalDue)} sub="including installments not due yet" />
      </div>
      {rows.length === 0 ? <div className="card"><Empty emoji="🎉" title={show === 'overdue' ? 'Nothing overdue' : 'No dues'} body="Everyone is paid up." /></div> : (
        <div className="card table-wrap">
          <table className="table">
            <thead><tr><th>Student</th><th className="hide-mobile">Batch</th><th className="r">Paid</th><th className="r">Due</th><th className="r">Overdue</th><th>Next due</th><th /></tr></thead>
            <tbody>
              {rows.map(r => {
                const amount = money.format(r.fee.overdue || r.fee.due)
                const phone = r.student?.phone || r.student?.guardianPhone || ''
                const text = reminderText(r, amount, center)
                return (
                  <tr key={r.enrollment.id} data-testid="due-row">
                    <td><button className="btn text" style={{ padding: 0 }} onClick={() => navigate(`/students/${r.enrollment.studentId}`)}><b>{r.student?.name ?? '?'}</b></button>
                      <div className="small muted">{r.student?.regNo}{phone ? ` · ${phone}` : ''}</div></td>
                    <td className="hide-mobile">{r.batch?.name}<div className="small muted">{r.course?.name}</div></td>
                    <td className="r num">{money.format(r.fee.paid)}<div className="small muted">of {money.format(r.fee.payable)}</div></td>
                    <td className="r num bold">{money.format(r.fee.due)}</td>
                    <td className={`r num ${r.fee.overdue > 0 ? 'danger bold' : 'muted'}`}>{r.fee.overdue > 0 ? money.format(r.fee.overdue) : '—'}</td>
                    <td className="small">{r.fee.nextDue ? <>{fullDate(r.fee.nextDue.date)}<div className="muted">{money.format(r.fee.nextDue.amount)}</div></> : '—'}</td>
                    <td className="r" style={{ whiteSpace: 'nowrap' }}>
                      {phone && <a className="icon-btn" title="WhatsApp reminder" target="_blank" rel="noreferrer" href={`https://wa.me/${whatsappNumber(phone)}?text=${encodeURIComponent(text)}`}><Icon name="message" size={18} /></a>}
                      {phone && <a className="icon-btn" title="SMS reminder" href={`sms:${phone}?body=${encodeURIComponent(text)}`}><Icon name="phone" size={18} /></a>}
                      {can.manageMoney && <button className="btn tonal small" onClick={() => open({ kind: 'collect', enrollmentId: r.enrollment.id })}>Collect</button>}
                    </td>
                  </tr>
                )
              })}
            </tbody>
            <tfoot><tr><td>Total</td><td className="hide-mobile" /><td /><td className="r num">{money.format(totalDue)}</td><td className="r num danger">{money.format(totalOverdue)}</td><td colSpan={2} /></tr></tfoot>
          </table>
        </div>
      )}
    </div>
  )
}
