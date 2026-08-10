import { WorkspaceSubNav } from '@/components/WorkspaceSubNav'
import { PROGRAMS_WORKSPACE_ITEMS } from '@/nav/workspaceNav'

export function ProgramsWorkspaceNav() {
  return <WorkspaceSubNav ariaLabel="Programs workspace" items={PROGRAMS_WORKSPACE_ITEMS} />
}
