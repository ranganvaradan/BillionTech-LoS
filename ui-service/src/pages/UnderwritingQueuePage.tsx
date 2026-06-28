import { ApplicationTable } from '@/components/ApplicationTable'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { useApplications } from '@/hooks/useApplications'

export function UnderwritingQueuePage() {
  const { data, loading, error } = useApplications({ status: 'UNDERWRITING', page: 0, size: 50 })

  return (
    <div>
      <PageHeader
        title="Underwriting"
        description="Applications waiting for a credit or underwriting decision."
      />
      {loading && <LoadingState label="Loading queue…" />}
      {error && <ErrorState message={error} />}
      {data && !loading && (
        <ApplicationTable
          rows={data.content}
          emptyMessage="No applications in underwriting (or you may need to connect the API / seed data)."
        />
      )}
    </div>
  )
}
