import { BorrowerIntakeWizard } from '@/components/intake/BorrowerIntakeWizard'

/**
 * Borrower self-service loan application: six steps (loan → personal/address → bank & KYC →
 * income → consents → review) with optional document uploads and local draft save/resume.
 * Staff flows use `ApplicationIntakeWizard` on `/applications/new` and `/sales/applications/new`;
 * all paths share the same create/update API and `intakeMode` metadata for ownership.
 */
export function BorrowerApplyPage() {
  return <BorrowerIntakeWizard />
}
