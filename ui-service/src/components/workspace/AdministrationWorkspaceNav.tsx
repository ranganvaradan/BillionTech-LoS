import { Link, useLocation } from 'react-router-dom'
import { administrationGroupLabel, administrationSiblingItems } from '@/nav/workspaceNav'

export function AdministrationWorkspaceNav() {
  const { pathname } = useLocation()
  const group = administrationGroupLabel(pathname)
  const siblings = administrationSiblingItems(pathname)
  const isHub = pathname === '/administration' || pathname.startsWith('/administration/')

  if (isHub) return null

  return (
    <div className="mb-4">
      <p className="mb-2 text-xs text-[var(--bt-gray-500)]">
          <Link to="/administration" className="font-medium text-bt-primary hover:underline">
            Administration
          </Link>
        {group ? <span> · {group}</span> : null}
      </p>
      {siblings.length > 0 ? (
        <nav className="bt-tabs mb-0" aria-label="Administration section">
          {siblings.map((item) => (
            <Link
              key={item.to}
              to={item.to}
              className={pathname === item.to || pathname.startsWith(`${item.to}/`) ? 'bt-tab active' : 'bt-tab'}
            >
              {item.label}
            </Link>
          ))}
        </nav>
      ) : null}
    </div>
  )
}
