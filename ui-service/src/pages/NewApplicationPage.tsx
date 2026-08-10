import { Link } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { canCreateOrNotifyBorrowerIntake } from '@/auth/types'
import { ApplicationIntakeWizard } from '@/components/intake/ApplicationIntakeWizard'
import { ErrorState } from '@/components/ErrorState'
import { PageHeader } from '@/components/PageHeader'

export function NewApplicationPage() {
  const { user } = useAuth()
  if (!canCreateOrNotifyBorrowerIntake(user?.role ?? '')) {
    return (
      <div className="space-y-4">
        <PageHeader title="New application" description="Create a loan application draft." />
        <ErrorState message="Only relationship managers and administrators can create applications. Credit officers can process existing cases." />
        <Link to="/applications" className="text-sm text-slate-600 underline">
          Back to applications
        </Link>
      </div>
    )
  }
  return <ApplicationIntakeWizard mode="ADMIN_INTERNAL" variant="staff" />
}
