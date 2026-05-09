/**
 * Aligns with backend `ApplicationStatus` and `ApplicationResponse` (JSON field names).
 */
export type ApplicationStatus =
  | 'DRAFT'
  | 'CONSENT_PENDING'
  | 'KYC_IN_PROGRESS'
  | 'KYC_FAILED'
  | 'UNDERWRITING'
  | 'UNDERWRITING_COMPLETED'
  | 'APPROVED'
  | 'REJECTED'
  | 'CAM_READY'
  | 'CAM_REVIEWED'
  | 'SANCTION_PENDING'
  | 'SANCTIONED'
  | 'KFS_GENERATED'
  | 'SANCTION_ISSUED'
  | 'ESIGN_PENDING'
  | 'ESIGN_COMPLETED'
  | 'READY_FOR_DISBURSEMENT'
  | 'DISBURSEMENT_PENDING'
  | 'DISBURSED'
  | 'WITHDRAWN'
  | 'ON_HOLD'

export interface ApplicationResponse {
  id: string
  applicationNumber: string
  customerId: string
  borrowerType: string
  loanProduct: string
  requestedAmount: number | null
  interestRate: number | null
  tenureMonths: number | null
  status: ApplicationStatus
  personalInfo: Record<string, unknown> | null
  businessInfo: Record<string, unknown> | null
  financialInfo: Record<string, unknown> | null
  collateralInfo: Record<string, unknown> | null
  remarks: string | null
  assignedTo: string | null
  sanctionedAmount: number | null
  approvedRate: number | null
  disbursedAmount: number | null
  disbursedAt: string | null
  lmsReferenceId: string | null
  esignTransactionId: string | null
  vkycRequired?: boolean | null
  vkycStatus?:
    | 'NOT_STARTED'
    | 'PENDING'
    | 'URL_GENERATED'
    | 'CUSTOMER_JOINED'
    | 'INITIATED'
    | 'AGENT_APPROVED'
    | 'AUDITOR_APPROVED'
    | 'AUDITOR_REJECTED'
    | 'COMPLETED'
    | 'AUTO_DECLINED'
    | 'ERROR'
    | 'REJECTED'
    | 'EXPIRED'
    | 'FAILED'
    | null
  vkycCompletedAt?: string | null
  vkycAgentId?: string | null
  vkycAuditorId?: string | null
  vkycReferenceId?: string | null
  vkycUrl?: string | null
  vkycUrlGeneratedAt?: string | null
  vkycUrlExpiryAt?: string | null
  vkycLastResentAt?: string | null
  vkycResendCount?: number | null
  vkycEmailSent?: boolean | null
  vkycEmailSentAt?: string | null
  vkycGeneratedBy?: string | null
  vkycTransactionId?: string | null
  vkycLastEvent?: string | null
  vkycEventPayload?: string | null
  vkycResultPayload?: string | null
  vkycAgentName?: string | null
  vkycAgentUpdatedOn?: string | null
  vkycCompletedOn?: string | null
  vkycVideoUrl?: string | null
  vkycPanImageUrl?: string | null
  vkycFaceImageUrl?: string | null
  amlHit?: boolean | null
  bureauScore: number | null
  manualBureauScore: number | null
  manualBureauRemarks: string | null
  manualBureauDocumentId: string | null
  creditDecision: string | null
  creditRiskScore: number | null
  createdAt: string | null
  updatedAt: string | null
  submittedAt: string | null
  /** Merged in GET /applications/{id}: provider vs manual + effective. */
  creditControlView?: Record<string, unknown> | null
  latestUnderwritingEvaluation?: Record<string, unknown> | null
}
