import { useState } from 'react'
import { buildCsv, download, plainAmount } from '../../data/export'
import { useData } from '../../data/store'
import { METHOD_LABELS } from '../../data/types'
import { currentMonth, fullDate, type YearMonth } from '../../domain/dates'
import { sumBy } from '../../domain/reports'
import { Donut, PALETTE } from '../charts'
import { Empty, MonthSelector, PageHead, useMoney } from '../common'
import { useDialogs } from '../dialogs/host'
import { Icon } from '../icons'

export default function ExpensesPage() {
  const data = useData()
  const money = useMoney()
  const { open } = useDialogs()
  const [month, setMonth] = useState<YearMonth>(currentMonth())
  const [headId, setHeadId] = useState('')
  const inMonth = data.expenses.filter(x => x.date.startsWith(month + '-'))
  const list = inMonth.filter(x => !headId || x.headId === headId)
  const pay = data.instructorPayments.filter(p => p.date.startsWith(month + '-'))
  const payTotal = pay.reduce((s, p) => s + p.amount, 0)
  const byHead = sumBy(inMonth, x => x.headId, x => x.amount)
  const total = inMonth.reduce((s, x) => s + x.amount, 0)
  const head = (id: string) => data.idx.expenseHeads.get(id)
  const exportCsv = () => download(`expenses-${month}.csv`, buildCsv(['Date', 'Head', 'Amount', 'Method', 'Paid to', 'Note'],
    [...list].reverse().map(x => [x.date, head(x.headId)?.name ?? '', plainAmount(x.amount), METHOD_LABELS[x.method], x.paidTo, x.note])))

  return (
    <div className="page">
      <PageHead title="Expenses">
        <button className="btn outline hide-mobile" onClick={exportCsv}><Icon name="download" size={18} />CSV</button>
        <button className="btn" onClick={() => open({ kind: 'expense' })} data-testid="add-expense"><Icon name="plus" size={18} />Expense</button>
      </PageHead>
      <MonthSelector month={month} onChange={setMonth} />
      <div className="grid-2" style={{ marginTop: 14 }}>
        <div className="stack">
          <div className="toolbar" style={{ marginBottom: 0 }}>
            <div className="chips">
              <button className={`chip ${headId === '' ? 'on' : ''}`} onClick={() => setHeadId('')}>All</button>
              {byHead.map(([id]) => <button key={id} className={`chip ${headId === id ? 'on' : ''}`} onClick={() => setHeadId(id)}>{head(id)?.emoji} {head(id)?.name ?? '?'}</button>)}
            </div>
          </div>
          {list.length === 0 ? <div className="card"><Empty emoji="💸" title="No expenses this month" body="Record rent, bills, salaries and purchases to see the center's real profit." /></div> : (
            <div className="card list">
              {list.map(x => (
                <button key={x.id} className="item" onClick={() => open({ kind: 'expense', id: x.id })} data-testid="expense-row">
                  <span className="avatar" style={{ width: 38, height: 38, fontSize: 18 }}>{head(x.headId)?.emoji ?? '📦'}</span>
                  <div className="grow">
                    <div className="title">{head(x.headId)?.name ?? 'Expense'}{x.paidTo ? ` · ${x.paidTo}` : ''}</div>
                    <div className="subtitle">{fullDate(x.date)} · {METHOD_LABELS[x.method]}{x.note ? ` · ${x.note}` : ''}</div>
                  </div>
                  <div className="end bold num danger">{money.format(x.amount)}</div>
                </button>
              ))}
            </div>
          )}
        </div>
        <div className="stack">
          <div className="card pad">
            <h2>By head</h2>
            <Donut slices={byHead.map(([, v], i) => ({ value: v, color: PALETTE[i % PALETTE.length] }))}>
              <div><div className="small muted">Expenses</div><div className="bold num" style={{ fontSize: 20 }}>{money.format(total)}</div></div>
            </Donut>
            {byHead.map(([id, v], i) => (
              <div key={id} className="row small" style={{ padding: '3px 0' }}>
                <span className="legend"><i style={{ background: PALETTE[i % PALETTE.length] }} /></span>
                <span className="grow">{head(id)?.emoji} {head(id)?.name ?? '?'}</span>
                <b className="num">{money.format(v)}</b>
                <span className="muted num" style={{ width: 44, textAlign: 'right' }}>{total ? Math.round((v / total) * 100) : 0}%</span>
              </div>
            ))}
            <div className="row small" style={{ borderTop: '1px solid var(--border)', marginTop: 8, paddingTop: 8 }}>
              <span className="grow">👩‍🏫 Instructor payments (in Instructors)</span><b className="num">{money.format(payTotal)}</b>
            </div>
          </div>
          <div className="card">
            <div className="card-head"><h2>Expense heads</h2><button className="btn text small" onClick={() => open({ kind: 'head' })}>Add</button></div>
            <div className="list">
              {data.expenseHeads.map(h => (
                <button key={h.id} className="item" onClick={() => open({ kind: 'head', id: h.id })} style={h.archived ? { opacity: 0.55 } : undefined}>
                  <span style={{ fontSize: 18 }}>{h.emoji}</span><span className="grow">{h.name}</span>{h.archived && <span className="pill">Archived</span>}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
