import { ApplicationTable } from '@/components/ApplicationTable'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { useApplications } from '@/hooks/useApplications'

export function KycQueuePage() {
  const { data, loading, error } = useApplications({ status: 'KYC_IN_PROGRESS', page: 0, size: 50 })

  return (
    <div>
      <PageHeader
        title="KYC in progress"
        description="Applications that are in identity and verification. Open one to run checks or review results."
      />
      {loading && <LoadingState label="Loading queue…" />}
      {error && <ErrorState message={error} />}
      {data && !loading && (
        <ApplicationTable
          rows={data.content}
          emptyMessage="No applications in KYC queue (or you may need to connect the API / seed data)."
        />
      )}
    </div>
  )
}
