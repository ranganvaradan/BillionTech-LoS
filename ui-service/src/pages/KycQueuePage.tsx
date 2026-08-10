import { ApplicationTable } from '@/components/ApplicationTable'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { ApplicationsWorkspaceNav } from '@/components/workspace/ApplicationsWorkspaceNav'
import { useApplications } from '@/hooks/useApplications'

export function KycQueuePage() {
  const { data, loading, error } = useApplications({ status: 'KYC_IN_PROGRESS', page: 0, size: 50 })

  return (
    <div>
      <PageHeader
        title="KYC"
        description="Applications that are in identity and verification. Open one to run checks or review results."
      />
      <ApplicationsWorkspaceNav />
      {loading && <LoadingState label="Loading queue…" />}
      {error && <ErrorState message={error} />}
      {data && !loading && (
        <ApplicationTable
          rows={data.content}
          emptyMessage="No applications in the KYC queue right now."
        />
      )}
    </div>
  )
}
