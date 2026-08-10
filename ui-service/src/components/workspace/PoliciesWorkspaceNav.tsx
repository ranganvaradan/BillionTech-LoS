import { WorkspaceSubNav } from '@/components/WorkspaceSubNav'
import { policiesWorkspaceItems, showStagingDemoNav } from '@/nav/workspaceNav'

export function PoliciesWorkspaceNav() {
  return (
    <WorkspaceSubNav
      ariaLabel="Policies workspace"
      items={policiesWorkspaceItems({ showDemoSamples: showStagingDemoNav() })}
    />
  )
}
