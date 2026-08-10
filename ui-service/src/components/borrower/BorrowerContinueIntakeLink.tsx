import { Link } from 'react-router-dom'
import type { ApplicationStatus } from '@/types/application'
import { isBorrowerResumableIntakeStatus } from '@/lib/borrowerApplicationDeletable'

export function BorrowerContinueIntakeLink({
  applicationId,
  status,
  label = 'Continue application',
  className = 'rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white',
}: {
  applicationId: string
  status: ApplicationStatus
  label?: string
  className?: string
}) {
  if (!isBorrowerResumableIntakeStatus(status)) return null
  return (
    <Link to={`/borrower/apply?resume=${applicationId}`} className={className}>
      {label}
    </Link>
  )
}
