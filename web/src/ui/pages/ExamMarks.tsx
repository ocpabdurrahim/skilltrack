import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { store, useData } from '../../data/store'
import { fullDate } from '../../domain/dates'
import { gradeFor, percentLabel } from '../../domain/results'
import { Empty, PageHead, usePermissions, useToast } from '../common'
import { useDialogs } from '../dialogs/host'
import { Icon } from '../icons'

/** Marks for one exam, typed in a column like a mark register. */
export default function ExamMarksPage() {
  const { id } = useParams()
  const data = useData()
  const can = usePermissions()
  const toast = useToast()
  const navigate = useNavigate()
  const { open } = useDialogs()
  const exam = data.exams.find(x => x.id === id)
  const [text, setText] = useState<Record<string, string>>(() =>
    Object.fromEntries(Object.entries(exam?.marks ?? {}).map(([k, v]) => [k, String(v)])))
  const [dirty, setDirty] = useState(false)
  if (!exam) return <div className="page"><Empty emoji="🔎" title="Exam not found" /></div>
  const batch = data.idx.batches.get(exam.batchId)
  const roster = data.enrollments.filter(e => e.batchId === exam.batchId && e.status !== 'DROPPED')
  const parse = (s: string): number | null | 'bad' => {
    if (s.trim() === '') return null
    const n = Number(s)
    return Number.isFinite(n) && n >= 0 && n <= exam.fullMarks ? Math.round(n * 100) / 100 : 'bad'
  }
  const bad = roster.filter(e => parse(text[e.id] ?? '') === 'bad')
  const save = () => {
    const marks: Record<string, number> = {}
    for (const e of roster) {
      const v = parse(text[e.id] ?? '')
      if (typeof v === 'number') marks[e.id] = v
    }
    // Marks of students no longer listed (e.g. dropped) are kept as they were.
    for (const [k, v] of Object.entries(exam.marks)) if (!roster.some(e => e.id === k)) marks[k] = v
    store.put('exams', { ...exam, marks })
    setDirty(false)
    toast('Marks saved')
  }
  const entered = roster.filter(e => typeof parse(text[e.id] ?? '') === 'number').length

  return (
    <div className="page">
      <PageHead title={exam.name} back={() => navigate(-1)}
        sub={`${batch?.name ?? 'Batch'} · ${fullDate(exam.date) || 'no date'} · full ${exam.fullMarks}, pass ${exam.passMarks}`}>
        {can.teach && <button className="btn outline" onClick={() => open({ kind: 'exam', batchId: exam.batchId, id: exam.id })}><Icon name="edit" size={18} />Exam</button>}
        <button className="btn tonal" onClick={() => navigate(`/batches/${exam.batchId}?tab=results`)}>Results</button>
      </PageHead>
      {roster.length === 0 ? <div className="card"><Empty emoji="🧑‍🎓" title="No students in this batch" /></div> : (
        <div className="card table-wrap" style={{ maxWidth: 720 }}>
          <table className="table">
            <thead><tr><th>Roll</th><th>Student</th><th className="r">Marks /{exam.fullMarks}</th><th className="r">%</th><th className="c">Grade</th></tr></thead>
            <tbody>
              {roster.map((e, i) => {
                const s = data.idx.students.get(e.studentId)
                const v = parse(text[e.id] ?? '')
                const p = typeof v === 'number' ? (v / exam.fullMarks) * 100 : null
                return (
                  <tr key={e.id}>
                    <td className="num">{e.rollNo}</td>
                    <td><b>{s?.name ?? '?'}</b><div className="small muted">{s?.regNo}</div></td>
                    <td className="r">
                      <input className={`input compact num ${v === 'bad' ? 'bad' : ''}`} inputMode="decimal" disabled={!can.teach}
                        value={text[e.id] ?? ''} data-testid="mark-input"
                        onChange={ev => { setText({ ...text, [e.id]: ev.target.value }); setDirty(true) }}
                        onKeyDown={ev => {
                          if (ev.key === 'Enter') (document.querySelectorAll<HTMLInputElement>('[data-testid="mark-input"]')[i + 1])?.focus()
                        }} />
                    </td>
                    <td className="r num">{p === null ? '—' : percentLabel(p)}</td>
                    <td className={`c bold ${typeof v === 'number' && v < exam.passMarks ? 'danger' : ''}`}>
                      {typeof v === 'number' ? gradeFor(p!, v < exam.passMarks).grade : ''}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
          <div className="row wrap" style={{ padding: 14 }}>
            <span className="small muted grow">{entered} of {roster.length} entered. Leave a box empty if the student was absent; Enter moves down.</span>
            {bad.length > 0 && <span className="error-text small">Marks must be between 0 and {exam.fullMarks}.</span>}
            {can.teach && <button className="btn" disabled={!dirty || bad.length > 0} onClick={save} data-testid="marks-save">Save marks</button>}
          </div>
        </div>
      )}
    </div>
  )
}
