import { useAuth } from '@/auth/useAuth'
import { isRelationshipManager } from '@/auth/types'
import { WorkspaceSubNav } from '@/components/WorkspaceSubNav'
import { applicationsWorkspaceItems } from '@/nav/workspaceNav'

export function ApplicationsWorkspaceNav() {
  const { user } = useAuth()
  const hideUnderwriting = user ? isRelationshipManager(user.role) : false
  return (
    <WorkspaceSubNav
      ariaLabel="Applications workspace"
      items={applicationsWorkspaceItems({ hideUnderwriting })}
    />
  )
}
