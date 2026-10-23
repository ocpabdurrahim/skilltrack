// Printable documents. Each opens on its own page with a Print button; the
// sheets are sized in millimetres so they print 1:1 on A4.
import { useEffect, type ReactNode } from 'react'
import { Route, Routes, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { attendanceMap, feeOf, feeRows, resultsMap } from '../../data/actions'
import { useData, usePhoto } from '../../data/store'
import { METHOD_LABELS, type Enrollment, type Student } from '../../data/types'
import { attendanceOf, isClassDay } from '../../domain/attendance'
import { daysLabel } from '../../domain/batches'
import { addMonthsToDate, fullDate, monthDays, monthLabel, today as todayStr, weekdayLabel } from '../../domain/dates'
import { batchResults, percentLabel } from '../../domain/results'
import { amountInWords, currencyWord } from '../../domain/words'
import { pct, useMoney } from '../common'
import { Icon, LogoMark } from '../icons'

export default function PrintPage() {
  return (
    <Routes>
      <Route path="receipt/:id" element={<Receipt />} />
      <Route path="certificate/:id" element={<CertificateDoc />} />
      <Route path="idcards" element={<IdCards />} />
      <Route path="results/:batchId" element={<ResultSheet />} />
      <Route path="marksheet/:enrollmentId" element={<MarkSheet />} />
      <Route path="admission/:studentId" element={<AdmissionForm />} />
      <Route path="dues" element={<DuesList />} />
      <Route path="attendance/:batchId/:month" element={<Register />} />
      <Route path="*" element={<Frame title="Not found"><div className="paper a4">Nothing to print here.</div></Frame>} />
    </Routes>
  )
}

function Frame({ title, landscape = false, children }: { title: string; landscape?: boolean; children: ReactNode }) {
  const navigate = useNavigate()
  useEffect(() => {
    const before = document.title
    document.title = title
    return () => { document.title = before }
  }, [title])
  return (
    <div>
      <style>{`@page { size: A4 ${landscape ? 'landscape' : 'portrait'}; margin: 0; }`}</style>
      <div className="print-bar">
        <button className="btn text" onClick={() => navigate(-1)}><Icon name="back" size={18} />Back</button>
        <b className="grow">{title}</b>
        <span className="small muted hide-mobile">A4 {landscape ? 'landscape' : 'portrait'} · turn off headers and footers in the print dialog</span>
        <button className="btn" onClick={() => window.print()} data-testid="print-now"><Icon name="printer" size={18} />Print / Save PDF</button>
      </div>
      <div className="print-area">{children}</div>
    </div>
  )
}

function DocHead({ compact = false }: { compact?: boolean }) {
  const { center } = useData()
  if (!center) return null
  return (
    <div className="doc-head">
      {center.logo ? <img src={center.logo} alt="" /> : <LogoMark size={compact ? 40 : 50} />}
      <div className="grow">
        <div className="n">{center.name}</div>
        {center.tagline && <div className="t" style={{ fontWeight: 600 }}>{center.tagline}</div>}
        <div className="t">{[center.address, center.phone, center.email, center.website].filter(Boolean).join(' · ')}</div>
      </div>
    </div>
  )
}

function Missing({ what }: { what: string }) {
  return <Frame title={what}><div className="paper a4"><p>This {what.toLowerCase()} was not found. It may have been deleted on another device.</p></div></Frame>
}

// ---- Money receipt: office and student copies on one A4 ------------------------

function Receipt() {
  const { id } = useParams()
  const data = useData()
  const money = useMoney()
  const p = data.payments.find(x => x.id === id)
  if (!p) return <Missing what="Receipt" />
  const s = data.idx.students.get(p.studentId)
  const e = data.idx.enrollments.get(p.enrollmentId)
  const b = e ? data.idx.batches.get(e.batchId) : undefined
  const c = e ? data.idx.courses.get(e.courseId) : undefined
  // Paid so far = this receipt and every earlier one for the same admission.
  const upTo = data.payments.filter(x => x.enrollmentId === p.enrollmentId &&
    (x.date < p.date || (x.date === p.date && x.createdAt <= p.createdAt)))
  const paidSoFar = upTo.reduce((sum, x) => sum + x.amount, 0)
  const payable = e ? Math.max(e.fee - e.discount, 0) : 0
  const remaining = Math.max(payable - paidSoFar, 0)
  const next = e ? feeOf(data, e, todayStr()).nextDue : null
  const words = amountInWords(p.amount, data.center?.grouping, currencyWord(data.center?.currencyCode ?? 'BDT'))
  const copy = (label: string) => (
    <div className="receipt" key={label}>
      <span className="copy">{label}</span>
      <DocHead compact />
      <div className="doc-title" style={{ margin: '2px 0 6px' }}>Money Receipt</div>
      <dl>
        <dt>Receipt no.</dt><dd>{p.receiptNo}</dd><dt>Date</dt><dd>{fullDate(p.date)}</dd>
        <dt>Student</dt><dd>{s?.name ?? '—'}</dd><dt>Reg. no.</dt><dd>{s?.regNo ?? '—'}</dd>
        <dt>Course</dt><dd>{c?.name ?? '—'}</dd><dt>Batch / roll</dt><dd>{b?.name ?? '—'}{e ? ` / ${e.rollNo}` : ''}</dd>
        <dt>Method</dt><dd>{METHOD_LABELS[p.method]}</dd><dt>Note</dt><dd>{p.note || '—'}</dd>
      </dl>
      <div className="row" style={{ alignItems: 'center', gap: 14, margin: '4px 0 6px' }}>
        <span className="amount-box">{money.format(p.amount)}</span>
        <span style={{ fontSize: 13 }}><span className="muted">In words: </span><b>{words}</b></span>
      </div>
      <table style={{ marginTop: 4 }}>
        <tbody>
          <tr><th>Course fee</th><td className="r">{money.format(e?.fee ?? 0)}</td><th>Discount</th><td className="r">{money.format(e?.discount ?? 0)}</td>
            <th>Payable</th><td className="r">{money.format(payable)}</td></tr>
          <tr><th>Paid so far</th><td className="r">{money.format(paidSoFar)}</td><th>Remaining</th><td className="r"><b>{money.format(remaining)}</b></td>
            <th>Next due</th><td className="r">{next && remaining > 0 ? `${fullDate(next.date)}` : '—'}</td></tr>
        </tbody>
      </table>
      <div className="sig-row" style={{ marginTop: 'auto' }}>
        <div className="sig">Student / guardian</div>
        <div className="sig">Received by{p.receivedBy ? `: ${p.receivedBy}` : ''}</div>
      </div>
    </div>
  )
  return (
    <Frame title={`Receipt ${p.receiptNo}`}>
      <div className="paper a4" data-testid="receipt-sheet">{copy('Office copy')}{copy('Student copy')}</div>
    </Frame>
  )
}

// ---- Certificate (A4 landscape) -------------------------------------------------

function CertificateDoc() {
  const { id } = useParams()
  const data = useData()
  const cert = data.certificates.find(c => c.id === id)
  if (!cert) return <Missing what="Certificate" />
  const center = data.center!
  const s = data.idx.students.get(cert.studentId)
  const e = data.idx.enrollments.get(cert.enrollmentId)
  const b = e ? data.idx.batches.get(e.batchId) : undefined
  const c = e ? data.idx.courses.get(e.courseId) : undefined
  const child = s?.gender === 'FEMALE' ? 'daughter' : s?.gender === 'MALE' ? 'son' : 'son/daughter'
  const parent = [s?.fatherName, s?.motherName].filter(Boolean).join(' & ')
  return (
    <Frame title={`Certificate ${cert.certNo}`} landscape>
      <div className="paper a4-land">
        <div className="certificate">
          <div className="row" style={{ gap: 14 }}>
            {center.logo ? <img src={center.logo} alt="" style={{ maxHeight: 64, maxWidth: 130 }} /> : <LogoMark size={56} />}
            <div style={{ textAlign: 'left' }}>
              <div className="center-name">{center.name}</div>
              <div style={{ fontSize: 13, color: '#555' }}>{[center.tagline, center.address].filter(Boolean).join(' · ')}</div>
            </div>
          </div>
          <div className="big-title">Certificate of Completion</div>
          <p style={{ color: '#555' }}>This is to certify that</p>
          <div className="student">{s?.name ?? ''}</div>
          <p>
            {parent ? <>{child} of <b>{parent}</b>, </> : null}registration no. <b>{s?.regNo}</b>, has successfully completed the course
            <br /><b style={{ fontSize: 20 }}>{c?.name ?? ''}</b>{c?.duration ? <> ({c.duration})</> : null}
            <br />in batch <b>{b?.name ?? ''}</b>{b ? <>, held from {fullDate(b.startDate)}{b.endDate ? <> to {fullDate(b.endDate)}</> : null}</> : null}.
          </p>
          {cert.grade && <div className="row" style={{ gap: 10, marginTop: 8 }}><span style={{ fontSize: 15 }}>Grade obtained</span><div className="grade-seal">{cert.grade}</div></div>}
          <div className="sig-row" style={{ width: '100%', marginTop: 'auto', justifyContent: 'flex-end', gap: 60 }}>
            <div className="sig">Course Instructor</div>
            <div className="sig">{center.signatory || 'Director'}</div>
          </div>
          <div className="meta">Certificate no. <b>{cert.certNo}</b><br />Issued on {fullDate(cert.issueDate)}</div>
        </div>
      </div>
    </Frame>
  )
}

// ---- ID cards, 8 per A4 ---------------------------------------------------------

function IdCard({ student, enrollment }: { student: Student; enrollment?: Enrollment }) {
  const data = useData()
  const center = data.center!
  const photo = usePhoto(student.id)
  const b = enrollment ? data.idx.batches.get(enrollment.batchId) : undefined
  const c = enrollment ? data.idx.courses.get(enrollment.courseId) : undefined
  const valid = b?.endDate || (enrollment ? addMonthsToDate(enrollment.date, 12) : '')
  return (
    <div className="id-card">
      <div className="top">
        {center.logo ? <img src={center.logo} alt="" /> : <LogoMark size={28} />}
        <div><div className="n">{center.name}</div><div className="t">{center.phone || center.tagline}</div></div>
      </div>
      <div className="body">
        <div className="pic">{photo ? <img src={photo} alt="" /> : '🧑‍🎓'}</div>
        <div className="info">
          <div className="nm">{student.name}</div>
          <div><b>ID:</b> {student.regNo}</div>
          {c && <div><b>Course:</b> {c.name}</div>}
          {b && <div><b>Batch:</b> {b.name}{enrollment ? ` · Roll ${enrollment.rollNo}` : ''}</div>}
          {student.phone && <div><b>Phone:</b> {student.phone}</div>}
          {student.guardianPhone && <div><b>Guardian:</b> {student.guardianPhone}</div>}
        </div>
      </div>
      <div className="foot"><span>STUDENT ID</span><span>{valid ? `Valid till ${fullDate(valid)}` : ''}</span></div>
    </div>
  )
}

function IdCards() {
  const data = useData()
  const [params] = useSearchParams()
  const ids = (params.get('ids') ?? '').split(',').filter(Boolean)
  const batchId = params.get('batch')
  let entries: { student: Student; enrollment?: Enrollment }[]
  if (batchId) {
    entries = data.enrollments.filter(e => e.batchId === batchId && e.status !== 'DROPPED')
      .map(e => ({ student: data.idx.students.get(e.studentId)!, enrollment: e })).filter(x => x.student)
  } else if (ids.length) {
    entries = ids.map(id => data.idx.students.get(id)).filter((s): s is Student => !!s).map(s => ({
      student: s, enrollment: data.enrollments.filter(e => e.studentId === s.id && e.status !== 'DROPPED').sort((a, b) => b.date.localeCompare(a.date))[0],
    }))
  } else {
    // Everyone currently studying.
    entries = data.enrollments.filter(e => e.status === 'ACTIVE')
      .map(e => ({ student: data.idx.students.get(e.studentId)!, enrollment: e })).filter(x => x.student && !x.student.archived)
  }
  const pages: (typeof entries)[] = []
  for (let i = 0; i < entries.length; i += 8) pages.push(entries.slice(i, i + 8))
  return (
    <Frame title={`ID cards (${entries.length})`}>
      {pages.length === 0 && <div className="paper a4">No students to print.</div>}
      {pages.map((page, n) => (
        <div key={n} className="paper a4">
          <div className="cards-sheet">{page.map(x => <IdCard key={x.student.id + (x.enrollment?.id ?? '')} student={x.student} enrollment={x.enrollment} />)}</div>
          <div className="muted" style={{ fontSize: 10, textAlign: 'center', marginTop: 8 }}>Cut along the card edges · 85.6 × 54 mm (credit-card size)</div>
        </div>
      ))}
    </Frame>
  )
}

// ---- Results ----------------------------------------------------------------------

function ResultSheet() {
  const { batchId } = useParams()
  const data = useData()
  const b = batchId ? data.idx.batches.get(batchId) : undefined
  if (!b) return <Missing what="Result sheet" />
  const c = data.idx.courses.get(b.courseId)
  const exams = data.exams.filter(x => x.batchId === b.id)
  const enrollments = data.enrollments.filter(e => e.batchId === b.id && e.status !== 'DROPPED')
  const results = batchResults(enrollments, exams, todayStr())
  return (
    <Frame title={`Results ${b.name}`} landscape={exams.length > 4}>
      <div className={`paper ${exams.length > 4 ? 'a4-land' : 'a4'}`}>
        <DocHead />
        <div className="doc-title">Result Sheet</div>
        <p style={{ fontSize: 13, margin: '0 0 8px' }}><b>Course:</b> {c?.name} · <b>Batch:</b> {b.name} · <b>Dates:</b> {fullDate(b.startDate)}{b.endDate ? ` – ${fullDate(b.endDate)}` : ''}</p>
        <table>
          <thead><tr><th>Roll</th><th>Reg. no.</th><th>Name</th>{exams.map(x => <th key={x.id} className="c">{x.name}<br />({x.fullMarks})</th>)}
            <th className="c">Total</th><th className="c">%</th><th className="c">Grade</th><th className="c">GPA</th><th className="c">Pos.</th></tr></thead>
          <tbody>
            {enrollments.map(e => {
              const s = data.idx.students.get(e.studentId)
              const r = results.get(e.id)
              return (
                <tr key={e.id}>
                  <td className="c">{e.rollNo}</td><td>{s?.regNo}</td><td>{s?.name}</td>
                  {exams.map(x => <td key={x.id} className="c">{typeof x.marks[e.id] === 'number' ? x.marks[e.id] : 'Abs'}</td>)}
                  <td className="c">{r ? `${r.obtained}/${r.full}` : '—'}</td><td className="c">{r ? percentLabel(r.percent) : '—'}</td>
                  <td className="c"><b>{r?.grade ?? '—'}</b></td><td className="c">{r ? r.point.toFixed(2) : '—'}</td><td className="c">{r?.position ?? '—'}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
        <p className="muted" style={{ fontSize: 11, marginTop: 8 }}>Grades: A+ 80–100 (5.00), A 70–79 (4.00), A- 60–69 (3.50), B 50–59 (3.00), C 40–49 (2.00), D 33–39 (1.00), F below 33 or below the pass mark in any exam.</p>
        <div className="sig-row"><div className="sig">Instructor</div><div className="sig">Exam controller</div><div className="sig">{data.center?.signatory || 'Director'}</div></div>
      </div>
    </Frame>
  )
}

function MarkSheet() {
  const { enrollmentId } = useParams()
  const data = useData()
  const e = enrollmentId ? data.idx.enrollments.get(enrollmentId) : undefined
  if (!e) return <Missing what="Mark sheet" />
  const s = data.idx.students.get(e.studentId)
  const b = data.idx.batches.get(e.batchId)
  const c = data.idx.courses.get(e.courseId)
  const r = resultsMap(data, todayStr()).get(e.id)
  const a = attendanceMap(data).get(e.id)
  const exams = data.exams.filter(x => x.batchId === e.batchId)
  return (
    <Frame title={`Mark sheet ${s?.name ?? ''}`}>
      <div className="paper a4">
        <DocHead />
        <div className="doc-title">Academic Transcript</div>
        <table style={{ marginBottom: 12 }}>
          <tbody>
            <tr><th>Name</th><td>{s?.name}</td><th>Reg. no.</th><td>{s?.regNo}</td></tr>
            <tr><th>Father's name</th><td>{s?.fatherName}</td><th>Roll</th><td>{e.rollNo}</td></tr>
            <tr><th>Course</th><td>{c?.name}</td><th>Batch</th><td>{b?.name}</td></tr>
            <tr><th>Duration</th><td>{b ? `${fullDate(b.startDate)}${b.endDate ? ` – ${fullDate(b.endDate)}` : ''}` : ''}</td><th>Attendance</th><td>{pct(a?.rate ?? null)}</td></tr>
          </tbody>
        </table>
        <table>
          <thead><tr><th>Exam</th><th>Date</th><th className="c">Full marks</th><th className="c">Pass marks</th><th className="c">Obtained</th><th className="c">Result</th></tr></thead>
          <tbody>
            {exams.map(x => {
              const m = x.marks[e.id]
              return <tr key={x.id}><td>{x.name}</td><td>{fullDate(x.date)}</td><td className="c">{x.fullMarks}</td><td className="c">{x.passMarks}</td>
                <td className="c">{typeof m === 'number' ? m : 'Absent'}</td><td className="c">{typeof m === 'number' ? (m >= x.passMarks ? 'Pass' : 'Fail') : '—'}</td></tr>
            })}
          </tbody>
          {r && <tfoot><tr><th colSpan={4}>Total</th><td className="c"><b>{r.obtained} / {r.full}</b></td><td className="c">{percentLabel(r.percent)}</td></tr></tfoot>}
        </table>
        {r && (
          <div className="row" style={{ gap: 30, marginTop: 16, fontSize: 15 }}>
            <div>Grade: <b style={{ fontSize: 22 }}>{r.grade}</b></div>
            <div>GPA: <b>{r.point.toFixed(2)}</b></div>
            {r.position && <div>Position: <b>{r.position}</b></div>}
            <div>Result: <b>{r.failed ? 'Failed' : 'Passed'}</b></div>
          </div>
        )}
        <div className="sig-row"><div className="sig">Instructor</div><div className="sig">{data.center?.signatory || 'Director'}</div></div>
      </div>
    </Frame>
  )
}

// ---- Admission form / student profile -------------------------------------------------

function AdmissionForm() {
  const { studentId } = useParams()
  const data = useData()
  const money = useMoney()
  const s = studentId ? data.idx.students.get(studentId) : undefined
  const photo = usePhoto(s?.id)
  if (!s) return <Missing what="Admission form" />
  const enrollments = data.enrollments.filter(e => e.studentId === s.id)
  const row = (k: string, v: string) => <tr key={k}><th style={{ width: '32%' }}>{k}</th><td>{v}</td></tr>
  return (
    <Frame title={`Admission form ${s.name}`}>
      <div className="paper a4">
        <DocHead />
        <div className="doc-title">Admission Form</div>
        <div className="row" style={{ alignItems: 'flex-start', gap: 16 }}>
          <table className="grow"><tbody>
            {row('Registration no.', s.regNo)}{row('Student\'s name', s.name)}{row('Father\'s name', s.fatherName)}{row('Mother\'s name', s.motherName)}
            {row('Date of birth', fullDate(s.dob))}{row('Gender', s.gender ? s.gender.charAt(0) + s.gender.slice(1).toLowerCase() : '')}
            {row('Phone', s.phone)}{row('Guardian\'s phone', s.guardianPhone)}{row('Email', s.email)}{row('NID / birth reg.', s.idNo)}
            {row('Education', s.education)}{row('School / college', s.institution)}{row('Address', s.address)}{row('Admission date', fullDate(s.joinDate))}
          </tbody></table>
          <div style={{ width: '35mm', height: '45mm', border: '1px solid #999', display: 'grid', placeItems: 'center', overflow: 'hidden', flex: 'none', fontSize: 11, color: '#888' }}>
            {photo ? <img src={photo} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} /> : 'Photo'}
          </div>
        </div>
        <h3 style={{ fontSize: 14, margin: '16px 0 6px' }}>Courses</h3>
        <table>
          <thead><tr><th>Course</th><th>Batch</th><th>Schedule</th><th>Admitted</th><th className="r">Fee</th><th className="r">Discount</th><th className="r">Payable</th></tr></thead>
          <tbody>
            {enrollments.map(e => {
              const b = data.idx.batches.get(e.batchId)
              return <tr key={e.id}><td>{data.idx.courses.get(e.courseId)?.name}</td><td>{b?.name} (roll {e.rollNo})</td>
                <td>{b ? [daysLabel(b.days), b.time].filter(Boolean).join(' · ') : ''}</td><td>{fullDate(e.date)}</td>
                <td className="r">{money.format(e.fee)}</td><td className="r">{money.format(e.discount)}</td><td className="r">{money.format(Math.max(e.fee - e.discount, 0))}</td></tr>
            })}
          </tbody>
        </table>
        {enrollments.some(e => e.installments.length > 0) && (
          <>
            <h3 style={{ fontSize: 14, margin: '16px 0 6px' }}>Payment plan</h3>
            <table><thead><tr><th>Course</th><th>Due date</th><th className="r">Amount</th></tr></thead>
              <tbody>{enrollments.flatMap(e => e.installments.map((i, n) => (
                <tr key={`${e.id}${n}`}><td>{data.idx.courses.get(e.courseId)?.name}</td><td>{fullDate(i.due)}</td><td className="r">{money.format(i.amount)}</td></tr>
              )))}</tbody></table>
          </>
        )}
        <p style={{ fontSize: 12, marginTop: 18 }}>I declare that the information above is correct, and I agree to follow the rules of {data.center?.name}.</p>
        <div className="sig-row"><div className="sig">Student</div><div className="sig">Guardian</div><div className="sig">{data.center?.signatory || 'Director'}</div></div>
      </div>
    </Frame>
  )
}

// ---- Dues list and attendance register ------------------------------------------------

function DuesList() {
  const data = useData()
  const money = useMoney()
  const [params] = useSearchParams()
  const show = params.get('show') === 'all' ? 'all' : 'overdue'
  const courseId = params.get('course') ?? ''
  const batchId = params.get('batch') ?? ''
  const today = todayStr()
  const rows = feeRows(data, today)
    .filter(r => (show === 'overdue' ? r.fee.overdue > 0 : r.fee.due > 0))
    .filter(r => (!courseId || r.enrollment.courseId === courseId) && (!batchId || r.enrollment.batchId === batchId))
    .sort((a, b) => (a.batch?.name ?? '').localeCompare(b.batch?.name ?? '') || a.enrollment.rollNo - b.enrollment.rollNo)
  return (
    <Frame title="Dues list">
      <div className="paper a4">
        <DocHead />
        <div className="doc-title">{show === 'overdue' ? 'Overdue fees' : 'Fee dues'} · {fullDate(today)}</div>
        <table>
          <thead><tr><th>Batch</th><th>Roll</th><th>Student</th><th>Phone</th><th className="r">Payable</th><th className="r">Paid</th><th className="r">Due</th><th className="r">Overdue</th></tr></thead>
          <tbody>{rows.map(r => (
            <tr key={r.enrollment.id}><td>{r.batch?.name}</td><td className="c">{r.enrollment.rollNo}</td><td>{r.student?.name}</td><td>{r.student?.phone}</td>
              <td className="r">{money.format(r.fee.payable)}</td><td className="r">{money.format(r.fee.paid)}</td><td className="r"><b>{money.format(r.fee.due)}</b></td><td className="r">{money.format(r.fee.overdue)}</td></tr>
          ))}</tbody>
          <tfoot><tr><th colSpan={6}>Total ({rows.length})</th><td className="r"><b>{money.format(rows.reduce((s, r) => s + r.fee.due, 0))}</b></td>
            <td className="r"><b>{money.format(rows.reduce((s, r) => s + r.fee.overdue, 0))}</b></td></tr></tfoot>
        </table>
      </div>
    </Frame>
  )
}

function Register() {
  const { batchId, month } = useParams()
  const data = useData()
  const b = batchId ? data.idx.batches.get(batchId) : undefined
  if (!b || !month) return <Missing what="Register" />
  const sessions = data.attendance.filter(a => a.batchId === b.id && a.date.startsWith(month))
  const byDate = new Map(sessions.map(a => [a.date, a]))
  const days = monthDays(month).filter(d => byDate.has(d) || isClassDay(b, d))
  const roster = data.enrollments.filter(e => e.batchId === b.id && e.status !== 'DROPPED')
  return (
    <Frame title={`Register ${b.name} ${monthLabel(month)}`} landscape>
      <div className="paper a4-land">
        <DocHead compact />
        <div className="doc-title">Attendance Register · {monthLabel(month)}</div>
        <p style={{ fontSize: 12, margin: '0 0 6px' }}><b>Batch:</b> {b.name} · <b>Course:</b> {data.idx.courses.get(b.courseId)?.name} · <b>Days:</b> {daysLabel(b.days)} {b.time}</p>
        <table style={{ fontSize: 10 }}>
          <thead><tr><th>Roll</th><th>Name</th>{days.map(d => <th key={d} className="c" style={{ padding: '2px 3px' }}>{Number(d.slice(8))}<br />{weekdayLabel(d).slice(0, 2)}</th>)}<th className="c">P</th><th className="c">A</th><th className="c">%</th></tr></thead>
          <tbody>{roster.map(e => {
            const st = attendanceOf(e.id, sessions)
            return (
              <tr key={e.id}><td className="c">{e.rollNo}</td><td style={{ whiteSpace: 'nowrap' }}>{data.idx.students.get(e.studentId)?.name}</td>
                {days.map(d => <td key={d} className="c" style={{ padding: '2px 3px' }}>{byDate.get(d)?.marks[e.id] ?? ''}</td>)}
                <td className="c">{st.present + st.late}</td><td className="c">{st.absent}</td><td className="c">{pct(st.rate)}</td></tr>
            )
          })}</tbody>
        </table>
        <p className="muted" style={{ fontSize: 10, marginTop: 6 }}>P present · A absent · L late · E excused (not counted)</p>
      </div>
    </Frame>
  )
}
