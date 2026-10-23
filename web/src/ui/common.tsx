import {
  createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode,
} from 'react'
import { usePhoto, useData } from '../data/store'
import type { EnrollmentStatus, Method } from '../data/types'
import { METHOD_LABELS, METHODS } from '../data/types'
import { batchStatus, STATUS_LABELS, type BatchStatus } from '../domain/batches'
import { addMonths, currentMonth, monthLabel, today, type YearMonth } from '../domain/dates'
import { amountToInput, compactMoney, formatMoney, parseAmount, sanitizeAmount } from '../domain/money'
import { permissions } from '../domain/roles'
import { Icon } from './icons'
import type { Batch } from '../data/types'

// ---- Money formatting from the center's currency ------------------------------

export function useMoney() {
  const { center } = useData()
  const symbol = center?.currencySymbol ?? '৳'
  const grouping = center?.grouping ?? 'SOUTH_ASIAN'
  return useMemo(() => ({
    symbol,
    grouping,
    code: center?.currencyCode ?? 'BDT',
    format: (minor: number, showPlus = false) => formatMoney(minor, symbol, grouping, showPlus),
    compact: (minor: number) => compactMoney(minor, symbol, grouping),
  }), [symbol, grouping, center?.currencyCode])
}

export function usePermissions() {
  const { role } = useData()
  return useMemo(() => permissions(role), [role])
}

/** Today's date, refreshed when the tab comes back after midnight. */
export function useToday(): string {
  const [value, setValue] = useState(today)
  useEffect(() => {
    const check = () => setValue(today())
    const timer = setInterval(check, 60_000)
    document.addEventListener('visibilitychange', check)
    return () => {
      clearInterval(timer)
      document.removeEventListener('visibilitychange', check)
    }
  }, [])
  return value
}

/** Case- and space-insensitive search over a few fields. */
export function matches(q: string, ...fields: (string | null | undefined)[]): boolean {
  const needle = q.trim().toLowerCase()
  if (!needle) return true
  return fields.some(f => f && f.toLowerCase().includes(needle))
}

// ---- Toasts ---------------------------------------------------------------------

const ToastContext = createContext<(message: string) => void>(() => {})
export const useToast = () => useContext(ToastContext)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<{ id: number; message: string }[]>([])
  const show = useCallback((message: string) => {
    const id = Date.now() + Math.random()
    setToasts(t => [...t, { id, message }])
    setTimeout(() => setToasts(t => t.filter(x => x.id !== id)), 3500)
  }, [])
  return (
    <ToastContext.Provider value={show}>
      {children}
      <div className="toasts" role="status">
        {toasts.map(t => <div key={t.id} className="toast">{t.message}</div>)}
      </div>
    </ToastContext.Provider>
  )
}

// ---- Small building blocks ------------------------------------------------------

export function Empty({ emoji, title, body, children }: { emoji: string; title: string; body?: string; children?: ReactNode }) {
  return (
    <div className="empty">
      <div className="e">{emoji}</div>
      <h3>{title}</h3>
      {body && <p>{body}</p>}
      {children && <div style={{ marginTop: 14 }}>{children}</div>}
    </div>
  )
}

export function Tile({ label, value, sub, className, onClick }: {
  label: string
  value: string
  sub?: string
  className?: string
  onClick?: () => void
}) {
  const body = (
    <>
      <div className="k">{label}</div>
      <div className={`v num ${className ?? ''}`}>{value}</div>
      {sub && <div className="s">{sub}</div>}
    </>
  )
  return onClick ? <button className="tile" onClick={onClick}>{body}</button> : <div className="tile">{body}</div>
}

export function MonthSelector({ month, onChange }: { month: YearMonth; onChange: (m: YearMonth) => void }) {
  const now = currentMonth()
  return (
    <div className="month-selector">
      <button className="icon-btn" onClick={() => onChange(addMonths(month, -1))} aria-label="Previous month"><Icon name="left" /></button>
      <div className="label">
        <b>{monthLabel(month)}</b>
        {month !== now && <button onClick={() => onChange(now)}>Back to this month</button>}
      </div>
      <button className="icon-btn" onClick={() => onChange(addMonths(month, 1))} aria-label="Next month"><Icon name="right" /></button>
    </div>
  )
}

export function Segmented<T extends string>({ options, value, onChange }: {
  options: [T, string][]
  value: T
  onChange: (v: T) => void
}) {
  return (
    <div className="segmented" role="tablist">
      {options.map(([v, label]) => (
        <button key={v} role="tab" aria-selected={v === value} className={v === value ? 'on' : ''} onClick={() => onChange(v)}>
          {label}
        </button>
      ))}
    </div>
  )
}

export function Tabs<T extends string>({ options, value, onChange }: {
  options: [T, string][]
  value: T
  onChange: (v: T) => void
}) {
  return (
    <div className="tabs" role="tablist">
      {options.map(([v, label]) => (
        <button key={v} role="tab" aria-selected={v === value} className={v === value ? 'on' : ''} onClick={() => onChange(v)}>
          {label}
        </button>
      ))}
    </div>
  )
}

export function Field({ label, hint, children, full = false }: {
  label: string
  hint?: ReactNode
  children: ReactNode
  full?: boolean
}) {
  return (
    <label className={`field ${full ? 'full' : ''}`}>
      <span>{label}</span>
      {children}
      {hint && <span className="hint">{hint}</span>}
    </label>
  )
}

