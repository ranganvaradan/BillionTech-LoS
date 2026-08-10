import {
  canAcceptBorrowerSubmission,
  canAccessSanction,
  canHandOffToCo,
  canRunKycFlow,
  canRunUnderwriting,
  isCamCheckerRole,
  isCamEditorRole,
  isRelationshipManager,
} from '@/auth/types'
import { staffCanContinueIntake } from '@/lib/intake/intakeResume'
import {
  anchorSkipsPostSanctionSteps,
  idBorrowerSkipsDisbursement,
  isInvoiceDiscountingAnchorApp,
} from '@/lib/invoiceDiscountingFlow'
import type { ApplicationResponse, ApplicationStatus } from '@/types/application'

export type WorkbenchTabId = 'overview' | 'kyc' | 'credit' | 'decision' | 'documents' | 'history'

export type WorkbenchTab = {
  id: WorkbenchTabId
  label: string
}

export type WorkbenchNextAction = {
  label: string
  description: string
  targetTab: WorkbenchTabId
}

export function visibleWorkbenchTabs(opts: { role: string }): WorkbenchTab[] {
  const isRm = isRelationshipManager(opts.role)
  const tabs: WorkbenchTab[] = [
    { id: 'overview', label: 'Overview' },
    { id: 'kyc', label: 'KYC' },
  ]
  if (!isRm) {
    tabs.push({ id: 'credit', label: 'Credit Assessment' })
  }
  tabs.push({ id: 'decision', label: 'Decision' })
  tabs.push({ id: 'documents', label: 'Documents' })
  tabs.push({ id: 'history', label: 'History' })
  return tabs
}

export function defaultWorkbenchTab(
  status: ApplicationStatus | string | null | undefined,
  visible: WorkbenchTabId[],
): WorkbenchTabId {
  const s = String(status ?? '').toUpperCase()
  let preferred: WorkbenchTabId = 'overview'

  if (s === 'KYC_IN_PROGRESS' || s === 'KYC_FAILED') {
    preferred = 'kyc'
  } else if (s === 'UNDERWRITING' || s === 'UNDERWRITING_COMPLETED') {
    preferred = 'credit'
  } else if (
    s === 'CAM_READY' ||
    s === 'CAM_SENT_BACK' ||
    s === 'CAM_REVIEWED' ||
    s === 'APPROVED' ||
    s === 'SANCTION_PENDING' ||
    s === 'SANCTIONED' ||
    s === 'SANCTION_ISSUED' ||
    s === 'KFS_GENERATED' ||
    s === 'ESIGN_PENDING' ||
    s === 'ESIGN_COMPLETED' ||
    s === 'READY_FOR_DISBURSEMENT' ||
    s === 'DISBURSEMENT_PENDING'
  ) {
    preferred = 'decision'
  } else if (s === 'DISBURSED' || s === 'REJECTED' || s === 'WITHDRAWN') {
    // Terminal / closed cases — overview summary (role visibility still overrides).
    preferred = 'overview'
  } else {
    preferred = 'overview'
  }

  if (visible.includes(preferred)) return preferred
  return visible[0] ?? 'overview'
}

export function showCamInDecision(opts: { role: string; app: ApplicationResponse }): boolean {
  if (isRelationshipManager(opts.role)) return false
  if (anchorSkipsPostSanctionSteps(opts.app)) return false
  return true
}

export function showEsignInDecision(opts: { role: string }): boolean {
  return !isRelationshipManager(opts.role)
}

export function showDisbursementInDecision(opts: {
  role: string
  app: ApplicationResponse
}): boolean {
  if (isRelationshipManager(opts.role)) return false
  if (anchorSkipsPostSanctionSteps(opts.app)) return false
  if (idBorrowerSkipsDisbursement(opts.app)) return false
  return true
}

export function nextActionUsesIntakeHref(app: ApplicationResponse, role: string): boolean {
  if (!staffCanContinueIntake(app, role)) return false
  return (
    app.status === 'DRAFT' ||
    app.status === 'CONSENT_PENDING' ||
    app.status === 'SENT_BACK_TO_RM' ||
    app.status === 'BORROWER_SENT_BACK'
  )
}

