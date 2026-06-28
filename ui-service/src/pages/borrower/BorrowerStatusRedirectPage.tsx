import { Navigate, useParams } from 'react-router-dom'
import { isUuid } from '@/lib/format'

/** Preserves old share links: /borrower/status/:id → /borrower/applications/:id */
export function BorrowerStatusRedirectPage() {
  const { applicationId } = useParams<{ applicationId: string }>()
  if (!applicationId || !isUuid(applicationId)) {
    return <Navigate to="/borrower/applications" replace />
  }
  return <Navigate to={`/borrower/applications/${applicationId}`} replace />
}
