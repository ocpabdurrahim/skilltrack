import { useData } from '../../data/store'
import { ROLE_HELP, ROLE_LABELS } from '../../data/types'
import { initials, PageHead } from '../common'
import { useDialogs } from '../dialogs/host'
import { Icon } from '../icons'

export default function StaffPage() {
  const data = useData()
  const { open } = useDialogs()
  const iAmOwner = data.role === 'OWNER'
  return (
    <div className="page">
      <PageHead title="Staff" sub="Who can sign in to this center, and what they can do">
        <button className="btn" onClick={() => open({ kind: 'member' })} data-testid="add-member"><Icon name="plus" size={18} />Add staff</button>
      </PageHead>
      <div className="grid-2">
        <div className="card list">
          {data.members.map(m => {
            // Only the owner may edit the owner's own record (the rules say so too).
            const editable = m.role !== 'OWNER' || (iAmOwner && m.email === data.email)
            const instructor = m.instructorId ? data.idx.instructors.get(m.instructorId) : undefined
            return (
              <button key={m.id} className="item" disabled={!editable} onClick={() => open({ kind: 'member', id: m.id })}
                style={!m.active ? { opacity: 0.55 } : undefined} data-testid="member-row">
                <span className="avatar" style={{ width: 40, height: 40 }}>{initials(m.name || m.email)}</span>
                <div className="grow">
                  <div className="title">{m.name || m.email}{m.email === data.email ? ' (you)' : ''}</div>
                  <div className="subtitle">{m.email}{instructor ? ` · ${instructor.name}` : ''}</div>
                </div>
                {!m.active && <span className="pill red">Inactive</span>}
                <span className="pill indigo">{ROLE_LABELS[m.role]}</span>
              </button>
            )
          })}
        </div>
        <div className="card pad">
          <h2>Roles</h2>
          <dl className="kv">
            {(['OWNER', 'ADMIN', 'OFFICE', 'TEACHER'] as const).map(r => (
              <div key={r} style={{ display: 'contents' }}><dt><b>{ROLE_LABELS[r]}</b></dt><dd>{ROLE_HELP[r]}</dd></div>
            ))}
          </dl>
          <p className="small muted" style={{ marginTop: 14 }}>
            Staff sign in with Google using the exact email you add here, on the web or in the Android app. Make someone inactive to
            stop their access at once; their records stay.
          </p>
        </div>
      </div>
    </div>
  )
}
