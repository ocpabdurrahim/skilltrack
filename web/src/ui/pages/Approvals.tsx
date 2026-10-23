// For the SkillTrack owner only: who asked to create a center, and who may.
// The server refuses this data to everyone else.
import { useEffect, useState } from 'react'
import { whatsappNumber } from '../../data/actions'
import {
  approve, decline, removeCreator, watchCreators, watchRequests, type CenterRequest, type Creator,
} from '../../data/approvals'
import { useFirebase } from '../App'
import { ConfirmDialog, Empty, Field, Modal, PageHead, Spinner, Tabs, useToast } from '../common'
import { Icon } from '../icons'

const when = (ms: number) => (ms ? new Date(ms).toLocaleString('en-GB', { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' }) : '—')

/** Live lists of requests and approvals (the app owner's view). */
export function useApprovals() {
  const { db } = useFirebase()
  const [requests, setRequests] = useState<CenterRequest[] | null>(null)
  const [creators, setCreators] = useState<Creator[] | null>(null)
  useEffect(() => watchRequests(db, setRequests), [db])
  useEffect(() => watchCreators(db, setCreators), [db])
  return { requests, creators }
}

export default function ApprovalsPage({ me, onBack }: { me: string; onBack?: () => void }) {
  const { db } = useFirebase()
  const toast = useToast()
  const { requests, creators } = useApprovals()
  const [tab, setTab] = useState<'requests' | 'approved'>('requests')
  const [adding, setAdding] = useState(false)
  const [removing, setRemoving] = useState<Creator | null>(null)

  if (!requests || !creators) return <Spinner />
  const waiting = requests.filter(r => r.status === 'PENDING')
  const answered = requests.filter(r => r.status !== 'PENDING')

  const act = (done: string, action: () => Promise<void>) =>
    action().then(() => toast(done), e => toast(`Didn't work: ${e instanceof Error ? e.message : String(e)}`))
  const approveRequest = (r: CenterRequest) => act(`${r.email} can now create a center`, () => approve(db, r.email, r.name, me, true))

  return (
    <div className="page">
      <PageHead title="Center requests" sub="Who may create a center on SkillTrack" back={onBack}>
        <button className="btn" onClick={() => setAdding(true)} data-testid="approve-email"><Icon name="plus" size={18} />Approve an email</button>
      </PageHead>
      <Tabs options={[['requests', `Requests (${waiting.length})`], ['approved', `Approved (${creators.length})`]]} value={tab} onChange={setTab} />

      {tab === 'requests' ? (
        <div className="stack" style={{ marginTop: 14 }}>
          {waiting.length === 0 && <Empty emoji="📭" title="No waiting requests" body="When someone asks to create a center, it shows up here." />}
          {waiting.map(r => (
            <div key={r.email} className="card pad stack" data-testid="request-row">
              <h2 style={{ margin: 0 }}>{r.centerName}</h2>
              <dl className="kv">
                <dt>Name</dt><dd>{r.name}</dd>
                <dt>Google email</dt><dd>{r.email}</dd>
                <dt>Phone</dt><dd>{r.phone}</dd>
                <dt>Asked</dt><dd>{when(r.createdAt)}</dd>
                {r.message && <><dt>Message</dt><dd>{r.message}</dd></>}
              </dl>
              <div className="row wrap">
                <button className="btn" onClick={() => approveRequest(r)} data-testid="approve-request"><Icon name="check" size={18} />Approve</button>
                <button className="btn text" onClick={() => act(`Declined ${r.email}`, () => decline(db, r.email, me))} data-testid="decline-request">Decline</button>
                {r.phone && <a className="btn text" href={`tel:${r.phone}`}><Icon name="phone" size={18} />Call</a>}
                {r.phone && (
                  <a className="btn text" target="_blank" rel="noreferrer"
                    href={`https://wa.me/${whatsappNumber(r.phone)}?text=${encodeURIComponent(`Assalamu alaikum ${r.name}, about your SkillTrack center "${r.centerName}": `)}`}>
                    <Icon name="message" size={18} />WhatsApp
                  </a>
                )}
              </div>
            </div>
          ))}
          {answered.length > 0 && (
            <div className="card list">
              <div className="card-head"><h2>Answered</h2></div>
              {answered.map(r => (
                <div key={r.email} className="item">
                  <div className="grow">
                    <div className="title">{r.name} · {r.centerName}</div>
                    <div className="subtitle">{r.email} · asked {when(r.createdAt)}</div>
                  </div>
                  {r.status === 'DECLINED' && <button className="btn text small" onClick={() => approveRequest(r)}>Approve after all</button>}
                  <span className={`pill ${r.status === 'APPROVED' ? 'green' : 'red'}`}>{r.status === 'APPROVED' ? 'Approved' : 'Declined'}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      ) : (
        <div className="stack" style={{ marginTop: 14 }}>
          {creators.length === 0 && <Empty emoji="🔐" title="Nobody approved yet" body="Only you can create centers. Approve a request, or an email directly." />}
          {creators.length > 0 && (
            <div className="card list">
              {creators.map(c => (
                <div key={c.email} className="item" data-testid="creator-row">
                  <div className="grow">
                    <div className="title">{c.name || c.email}</div>
                    <div className="subtitle">{c.name ? `${c.email} · ` : ''}approved {when(c.approvedAt)}</div>
                  </div>
                  <button className="btn danger small" onClick={() => setRemoving(c)}>Remove</button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {adding && (
        <ApproveEmailDialog
          onClose={() => setAdding(false)}
          onApprove={(email, name) => {
            setAdding(false)
            act(`${email} can now create a center`, () => approve(db, email, name, me, requests.some(r => r.email === email)))
          }}
        />
      )}
      {removing && (
        <ConfirmDialog
          title={`Remove ${removing.email}?`}
          text="They won't be able to create more centers. Centers they already made keep working."
          confirm="Remove"
          danger
          onClose={() => setRemoving(null)}
          onConfirm={() => act(`${removing.email} removed`, () => removeCreator(db, removing.email))}
        />
      )}
    </div>
  )
}

function ApproveEmailDialog({ onClose, onApprove }: { onClose: () => void; onApprove: (email: string, name: string) => void }) {
  const [email, setEmail] = useState('')
  const [name, setName] = useState('')
  const clean = email.trim().toLowerCase()
  const ok = /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(clean)
  return (
    <Modal onClose={onClose} narrow label="Approve an email">
      <h2>Approve an email</h2>
      <p className="muted">They can create centers after signing in with this Google account.</p>
      <div className="form-grid">
        <Field label="Google email *" full><input className="input" type="email" autoFocus value={email} onChange={e => setEmail(e.target.value)} data-testid="approve-email-input" /></Field>
        <Field label="Name (for your list)" full><input className="input" value={name} onChange={e => setName(e.target.value)} /></Field>
      </div>
      <div className="actions">
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" disabled={!ok} onClick={() => onApprove(clean, name.trim())}>Approve</button>
      </div>
    </Modal>
  )
}
