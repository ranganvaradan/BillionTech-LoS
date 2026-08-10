import type { ApplicationResponse } from '@/types/application'
import type { StepExecutionRecordView } from '@/types/stepExecution'
import type { WorkflowConfigResponse } from '@/types/workflow'

type ApplicationTabId =
  | 'summary'
  | 'borrower'
  | 'collateral'
  | 'kyc'
  | 'vkyc'
  | 'documents'
  | 'bankData'
  | 'underwriting'
  | 'cam'
  | 'sanction'
  | 'esign'
  | 'disbursement'
  | 'history'

type ApplicationTab<T extends ApplicationTabId = ApplicationTabId> = {
  id: T
  label: string
}

type VkycGateInput = {
  app: ApplicationResponse | null | undefined
  workflow: WorkflowConfigResponse | null | undefined
  stepExecutions: StepExecutionRecordView[] | null | undefined
  eligibility: Record<string, unknown> | null | undefined
  timeline: Record<string, unknown> | null | undefined
}

/**
 * Sections downstream of VKYC that may need to be disabled while VKYC is pending
 * auditor approval. Each flag is true when actions inside that section must remain
 * blocked until {@code auditorApproved} becomes true. The mapping is workflow-position
 * driven and mirrors the backend `assertVkycCleared` tier rules:
 *
 *   - AFTER_UNDERWRITING → blocks CAM, sanction, eSign, disbursement
 *   - BEFORE_ESIGN       → blocks eSign, disbursement
 *   - AFTER_ESIGN        → blocks disbursement
 *   - other / null       → defaults to eSign + disbursement (safe default)
 */
export type VkycDownstreamBlockState = {
  cam: boolean
  sanction: boolean
  esign: boolean
  disburse: boolean
}

export type VkycWorkflowGate = {
  configured: boolean
  eligible: boolean
  workflowReached: boolean
  kycComplete: boolean
  applicationTerminal: boolean
  vkycStarted: boolean
  /**
   * Strict "VKYC cleared" — true ONLY when auditor approval (or its terminal
   * COMPLETED follow-up) has been recorded. URL generation, customer joining,
   * or agent approval do NOT count.
   */
  vkycComplete: boolean
  /** Alias for {@link vkycComplete}, kept for readability at call sites. */
  auditorApproved: boolean
  /**
   * VKYC actually applies to this application: a VKYC step is configured AND the
   * trigger conditions evaluated to eligible. Used to decide whether downstream
   * sections need gating at all.
   */
  applicable: boolean
  visible: boolean
  canGenerate: boolean
  insertAfterTab: ApplicationTabId
  downstreamBlocked: VkycDownstreamBlockState
}

const VKYC_STEPS = new Set(['VIDEO_KYC', 'VKYC'])
const SUCCESS = 'SUCCESS'
const TERMINAL_APPLICATION_STATUSES = new Set(['REJECTED', 'WITHDRAWN', 'DISBURSED'])
const POST_KYC_APPLICATION_STATUSES = new Set([
  'UNDERWRITING',
  'UNDERWRITING_COMPLETED',
  'APPROVED',
  'CAM_READY',
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
])
const AFTER_UNDERWRITING_STATUSES = new Set([
  'UNDERWRITING_COMPLETED',
  'APPROVED',
  'CAM_READY',
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
])
const AFTER_ESIGN_STATUSES = new Set(['ESIGN_COMPLETED', 'READY_FOR_DISBURSEMENT', 'DISBURSEMENT_PENDING', 'DISBURSED'])
const BEFORE_ESIGN_STATUSES = new Set(['SANCTIONED', 'KFS_GENERATED', 'SANCTION_ISSUED', 'ESIGN_PENDING'])
/**
 * Statuses that mean VKYC is "started" — i.e. the tab has produced state we want
 * to keep visible. Distinct from {@link AUDITOR_CLEARED_STATUSES}; these statuses
 * still leave downstream actions blocked.
 */
const VKYC_STARTED_STATUSES = new Set(['URL_GENERATED', 'CUSTOMER_JOINED', 'AGENT_APPROVED', 'INITIATED'])
/**
 * Statuses that count as "VKYC cleared by auditor". URL generation, customer
 * joined, and agent approval are intentionally NOT in this set — only the
 * auditor approval (or its terminal COMPLETED state) unblocks downstream
 * workflow actions, mirroring the backend `VKYC_AUDITOR_CLEARED` set.
 */
