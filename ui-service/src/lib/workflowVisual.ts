/**
 * Aligned with backend workflow JSON: `KycOrchestrationServiceImpl` / `KycIdentityWorkflow` — identity
 * steps run in the KYC sub-workflow; `BUREAU_PULL` is a separate flow step (after KYC success).
 */
import {
  defaultProviderForMatrixStep,
  normalizeProviderForStep,
  providersForWorkflowStep,
  type MatrixStep,
  INTEGRATION_MATRIX,
} from '@/lib/integrationProviderMatrix'

/** Identity & verification — executed inside {@code KYC_WORKFLOW} (Kyc sub-workflow) */
export const KYC_IDENTITY_WORKFLOW_STEPS = [
  'PAN_VERIFY',
  'AADHAAR_OTP',
  'MOBILE_OTP',
  'MNRL',
  'EMAIL_OTP',
  'GSTIN_VERIFY',
  'VOTER_ID_VERIFY',
  'DL_VERIFY',
  'BANK_PENNY_DROP',
  'FACE_MATCH',
  'LIVENESS',
  'VIDEO_KYC',
  'CKYC_DOWNLOAD',
  'CKYC_UPLOAD',
  'UDYAM_VERIFY',
  'CIN_MCA21',
  'AML_SCREENING',
] as const

export const POST_KYC_WORKFLOW_STEPS = ['BUREAU_PULL', 'ESIGN_KFS', 'ESIGN_AGREEMENT'] as const

export const WORKFLOW_STEP_TYPES = [
  ...KYC_IDENTITY_WORKFLOW_STEPS,
  ...POST_KYC_WORKFLOW_STEPS,
] as const

export type WorkflowStepType = (typeof WORKFLOW_STEP_TYPES)[number]

/** Union of all provider codes that may appear in the workflow JSON (per integration matrix + bureau). */
export const ALL_WORKFLOW_PROVIDER_CODES = [
  ...new Set(INTEGRATION_MATRIX.flatMap((m) => m.providers)),
] as const

export function isPostKycWorkflowStep(step: string): boolean {
  return (POST_KYC_WORKFLOW_STEPS as readonly string[]).includes(step)
}

/** Default `steps[].provider` in JSON when the user leaves provider blank. */
export function defaultProviderForWorkflowStep(step: string): string {
  return defaultProviderForMatrixStep(step)
}

const KNOWN_KEYS = new Set([
  'step',
  'name',
  'provider',
  'mandatory',
  'order',
  'notifications',
  'allowPhysicalKycFallback',
])

export interface StepNotificationConfig {
  id: string
  enabled: boolean
  eventType: string
  channel: string
  templateCode: string
  recipientType: string
  delaySeconds: number
}

export interface VisualWorkflowStep {
  id: string
  step: string
  /** Optional display / label (JSON `name` when set) */
  name: string
  provider: string
  mandatory: boolean
  order: number
  notifications: StepNotificationConfig[]
  /** When {@code VIDEO_KYC} / {@code VKYC} exists: allow PKYC fallback completion path (runtime gated). */
  allowPhysicalKycFallback: boolean
  /** Unrecognized fields preserved for power users (merged into each step object on save) */
  extra: Record<string, unknown>
}

function newId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `s-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function extraFromMap(m: Record<string, unknown>): Record<string, unknown> {
  const o: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(m)) {
    if (!KNOWN_KEYS.has(k)) o[k] = v
  }
  return o
}

function parseNotifications(raw: unknown): StepNotificationConfig[] {
  if (!Array.isArray(raw)) return []
  return raw.map((item) => {
    const m = item && typeof item === 'object' && !Array.isArray(item) ? (item as Record<string, unknown>) : {}
    return {
      id: String(m.id ?? newId()),
      enabled: m.enabled !== false,
      eventType: String(m.eventType ?? ''),
      channel: String(m.channel ?? 'EMAIL'),
      templateCode: String(m.templateCode ?? ''),
      recipientType: String(m.recipientType ?? 'BORROWER_EMAIL'),
      delaySeconds: typeof m.delaySeconds === 'number' ? m.delaySeconds : 0,
    }
  })
}

export function parseWorkflowStepsFromJson(steps: Record<string, unknown>[]): VisualWorkflowStep[] {
  return steps.map((raw, i) => {
    const m = raw as Record<string, unknown>
    const step = String(m.step ?? 'PAN_VERIFY')
    const rawProv = m.provider != null ? String(m.provider) : defaultProviderForWorkflowStep(step)
    const fk = m.allowPhysicalKycFallback
    const allowPkycFallback =
      fk === true || String(fk ?? '').trim().toLowerCase() === 'true'
    return {
      id: newId(),
      step,
      name: m.name != null ? String(m.name) : '',
      provider: normalizeProviderForStep(step, rawProv),
      mandatory: m.mandatory !== false,
      order: typeof m.order === 'number' ? m.order : i + 1,
      notifications: parseNotifications(m.notifications),
      allowPhysicalKycFallback: allowPkycFallback,
      extra: extraFromMap(m),
    }
  })
}

function assignOrders(list: VisualWorkflowStep[]): VisualWorkflowStep[] {
  return list.map((s, i) => ({ ...s, order: i + 1 }))
}

export function visualStepsToJsonArray(visual: VisualWorkflowStep[]): Record<string, unknown>[] {
  return assignOrders(visual).map((s) => {
    const o: Record<string, unknown> = {
      ...s.extra,
      step: s.step,
      mandatory: s.mandatory,
      order: s.order,
    }
    if (s.notifications.length > 0) {
      o.notifications = s.notifications.map((n, idx) => ({
        id: n.id || newId(),
        enabled: n.enabled,
        eventType: n.eventType.trim(),
        channel: n.channel.trim().toUpperCase(),
        templateCode: n.templateCode.trim(),
        recipientType: n.recipientType.trim().toUpperCase() || 'BORROWER_EMAIL',
        delaySeconds: Number.isFinite(n.delaySeconds) ? Math.max(0, n.delaySeconds) : 0,
        order: idx + 1,
      }))
    }
    o.provider = normalizeProviderForStep(s.step, s.provider.trim() || defaultProviderForWorkflowStep(s.step))
    if (s.name.trim()) o.name = s.name.trim()
    const vkycFamily = s.step === 'VIDEO_KYC' || s.step === 'VKYC'
    if (vkycFamily && s.allowPhysicalKycFallback) {
      o.allowPhysicalKycFallback = true
    }
    return o
  })
}

export function createEmptyVisualStep(): VisualWorkflowStep {
  return {
    id: newId(),
    step: 'PAN_VERIFY',
    name: '',
    provider: defaultProviderForWorkflowStep('PAN_VERIFY'),
    mandatory: true,
    order: 1,
    notifications: [],
    allowPhysicalKycFallback: false,
    extra: {},
  }
}

export function getStepMatrixHelp(step: string): { purpose: string; appliesTo: string } {
  const row = INTEGRATION_MATRIX.find((m: MatrixStep) => m.step === step)
  return {
    purpose: row?.purpose ?? '—',
    appliesTo: row?.appliesTo ?? '—',
  }
}

export { providersForWorkflowStep }