/**
 * Stage-aware primary CTA — navigates to existing action surfaces; does not reimplement mutations.
 */
export function resolveWorkbenchNextAction(opts: {
  app: ApplicationResponse
  role: string
}): WorkbenchNextAction | null {
  const { app, role } = opts
  const status = app.status

  if (nextActionUsesIntakeHref(app, role)) {
    return {
      label: status === 'SENT_BACK_TO_RM' ? 'Continue intake' : 'Continue intake',
      description:
        status === 'SENT_BACK_TO_RM'
          ? 'Update the application and return it to credit when ready.'
          : 'Complete or resume the application intake.',
      targetTab: 'overview',
    }
  }

  if (status === 'SENT_BACK_TO_RM' && canHandOffToCo(role)) {
    return {
      label: 'Review & hand off',
      description: 'Review the case and hand off to the credit officer when ready.',
      targetTab: 'overview',
    }
  }

  if (status === 'BORROWER_SUBMITTED' && (canHandOffToCo(role) || canAcceptBorrowerSubmission(role))) {
    return {
      label: 'Review submission',
      description: 'Use Overview controls to hand off, send back, or accept for processing.',
      targetTab: 'overview',
    }
  }

  if (status === 'PENDING_CREDIT_OFFICER' && canAcceptBorrowerSubmission(role)) {
    return {
      label: 'Accept for KYC',
      description: 'Accept this application on Overview to begin KYC processing.',
      targetTab: 'overview',
    }
  }

  if (status === 'KYC_IN_PROGRESS' && canRunKycFlow(role)) {
    return {
      label: 'Run KYC',
      description: 'Open KYC and run identity verification.',
      targetTab: 'kyc',
    }
  }

  if (status === 'KYC_FAILED' && canRunKycFlow(role)) {
    return {
      label: 'Retry KYC',
      description: 'Open KYC to retry verification or review exceptions.',
      targetTab: 'kyc',
    }
  }

  if ((status === 'UNDERWRITING' || status === 'UNDERWRITING_COMPLETED') && canRunUnderwriting(role)) {
    return {
      label: 'Review assessment',
      description: isInvoiceDiscountingAnchorApp(app)
        ? 'Complete or review anchor rating under Credit Assessment.'
        : 'Run or review live underwriting under Credit Assessment.',
      targetTab: 'credit',
    }
  }

  if (status === 'CAM_READY' && (isCamEditorRole(role) || isCamCheckerRole(role))) {
    return {
      label: 'Complete CAM',
      description: 'Open Decision to complete or review the credit appraisal memo.',
      targetTab: 'decision',
    }
  }

  if (status === 'CAM_SENT_BACK' && isCamEditorRole(role)) {
    return {
      label: 'Update CAM',
      description: 'Address send-back comments on the credit appraisal memo.',
      targetTab: 'decision',
    }
  }

  if (status === 'CAM_REVIEWED' && canAccessSanction(role)) {
    return {
      label: 'Proceed to sanction',
      description: 'Continue to sanction and terms in Decision.',
      targetTab: 'decision',
    }
  }

  if (
    (status === 'SANCTION_PENDING' || status === 'APPROVED') &&
    canAccessSanction(role)
  ) {
    return {
      label: 'Review sanction',
      description: 'Issue sanction terms / KFS in Decision.',
      targetTab: 'decision',
    }
  }

  if (
    (status === 'KFS_GENERATED' || status === 'SANCTIONED' || status === 'SANCTION_ISSUED') &&
    !isRelationshipManager(role)
  ) {
    return {
      label: 'Continue to eSign',
      description: 'Send or complete eSign under Decision → Completion.',
      targetTab: 'decision',
    }
  }

  if (status === 'ESIGN_PENDING' && !isRelationshipManager(role)) {
    return {
      label: 'Complete eSign',
      description: 'Manage eSign under Decision → Completion.',
      targetTab: 'decision',
    }
  }

  if (
    (status === 'ESIGN_COMPLETED' ||
      status === 'READY_FOR_DISBURSEMENT' ||
      status === 'DISBURSEMENT_PENDING') &&
    showDisbursementInDecision({ role, app })
  ) {
    return {
      label: 'Proceed to disbursement',
      description: 'Complete disbursement readiness under Decision → Completion.',
      targetTab: 'decision',
    }
  }

  if (
    isRelationshipManager(role) &&
    (status === 'SANCTION_PENDING' ||
      status === 'SANCTIONED' ||
      status === 'KFS_GENERATED' ||
      status === 'CAM_REVIEWED' ||
      isInvoiceDiscountingAnchorApp(app))
  ) {
    return {
      label: 'Open Decision',
      description: 'Review sanction / program setup for this application.',
      targetTab: 'decision',
    }
  }

  return null
}