const AUDITOR_CLEARED_STATUSES = new Set(['AUDITOR_APPROVED', 'COMPLETED'])
/**
 * Terminal failure statuses for VKYC. Distinct from "auditor cleared" — these
 * leave the application in a non-progressable state but should not flip the
 * downstream gate to "open".
 */
const VKYC_TERMINAL_FAILURE_STATUSES = new Set(['REJECTED', 'AUDITOR_REJECTED', 'AUTO_DECLINED', 'ERROR', 'EXPIRED', 'FAILED'])
const FINAL_VKYC_STATUSES = new Set([
  ...AUDITOR_CLEARED_STATUSES,
  ...VKYC_TERMINAL_FAILURE_STATUSES,
])

function normalized(value: unknown): string {
  return value == null ? '' : String(value).trim().toUpperCase()
}

function stepName(step: Record<string, unknown>): string {
  return normalized(step.step)
}

function orderOf(step: Record<string, unknown>, fallback: number): number {
  const raw = step.order
  if (typeof raw === 'number') return raw
  const parsed = Number.parseInt(String(raw ?? ''), 10)
  return Number.isFinite(parsed) ? parsed : fallback
}

function sortedWorkflowSteps(workflow: WorkflowConfigResponse | null | undefined): Record<string, unknown>[] {
  return [...(workflow?.steps ?? [])].sort((a, b) => orderOf(a, 0) - orderOf(b, 0))
}

function hasSuccessfulStep(rows: StepExecutionRecordView[] | null | undefined, stepType: string): boolean {
  return Boolean(rows?.some((r) => normalized(r.stepType) === stepType && normalized(r.status) === SUCCESS))
}

function deriveKycComplete(app: ApplicationResponse | null | undefined, rows: StepExecutionRecordView[] | null | undefined): boolean {
  if (!app) return false
  if (hasSuccessfulStep(rows, 'KYC_WORKFLOW')) return true
  return POST_KYC_APPLICATION_STATUSES.has(normalized(app.status))
}

function deriveBureauComplete(app: ApplicationResponse | null | undefined, rows: StepExecutionRecordView[] | null | undefined): boolean {
  if (!app) return false
  if (hasSuccessfulStep(rows, 'BUREAU_PULL')) return true
  return app.bureauScore != null && Number(app.bureauScore) > 0
}

function workflowHasVkyc(workflow: WorkflowConfigResponse | null | undefined): boolean {
  return sortedWorkflowSteps(workflow).some((s) => VKYC_STEPS.has(stepName(s)))
}

function previousStepsForVkyc(workflow: WorkflowConfigResponse | null | undefined): string[] {
  const steps = sortedWorkflowSteps(workflow)
  const vkycIndex = steps.findIndex((s) => VKYC_STEPS.has(stepName(s)))
  if (vkycIndex < 0) return []
  return steps.slice(0, vkycIndex).map(stepName).filter(Boolean)
}

function reachedByConfiguredPosition(app: ApplicationResponse, position: string, kycComplete: boolean): boolean {
  const status = normalized(app.status)
  if (position === 'AFTER_ESIGN') return AFTER_ESIGN_STATUSES.has(status)
  if (position === 'AFTER_UNDERWRITING') return AFTER_UNDERWRITING_STATUSES.has(status)
  if (position === 'BEFORE_ESIGN') return kycComplete && BEFORE_ESIGN_STATUSES.has(status)
  return false
}

function reachedByCustomOrder(
  app: ApplicationResponse,
  workflow: WorkflowConfigResponse | null | undefined,
  rows: StepExecutionRecordView[] | null | undefined,
  kycComplete: boolean,
): boolean {
  const previous = previousStepsForVkyc(workflow)
  if (previous.length === 0) return kycComplete
  if (previous.some((s) => s === 'BUREAU_PULL')) return kycComplete && deriveBureauComplete(app, rows)
  if (previous.some((s) => s.startsWith('ESIGN'))) return AFTER_ESIGN_STATUSES.has(normalized(app.status))
  return kycComplete
}

