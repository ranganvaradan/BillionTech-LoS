/** Demo session after POST /api/v1/auth/login (not JWT). */
export interface SessionUser {
  userId: string
  name: string
  email: string
  role: string
  institution: string
  /** True when the account uses a temporary password and must set a new one before continuing. */
  passwordResetRequired?: boolean
}

const STORAGE_KEY = 'los_demo_session_v1'

export function loadSessionUser(): SessionUser | null {
  if (typeof window === 'undefined') return null
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const p = JSON.parse(raw) as unknown
    if (!p || typeof p !== 'object') return null
    const o = p as Record<string, unknown>
    if (
      typeof o.userId !== 'string' ||
      typeof o.name !== 'string' ||
      typeof o.email !== 'string' ||
      typeof o.role !== 'string' ||
      typeof o.institution !== 'string'
    ) {
      return null
    }
    return o as unknown as SessionUser
  } catch {
    return null
  }
}

export function saveSessionUser(user: SessionUser): void {
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(user))
}

export function clearSessionUser(): void {
  window.localStorage.removeItem(STORAGE_KEY)
}

/** Admin-only sidebar entries (workflows, rules, IAM-style screens). */
export function canAccessAdminConfigNav(role: string): boolean {
  return role === 'ADMINISTRATOR' || role === 'CREDIT_MANAGER'
}

/** Roles allowed to delete borrower applications (aligned with los-core ApplicationDeletionService). */
export function canDeleteApplication(role: string): boolean {
  const r = String(role ?? '')
    .trim()
    .toUpperCase()
  return (
    r === 'ADMIN' ||
    r === 'ADMINISTRATOR' ||
    r === 'CREDIT_MANAGER' ||
    r === 'CREDIT_OFFICER' ||
    r === 'PLATFORM_ADMIN'
  )
}

export const BORROWER_ROLE = 'BORROWER'

export function isBorrowerRole(role: string): boolean {
  return role === BORROWER_ROLE
}

/** Roles allowed to complete Physical KYC (PKYC) fallback — aligned with los-core {@code VkycWorkflowService}. */
export function canCompletePhysicalVkyc(role: string): boolean {
  const r = String(role ?? '')
    .trim()
    .toUpperCase()
  return (
    r === 'ADMIN' ||
    r === 'ADMINISTRATOR' ||
    r === 'OPERATIONS' ||
    r === 'VKYC_MANAGER' ||
    r === 'RISK_MANAGER' ||
    r === 'CREDIT_MANAGER' ||
    r === 'BRANCH_VERIFIER' ||
    r === 'KYC_REVIEWER'
  )
}

const CAM_MAKER_ROLES = new Set([
  'CREDIT_OFFICER',
  'ADMIN',
  'ADMINISTRATOR',
  'RISK_MANAGER',
])

/** Roles allowed to edit CAM fields (makers + credit manager checkers). */
const CAM_EDITOR_ROLES = new Set([
  'CREDIT_OFFICER',
  'CREDIT_MANAGER',
  'ADMIN',
  'ADMINISTRATOR',
  'RISK_MANAGER',
])

const CAM_CHECKER_ROLES = new Set([
  'CREDIT_MANAGER',
  'ADMIN',
  'ADMINISTRATOR',
  'RISK_MANAGER',
])

const L2_SANCTION_ROLES = new Set([
  'CREDIT_MANAGER',
  'ADMIN',
  'ADMINISTRATOR',
  'RISK_MANAGER',
])

export function isCamMakerRole(role: string): boolean {
  return CAM_MAKER_ROLES.has(String(role ?? '').trim().toUpperCase())
}

export function isCamEditorRole(role: string): boolean {
  return CAM_EDITOR_ROLES.has(String(role ?? '').trim().toUpperCase())
}

export function isCamCheckerRole(role: string): boolean {
  return CAM_CHECKER_ROLES.has(String(role ?? '').trim().toUpperCase())
}

export function isL2SanctionRole(role: string): boolean {
  return L2_SANCTION_ROLES.has(String(role ?? '').trim().toUpperCase())
}

export function isRelationshipManager(role: string): boolean {
  return String(role ?? '').trim().toUpperCase() === 'RELATIONSHIP_MANAGER'
}

/** CO, CM, Admin — not RM. */
export function canRunKycFlow(role: string): boolean {
  const r = String(role ?? '').trim().toUpperCase()
  return (
    r === 'CREDIT_OFFICER' ||
    r === 'CREDIT_MANAGER' ||
    r === 'ADMIN' ||
    r === 'ADMINISTRATOR' ||
    r === 'RISK_MANAGER'
  )
}

/** Same gate as KYC run for underwriting actions. */
export function canRunUnderwriting(role: string): boolean {
  return canRunKycFlow(role)
}

/** CAM tab actions — not RM. */
export function canAccessCamActions(role: string): boolean {
  return isCamEditorRole(role) || isCamCheckerRole(role)
}

/** Sanction decision actions — CM/Admin only (not CO, not RM). */
export function canAccessSanction(role: string): boolean {
  return isL2SanctionRole(role)
}

/** Roles allowed to create applications and use Save draft & notify borrower (not CREDIT_OFFICER). */
export function canCreateOrNotifyBorrowerIntake(role: string): boolean {
  const r = String(role ?? '')
    .trim()
    .toUpperCase()
  return r === 'RELATIONSHIP_MANAGER' || r === 'ADMIN' || r === 'ADMINISTRATOR'
}

export function canHandOffToCo(role: string): boolean {
  const r = String(role ?? '')
    .trim()
    .toUpperCase()
  return r === 'RELATIONSHIP_MANAGER' || r === 'ADMIN' || r === 'ADMINISTRATOR'
}

export function canSendBackToRm(role: string): boolean {
  const r = String(role ?? '')
    .trim()
    .toUpperCase()
  return r === 'CREDIT_OFFICER' || r === 'ADMIN' || r === 'ADMINISTRATOR'
}

export function canSendBackToBorrower(role: string): boolean {
  const r = String(role ?? '')
    .trim()
    .toUpperCase()
  return r === 'RELATIONSHIP_MANAGER' || r === 'ADMIN' || r === 'ADMINISTRATOR'
}

export function canAcceptBorrowerSubmission(role: string): boolean {
  const r = String(role ?? '')
    .trim()
    .toUpperCase()
  return r === 'CREDIT_OFFICER' || r === 'ADMIN' || r === 'ADMINISTRATOR'
}