/** A text box for an amount; [value] is the text, parse it with parseAmount. */
export function MoneyInput({ value, onChange, autoFocus, placeholder, testId }: {
  value: string
  onChange: (v: string) => void
  autoFocus?: boolean
  placeholder?: string
  testId?: string
}) {
  const money = useMoney()
  return (
    <div className="row" style={{ gap: 0 }}>
      <span className="muted" style={{ marginRight: 6, fontWeight: 650 }}>{money.symbol}</span>
      <input className="input num" inputMode="decimal" value={value} autoFocus={autoFocus} placeholder={placeholder ?? '0'}
        data-testid={testId} onChange={e => { const s = sanitizeAmount(e.target.value); if (s !== null) onChange(s) }} />
    </div>
  )
}

export const amountText = (minor: number) => (minor ? amountToInput(minor) : '')
export const parseMoney = (s: string) => parseAmount(s) ?? 0

export function MethodSelect({ value, onChange }: { value: Method; onChange: (m: Method) => void }) {
  return (
    <select className="input" value={value} onChange={e => onChange(e.target.value as Method)}>
      {METHODS.map(m => <option key={m} value={m}>{METHOD_LABELS[m]}</option>)}
    </select>
  )
}

export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  return ((parts[0]?.[0] ?? '?') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase()
}

/**
 * A student's initials, or their photo when [photo] is set. Lists leave photos
 * off: each one is a separate download (docs/data-model.md, "photos").
 */
export function Avatar({ studentId, name, size = 40, photo: withPhoto = false }: {
  studentId?: string
  name: string
  size?: number
  photo?: boolean
}) {
  const photo = usePhoto(withPhoto ? studentId : null)
  return (
    <span className="avatar" style={{ width: size, height: size, fontSize: size * 0.38 }}>
      {photo ? <img src={photo} alt="" /> : initials(name)}
    </span>
  )
}

const BATCH_PILL: Record<BatchStatus, string> = { RUNNING: 'green', UPCOMING: 'blue', COMPLETED: 'indigo', CANCELLED: 'red' }

export function BatchStatusPill({ batch, date }: { batch: Batch; date: string }) {
  const s = batchStatus(batch, date)
  return <span className={`pill ${BATCH_PILL[s]}`}>{STATUS_LABELS[s]}</span>
}

const ENROLL_PILL: Record<EnrollmentStatus, string> = { ACTIVE: 'green', COMPLETED: 'indigo', DROPPED: 'red' }
const ENROLL_LABEL: Record<EnrollmentStatus, string> = { ACTIVE: 'Active', COMPLETED: 'Completed', DROPPED: 'Dropped' }

export function EnrollmentPill({ status }: { status: EnrollmentStatus }) {
  return <span className={`pill ${ENROLL_PILL[status]}`}>{ENROLL_LABEL[status]}</span>
}

export function ProgressBar({ fraction, color, height = 8 }: { fraction: number; color: string; height?: number }) {
  const width = Math.max(0, Math.min(1, fraction)) * 100
  return (
    <div className="progress" style={{ height }}>
      <div style={{ width: `${width}%`, background: color }} />
    </div>
  )
}

export function rateColor(rate: number | null): string {
  if (rate === null) return 'var(--muted)'
  return rate >= 0.8 ? 'var(--success)' : rate >= 0.6 ? 'var(--warning)' : 'var(--danger)'
}

export const pct = (rate: number | null) => (rate === null ? '—' : `${Math.round(rate * 100)}%`)

// ---- Dialogs ----------------------------------------------------------------------

export function Modal({ children, onClose, narrow = false, wide = false, sheet = true, label }: {
  children: ReactNode
  onClose: () => void
  narrow?: boolean
  wide?: boolean
  /** Full-screen on phones. */
  sheet?: boolean
  label: string
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])
  return (
    <div className={`backdrop ${sheet ? 'sheet' : ''}`} onMouseDown={e => e.target === e.currentTarget && onClose()}>
      <div className={`dialog ${narrow ? 'narrow' : ''} ${wide ? 'wide' : ''}`} role="dialog" aria-modal="true" aria-label={label}>
        {children}
      </div>
    </div>
  )
}

export function ConfirmDialog({ title, text, confirm, danger = false, onConfirm, onClose }: {
  title: string
  text: string
  confirm: string
  danger?: boolean
  onConfirm: () => void
  onClose: () => void
}) {
  return (
    <Modal onClose={onClose} narrow sheet={false} label={title}>
      <h2>{title}</h2>
      <p className="muted">{text}</p>
      <div className="actions">
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className={`btn ${danger ? 'danger' : ''}`} onClick={() => { onConfirm(); onClose() }}>{confirm}</button>
      </div>
    </Modal>
  )
}

export function Spinner() {
  return <div className="center-screen"><div className="spinner" aria-label="Loading" /></div>
}

export function PageHead({ title, sub, children, back }: { title: string; sub?: ReactNode; children?: ReactNode; back?: () => void }) {
  return (
    <div className="page-head">
      {back && <button className="icon-btn" onClick={back} aria-label="Back"><Icon name="back" /></button>}
      <div className="grow" style={{ minWidth: 160 }}>
        <h1 style={{ margin: 0 }}>{title}</h1>
        {sub && <div className="sub">{sub}</div>}
      </div>
      {children}
    </div>
  )
}
