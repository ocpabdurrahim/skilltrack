import type { ReactNode } from 'react'

/** A ring chart; [slices] are value/colour pairs, [children] go in the hole. */
export function Donut({ slices, children, size = 200, thickness = 24 }: {
  slices: { value: number; color: string }[]
  children?: ReactNode
  size?: number
  thickness?: number
}) {
  const r = (size - thickness) / 2
  const c = 2 * Math.PI * r
  const total = slices.reduce((s, x) => s + x.value, 0)
  const gap = slices.length > 1 ? 2 : 0
  let offset = 0
  return (
    <div className="donut-wrap" style={{ width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} style={{ transform: 'rotate(-90deg)' }} aria-hidden="true">
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--chip)" strokeWidth={thickness} />
        {total > 0 && slices.map((s, i) => {
          const length = (s.value / total) * c
          const dash = Math.max(0, length - gap)
          const el = (
            <circle key={i} cx={size / 2} cy={size / 2} r={r} fill="none" stroke={s.color} strokeWidth={thickness}
              strokeDasharray={`${dash} ${c - dash}`} strokeDashoffset={-offset} />
          )
          offset += length
          return el
        })}
      </svg>
      <div className="donut-center">{children}</div>
    </div>
  )
}

/** Side-by-side bars per group, e.g. income, spending and profit per month. */
export function GroupedBars({ groups, colors, highlight = -1, height = 160 }: {
  groups: { label: string; values: number[]; title?: string }[]
  colors: string[]
  highlight?: number
  height?: number
}) {
  const max = Math.max(1, ...groups.flatMap(g => g.values))
  return (
    <div className="bars">
      {groups.map((g, i) => (
        <div className="group" key={i} title={g.title}>
          <div className="cols" style={{ height }}>
            {g.values.map((v, s) => (
              <div key={s} className="col" style={{ height: v > 0 ? `${Math.max(2, (v / max) * 100)}%` : 0, background: colors[s % colors.length] }} />
            ))}
          </div>
          <div className="tiny" style={{ fontWeight: i === highlight ? 700 : 500, color: i === highlight ? 'var(--text)' : 'var(--muted)' }}>{g.label}</div>
        </div>
      ))}
    </div>
  )
}

/** Horizontal bars with a label and a value, biggest first. */
export function HBars({ rows, color = 'var(--primary)' }: { rows: { label: string; value: number; text: string }[]; color?: string }) {
  const max = Math.max(1, ...rows.map(r => r.value))
  return (
    <div>
      {rows.map((r, i) => (
        <div className="hbar" key={i}>
          <span className="ellipsis">{r.label}</span>
          <div className="progress" style={{ height: 10 }}><div style={{ width: `${(r.value / max) * 100}%`, background: color }} /></div>
          <span className="num bold">{r.text}</span>
        </div>
      ))}
    </div>
  )
}

export const PALETTE = ['#4355b9', '#e09a12', '#1e8e4e', '#d23f31', '#1a73e8', '#8e44ad', '#16a085', '#c0392b', '#7f8c8d', '#d35400']
