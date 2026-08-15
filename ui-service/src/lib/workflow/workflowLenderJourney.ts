/**
 * Lender-facing journey projection over existing workflow steps / notifications.
 * Not a second workflow engine — presentation only.
 */

import { KYC_IDENTITY_WORKFLOW_STEPS, isPostKycWorkflowStep, type VisualWorkflowStep } from '@/lib/workflowVisual'
import type { ProcessNotificationConfig } from '@/lib/workflowProcessNotifications'
import type { WorkflowIntakeConfig } from '@/types/workflow'

export type JourneyStageId =
  | 'APPLICATION'
  | 'IDENTITY_KYC'
  | 'DATA_COLLECTION'
  | 'CREDIT_ASSESSMENT'
  | 'MANUAL_REVIEW'
  | 'APPROVAL'
  | 'OFFER_KFS'
  | 'ESIGN'
  | 'DISBURSEMENT'

export type JourneyStageSupport = 'CONFIGURED' | 'INFORMATIVE' | 'AVAILABLE_VIA_STEPS' | 'AVAILABLE_VIA_NOTIFICATIONS'

export type JourneyStageView = {
  id: JourneyStageId
  name: string
  purpose: string
  support: JourneyStageSupport
  /** Short status for readiness strip */
  statusLabel: string
  detail?: string
}

const KYC_SET = new Set<string>(KYC_IDENTITY_WORKFLOW_STEPS as readonly string[])

function hasProcess(notifications: ProcessNotificationConfig[], code: string): boolean {
  return notifications.some((n) => n.processCode === code && n.enabled !== false)
}

function hasStep(steps: VisualWorkflowStep[], ...types: string[]): boolean {
  const set = new Set(types.map((t) => t.toUpperCase()))
  return steps.some((s) => set.has(String(s.step ?? '').toUpperCase()))
}

export function projectLenderJourney(input: {
  intakeConfig: WorkflowIntakeConfig
  steps: VisualWorkflowStep[]
  processNotifications: ProcessNotificationConfig[]
}): JourneyStageView[] {
  const { intakeConfig, steps, processNotifications } = input
  const kycSteps = steps.filter((s) => KYC_SET.has(String(s.step ?? '').toUpperCase()))
  const hasKyc = kycSteps.length > 0
  const hasEsignKfs = hasStep(steps, 'ESIGN_KFS')
  const hasEsignAgreement = hasStep(steps, 'ESIGN_AGREEMENT')
  const hasBureauPull = hasStep(steps, 'BUREAU_PULL')

  const personalConfigured =
    Boolean(intakeConfig.personalFields?.dateOfBirth?.collect) ||
    Boolean(intakeConfig.personalFields?.gender?.collect) ||
    Boolean(intakeConfig.personalFields?.occupation?.collect) ||
    Boolean(intakeConfig.personalFields?.loanPurpose?.collect) ||
    (intakeConfig.standaloneDocuments?.length ?? 0) > 0 ||
    (intakeConfig.customFields?.length ?? 0) > 0

  const stages: JourneyStageView[] = [
    {
      id: 'APPLICATION',
      name: 'Application',
      purpose: 'Collect applicant information and documents.',
      support: 'CONFIGURED',
      statusLabel: personalConfigured ? 'Configured' : 'Needs attention',
      detail: personalConfigured
        ? 'Intake fields and/or documents are configured.'
        : 'Add the information and documents applicants should provide.',
    },
    {
      id: 'IDENTITY_KYC',
      name: 'Identity & KYC',
      purpose: 'Complete required identity and KYC checks.',
      support: 'CONFIGURED',
      statusLabel: hasKyc ? `${kycSteps.length} step(s)` : 'Needs attention',
      detail: hasKyc
        ? kycSteps.map((s) => s.name || s.step).join(' → ')
        : 'Add at least one identity / KYC step.',
    },
    {
      id: 'DATA_COLLECTION',
      name: 'Data collection',
      purpose: 'Collect external information required by the credit policy.',
      support: 'INFORMATIVE',
      statusLabel: 'Policy-driven',
      detail: hasBureauPull
        ? 'Policy determines required data. A Bureau pull step is present in this workflow for operational sequencing where configured.'
        : 'Policy determines required data. Collection runs from sources available to your organisation after identity and consent prerequisites.',
    },
    {
      id: 'CREDIT_ASSESSMENT',
      name: 'Credit assessment',
      purpose: 'Evaluate hard Policy rules and the linked Scorecard where configured.',
      support: 'INFORMATIVE',
      statusLabel: 'Via Customer Category',
      detail:
        'Applications reaching this stage are evaluated against the Policy selected for the Customer Category. Hard rules run first; the linked Scorecard applies where configured. Unavailable required data follows fail-closed semantics.',
    },
  ]

  if (hasProcess(processNotifications, 'CAM') || hasProcess(processNotifications, 'UNDERWRITING')) {
    stages.push({
      id: 'MANUAL_REVIEW',
      name: 'Manual review',
      purpose: 'Human review where required by existing underwriting / CAM outcomes.',
      support: 'AVAILABLE_VIA_NOTIFICATIONS',
      statusLabel: 'Notifications configured',
      detail: 'Review outcomes are driven by existing underwriting authority — not a separate Workflow rule engine.',
    })
  } else {
    stages.push({
      id: 'MANUAL_REVIEW',
      name: 'Manual review',
      purpose: 'Human review where required by existing underwriting / CAM outcomes.',
      support: 'INFORMATIVE',
      statusLabel: 'As required by Policy',
      detail: 'Occurs when existing underwriting / CAM outcomes require it. Configure related notifications under Notifications if needed.',
    })
  }

  stages.push({
    id: 'APPROVAL',
    name: 'Approval',
    purpose: 'Record authorised credit decision where supported.',
    support: hasProcess(processNotifications, 'SANCTION')
      ? 'AVAILABLE_VIA_NOTIFICATIONS'
      : 'INFORMATIVE',
    statusLabel: hasProcess(processNotifications, 'SANCTION')
      ? 'Notifications configured'
      : 'Existing decision authority',
    detail: 'Approval remains with existing credit / sanction authority — not configured as underwriting rules in Workflow.',
  })

  if (hasEsignKfs || hasEsignAgreement) {
    if (hasEsignKfs) {
      stages.push({
        id: 'OFFER_KFS',
        name: 'Offer / KFS',
        purpose: 'Generate or accept lending terms (KFS) where supported.',
        support: 'AVAILABLE_VIA_STEPS',
        statusLabel: 'Step configured',
        detail: 'ESIGN_KFS step is present in this workflow.',
      })
    }
    stages.push({
      id: 'ESIGN',
      name: 'eSign',
      purpose: 'Complete agreement execution where supported.',
      support: 'AVAILABLE_VIA_STEPS',
      statusLabel: 'Step configured',
      detail: hasEsignAgreement
        ? 'ESIGN_AGREEMENT step is present.'
        : 'KFS eSign step is present; agreement eSign can be added if required.',
    })
  } else {
    stages.push({
      id: 'OFFER_KFS',
      name: 'Offer / KFS',
      purpose: 'Generate or accept lending terms where supported.',
      support: 'INFORMATIVE',
      statusLabel: 'Add eSign KFS step if needed',
      detail: 'Not configured as a workflow step yet. Add ESIGN_KFS under Identity & KYC / post-KYC steps when required.',
    })
    stages.push({
      id: 'ESIGN',
      name: 'eSign',
      purpose: 'Complete agreement execution where supported.',
      support: 'INFORMATIVE',
      statusLabel: 'Add eSign step if needed',
      detail: 'Not configured as a workflow step yet.',
    })
  }

  stages.push({
    id: 'DISBURSEMENT',
    name: 'Disbursement',
    purpose: 'Proceed to disbursement readiness / execution where supported.',
    support: hasProcess(processNotifications, 'DISBURSEMENT')
      ? 'AVAILABLE_VIA_NOTIFICATIONS'
      : 'INFORMATIVE',
    statusLabel: hasProcess(processNotifications, 'DISBURSEMENT')
      ? 'Notifications configured'
      : 'LMS / fulfilment',
    detail: 'Disbursement execution lives in existing LMS / fulfilment paths. Workflow can carry related notifications.',
  })

  return stages
}

