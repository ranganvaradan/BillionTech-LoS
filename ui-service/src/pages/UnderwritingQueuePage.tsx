import { ApplicationTable } from '@/components/ApplicationTable'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { ApplicationsWorkspaceNav } from '@/components/workspace/ApplicationsWorkspaceNav'
import { useApplications } from '@/hooks/useApplications'

export function UnderwritingQueuePage() {
  const { data, loading, error } = useApplications({ status: 'UNDERWRITING', page: 0, size: 50 })

  return (
    <div>
      <PageHeader
        title="Credit Assessment"
        description="Applications waiting for credit assessment or review."
      />
      <ApplicationsWorkspaceNav />
      {loading && <LoadingState label="Loading queue…" />}
      {error && <ErrorState message={error} />}
      {data && !loading && (
        <ApplicationTable
          rows={data.content}
          emptyMessage="No applications awaiting credit assessment right now."
        />
      )}
    </div>
  )
}
