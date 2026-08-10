import {
  canAcceptBorrowerSubmission,
  canCompletePhysicalVkyc,
  canCreateOrNotifyBorrowerIntake,
  canRunKycFlow,
  canRunUnderwriting,
  isRelationshipManager,
} from '@/auth/types'
import type { ApplicationStatus } from '@/types/application'

export type PipelineStageId = 'intake' | 'kyc' | 'underwriting' | 'decision' | 'completed'

export type PipelineStage = {
  id: PipelineStageId
  label: string
  count: number
  /** Safe deep-link when a sensible workspace/filter exists; null = context only. */
  to: string | null
}

export type AttentionItemId =
  | 'borrower_submitted'
  | 'pending_credit_officer'
  | 'sent_back_to_rm'
  | 'kyc'
  | 'underwriting'
  | 'program_approvals'
  | 'settlements'

export type AttentionItem = {
  id: AttentionItemId
  label: string
  description: string
  count: number
  to: string
}

const PIPELINE_STATUSES: Record<PipelineStageId, ApplicationStatus[]> = {
  intake: [
    'DRAFT',
    'CONSENT_PENDING',
    'BORROWER_SUBMITTED',
    'PENDING_CREDIT_OFFICER',
    'SENT_BACK_TO_RM',
    'BORROWER_SENT_BACK',
    'ON_HOLD',
  ],
  kyc: ['KYC_IN_PROGRESS', 'KYC_FAILED'],
  underwriting: ['UNDERWRITING', 'UNDERWRITING_COMPLETED', 'CAM_READY', 'CAM_SENT_BACK', 'CAM_REVIEWED'],
  decision: [
    'APPROVED',
    'REJECTED',
    'SANCTION_PENDING',
    'SANCTIONED',
    'KFS_GENERATED',
    'SANCTION_ISSUED',
  ],
  completed: [
    'ESIGN_PENDING',
    'ESIGN_COMPLETED',
    'READY_FOR_DISBURSEMENT',
    'DISBURSEMENT_PENDING',
    'DISBURSED',
    'WITHDRAWN',
  ],
}

export function parseByStatus(summary: Record<string, unknown> | null | undefined): Record<string, number> {
  const raw = summary?.byStatus
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return {}
  const out: Record<string, number> = {}
  for (const [k, v] of Object.entries(raw as Record<string, unknown>)) {
    const n = typeof v === 'number' ? v : Number(v)
    if (Number.isFinite(n)) out[k] = n
  }
  return out
}

export function summaryTotal(summary: Record<string, unknown> | null | undefined): number {
  const t = summary?.total
  if (typeof t === 'number' && Number.isFinite(t)) return t
  const by = parseByStatus(summary)
  return Object.values(by).reduce((a, b) => a + b, 0)
}

function statusCount(byStatus: Record<string, number>, status: string): number {
  return byStatus[status] ?? 0
}

function sumStatuses(byStatus: Record<string, number>, statuses: string[]): number {
  return statuses.reduce((acc, s) => acc + statusCount(byStatus, s), 0)
}

/** Aggregate byStatus into business pipeline stages (UX-3A mapping). */
export function buildPipelineStages(
  byStatus: Record<string, number>,
  opts: { underwritingActionable: boolean },
): PipelineStage[] {
  return [
    {
      id: 'intake',
      label: 'Intake',
      count: sumStatuses(byStatus, PIPELINE_STATUSES.intake),
      to: '/applications',
    },
    {
      id: 'kyc',
      label: 'KYC',
      count: sumStatuses(byStatus, PIPELINE_STATUSES.kyc),
      to: '/kyc',
    },
    {
      id: 'underwriting',
      label: 'Underwriting',
      count: sumStatuses(byStatus, PIPELINE_STATUSES.underwriting),
      // Pipeline context for RM; actionable work link only when allowed
      to: opts.underwritingActionable ? '/underwriting' : null,
    },
    {
      id: 'decision',
      label: 'Decision',
      count: sumStatuses(byStatus, PIPELINE_STATUSES.decision),
      to: '/applications',
    },
    {
      id: 'completed',
      label: 'Completed',
      count: sumStatuses(byStatus, PIPELINE_STATUSES.completed),
      to: '/applications',
    },
  ]
}

