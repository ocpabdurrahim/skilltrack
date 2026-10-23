import { useMemo, useState } from 'react'
import { feeRows, groupBy } from '../../data/actions'
import { buildCsv, download, plainAmount } from '../../data/export'
import { useData } from '../../data/store'
import { METHOD_LABELS, type Method } from '../../data/types'
import { currentMonth, monthLabel, shortMonthLabel, type YearMonth } from '../../domain/dates'
import { between, lastMonths, monthSummary, sumBy } from '../../domain/reports'
import { GroupedBars, HBars } from '../charts'
import { Empty, MonthSelector, PageHead, Tabs, Tile, useMoney, useToday } from '../common'
import { Icon } from '../icons'

type Tab = 'profit' | 'collection' | 'dues' | 'admissions'

export default function ReportsPage() {
  const [tab, setTab] = useState<Tab>('profit')
  return (
    <div className="page">
      <PageHead title="Reports" />
      <Tabs options={[['profit', 'Income & profit'], ['collection', 'Collection'], ['dues', 'Dues by course'], ['admissions', 'Admissions']]} value={tab} onChange={setTab} />
      {tab === 'profit' && <Profit />}
      {tab === 'collection' && <Collection />}
      {tab === 'dues' && <DuesByCourse />}
      {tab === 'admissions' && <Admissions />}
    </div>
  )
}

function Profit() {
  const data = useData()
  const money = useMoney()
  const [month, setMonth] = useState<YearMonth>(currentMonth())
  const months = lastMonths(month, 12)
  const summaries = useMemo(() => months.map(m => monthSummary(m, data.payments, data.expenses, data.instructorPayments)),
    [months.join(), data.payments, data.expenses, data.instructorPayments]) // eslint-disable-line react-hooks/exhaustive-deps
  const s = summaries[summaries.length - 1]
  const income = data.payments.filter(p => p.date.startsWith(month + '-'))
  const byCourse = sumBy(income, p => data.idx.enrollments.get(p.enrollmentId)?.courseId ?? '', p => p.amount)
  const byHead = sumBy(data.expenses.filter(x => x.date.startsWith(month + '-')), x => x.headId, x => x.amount)
  const year = summaries.reduce((a, x) => ({ income: a.income + x.income, spending: a.spending + x.spending }), { income: 0, spending: 0 })
  const exportCsv = () => download(`income-expense-${months[0]}-to-${month}.csv`, buildCsv(
    ['Month', 'Income', 'Expenses', 'Instructor pay', 'Profit'],
    summaries.map(x => [x.month, plainAmount(x.income), plainAmount(x.expenses), plainAmount(x.instructorPay), plainAmount(x.profit)])))
  return (
    <div className="stack">
      <div className="row wrap"><MonthSelector month={month} onChange={setMonth} /><div className="grow" />
        <button className="btn outline small" onClick={exportCsv}><Icon name="download" size={16} />CSV</button></div>
      <div className="tiles">
        <Tile label={`Income · ${monthLabel(month)}`} value={money.format(s.income)} className="success" />
        <Tile label="Expenses" value={money.format(s.expenses)} />
        <Tile label="Instructor pay" value={money.format(s.instructorPay)} />
        <Tile label="Profit" value={money.format(s.profit)} className={s.profit < 0 ? 'danger' : 'success'} />
      </div>
      <div className="card pad">
        <div className="row wrap" style={{ marginBottom: 10 }}>
          <h2 className="grow" style={{ margin: 0 }}>Last 12 months</h2>
          <span className="legend"><i style={{ background: 'var(--success)' }} />Income</span>
          <span className="legend"><i style={{ background: 'var(--danger)' }} />Spending</span>
          <span className="legend"><i style={{ background: 'var(--primary)' }} />Profit</span>
        </div>
        <GroupedBars highlight={11} colors={['var(--success)', 'var(--danger)', 'var(--primary)']}
          groups={summaries.map(x => ({
            label: shortMonthLabel(x.month), values: [x.income, x.spending, Math.max(x.profit, 0)],
            title: `${monthLabel(x.month)}: income ${money.format(x.income)}, spending ${money.format(x.spending)}, profit ${money.format(x.profit)}`,
          }))} />
        <div className="small muted" style={{ marginTop: 10 }}>12 months: income {money.format(year.income)} · spending {money.format(year.spending)} · profit {money.format(year.income - year.spending)}</div>
      </div>
      <div className="grid-2">
        <div className="card pad">
          <h2>Income by course</h2>
          {byCourse.length === 0 ? <div className="muted small">No income this month.</div> :
            <HBars color="var(--success)" rows={byCourse.map(([id, v]) => ({ label: data.idx.courses.get(id)?.name ?? 'Other', value: v, text: money.format(v) }))} />}
        </div>
        <div className="card pad">
          <h2>Spending by head</h2>
          {byHead.length === 0 && s.instructorPay === 0 ? <div className="muted small">No spending this month.</div> :
            <HBars color="var(--danger)" rows={[
              ...byHead.map(([id, v]) => ({ label: `${data.idx.expenseHeads.get(id)?.emoji ?? ''} ${data.idx.expenseHeads.get(id)?.name ?? 'Other'}`, value: v, text: money.format(v) })),
              ...(s.instructorPay ? [{ label: '👩‍🏫 Instructor pay', value: s.instructorPay, text: money.format(s.instructorPay) }] : []),
            ].sort((a, b) => b.value - a.value)} />}
        </div>
      </div>
    </div>
  )
}

