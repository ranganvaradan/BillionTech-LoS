import { ApplicationIntakeWizard } from '@/components/intake/ApplicationIntakeWizard'

/**
 * Borrower self-service loan application — same wizard as staff intake
 * (workflow-driven fields, KYC visibility, document slots) with borrower portal resume/delegation.
 */
export function BorrowerApplyPage() {
  return <ApplicationIntakeWizard mode="BORROWER_SELF_SERVICE" variant="borrower" />
}
