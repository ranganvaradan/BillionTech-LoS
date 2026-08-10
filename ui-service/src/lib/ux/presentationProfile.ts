/**
 * UX-4B5 — presentation-only role profiles.
 * Highlights / defaults / de-emphasis only. Never grants access.
 */
import {
  canAccessAdminConfigNav,
  canCreateOrNotifyBorrowerIntake,
  canRunKycFlow,
  canRunUnderwriting,
  isCamCheckerRole,
  isCamMakerRole,
  isL2SanctionRole,
  isRelationshipManager,
} from '@/auth/types'
import { ASSIGNMENT_ROLE_LABELS, type AssignmentRoleCode } from '@/api/losDirectory'

export type PresentationPersona =
  | 'relationship_manager'
  | 'credit_officer'
  | 'credit_manager'
  | 'operations_kyc'
  | 'accounts'
  | 'risk'
  | 'admin'
  | 'staff'

export type PresentationProfile = {
  persona: PresentationPersona
  /** Friendly role label for header */
  roleLabel: string
  /** Short dashboard description under welcome */
  dashboardFocus: string
  /** Emphasise New Application when RBAC already allows */
  emphasiseNewApplication: boolean
  /** Prefer KYC subsection on landing when role is ops/kyc */
  preferOpsKycLanding: boolean
  /** Prefer VKYC sub-tab when gate visible */
  preferVkycSubTab: boolean
  /** De-emphasise underwriting queue copy for this persona */
  deEmphasiseUnderwritingQueues: boolean
}

const EXTRA_ROLE_LABELS: Record<string, string> = {
  ADMIN: 'Administrator',
  PLATFORM_ADMIN: 'Platform Admin',
  RISK_MANAGER: 'Risk Manager',
  VKYC_MANAGER: 'VKYC Manager',
  KYC_REVIEWER: 'KYC Reviewer',
  BRANCH_VERIFIER: 'Branch Verifier',
  UNDERWRITER: 'Underwriter',
  SALES_OFFICER: 'Sales Officer',
  ACCOUNTS: 'Accounts',
  OPERATIONS: 'Operations',
}

export function formatRoleLabel(role: string | null | undefined): string {
  const r = String(role ?? '').trim().toUpperCase()
  if (!r) return '—'
  if (r in ASSIGNMENT_ROLE_LABELS) return ASSIGNMENT_ROLE_LABELS[r as AssignmentRoleCode]
  if (EXTRA_ROLE_LABELS[r]) return EXTRA_ROLE_LABELS[r]
  return r
    .split('_')
    .filter(Boolean)
    .map((w) => {
      if (w === 'KYC' || w === 'VKYC' || w === 'RM') return w
      return w.charAt(0) + w.slice(1).toLowerCase()
    })
    .join(' ')
}

