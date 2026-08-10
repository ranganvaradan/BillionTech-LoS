import { formatAssessmentOutcome, formatCamStatusLabel } from '@/lib/credit/assessmentPresentation'
import { formatStatusLabel } from '@/lib/dashboardLabels'
import type { ApplicationResponse } from '@/types/application'

export type DecisionFocusSection = 'cam' | 'sanction' | 'esign' | 'disburse' | null

export type DecisionSummaryModel = {
  assessmentOutcome: string
  camStatusLabel: string
  sanctionStatusLabel: string
  requestedAmount: number | null | undefined
  sanctionedAmount: number | null | undefined
  stageLabel: string
  nextFocus: DecisionFocusSection
  nextFocusLabel: string | null
  emptyHint: string | null
}

function sanctionLabelFromStatus(status: string): string {
  const s = status.toUpperCase()
  if (s === 'SANCTION_PENDING') return 'Pending'
  if (s === 'SANCTIONED' || s === 'SANCTION_ISSUED') return 'Sanctioned'
  if (s === 'KFS_GENERATED') return 'KFS generated'
  if (s === 'REJECTED') return 'Rejected'
  if (
    [
      'ESIGN_PENDING',
      'ESIGN_COMPLETED',
      'READY_FOR_DISBURSEMENT',
      'DISBURSEMENT_PENDING',
      'DISBURSED',
    ].includes(s)
  ) {
    return 'Sanctioned'
  }
  if (s === 'CAM_REVIEWED' || s === 'APPROVED') return 'Ready for sanction'
  if (s === 'CAM_READY' || s === 'CAM_SENT_BACK') return 'Awaiting appraisal'
  return 'Not started'
}

export function buildDecisionSummary(app: ApplicationResponse): DecisionSummaryModel {
  const status = String(app.status ?? '')
  const s = status.toUpperCase()
  const assessmentOutcome = formatAssessmentOutcome(app.creditDecision ?? null)
  const camStatusLabel = formatCamStatusLabel(app.camStatus ?? null)
  const sanctionStatusLabel = sanctionLabelFromStatus(s)
  const stageLabel = formatStatusLabel(status)

  let nextFocus: DecisionFocusSection = null
  let nextFocusLabel: string | null = null
  let emptyHint: string | null = null

  if (s === 'CAM_READY' || s === 'CAM_SENT_BACK' || (s === 'UNDERWRITING_COMPLETED' && !app.camStatus)) {
    nextFocus = 'cam'
    nextFocusLabel = 'Complete CAM'
    if (!app.camStatus || app.camStatus === 'DRAFT') {
      emptyHint = 'Credit appraisal has not been started.'
    }
  } else if (s === 'CAM_REVIEWED' || s === 'SANCTION_PENDING' || s === 'APPROVED') {
    nextFocus = 'sanction'
    nextFocusLabel = 'Review Sanction'
  } else if (s === 'KFS_GENERATED' || s === 'SANCTIONED' || s === 'SANCTION_ISSUED' || s === 'ESIGN_PENDING') {
    nextFocus = 'esign'
    nextFocusLabel = 'Send for eSign'
  } else if (s === 'ESIGN_COMPLETED' || s === 'READY_FOR_DISBURSEMENT' || s === 'DISBURSEMENT_PENDING') {
    nextFocus = 'disburse'
    nextFocusLabel = 'Disburse'
  } else if (!app.creditDecision && !app.camStatus) {
    emptyHint = 'No credit decision recorded yet. Complete Credit Assessment first when ready.'
  }

  return {
    assessmentOutcome: app.creditDecision ? assessmentOutcome : '—',
    camStatusLabel,
    sanctionStatusLabel,
    requestedAmount: app.requestedAmount,
    sanctionedAmount: app.sanctionedAmount,
    stageLabel,
    nextFocus,
    nextFocusLabel,
    emptyHint,
  }
}

/** Whether Completion → eSign should render the full section vs a calm deferral. */
export function completionEsignActive(status: string | null | undefined): boolean {
  const s = String(status ?? '').toUpperCase()
  return [
    'KFS_GENERATED',
    'SANCTIONED',
    'SANCTION_ISSUED',
    'ESIGN_PENDING',
    'ESIGN_COMPLETED',
    'READY_FOR_DISBURSEMENT',
    'DISBURSEMENT_PENDING',
    'DISBURSED',
  ].includes(s)
}

export function completionDisburseActive(status: string | null | undefined): boolean {
  const s = String(status ?? '').toUpperCase()
  return ['ESIGN_COMPLETED', 'READY_FOR_DISBURSEMENT', 'DISBURSEMENT_PENDING', 'DISBURSED'].includes(s)
}

export function camNextActionHint(opts: {
  camStatus: string | null | undefined
  isMaker: boolean
  isChecker: boolean
}): string | null {
  const s = String(opts.camStatus ?? 'DRAFT').toUpperCase()
  if (s === 'APPROVED') return 'CAM approved — continue to Sanction & Terms when ready.'
  if (s === 'SUBMITTED' && opts.isChecker) return 'Review and approve or send back.'
  if (s === 'SUBMITTED' && opts.isMaker) return 'Waiting for manager review.'
  if ((s === 'DRAFT' || s === 'SENT_BACK' || s === 'REJECTED') && opts.isMaker) {
    return s === 'DRAFT' ? 'Save and submit for manager review.' : 'Update and resubmit for review.'
  }
  if (s === 'SENT_BACK') return 'Address send-back comments and resubmit.'
  return null
}