function deriveInsertAfterTab(workflow: WorkflowConfigResponse | null | undefined): ApplicationTabId {
  const position = normalized(workflow?.workflowPosition)
  if (position === 'AFTER_ESIGN') return 'esign'
  if (position === 'AFTER_UNDERWRITING') return 'underwriting'
  if (position === 'BEFORE_ESIGN') return 'sanction'
  const previous = previousStepsForVkyc(workflow)
  if (previous.some((s) => s.startsWith('ESIGN'))) return 'esign'
  if (previous.some((s) => s === 'BUREAU_PULL')) return 'kyc'
  return 'kyc'
}

/**
 * Computes which downstream loan-flow sections must be blocked while VKYC is
 * still pending auditor approval.
 *
 * Mirrors the backend tier mapping in
 * {@code VkycWorkflowService.positionGovernsFromTier} so that the UI never
 * shows an action as actionable when the backend will reject it. When VKYC
 * does not apply to this application or has already been auditor-approved, all
 * flags are false.
 */
function deriveDownstreamBlocked(
  position: string,
  applicable: boolean,
  auditorApproved: boolean,
): VkycDownstreamBlockState {
  if (!applicable || auditorApproved) {
    return { cam: false, sanction: false, esign: false, disburse: false }
  }
  if (position === 'AFTER_UNDERWRITING') {
    return { cam: true, sanction: true, esign: true, disburse: true }
  }
  if (position === 'AFTER_ESIGN') {
    return { cam: false, sanction: false, esign: false, disburse: true }
  }
  return { cam: false, sanction: false, esign: true, disburse: true }
}

export function buildVkycWorkflowGate(input: VkycGateInput): VkycWorkflowGate {
  const { app, workflow, stepExecutions, eligibility, timeline } = input
  const configured = workflowHasVkyc(workflow)
  const eligible = eligibility?.eligible === true
  const status = normalized(app?.status)
  const vkycStatus = normalized(timeline?.vkycStatus ?? app?.vkycStatus ?? 'NOT_STARTED')
  const hasVkycUrl = String(timeline?.vkycUrl ?? app?.vkycUrl ?? '').trim().length > 0
  const applicationTerminal = TERMINAL_APPLICATION_STATUSES.has(status)
  const kycComplete = deriveKycComplete(app, stepExecutions)
  const position = normalized(workflow?.workflowPosition)
  const workflowReached = app
    ? position === 'CUSTOM' || !position
      ? reachedByCustomOrder(app, workflow, stepExecutions, kycComplete)
      : reachedByConfiguredPosition(app, position, kycComplete)
    : false
  const auditorApproved = AUDITOR_CLEARED_STATUSES.has(vkycStatus)
  const vkycStarted = hasVkycUrl
    || VKYC_STARTED_STATUSES.has(vkycStatus)
    || FINAL_VKYC_STATUSES.has(vkycStatus)
  // "applicable" mirrors the backend's eligibility gate: VKYC is only required
  // when it is configured AND the trigger rules evaluated to eligible (or no
  // rules were configured, which the backend treats as eligible by default).
  const applicable = configured && eligible
  const visible = configured && (vkycStarted || (eligible && workflowReached && !applicationTerminal))
  const canGenerate = visible && eligible && workflowReached && kycComplete && !applicationTerminal && !auditorApproved && !VKYC_TERMINAL_FAILURE_STATUSES.has(vkycStatus)

  return {
    configured,
    eligible,
    workflowReached,
    kycComplete,
    applicationTerminal,
    vkycStarted,
    vkycComplete: auditorApproved,
    auditorApproved,
    applicable,
    visible,
    canGenerate,
    insertAfterTab: deriveInsertAfterTab(workflow),
    downstreamBlocked: deriveDownstreamBlocked(position, applicable, auditorApproved),
  }
}

export function insertVkycTab<T extends ApplicationTabId>(
  tabs: Array<ApplicationTab<T>>,
  gate: Pick<VkycWorkflowGate, 'visible' | 'insertAfterTab'>,
): Array<ApplicationTab<T | 'vkyc'>> {
  if (!gate.visible) return tabs
  if (tabs.some((t) => t.id === 'vkyc')) return tabs
  const out: Array<ApplicationTab<T | 'vkyc'>> = [...tabs]
  const index = out.findIndex((t) => t.id === gate.insertAfterTab)
  out.splice(index >= 0 ? index + 1 : out.length, 0, { id: 'vkyc', label: 'VKYC' })
  return out
}