export function isOpsKycRole(role: string): boolean {
  const r = String(role ?? '')
    .trim()
    .toUpperCase()
  return (
    r === 'OPERATIONS' ||
    r === 'VKYC_MANAGER' ||
    r === 'KYC_REVIEWER' ||
    r === 'BRANCH_VERIFIER' ||
    canCompletePhysicalVkyc(role)
  )
}

export function canSeeKycAttention(role: string): boolean {
  return canRunKycFlow(role) || isOpsKycRole(role)
}

export function canSeeUnderwritingAttention(role: string): boolean {
  if (isRelationshipManager(role)) return false
  return canRunUnderwriting(role)
}

export function canSeePendingCreditOfficerAttention(role: string): boolean {
  if (isRelationshipManager(role)) return false
  return canAcceptBorrowerSubmission(role) || canRunKycFlow(role)
}

export function canSeeSentBackToRmAttention(role: string): boolean {
  return isRelationshipManager(role) || canCreateOrNotifyBorrowerIntake(role)
}

export function canSeeBorrowerSubmittedAttention(_role: string): boolean {
  return true
}

/** Build attention rows with count > 0 only. */
export function buildAttentionItems(input: {
  role: string
  byStatus: Record<string, number>
  programApprovalsPending: number | null
  settlementOpenCount: number | null
}): AttentionItem[] {
  const { role, byStatus, programApprovalsPending, settlementOpenCount } = input
  const items: AttentionItem[] = []

  const borrowerSubmitted = statusCount(byStatus, 'BORROWER_SUBMITTED')
  if (canSeeBorrowerSubmittedAttention(role) && borrowerSubmitted > 0) {
    items.push({
      id: 'borrower_submitted',
      label: 'Borrower Submitted',
      description: 'Applications the borrower completed — review or accept for processing',
      count: borrowerSubmitted,
      to: '/borrower-submissions',
    })
  }

  const pendingCo = statusCount(byStatus, 'PENDING_CREDIT_OFFICER')
  if (canSeePendingCreditOfficerAttention(role) && pendingCo > 0) {
    items.push({
      id: 'pending_credit_officer',
      label: 'Pending Credit Officer',
      description: 'Applications waiting for credit officer acceptance',
      count: pendingCo,
      to: '/applications?status=PENDING_CREDIT_OFFICER',
    })
  }

  const sentBack = statusCount(byStatus, 'SENT_BACK_TO_RM')
  if (canSeeSentBackToRmAttention(role) && sentBack > 0) {
    items.push({
      id: 'sent_back_to_rm',
      label: 'Sent Back to RM',
      description: 'Applications returned for relationship manager follow-up',
      count: sentBack,
      to: '/applications?status=SENT_BACK_TO_RM',
    })
  }

  const kyc = statusCount(byStatus, 'KYC_IN_PROGRESS')
  if (canSeeKycAttention(role) && kyc > 0) {
    items.push({
      id: 'kyc',
      label: 'KYC in Progress',
      description: 'Applications currently in identity and verification',
      count: kyc,
      to: '/kyc',
    })
  }

  const uw = statusCount(byStatus, 'UNDERWRITING')
  if (canSeeUnderwritingAttention(role) && uw > 0) {
    items.push({
      id: 'underwriting',
      label: 'Underwriting',
      description: 'Applications awaiting credit review',
      count: uw,
      to: '/underwriting',
    })
  }

  if (programApprovalsPending != null && programApprovalsPending > 0) {
    items.push({
      id: 'program_approvals',
      label: 'Program Approvals',
      description: `${programApprovalsPending} awaiting your review`,
      count: programApprovalsPending,
      to: '/plp/program-approvals',
    })
  }

  if (settlementOpenCount != null && settlementOpenCount > 0) {
    items.push({
      id: 'settlements',
      label: 'Settlement items',
      description: 'Open payment lines ready for settlement batch',
      count: settlementOpenCount,
      to: '/pg-settlements',
    })
  }

  return items
}

export function firstNameFromDisplayName(name: string | null | undefined): string {
  const p = String(name ?? '')
    .trim()
    .split(/\s+/)
    .filter(Boolean)
  return p[0] || 'there'
}

export function welcomeLine(name: string | null | undefined): string {
  return `Welcome, ${firstNameFromDisplayName(name)}`
}

/** Exported for tests — pipeline status membership. */
export function pipelineStatusesForTests(): typeof PIPELINE_STATUSES {
  return PIPELINE_STATUSES
}