export type WorkflowSetupItem = {
  id: string
  label: string
  done: boolean
  optional?: boolean
  hint?: string
}

export function workflowSetupStatus(input: {
  intakeConfig: WorkflowIntakeConfig
  steps: VisualWorkflowStep[]
  processNotifications: ProcessNotificationConfig[]
  name: string
}): { items: WorkflowSetupItem[]; ready: boolean; attention: string[] } {
  const journey = projectLenderJourney(input)
  const app = journey.find((s) => s.id === 'APPLICATION')
  const kyc = journey.find((s) => s.id === 'IDENTITY_KYC')
  const hasName = Boolean(input.name.trim()) && input.name.trim().toLowerCase() !== 'new workflow'

  const items: WorkflowSetupItem[] = [
    {
      id: 'name',
      label: 'Workflow named',
      done: hasName,
      hint: hasName ? undefined : 'Give this journey a clear lender-facing name.',
    },
    {
      id: 'application',
      label: 'Application configured',
      done: app?.statusLabel !== 'Needs attention',
      hint: app?.detail,
    },
    {
      id: 'kyc',
      label: 'Identity & KYC configured',
      done: kyc?.statusLabel !== 'Needs attention',
      hint: kyc?.detail,
    },
    {
      id: 'data',
      label: 'Data collection available',
      done: true,
      optional: true,
      hint: 'Policy drives required data; sources come from Data & Parameters.',
    },
    {
      id: 'journey',
      label: 'Journey reviewed',
      done: true,
      optional: true,
    },
    {
      id: 'notifications',
      label: 'Notifications',
      done: input.processNotifications.some((n) => n.enabled !== false),
      optional: true,
      hint: 'Optional — configure business-event notifications when ready.',
    },
  ]

  const attention = items.filter((i) => !i.done && !i.optional).map((i) => i.hint || i.label)
  const ready = attention.length === 0
  return { items, ready, attention }
}

export function isKycIdentityStep(step: string): boolean {
  return KYC_SET.has(String(step ?? '').toUpperCase())
}

export function isPostKycStep(step: string): boolean {
  return isPostKycWorkflowStep(step)
}

/** Business labels for notification process codes (lender UX). */
export const NOTIFICATION_PROCESS_BUSINESS_LABELS: Record<string, string> = {
  KYC: 'KYC completed / failed',
  UNDERWRITING: 'Application under review',
  CAM: 'Credit appraisal memo',
  VKYC: 'Video KYC',
  ESIGN: 'Agreement / eSign',
  SANCTION: 'Application approved / declined',
  DISBURSEMENT: 'Disbursement',
  WELCOME: 'Welcome',
  REJECTION: 'Application declined',
  REPAYMENT: 'Repayment reminder',
  DOCUMENT_COLLECTION: 'Additional information required',
  LOAN_ACTIVATION: 'Loan activated',
}
