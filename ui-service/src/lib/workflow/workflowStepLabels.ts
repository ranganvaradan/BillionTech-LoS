/**
 * Lender-facing labels for workflow step types and providers.
 * Persisted/API values remain PAN_VERIFY, PERFIOS, etc. — presentation only.
 */

import { WORKFLOW_STEP_TYPES, type WorkflowStepType } from '@/lib/workflowVisual'

const STEP_LABELS: Record<string, string> = {
  PAN_VERIFY: 'PAN Verification',
  AADHAAR_OTP: 'Aadhaar Verification',
  MOBILE_OTP: 'Mobile OTP Verification',
  MNRL: 'Mobile Number Validation (MNRL)',
  EMAIL_OTP: 'Email OTP Verification',
  GSTIN_VERIFY: 'GSTIN Verification',
  VOTER_ID_VERIFY: 'Voter ID Verification',
  DL_VERIFY: 'Driving Licence Verification',
  BANK_PENNY_DROP: 'Bank Account Verification',
  FACE_MATCH: 'Face Match',
  LIVENESS: 'Liveness Check',
  VIDEO_KYC: 'Video KYC',
  VKYC: 'Video KYC',
  CKYC_DOWNLOAD: 'CKYC Download',
  CKYC_UPLOAD: 'CKYC Upload',
  UDYAM_VERIFY: 'Udyam Verification',
  CIN_MCA21: 'CIN / MCA21 Verification',
  AML_SCREENING: 'AML Screening',
  ITR_RETURN_FORMS: 'ITR Return Forms',
  GST_ANALYSIS: 'GST Analysis',
  BUREAU_PULL: 'Bureau Pull',
  ESIGN_KFS: 'eSign KFS',
  ESIGN_AGREEMENT: 'eSign Agreement',
}

const STEP_EXPLANATIONS: Record<string, string> = {
  PAN_VERIFY: "Verify PAN and match the applicant's name.",
  AADHAAR_OTP: 'Verify identity using Aadhaar OTP.',
  MOBILE_OTP: 'Verify the applicant mobile number with a one-time password.',
  MNRL: 'Validate the mobile number against the national registry list where configured.',
  EMAIL_OTP: 'Verify the applicant email address with a one-time password.',
  GSTIN_VERIFY: 'Verify the GST identification number for the entity.',
  VOTER_ID_VERIFY: 'Verify voter ID as a secondary identity document.',
  DL_VERIFY: 'Verify driving licence as a secondary identity document.',
  BANK_PENNY_DROP: 'Confirm bank account ownership with a penny-drop verification.',
  FACE_MATCH: 'Match the applicant face against an identity photo where configured.',
  LIVENESS: 'Confirm the applicant is physically present during capture.',
  VIDEO_KYC: 'Complete video KYC with an agent where required.',
  VKYC: 'Complete video KYC with an agent where required.',
  CKYC_DOWNLOAD: 'Download existing CKYC records where available.',
  CKYC_UPLOAD: 'Upload KYC details to CKYC where required.',
  UDYAM_VERIFY: 'Verify Udyam registration for the business.',
  CIN_MCA21: 'Verify company identification via MCA21.',
  AML_SCREENING: 'Screen the applicant against AML watchlists where configured.',
  ITR_RETURN_FORMS: 'Collect or verify ITR return forms where configured.',
  GST_ANALYSIS: 'Analyse GST filings where configured.',
  BUREAU_PULL: 'Pull bureau data after identity checks where operationally sequenced.',
  ESIGN_KFS: 'Electronically sign the Key Fact Statement.',
  ESIGN_AGREEMENT: 'Electronically sign the loan agreement.',
}

const PROVIDER_LABELS: Record<string, string> = {
  PERFIOS: 'Perfios',
  AUTHBRIDGE: 'Authbridge',
  KARZA: 'Karza',
  EQUIFAX: 'Equifax',
  CIBIL: 'CIBIL',
  CRIF: 'CRIF',
  EXPERIAN: 'Experian',
  SUREPASS: 'Surepass',
  DIGIO: 'Digio',
  LEEGALITY: 'Leegality',
  HYPERVERGE: 'HyperVerge',
}

/** Business label for a persisted workflow step type code. */
export function workflowStepBusinessLabel(step: string | null | undefined): string {
  const code = String(step ?? '').trim().toUpperCase()
  if (!code) return 'Step'
  return STEP_LABELS[code] ?? humanizeCode(code)
}

/** Short plain-English explanation for the step type. */
export function workflowStepExplanation(step: string | null | undefined): string {
  const code = String(step ?? '').trim().toUpperCase()
  return STEP_EXPLANATIONS[code] ?? 'Complete this verification step as configured.'
}

/** Business label for a persisted provider code (value attribute stays the code). */
export function workflowProviderBusinessLabel(provider: string | null | undefined): string {
  const code = String(provider ?? '').trim().toUpperCase()
  if (!code) return ''
  return PROVIDER_LABELS[code] ?? humanizeCode(code)
}

export function workflowStepSelectOptions(): { value: string; label: string }[] {
  return (WORKFLOW_STEP_TYPES as readonly WorkflowStepType[]).map((t) => ({
    value: t,
    label: workflowStepBusinessLabel(t),
  }))
}

function humanizeCode(code: string): string {
  return code
    .split(/[_-]+/)
    .filter(Boolean)
    .map((w) => w.charAt(0) + w.slice(1).toLowerCase())
    .join(' ')
}
