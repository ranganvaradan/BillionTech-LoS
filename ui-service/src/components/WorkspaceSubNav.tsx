import { NavLink, useNavigate } from 'react-router-dom'
import type { WorkspaceNavItem } from '@/nav/workspaceNav'

type Props = {
  items: WorkspaceNavItem[]
  ariaLabel: string
  /** Optional context line above the tabs (e.g. Administration › Live Underwriting). */
  contextLabel?: string | null
}

export function WorkspaceSubNav({ items, ariaLabel, contextLabel }: Props) {
  const navigate = useNavigate()
  if (items.length === 0) return null

  return (
    <div className="mb-4">
      {contextLabel ? (
        <p className="mb-2 text-xs font-medium uppercase tracking-wide text-[var(--bt-gray-500)]">{contextLabel}</p>
      ) : null}
      <nav className="bt-tabs mb-0" aria-label={ariaLabel}>
        {items.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            onClick={(e) => {
              if (item.to !== '/credit-intelligence/policy-studio') return
              e.preventDefault()
              navigate('/credit-intelligence/policy-studio', {
                state: { openPoliciesLanding: true, ts: Date.now() },
              })
            }}
            className={({ isActive }) => (isActive ? 'bt-tab active' : 'bt-tab')}
          >
            {item.label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
