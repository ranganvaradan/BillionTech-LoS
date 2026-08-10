export const STATUS_LABELS: Record<string, string> = {
  DRAFT: 'Draft',
  CONSENT_PENDING: 'Consent pending',
  BORROWER_SUBMITTED: 'Borrower submitted',
  PENDING_CREDIT_OFFICER: 'Pending credit officer',
  SENT_BACK_TO_RM: 'Sent back to RM',
  BORROWER_SENT_BACK: 'Sent back to borrower',
  KYC_IN_PROGRESS: 'KYC in progress',
  KYC_FAILED: 'KYC failed',
  UNDERWRITING: 'In review',
  UNDERWRITING_COMPLETED: 'Assessment completed',
  APPROVED: 'Approved',
  REJECTED: 'Declined',
  CAM_READY: 'CAM ready',
  CAM_SENT_BACK: 'CAM sent back',
  CAM_REVIEWED: 'CAM reviewed',
  SANCTION_PENDING: 'Sanction pending',
  SANCTIONED: 'Sanctioned',
  KFS_GENERATED: 'KFS generated',
  SANCTION_ISSUED: 'Sanction issued',
  ESIGN_PENDING: 'eSign pending',
  ESIGN_COMPLETED: 'eSign completed',
  READY_FOR_DISBURSEMENT: 'Ready for disbursement',
  DISBURSEMENT_PENDING: 'Disbursement pending',
  DISBURSED: 'Completed',
  WITHDRAWN: 'Withdrawn',
  ON_HOLD: 'On hold',
}

/** Status filter options for Applications list (values remain API enums). */
export const APPLICATION_STATUS_FILTER_OPTIONS: string[] = [
  '',
  'DRAFT',
  'CONSENT_PENDING',
  'BORROWER_SUBMITTED',
  'PENDING_CREDIT_OFFICER',
  'SENT_BACK_TO_RM',
  'BORROWER_SENT_BACK',
  'KYC_IN_PROGRESS',
  'KYC_FAILED',
  'UNDERWRITING',
  'UNDERWRITING_COMPLETED',
  'CAM_READY',
  'CAM_SENT_BACK',
  'CAM_REVIEWED',
  'APPROVED',
  'SANCTION_PENDING',
  'SANCTIONED',
  'KFS_GENERATED',
  'SANCTION_ISSUED',
  'ESIGN_PENDING',
  'ESIGN_COMPLETED',
  'READY_FOR_DISBURSEMENT',
  'DISBURSEMENT_PENDING',
  'DISBURSED',
  'REJECTED',
  'WITHDRAWN',
  'ON_HOLD',
]

function formatSnakeCaseLabel(key: string): string {
  return key
    .split('_')
    .filter(Boolean)
    .map((word) => {
      const upper = word.toUpperCase()
      if (upper === 'KYC' || upper === 'CAM' || upper === 'KFS' || upper === 'RM') return upper
      if (upper === 'ESIGN') return 'eSign'
      return word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()
    })
    .join(' ')
}

/** Human labels for application status codes and legacy dashboard keys. */
export function formatStatusLabel(key: string): string {
  const k = key.trim()
  if (STATUS_LABELS[k]) return STATUS_LABELS[k]
  if (k.includes('_')) return formatSnakeCaseLabel(k)
  return k
    .replace(/([A-Z])/g, ' $1')
    .replace(/^./, (s) => s.toUpperCase())
    .trim()
}