/** KYC inner nav — labels only; does not change available actions. */
export type KycWorkbenchSubSectionId = 'identity' | 'vkyc' | 'banking' | 'verification'

export function kycWorkbenchSubSections(opts: {
  vkycVisible: boolean
  bankingVisible: boolean
}): { id: KycWorkbenchSubSectionId; label: string }[] {
  const subs: { id: KycWorkbenchSubSectionId; label: string }[] = [
    { id: 'identity', label: 'Identity Verification' },
  ]
  if (opts.vkycVisible) subs.push({ id: 'vkyc', label: 'Video / Physical KYC' })
  if (opts.bankingVisible) subs.push({ id: 'banking', label: 'Banking Evidence' })
  subs.push({ id: 'verification', label: 'Business Verification' })
  return subs
}

export type CollateralOverviewCopy = {
  snapshotHint: string | null
  body: string
  /** When true, UI may link to existing continue-intake surface (never Credit Assessment for RM). */
  suggestContinueIntake: boolean
}

/**
 * Overview collateral wording — never directs RM to the hidden Credit Assessment tab.
 */
export function collateralOverviewCopy(opts: {
  role: string
  hasCollateralIntake: boolean
  canContinueIntake: boolean
}): CollateralOverviewCopy {
  const creditVisible = visibleWorkbenchTabs({ role: opts.role }).some((t) => t.id === 'credit')
  if (creditVisible) {
    return {
      snapshotHint: 'Collateral for this secured product is under Credit Assessment.',
      body: opts.hasCollateralIntake
        ? 'Open Credit Assessment → Collateral for the full breakdown.'
        : 'No collateral intake on file yet. Open Credit Assessment → Collateral after the applicant completes the step.',
      suggestContinueIntake: false,
    }
  }
  if (opts.hasCollateralIntake) {
    return {
      snapshotHint: null,
      body: 'Collateral details are available in the application summary.',
      suggestContinueIntake: false,
    }
  }
  if (opts.canContinueIntake) {
    return {
      snapshotHint: null,
      body: 'No collateral intake on file yet. Continue intake to capture or correct collateral details.',
      suggestContinueIntake: true,
    }
  }
  return {
    snapshotHint: null,
    body: 'No collateral intake on file yet. Collateral details will appear in the application summary when available.',
    suggestContinueIntake: false,
  }
}

/**
 * Credit Manager primary tabs (Prospect Demo / simplified journey).
 * Technical surfaces remain under Advanced in the Studio page.
 */
export const PROSPECT_DEMO_VISIBLE_TAB_IDS = [
  'rules',
  'simulation',
  'lifecycle',
  'overview',
] as const

/** Demoted from primary navigation — still reachable via Advanced. */
export const PROSPECT_DEMO_HIDDEN_TAB_IDS = [
  'kyc-eligibility',
  'structure',
  'ambiguities',
  'data-readiness',
  'tests',
  'approvals',
] as const

export const POLICY_STUDIO_PRIMARY_TAB_IDS = [
  'rules',
  'simulation',
  'lifecycle',
  'overview',
] as const

export const POLICY_STUDIO_ADVANCED_TAB_IDS = [
  'kyc-eligibility',
  'structure',
  'ambiguities',
  'data-readiness',
  'tests',
  'approvals',
] as const
