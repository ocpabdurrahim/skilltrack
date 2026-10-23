import { signOut } from 'firebase/auth'
import { useRef, useState } from 'react'
import { logoFromFile } from '../../data/image'
import { store, useData } from '../../data/store'
import type { Center } from '../../data/types'
import { formatMoney, type Grouping } from '../../domain/money'
import { useFirebase, useSession } from '../App'
import { Field, PageHead, Segmented, usePermissions, useToast } from '../common'
import { Icon, LogoMark } from '../icons'
import { CURRENCIES, THEMES, useTheme, type ThemeMode } from '../preferences'

export const APK_URL = 'https://github.com/ocpabdurrahim/skilltrack/releases/latest'

export default function SettingsPage() {
  const data = useData()
  const can = usePermissions()
  const { auth } = useFirebase()
  const { user, switchCenter } = useSession()
  const [theme, setTheme] = useTheme()
  return (
    <div className="page">
      <PageHead title="Settings" />
      <div className="grid-2">
        <div className="stack">
          {can.manageCenter ? <CenterProfile /> : (
            <div className="card pad">
              <h2>{data.center?.name}</h2>
              <p className="muted small">{[data.center?.address, data.center?.phone].filter(Boolean).join(' · ')}</p>
              <p className="small muted">Only the owner or an admin can change the center's details.</p>
            </div>
          )}
        </div>
        <div className="stack">
          <div className="card pad stack">
            <h2 style={{ margin: 0 }}>This device</h2>
            <Field label="Theme">
              <Segmented options={THEMES.map(t => [t.value, t.label] as [ThemeMode, string])} value={theme} onChange={setTheme} />
            </Field>
          </div>
          <div className="card list">
            <div className="item">
              <span className="avatar" style={{ width: 40, height: 40 }}>{(user.displayName ?? user.email ?? '?')[0]}</span>
              <div className="grow"><div className="title">{user.displayName ?? 'Signed in'}</div><div className="subtitle">{user.email} · {data.role.toLowerCase()}</div></div>
            </div>
            <button className="item" onClick={switchCenter} data-testid="switch-center"><Icon name="swap" /><div className="grow"><div className="title">Switch center</div><div className="subtitle">Open another center, or create one</div></div></button>
            <a className="item" href={APK_URL} target="_blank" rel="noreferrer"><Icon name="android" /><div className="grow"><div className="title">Android app</div><div className="subtitle">Download the APK; it updates itself</div></div></a>
            <button className="item" onClick={() => { store.stop(); void signOut(auth) }} data-testid="sign-out"><Icon name="logout" /><div className="grow"><div className="title">Sign out</div><div className="subtitle">Everything stays in the center's cloud</div></div></button>
          </div>
        </div>
      </div>
    </div>
  )
}

function CenterProfile() {
  const data = useData()
  const toast = useToast()
  const [c, setC] = useState<Center>(data.center!)
  const file = useRef<HTMLInputElement>(null)
  const set = <K extends keyof Center>(k: K, v: Center[K]) => setC(prev => ({ ...prev, [k]: v }))
  const save = () => {
    store.setCenter({ ...c, name: c.name.trim() || data.center!.name, studentPrefix: c.studentPrefix.trim() || 'ST',
      receiptPrefix: c.receiptPrefix.trim() || 'MR', certificatePrefix: c.certificatePrefix.trim() || 'CERT' })
    toast('Center saved')
  }
  const pickLogo = async (f: File | undefined) => {
    if (!f) return
    try {
      set('logo', await logoFromFile(f))
    } catch (e) {
      toast(e instanceof Error ? e.message : String(e))
    }
  }
  const text = (k: keyof Center, label: string, full = false) => (
    <Field label={label} full={full}><input className="input" value={c[k] as string} onChange={e => set(k, e.target.value as never)} /></Field>
  )
  const year = new Date().getFullYear()
  return (
    <div className="card pad stack">
      <h2 style={{ margin: 0 }}>Center profile</h2>
      <div className="row" style={{ gap: 16 }}>
        <div style={{ width: 96, height: 96, borderRadius: 18, border: '1px solid var(--border)', display: 'grid', placeItems: 'center', overflow: 'hidden', background: '#fff' }}>
          {c.logo ? <img src={c.logo} alt="Logo" style={{ maxWidth: '100%', maxHeight: '100%' }} /> : <LogoMark size={56} />}
        </div>
        <div className="stack" style={{ gap: 6 }}>
          <input ref={file} type="file" accept="image/*" hidden onChange={e => { void pickLogo(e.target.files?.[0]); e.target.value = '' }} />
          <button className="btn tonal small" onClick={() => file.current?.click()}>Upload logo</button>
          {c.logo && <button className="btn text small" onClick={() => set('logo', '')}>Remove logo</button>}
          <span className="tiny muted">Shown on receipts, certificates and ID cards.</span>
        </div>
      </div>
      <div className="form-grid">
        {text('name', 'Center name', true)}
        {text('tagline', 'Tagline', true)}
        {text('address', 'Address', true)}
        {text('phone', 'Phone')}
        {text('email', 'Email')}
        {text('website', 'Website')}
        {text('signatory', 'Signatory title (certificates)')}
      </div>
      <h3 style={{ margin: '6px 0 0', fontSize: 15 }}>Money</h3>
      <div className="form-grid">
        <Field label="Currency">
          <select className="input" value={c.currencyCode} onChange={e => {
            const cur = CURRENCIES.find(x => x.code === e.target.value)
            if (cur) setC({ ...c, currencyCode: cur.code, currencySymbol: cur.symbol, grouping: cur.grouping })
          }}>
            {CURRENCIES.map(x => <option key={x.code} value={x.code}>{x.symbol} {x.name}</option>)}
          </select>
        </Field>
        <Field label="Digit grouping" hint={formatMoney(1234567_00, c.currencySymbol, c.grouping)}>
          <Segmented options={[['SOUTH_ASIAN', '12,34,567'], ['INTERNATIONAL', '1,234,567']]} value={c.grouping} onChange={(g: Grouping) => set('grouping', g)} />
        </Field>
      </div>
      <h3 style={{ margin: '6px 0 0', fontSize: 15 }}>Numbering</h3>
      <div className="form-grid">
        <Field label="Student prefix" hint={`${c.studentPrefix || 'ST'}-${year}-0001`}><input className="input" value={c.studentPrefix} onChange={e => set('studentPrefix', e.target.value.toUpperCase())} /></Field>
        <Field label="Receipt prefix" hint={`${c.receiptPrefix || 'MR'}-${year}-0001`}><input className="input" value={c.receiptPrefix} onChange={e => set('receiptPrefix', e.target.value.toUpperCase())} /></Field>
        <Field label="Certificate prefix" hint={`${c.certificatePrefix || 'CERT'}-${year}-0001`}><input className="input" value={c.certificatePrefix} onChange={e => set('certificatePrefix', e.target.value.toUpperCase())} /></Field>
      </div>
      <div className="row" style={{ justifyContent: 'flex-end' }}><button className="btn" onClick={save} data-testid="center-save">Save</button></div>
    </div>
  )
}
