import { useRef, useState } from 'react'
import { blankStudent } from '../../data/actions'
import { photoFromFile } from '../../data/image'
import { store, useData, usePhoto } from '../../data/store'
import type { Gender, Student } from '../../data/types'
import { ConfirmDialog, Field, Modal, useToast, useToday } from '../common'
import { Icon } from '../icons'
import type { DialogRequest } from './host'

export default function StudentDialog({ request, onClose }: {
  request: Extract<DialogRequest, { kind: 'student' }>
  onClose: () => void
}) {
  const data = useData()
  const toast = useToast()
  const date = useToday()
  const existing = request.id ? data.idx.students.get(request.id) : undefined
  const [s, setS] = useState<Student>(() => existing ?? { ...blankStudent(data, date), ...request.prefill })
  const savedPhoto = usePhoto(existing?.id)
  const [photo, setPhoto] = useState<string | null | undefined>(undefined) // undefined = unchanged
  const [error, setError] = useState<string | null>(null)
  const [confirmArchive, setConfirmArchive] = useState(false)
  const file = useRef<HTMLInputElement>(null)
  const set = <K extends keyof Student>(k: K, v: Student[K]) => setS(prev => ({ ...prev, [k]: v }))
  const shownPhoto = photo === undefined ? savedPhoto : photo

  const duplicateReg = data.students.some(x => x.id !== s.id && x.regNo.trim() !== '' && x.regNo === s.regNo.trim())

  const pick = async (f: File | undefined) => {
    if (!f) return
    try {
      setPhoto(await photoFromFile(f))
    } catch (e) {
      toast(e instanceof Error ? e.message : String(e))
    }
  }

  const save = () => {
    if (!s.name.trim()) return setError('Enter the student\'s name.')
    const student = { ...s, name: s.name.trim(), regNo: s.regNo.trim(), phone: s.phone.trim() }
    store.put('students', student)
    if (photo !== undefined) store.setPhoto(student.id, photo)
    toast(existing ? 'Student saved' : `${student.name} added (${student.regNo})`)
    request.onSaved?.(student)
    onClose()
  }

  const text = (k: keyof Student, label: string, opts: { type?: string; full?: boolean; placeholder?: string } = {}) => (
    <Field label={label} full={opts.full}>
      <input className="input" type={opts.type ?? 'text'} value={s[k] as string} placeholder={opts.placeholder}
        onChange={e => set(k, e.target.value as never)} data-testid={`student-${k}`} />
    </Field>
  )

  return (
    <Modal onClose={onClose} wide label={existing ? 'Edit student' : 'New student'}>
      <h2>{existing ? 'Edit student' : 'New student'}</h2>
      <div className="row" style={{ alignItems: 'flex-start', gap: 18, flexWrap: 'wrap' }}>
        <div className="stack" style={{ gap: 8, alignItems: 'center' }}>
          <div className="photo-box">{shownPhoto ? <img src={shownPhoto} alt="" /> : '🧑‍🎓'}</div>
          <input ref={file} type="file" accept="image/*" hidden onChange={e => { void pick(e.target.files?.[0]); e.target.value = '' }} />
          <button className="btn tonal small" onClick={() => file.current?.click()}><Icon name="camera" size={16} />Photo</button>
          {shownPhoto && <button className="btn text small" onClick={() => setPhoto(null)}>Remove</button>}
        </div>
        <div className="form-grid grow" style={{ minWidth: 260 }}>
          {text('name', 'Name *', { full: true })}
          <Field label="Registration no." hint={duplicateReg ? <span className="error-text">Another student already has this number.</span> : undefined}>
            <input className="input" value={s.regNo} onChange={e => set('regNo', e.target.value)} />
          </Field>
          <Field label="Admission (join) date">
            <input className="input" type="date" value={s.joinDate} onChange={e => set('joinDate', e.target.value)} />
          </Field>
          {text('phone', 'Phone', { type: 'tel', placeholder: '01XXXXXXXXX' })}
          {text('guardianPhone', 'Guardian\'s phone', { type: 'tel' })}
          {text('fatherName', 'Father\'s name')}
          {text('motherName', 'Mother\'s name')}
          <Field label="Gender">
            <select className="input" value={s.gender} onChange={e => set('gender', e.target.value as Gender)}>
              <option value="">—</option>
              <option value="MALE">Male</option>
              <option value="FEMALE">Female</option>
              <option value="OTHER">Other</option>
            </select>
          </Field>
          <Field label="Date of birth">
            <input className="input" type="date" value={s.dob} onChange={e => set('dob', e.target.value)} />
          </Field>
          {text('email', 'Email', { type: 'email' })}
          {text('idNo', 'NID / birth registration no.')}
          {text('education', 'Education', { placeholder: 'e.g. HSC, Honours' })}
          {text('institution', 'School / college')}
          {text('address', 'Address', { full: true })}
          <Field label="Note" full>
            <textarea className="input" value={s.note} onChange={e => set('note', e.target.value)} />
          </Field>
        </div>
      </div>
      {error && <p className="error-text small">{error}</p>}
      <div className="actions">
        {existing && (
          <button className="btn danger" style={{ marginRight: 'auto' }}
            onClick={() => (s.archived ? (store.put('students', { ...s, archived: false }), onClose()) : setConfirmArchive(true))}>
            {s.archived ? 'Restore' : 'Archive'}
          </button>
        )}
        <button className="btn text" onClick={onClose}>Cancel</button>
        <button className="btn" onClick={save} data-testid="student-save">Save</button>
      </div>
      {confirmArchive && (
        <ConfirmDialog title="Archive this student?" danger confirm="Archive"
          text="They disappear from the active lists. Their admissions, payments and results are kept, and you can restore them later."
          onConfirm={() => { store.put('students', { ...s, archived: true }); onClose() }} onClose={() => setConfirmArchive(false)} />
      )}
    </Modal>
  )
}
