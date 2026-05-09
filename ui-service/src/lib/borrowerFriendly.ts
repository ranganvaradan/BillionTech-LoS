import type { ApplicationStatus } from '@/types/application'

/**
 * One-line label for the application (no internal LOS jargon).
 */
export function friendlyStatusHeadline(status: ApplicationStatus): string {
  const m: Record<ApplicationStatus, string> = {
    DRAFT: 'Application in progress',
    CONSENT_PENDING: 'Waiting for your consent',
    KYC_IN_PROGRESS: 'Verification in progress',
    KYC_FAILED: 'We need a bit more information',
    UNDERWRITING: 'We are reviewing your application',
    UNDERWRITING_COMPLETED: 'We are preparing your file',
    APPROVED: 'Loan approved',
    REJECTED: 'Application did not go through',
    SANCTION_ISSUED: 'Your terms are being prepared',
    CAM_READY: 'We are finalising the credit summary',
    CAM_REVIEWED: 'Moving to the next step',
    SANCTION_PENDING: 'Final approval in progress',
    SANCTIONED: 'Your loan is approved on agreed terms',
    KFS_GENERATED: 'Key facts are ready to review and sign',
    ESIGN_PENDING: 'Agreement pending signature',
    ESIGN_COMPLETED: 'Agreement signed',
    READY_FOR_DISBURSEMENT: 'Disbursement in progress',
    DISBURSEMENT_PENDING: 'Disbursement in progress',
    DISBURSED: 'Loan disbursed',
    WITHDRAWN: 'Application withdrawn',
    ON_HOLD: 'We have paused for now',
  }
  return m[status] ?? 'Processing'
}