export function resolvePresentationProfile(role: string | null | undefined): PresentationProfile {
  const r = String(role ?? '').trim()
  const ru = r.toUpperCase()
  const roleLabel = formatRoleLabel(r)

  if (ru === 'ADMINISTRATOR' || ru === 'ADMIN' || ru === 'PLATFORM_ADMIN') {
    return {
      persona: 'admin',
      roleLabel,
      dashboardFocus: 'Oversee lending operations, policies, and administration.',
      emphasiseNewApplication: canCreateOrNotifyBorrowerIntake(r),
      preferOpsKycLanding: false,
      preferVkycSubTab: false,
      deEmphasiseUnderwritingQueues: false,
    }
  }

  if (ru === 'CREDIT_MANAGER') {
    return {
      persona: 'credit_manager',
      roleLabel,
      dashboardFocus: 'Review credit exceptions, CAM approvals, sanctions, and policies.',
      emphasiseNewApplication: false,
      preferOpsKycLanding: false,
      preferVkycSubTab: false,
      deEmphasiseUnderwritingQueues: false,
    }
  }

  if (ru === 'CREDIT_OFFICER' || isCamMakerRole(r)) {
    return {
      persona: 'credit_officer',
      roleLabel,
      dashboardFocus: 'Work KYC, credit assessment, and CAM maker queues.',
      emphasiseNewApplication: false,
      preferOpsKycLanding: false,
      preferVkycSubTab: false,
      deEmphasiseUnderwritingQueues: false,
    }
  }

  if (isRelationshipManager(r)) {
    return {
      persona: 'relationship_manager',
      roleLabel,
      dashboardFocus: 'Manage intake, borrower handoff, and program actions.',
      emphasiseNewApplication: true,
      preferOpsKycLanding: false,
      preferVkycSubTab: false,
      deEmphasiseUnderwritingQueues: true,
    }
  }

  if (ru === 'ACCOUNTS') {
    return {
      persona: 'accounts',
      roleLabel,
      dashboardFocus: 'Track settlements and completed lending operations.',
      emphasiseNewApplication: false,
      preferOpsKycLanding: false,
      preferVkycSubTab: false,
      deEmphasiseUnderwritingQueues: true,
    }
  }

  if (ru === 'RISK_MANAGER') {
    return {
      persona: 'risk',
      roleLabel,
      dashboardFocus: 'Review credit risk posture across applications and policies.',
      emphasiseNewApplication: false,
      preferOpsKycLanding: false,
      preferVkycSubTab: false,
      deEmphasiseUnderwritingQueues: false,
    }
  }

  if (ru === 'OPERATIONS' || ru === 'KYC_REVIEWER' || ru === 'VKYC_MANAGER' || ru === 'BRANCH_VERIFIER') {
    return {
      persona: 'operations_kyc',
      roleLabel,
      dashboardFocus: 'Complete KYC, VKYC, and verification work on applications.',
      emphasiseNewApplication: false,
      preferOpsKycLanding: true,
      preferVkycSubTab: ru === 'VKYC_MANAGER',
      deEmphasiseUnderwritingQueues: true,
    }
  }

  return {
    persona: 'staff',
    roleLabel,
    dashboardFocus: "Here's what needs attention across lending operations.",
    emphasiseNewApplication: canCreateOrNotifyBorrowerIntake(r),
    preferOpsKycLanding: canRunKycFlow(r) && !canRunUnderwriting(r),
    preferVkycSubTab: false,
    deEmphasiseUnderwritingQueues: !canRunUnderwriting(r) && !isCamCheckerRole(r) && !isL2SanctionRole(r),
  }
}

export type KycSubSectionId = 'identity' | 'vkyc' | 'banking' | 'verification'

/** Presentation default for KYC sub-tab — never unlocks a hidden subsection. */
export function defaultKycSubSection(opts: {
  role: string
  vkycVisible: boolean
}): KycSubSectionId {
  const profile = resolvePresentationProfile(opts.role)
  if (profile.preferVkycSubTab && opts.vkycVisible) return 'vkyc'
  return 'identity'
}

/** Destructive / exception actions — avoid primary orange styling. */
export function destructiveActionClassName(size: 'sm' | 'md' = 'md'): string {
  return size === 'sm' ? 'bt-btn bt-btn-danger bt-btn-sm' : 'bt-btn bt-btn-danger'
}

/** Exception / send-back style (amber) — not forward primary. */
export function exceptionActionClassName(size: 'sm' | 'md' = 'md'): string {
  const base =
    'rounded-md border border-amber-500 bg-amber-50 font-medium text-amber-950 disabled:opacity-50'
  return size === 'sm' ? `${base} px-3 py-1.5 text-sm` : `${base} px-3 py-1.5 text-sm`
}

/**
 * Whether Reports appears in primary nav under current gates.
 * Presentation cannot expand this — documents RBAC backlog for Risk.
 */
export function reportsNavVisibleForRole(role: string): boolean {
  return canAccessAdminConfigNav(role)
}
