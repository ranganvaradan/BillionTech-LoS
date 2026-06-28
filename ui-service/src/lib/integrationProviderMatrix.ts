/**
 * Default LOS integration provider hierarchy (aligned with {@code V27} aggregator_routing seed).
 * Drives workflow editor options and help text; backend routing uses DB rows.
 */

export type MatrixStep = {
  step: string
  purpose: string
  appliesTo: string
  /** Priority order: index 0 = primary, then fallbacks */
  providers: string[]
}

export const INTEGRATION_MATRIX: MatrixStep[] = [
  {
    step: 'AADHAAR_OTP',
    purpose: 'Aadhaar eKYC via OTP',
    appliesTo: 'All / secondary ID',
    providers: ['PERFIOS', 'AUTHBRIDGE'],
  },
  {
    step: 'PAN_VERIFY',
    purpose: 'PAN verification and name match',
    appliesTo: 'All',
    providers: ['PERFIOS', 'AUTHBRIDGE'],
  },
  {
    step: 'GSTIN_VERIFY',
    purpose: 'GSTIN validation',
    appliesTo: 'Proprietor, Partnership, Company',
    providers: ['PERFIOS', 'AUTHBRIDGE'],
  },
  {
    step: 'VOTER_ID_VERIFY',
    purpose: 'Voter ID verification',
    appliesTo: 'Optional secondary ID',
    providers: ['PERFIOS', 'AUTHBRIDGE'],
  },
  {
    step: 'DL_VERIFY',
    purpose: 'Driving License verification',
    appliesTo: 'Optional secondary ID',
    providers: ['PERFIOS', 'AUTHBRIDGE'],
  },
  {
    step: 'BANK_PENNY_DROP',
    purpose: 'Bank account verification',
    appliesTo: 'All',
    providers: ['PERFIOS', 'AUTHBRIDGE'],
  },
  {
    step: 'FACE_MATCH',
    purpose: 'Selfie vs Aadhaar face match',
    appliesTo: 'Individual, Proprietor',
    providers: ['HYPERVERGE'],
  },
  {
    step: 'LIVENESS',
    purpose: 'Liveness detection',
    appliesTo: 'Individual, Proprietor',
    providers: ['HYPERVERGE'],
  },
  {
    step: 'VIDEO_KYC',
    purpose: 'V-CIP',
    appliesTo: 'On-demand',
    providers: ['HYPERVERGE'],
  },
  {
    step: 'UDYAM_VERIFY',
    purpose: 'Udyam registration verification',
    appliesTo: 'MSME loans',
    providers: ['PERFIOS', 'AUTHBRIDGE'],
  },
  {
    step: 'CIN_MCA21',
    purpose: 'Company/director verification',
    appliesTo: 'Company',
    providers: ['PERFIOS', 'AUTHBRIDGE'],
  },
  {
    step: 'AML_SCREENING',
    purpose: 'AML check',
    appliesTo: 'All',
    providers: ['PERFIOS', 'AUTHBRIDGE'],
  },
  {
    step: 'CKYC_DOWNLOAD',
    purpose: 'Central KYC record download',
    appliesTo: 'All',
    providers: ['CKYC_REGISTRY'],
  },
  {
    step: 'CKYC_UPLOAD',
    purpose: 'Central KYC record upload',
    appliesTo: 'Post-KYC completion',
    providers: ['CKYC_REGISTRY'],
  },
  {
    step: 'MOBILE_OTP',
    purpose: 'Mobile OTP',
    appliesTo: 'All',
    providers: ['PERFIOS', 'AUTHBRIDGE'],
  },
  {
    step: 'MNRL',
    purpose: 'Mobile number risk lookup',
    appliesTo: 'All',
    providers: ['KARZA', 'PERFIOS', 'AUTHBRIDGE'],
  },
  {
    step: 'EMAIL_OTP',
    purpose: 'Email OTP',
    appliesTo: 'All',
    providers: ['PERFIOS', 'AUTHBRIDGE'],
  },
  {
    step: 'BUREAU_PULL',
    purpose: 'Credit bureau report pull',
    appliesTo: 'All post-KYC',
    providers: ['EQUIFAX'],
  },
  {
    step: 'ESIGN_KFS',
    purpose: 'KFS digital signing',
    appliesTo: 'All post-approval',
    providers: ['EMSIGNER', 'AUTHBRIDGE_ESIGN'],
  },
  {
    step: 'ESIGN_AGREEMENT',
    purpose: 'Loan agreement signing',
    appliesTo: 'All post-KFS',
    providers: ['EMSIGNER', 'AUTHBRIDGE_ESIGN'],
  },
]

const STEP_MAP = new Map(INTEGRATION_MATRIX.map((m) => [m.step, m]))

export function matrixForStep(step: string): MatrixStep | undefined {
  return STEP_MAP.get(step)
}

export function providersForWorkflowStep(step: string): string[] {
  return matrixForStep(step)?.providers ?? ['PERFIOS', 'AUTHBRIDGE']
}

export function defaultProviderForMatrixStep(step: string): string {
  const p = providersForWorkflowStep(step)
  return p[0] ?? 'PERFIOS'
}

export function normalizeProviderForStep(step: string, provider: string): string {
  const allowed = new Set(providersForWorkflowStep(step))
  if (allowed.has(provider)) return provider
  return defaultProviderForMatrixStep(step)
}
