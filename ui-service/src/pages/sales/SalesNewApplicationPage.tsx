import { Link } from 'react-router-dom'
import { ApplicationIntakeWizard } from '@/components/intake/ApplicationIntakeWizard'
import { useAuth } from '@/auth/useAuth'
import { canCreateOrNotifyBorrowerIntake } from '@/auth/types'
import { ErrorState } from '@/components/ErrorState'
import { PageHeader } from '@/components/PageHeader'

export function SalesNewApplicationPage() {
  const { user } = useAuth()
  if (!canCreateOrNotifyBorrowerIntake(user?.role ?? '')) {
    return (
      <div className="space-y-4">
        <PageHeader title="New application (sales)" description="Sales-assisted intake." />
        <ErrorState message="Only relationship managers and administrators can create applications." />
        <Link to="/applications" className="text-sm text-slate-600 underline">
          Back to applications
        </Link>
      </div>
    )
  }
  return <ApplicationIntakeWizard mode="SALES_ASSISTED" variant="staff" />
}
