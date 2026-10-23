import { useNavigate } from 'react-router-dom'
import { useData } from '../../data/store'
import { batchStatus } from '../../domain/batches'
import { Empty, PageHead, useMoney, usePermissions, useToday } from '../common'
import { useDialogs } from '../dialogs/host'
import { Icon } from '../icons'

export default function CoursesPage() {
  const data = useData()
  const money = useMoney()
  const can = usePermissions()
  const navigate = useNavigate()
  const { open } = useDialogs()
  const today = useToday()
  return (
    <div className="page">
      <PageHead title="Courses" sub={`${data.courses.filter(c => !c.archived).length} offered`}>
        {can.manageSetup && <button className="btn" onClick={() => open({ kind: 'course' })} data-testid="add-course"><Icon name="plus" size={18} />Course</button>}
      </PageHead>
      {data.courses.length === 0 ? (
        <div className="card"><Empty emoji="📚" title="No courses yet" body="Add the courses you teach, with their fee and duration. Batches and admissions come next.">
          {can.manageSetup && <button className="btn" onClick={() => open({ kind: 'course' })}>Add a course</button>}
        </Empty></div>
      ) : (
        <div className="card table-wrap">
          <table className="table">
            <thead><tr><th>Course</th><th>Code</th><th>Duration</th>{can.seeMoney && <th className="r">Fee</th>}<th className="r">Batches</th><th className="r">Students</th><th /></tr></thead>
            <tbody>
              {data.courses.map(c => {
                const batches = data.batches.filter(b => b.courseId === c.id)
                const running = batches.filter(b => batchStatus(b, today) === 'RUNNING').length
                const students = data.enrollments.filter(e => e.courseId === c.id && e.status === 'ACTIVE').length
                return (
                  <tr key={c.id} className="click" onClick={() => navigate(`/batches?course=${c.id}`)} style={c.archived ? { opacity: 0.55 } : undefined}>
                    <td><b>{c.name}</b>{c.archived && <span className="pill" style={{ marginLeft: 6 }}>Archived</span>}{c.description && <div className="small muted ellipsis" style={{ maxWidth: 360 }}>{c.description}</div>}</td>
                    <td>{c.code}</td>
                    <td>{c.duration}</td>
                    {can.seeMoney && <td className="r num">{money.format(c.fee)}</td>}
                    <td className="r num">{batches.length}{running ? <span className="small muted"> ({running} running)</span> : null}</td>
                    <td className="r num">{students}</td>
                    <td className="r" onClick={e => e.stopPropagation()}>
                      {can.manageSetup && <button className="icon-btn" aria-label="Edit" onClick={() => open({ kind: 'course', id: c.id })}><Icon name="edit" size={18} /></button>}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
