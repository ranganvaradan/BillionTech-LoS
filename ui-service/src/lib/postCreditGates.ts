import type { ApplicationResponse, ApplicationStatus } from '@/types/application'

const CAM_REVIEWED_ONWARD: ApplicationStatus[] = [
  'CAM_REVIEWED',
  'SANCTION_PENDING',
  'SANCTIONED',
  'KFS_GENERATED',
  'SANCTION_ISSUED',
  'ESIGN_PENDING',
  'ESIGN_COMPLETED',
  'READY_FOR_DISBURSEMENT',
  'DISBURSEMENT_PENDING',
  'DISBURSED',
]

const KFS_ONWARD: ApplicationStatus[] = [
  'KFS_GENERATED',
  'SANCTION_ISSUED',
  'ESIGN_PENDING',
  'ESIGN_COMPLETED',
  'READY_FOR_DISBURSEMENT',
  'DISBURSEMENT_PENDING',
  'DISBURSED',
]

const ESIGN_DONE: ApplicationStatus[] = [
  'ESIGN_COMPLETED',
  'READY_FOR_DISBURSEMENT',
  'DISBURSEMENT_PENDING',
  'DISBURSED',
]

export function isKycCompleteForDisbursement(status: ApplicationStatus): boolean {
  return !['DRAFT', 'CONSENT_PENDING', 'KYC_IN_PROGRESS', 'KYC_FAILED'].includes(status)
}

export function isUnderwritingSettled(app: ApplicationResponse): boolean {
  const s = app.status
  if (s === 'UNDERWRITING') {
    return false
  }
  if (!isKycCompleteForDisbursement(s)) {
    return false
  }
  if (s === 'REJECTED') {
    return app.creditDecision != null
  }
  return true
}

export function isCamReviewedForDisbursement(status: ApplicationStatus): boolean {
  return CAM_REVIEWED_ONWARD.includes(status) || status === 'APPROVED'
}

export function isSanctionIssued(status: ApplicationStatus): boolean {
  return (
    ['SANCTIONED', 'KFS_GENERATED', 'SANCTION_ISSUED', 'ESIGN_PENDING', 'ESIGN_COMPLETED'].includes(
      status,
    ) || ['READY_FOR_DISBURSEMENT', 'DISBURSEMENT_PENDING', 'DISBURSED'].includes(status)
  )
}

export function isKfsGeneratedForDisbursement(status: ApplicationStatus): boolean {
  return KFS_ONWARD.includes(status) || status === 'DISBURSED'
}

export function isEsignCompleteForDisbursement(status: ApplicationStatus): boolean {
  return ESIGN_DONE.includes(status) || status === 'DISBURSED'
}

export function disburseChecklist(app: ApplicationResponse) {
  const s = app.status
  return {
    kyc: isKycCompleteForDisbursement(s),
    underwriting: isUnderwritingSettled(app),
    cam: isCamReviewedForDisbursement(s),
    sanction: isSanctionIssued(s),
    kfs: isKfsGeneratedForDisbursement(s),
    esign: isEsignCompleteForDisbursement(s),
  }
}
