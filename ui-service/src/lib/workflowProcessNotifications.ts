import type { StepNotificationConfig, VisualWorkflowStep } from '@/lib/workflowVisual'

export type ProcessNotificationConfig = {
  id: string
  processCode: string
  eventType: string
  channel: string
  templateCode: string
  recipientType: string
  delaySeconds: number
  enabled: boolean
}

export type ProcessDefinition = {
  code: string
  label: string
  events: string[]
}

export const PROCESS_DEFINITIONS: ProcessDefinition[] = [
  { code: 'KYC', label: 'KYC', events: ['KYC_SUCCESS', 'KYC_FAILED', 'KYC_PENDING'] },
  { code: 'UNDERWRITING', label: 'Underwriting', events: ['UNDERWRITING_STARTED', 'UNDERWRITING_APPROVED', 'UNDERWRITING_REJECTED'] },
  { code: 'CAM', label: 'CAM', events: ['CAM_READY', 'CAM_REVIEWED', 'CAM_REJECTED'] },
  {
    code: 'VKYC',
    label: 'VKYC',
    events: ['VKYC_LINK', 'VKYC_APPROVED', 'VKYC_REJECTED', 'VKYC_EXPIRY_REMINDER', 'VKYC_COMPLETED_VIA_PKY'],
  },
  { code: 'ESIGN', label: 'eSign', events: ['ESIGN_LINK', 'ESIGN_REMINDER', 'ESIGN_COMPLETED', 'ESIGN_EXPIRED'] },
  { code: 'SANCTION', label: 'Sanction', events: ['SANCTION_APPROVED', 'SANCTION_REJECTED'] },
  { code: 'DISBURSEMENT', label: 'Disbursement', events: ['DISBURSEMENT_INITIATED', 'DISBURSEMENT_COMPLETED', 'DISBURSEMENT_FAILED'] },
  { code: 'WELCOME', label: 'Welcome', events: ['WELCOME_CUSTOMER'] },
  { code: 'REJECTION', label: 'Rejection', events: ['APPLICATION_REJECTED'] },
  { code: 'REPAYMENT', label: 'Repayment', events: ['REMINDER'] },
  { code: 'DOCUMENT_COLLECTION', label: 'Document Collection', events: ['DOC_REQUESTED', 'DOC_PENDING'] },
  { code: 'LOAN_ACTIVATION', label: 'Loan Activation', events: ['LOAN_ACTIVATED'] },
]

const STEP_TO_PROCESS: Record<string, string> = {
  PAN_VERIFY: 'KYC',
  AADHAAR_OTP: 'KYC',
  MOBILE_OTP: 'KYC',
  EMAIL_OTP: 'KYC',
  GSTIN_VERIFY: 'KYC',
  VOTER_ID_VERIFY: 'KYC',
  DL_VERIFY: 'KYC',
  BANK_PENNY_DROP: 'KYC',
  MNRL: 'KYC',
  FACE_MATCH: 'KYC',
  LIVENESS: 'KYC',
  CKYC_DOWNLOAD: 'KYC',
  CKYC_UPLOAD: 'KYC',
  UDYAM_VERIFY: 'KYC',
  CIN_MCA21: 'KYC',
  AML_SCREENING: 'KYC',
  VIDEO_KYC: 'VKYC',
  BUREAU_PULL: 'UNDERWRITING',
  ESIGN_KFS: 'ESIGN',
  ESIGN_AGREEMENT: 'ESIGN',
}

function newId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `pn-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export function processCodeForWorkflowStep(step: string): string {
  const n = step.trim().toUpperCase()
  return STEP_TO_PROCESS[n] ?? n
}

export function parseProcessNotifications(raw: unknown): ProcessNotificationConfig[] {
  if (!Array.isArray(raw)) return []
  return raw.map((item) => {
    const m = item && typeof item === 'object' && !Array.isArray(item) ? (item as Record<string, unknown>) : {}
    return {
      id: String(m.id ?? newId()),
      processCode: String(m.processCode ?? '').trim().toUpperCase(),
      eventType: String(m.eventType ?? '').trim().toUpperCase(),
      channel: String(m.channel ?? 'EMAIL').trim().toUpperCase(),
      templateCode: String(m.templateCode ?? '').trim(),
      recipientType: String(m.recipientType ?? 'BORROWER_EMAIL').trim().toUpperCase(),
      delaySeconds: typeof m.delaySeconds === 'number' ? m.delaySeconds : 0,
      enabled: m.enabled !== false,
    }
  })
}

export function processNotificationsToJsonArray(list: ProcessNotificationConfig[]): Record<string, unknown>[] {
  return list.map((n, idx) => ({
    id: n.id || newId(),
    processCode: n.processCode.trim().toUpperCase(),
    eventType: n.eventType.trim().toUpperCase(),
    channel: n.channel.trim().toUpperCase(),
    templateCode: n.templateCode.trim(),
    recipientType: n.recipientType.trim().toUpperCase() || 'BORROWER_EMAIL',
    delaySeconds: Number.isFinite(n.delaySeconds) ? Math.max(0, n.delaySeconds) : 0,
    enabled: n.enabled,
    order: idx + 1,
  }))
}

/**
 * Compatibility helper: derive first-pass process mappings from legacy step-level notifications.
 * Used only when workflow has no saved process mappings yet.
 */
export function deriveProcessNotificationsFromSteps(steps: VisualWorkflowStep[]): ProcessNotificationConfig[] {
  const out: ProcessNotificationConfig[] = []
  const seen = new Set<string>()
  for (const s of steps) {
    const processCode = processCodeForWorkflowStep(s.step)
    for (const n of s.notifications) {
      const key = `${processCode}|${n.eventType.trim().toUpperCase()}|${n.channel.trim().toUpperCase()}`
      if (seen.has(key)) continue
      seen.add(key)
      out.push(stepNotificationToProcess(processCode, n))
    }
  }
  return out
}

function stepNotificationToProcess(processCode: string, n: StepNotificationConfig): ProcessNotificationConfig {
  return {
    id: n.id || newId(),
    processCode: processCode.trim().toUpperCase(),
    eventType: n.eventType.trim().toUpperCase(),
    channel: n.channel.trim().toUpperCase(),
    templateCode: n.templateCode.trim(),
    recipientType: n.recipientType.trim().toUpperCase() || 'BORROWER_EMAIL',
    delaySeconds: Number.isFinite(n.delaySeconds) ? Math.max(0, n.delaySeconds) : 0,
    enabled: n.enabled,
  }
}

