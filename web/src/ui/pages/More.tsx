import { NavLink } from 'react-router-dom'
import { useData } from '../../data/store'
import { isAppOwner } from '../../data/approvals'
import { permissions } from '../../domain/roles'
import { NAV, useSession } from '../App'
import { PageHead } from '../common'
import { Icon } from '../icons'

/** Everything that doesn't fit the phone's bottom bar. */
export default function MorePage() {
  const data = useData()
  const { user } = useSession()
  const can = permissions(data.role)
  const owner = isAppOwner(user.email)
  const items = NAV.filter(n => n.show(can, owner) && !['/', '/students', '/batches'].includes(n.to))
  return (
    <div className="page">
      <PageHead title="More" sub={data.center?.name} />
      <div className="card list">
        {items.map(n => (
          <NavLink key={n.to} to={n.to} className="item"><Icon name={n.icon} /><span className="grow title">{n.label}</span><Icon name="right" size={18} /></NavLink>
        ))}
      </div>
    </div>
  )
}