function Collection() {
  const data = useData()
  const money = useMoney()
  const month = currentMonth()
  const [from, setFrom] = useState(`${month}-01`)
  const [to, setTo] = useState('')
  const list = data.payments.filter(p => between(p.date, from, to))
  const total = list.reduce((s, p) => s + p.amount, 0)
  const byMethod = sumBy(list, p => p.method, p => p.amount)
  const byDay = sumBy(list, p => p.date, p => p.amount).sort((a, b) => b[0].localeCompare(a[0]))
  const byStaff = sumBy(list, p => p.receivedBy || '—', p => p.amount)
  return (
    <div className="stack">
      <div className="toolbar" style={{ marginBottom: 0 }}>
        <span className="small muted">From</span><input className="input" type="date" value={from} onChange={e => setFrom(e.target.value)} />
        <span className="small muted">to</span><input className="input" type="date" value={to} onChange={e => setTo(e.target.value)} />
      </div>
      <div className="tiles"><Tile label="Collected" value={money.format(total)} sub={`${list.length} receipts`} className="success" /></div>
      <div className="grid-2">
        <div className="card pad"><h2>By method</h2>
          {byMethod.length === 0 ? <div className="muted small">Nothing in this range.</div> :
            <HBars rows={byMethod.map(([m, v]) => ({ label: METHOD_LABELS[m as Method], value: v, text: money.format(v) }))} />}
          <h2 style={{ marginTop: 18 }}>By staff</h2>
          <HBars color="var(--info)" rows={byStaff.map(([m, v]) => ({ label: m, value: v, text: money.format(v) }))} />
        </div>
        <div className="card table-wrap">
          <table className="table">
            <thead><tr><th>Day</th><th className="r">Receipts</th><th className="r">Amount</th></tr></thead>
            <tbody>{byDay.map(([d, v]) => <tr key={d}><td>{d}</td><td className="r num">{list.filter(p => p.date === d).length}</td><td className="r num bold">{money.format(v)}</td></tr>)}</tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

function DuesByCourse() {
  const data = useData()
  const money = useMoney()
  const today = useToday()
  const rows = feeRows(data, today)
  const byCourse = groupBy(rows, r => r.enrollment.courseId)
  const lines = [...byCourse.entries()].map(([id, rs]) => ({
    id, name: data.idx.courses.get(id)?.name ?? 'Other', payable: rs.reduce((s, r) => s + r.fee.payable, 0),
    paid: rs.reduce((s, r) => s + r.fee.paid, 0), due: rs.reduce((s, r) => s + Math.max(r.fee.due, 0), 0),
    overdue: rs.reduce((s, r) => s + r.fee.overdue, 0), students: rs.length,
  })).sort((a, b) => b.due - a.due)
  if (lines.length === 0) return <div className="card"><Empty emoji="📊" title="No admissions yet" /></div>
  const t = lines.reduce((a, l) => ({ payable: a.payable + l.payable, paid: a.paid + l.paid, due: a.due + l.due, overdue: a.overdue + l.overdue }), { payable: 0, paid: 0, due: 0, overdue: 0 })
  return (
    <div className="card table-wrap">
      <table className="table">
        <thead><tr><th>Course</th><th className="r">Admissions</th><th className="r">Payable</th><th className="r">Paid</th><th className="r">Due</th><th className="r">Overdue</th><th className="r">Collected</th></tr></thead>
        <tbody>{lines.map(l => (
          <tr key={l.id}><td><b>{l.name}</b></td><td className="r num">{l.students}</td><td className="r num">{money.format(l.payable)}</td><td className="r num">{money.format(l.paid)}</td>
            <td className="r num bold">{money.format(l.due)}</td><td className="r num danger">{money.format(l.overdue)}</td>
            <td className="r num">{l.payable ? Math.round((l.paid / l.payable) * 100) : 100}%</td></tr>
        ))}</tbody>
        <tfoot><tr><td>Total</td><td /><td className="r num">{money.format(t.payable)}</td><td className="r num">{money.format(t.paid)}</td><td className="r num">{money.format(t.due)}</td><td className="r num">{money.format(t.overdue)}</td><td /></tr></tfoot>
      </table>
    </div>
  )
}

function Admissions() {
  const data = useData()
  const month = currentMonth()
  const months = lastMonths(month, 12)
  const counts = months.map(m => data.enrollments.filter(e => e.date.startsWith(m + '-')).length)
  const enquiries = months.map(m => data.enquiries.filter(q => q.date.startsWith(m + '-')).length)
  const converted = data.enquiries.filter(q => q.status === 'CONVERTED').length
  const lost = data.enquiries.filter(q => q.status === 'LOST').length
  const bySource = sumBy(data.enquiries, q => q.source, () => 1)
  return (
    <div className="stack">
      <div className="tiles">
        <Tile label={`Admissions · ${monthLabel(month)}`} value={String(counts[11])} />
        <Tile label="Admissions · 12 months" value={String(counts.reduce((a, b) => a + b, 0))} />
        <Tile label="Enquiry conversion" value={converted + lost ? `${Math.round((converted / (converted + lost)) * 100)}%` : '—'} sub={`${converted} admitted, ${lost} lost`} />
      </div>
      <div className="card pad">
        <div className="row wrap" style={{ marginBottom: 10 }}><h2 className="grow" style={{ margin: 0 }}>Per month</h2>
          <span className="legend"><i style={{ background: 'var(--primary)' }} />Admissions</span><span className="legend"><i style={{ background: 'var(--warning)' }} />Enquiries</span></div>
        <GroupedBars highlight={11} colors={['var(--primary)', 'var(--warning)']}
          groups={months.map((m, i) => ({ label: shortMonthLabel(m), values: [counts[i], enquiries[i]], title: `${monthLabel(m)}: ${counts[i]} admissions, ${enquiries[i]} enquiries` }))} />
      </div>
      <div className="card pad"><h2>Where enquiries come from</h2>
        {bySource.length === 0 ? <div className="muted small">No enquiries yet.</div> :
          <HBars color="var(--warning)" rows={bySource.map(([k, v]) => ({ label: k.replace('_', '-').toLowerCase().replace(/^./, c => c.toUpperCase()), value: v, text: String(v) }))} />}
      </div>
    </div>
  )
}
