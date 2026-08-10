import type { ApplicationStatus } from '@/types/application'

/** Drafts the borrower may delete from the portal (not yet submitted to processing). */
export function isBorrowerDeletableApplicationStatus(s: ApplicationStatus): boolean {
  return s === 'DRAFT' || s === 'CONSENT_PENDING'
}

/** Applications the borrower can resume in the intake wizard. */
export function isBorrowerResumableIntakeStatus(s: ApplicationStatus): boolean {
  return s === 'DRAFT' || s === 'CONSENT_PENDING' || s === 'BORROWER_SENT_BACK'
}

export function isDelegatedBorrowerIntake(app: {
  status: ApplicationStatus
  intakeOwner?: string | null
}): boolean {
  if (app.status !== 'CONSENT_PENDING' && app.status !== 'BORROWER_SENT_BACK') return false
  // Prefer explicit BORROWER owner; if API/DB omitted intakeOwner, still use delegated submit
  // for these statuses (staff notify leaves apps in CONSENT_PENDING for the borrower portal).
  return !app.intakeOwner || app.intakeOwner === 'BORROWER'
}
