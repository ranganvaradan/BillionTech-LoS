import { http } from './http'

export type LifecycleStatus = 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'ACTIVE' | 'RETIRED'

export type LifecycleAction =
  | 'EDIT'
  | 'DELETE'
  | 'SUBMIT'
  | 'APPROVE'
  | 'RETURN'
  | 'ACTIVATE'
  | 'RETIRE'
  | 'COPY'

export interface LifecycleActionRequest {
  remarks?: string | null
  reason?: string | null
  /** Exact Policy Studio catalogue row. Submit may persist this before transitioning. */
  policyApplicabilityId?: string | null
  policyDocumentId?: string | null
  policyVersionLabel?: string | null
}

export interface ActivationCheck {
  code: string
  label: string
  ok: boolean
  detail: string | null
}

export interface ActivationReadiness {
  id: string
  objectType: string
  status: string
  ready: boolean
  checks: ActivationCheck[]
  overlapWarnings: Record<string, unknown>[]
}

export interface CustomerCategory {
  id: string
  code: string
  versionNo: number
  name: string
  description: string | null
  status: LifecycleStatus
  /** Transitional alias — same value as {@link entityType}. */
  borrowerType: string
  loanProduct: string
  /** Transitional alias — same value as {@link customerRole}. */
  intakeSegment: string
  /** Canonical Entity Type (design lock); mirrors borrowerType during compatibility. */
  entityType?: string
  /** Canonical Customer Role (design lock); mirrors intakeSegment during compatibility. */
  customerRole?: string
  minAmount: number | null
  maxAmount: number | null
  /** Transitional internal package id — optional; not required for new Categories. */
  policySetId?: string | null
  seedSourceRuleSetId?: string | null
  reviewStatus?: string | null
  inferenceNotes?: Record<string, unknown>
  effectiveFrom: string | null
  effectiveUntil: string | null
  createdAt?: string | null
  updatedAt?: string | null
  createdBy?: string | null
  updatedBy?: string | null
  submittedBy?: string | null
  submittedAt?: string | null
  approvedBy?: string | null
  approvedAt?: string | null
  activatedBy?: string | null
  activatedAt?: string | null
  retiredBy?: string | null
  retiredAt?: string | null
  retirementReason?: string | null
  reasonForChange?: string | null
  replacesCategoryId?: string | null
  overlapWarnings?: Record<string, unknown>[]
  governanceJson?: Record<string, unknown> | null
  allowedActions?: LifecycleAction[]
  history?: Record<string, unknown>[]
  /** Principal Policy Studio catalogue id (exact Policy Version). */
  policyApplicabilityId?: string | null
  policyDocumentId?: string | null
  policyVersionLabel?: string | null
  policyLineageId?: string | null
  policyName?: string | null
  policyBusinessStatus?: string | null
  /** LINKED | POLICY_LINKAGE_REQUIRED */
  policyLinkageStatus?: string | null
  /** W2 exact Workflow Version id. */
  workflowId?: string | null
  workflowVersion?: number | null
  workflowContentHash?: string | null
  workflowName?: string | null
  /** LINKED | WORKFLOW_LINKAGE_REQUIRED */
  workflowLinkageStatus?: string | null
}

/**
 * Category create/update body.
 * Precedence: when both canonical and transitional fields are present they must agree
 * (case-insensitive). Disagreement → TERMINOLOGY_CONFLICT_* (fail closed).
 * Prefer sending {@code entityType}/{@code customerRole}; transitional aliases remain accepted.
 * Policy bind: prefer {@code policyApplicabilityId}; {@code policySetId} is transitional/optional.
 */
export interface CategoryRequest {
  code: string
  name: string
  description?: string | null
  borrowerType?: string
  loanProduct: string
  intakeSegment?: string
  entityType?: string
  customerRole?: string
  minAmount?: number | null
  maxAmount?: number | null
  /** Transitional — optional; not required for new Categories. */
  policySetId?: string | null
  policyApplicabilityId?: string | null
  policyDocumentId?: string | null
  policyVersionLabel?: string | null
  /** W2 — exact Workflow Version; independent of Policy. */
  workflowId?: string | null
  workflowVersion?: number | null
  effectiveFrom?: string | null
  effectiveUntil?: string | null
  reasonForChange?: string | null
}

/** Policy Studio catalogue picker row for Category admin. */
export interface EligiblePolicy {
  policyApplicabilityId: string
  policyDocumentId: string | null
  policyName: string
  policyVersionLabel: string
  enginePolicyVersionId?: string | null
  businessStatus: string | null
  effectiveFrom?: string | null
  effectiveUntil?: string | null
  products?: string[]
  entityTypes?: string[]
  customerRoleApplicability?: string | null
  minLoanAmount?: number | null
  maxLoanAmount?: number | null
  dataReadinessStatus?: string | null
  testsStatus?: string | null
  simulationReviewStatus?: string | null
  shadowEligibility?: string | null
  shadowRoutable?: boolean | null
  productionAuthority?: string | null
  allowCanonicalAuthority?: boolean | null
  /** True only when compatibilityStatus === COMPATIBLE */
  compatibleWithCategory: boolean
  /** COMPATIBLE | INCOMPATIBLE | NEEDS_ADDITIONAL_SCOPE_CONTEXT */
  compatibilityStatus?: string | null
  compatibilityReasons?: string[]
  compatibilityNotes?: string[]
  /** Business-facing scope summary */
  scopeSummary?: string | null
  /** Server authority — do not infer from name/status on the client. */
  eligibleForCategoryLinkage?: boolean
  linkageOwnerType?: string | null
  linkageOwnerId?: string | null
  lifecycleAuthority?: string | null
  ineligibleReason?: string | null
}

export interface CategoryPolicyCompatibilityReportRow {
  categoryCode: string
  categoryName: string
  status: string
  compatiblePolicyVersionCount: number
  incompatibleCount: number
  needsContextCount: number
}

/** W2 Workflow picker row for Category admin. */
export interface EligibleWorkflow {
  workflowId: string
  workflowName: string
  workflowVersion: number
  active: boolean
  publicationStatus?: string | null
  workflowFamilyId?: string | null
  eligibleForNewBind?: boolean
  entityTypeApplicability?: string | null
  customerRoleApplicability?: string | null
  productApplicability?: string | null
  journeyStepSummary?: string | null
  contentHash?: string | null
  compatibleWithCategory: boolean
  /** COMPATIBLE | INCOMPATIBLE */
  compatibilityStatus?: string | null
  compatibilityReasons?: string[]
  compatibilityNotes?: string[]
  scopeSummary?: string | null
}

export interface EligibleRuleSet {
  id: string
  name: string
  borrowerType: string
  loanProduct: string
  minAmount: number | null
  maxAmount: number | null
  priority: number
  active: boolean
}

export interface EligibleScorecard {
  id: string
  name: string
  borrowerType: string
  loanProduct: string
  minAmount: number | null
  maxAmount: number | null
  priority: number
  status: string
  active: boolean
}

export function listCustomerCategories() {
  return http.get<CustomerCategory[]>('/customer-categories').then((r) => r.data)
}

export function getCustomerCategory(id: string) {
  return http.get<CustomerCategory>(`/customer-categories/${id}`).then((r) => r.data)
}

export function createCustomerCategory(body: CategoryRequest) {
  return http.post<CustomerCategory>('/customer-categories', body).then((r) => r.data)
}

export function updateCustomerCategory(id: string, body: CategoryRequest) {
  return http.put<CustomerCategory>(`/customer-categories/${id}`, body).then((r) => r.data)
}

export function deleteCustomerCategory(id: string) {
  return http.delete(`/customer-categories/${id}`)
}

export function submitCustomerCategory(id: string, body?: LifecycleActionRequest) {
  return http.post<CustomerCategory>(`/customer-categories/${id}/submit`, body ?? {}).then((r) => r.data)
}

export function approveCustomerCategory(id: string, body?: LifecycleActionRequest) {
  return http.post<CustomerCategory>(`/customer-categories/${id}/approve`, body ?? {}).then((r) => r.data)
}

export function returnCustomerCategory(id: string, body?: LifecycleActionRequest) {
  return http.post<CustomerCategory>(`/customer-categories/${id}/return`, body ?? {}).then((r) => r.data)
}

export function activateCustomerCategory(id: string) {
  return http.post<CustomerCategory>(`/customer-categories/${id}/activate`).then((r) => r.data)
}

export function retireCustomerCategory(id: string, body: LifecycleActionRequest) {
  return http.post<CustomerCategory>(`/customer-categories/${id}/retire`, body).then((r) => r.data)
}

export function copyCustomerCategory(id: string, body?: LifecycleActionRequest) {
  return http.post<CustomerCategory>(`/customer-categories/${id}/copy`, body ?? {}).then((r) => r.data)
}

export function customerCategoryHistory(id: string) {
  return http.get<Record<string, unknown>[]>(`/customer-categories/${id}/history`).then((r) => r.data)
}

export function customerCategoryActivationReadiness(id: string) {
  return http.get<ActivationReadiness>(`/customer-categories/${id}/activation-readiness`).then((r) => r.data)
}

export function listCategoryOverlaps() {
  return http.get<Record<string, unknown>[]>('/customer-categories/meta/overlaps').then((r) => r.data)
}

export function listEligiblePolicies(params?: {
  entityType?: string
  borrowerType?: string
  loanProduct?: string
  customerRole?: string
  intakeSegment?: string
  minAmount?: number
  maxAmount?: number
  effectiveFrom?: string
  effectiveUntil?: string
}) {
  return http
    .get<EligiblePolicy[]>('/customer-categories/meta/eligible-policies', { params })
    .then((r) => r.data)
}

export function listEligibleWorkflows(params?: {
  entityType?: string
  borrowerType?: string
  loanProduct?: string
  customerRole?: string
  intakeSegment?: string
}) {
  return http
    .get<EligibleWorkflow[]>('/customer-categories/meta/eligible-workflows', { params })
    .then((r) => r.data)
}

export function fetchPolicyScopeCompatibilityReport() {
  return http
    .get<CategoryPolicyCompatibilityReportRow[]>(
      '/customer-categories/meta/policy-scope-compatibility-report',
    )
    .then((r) => r.data)
}

export function listEligibleRuleSets(params?: {
  borrowerType?: string
  loanProduct?: string
  amount?: number
}) {
  return http
    .get<EligibleRuleSet[]>('/customer-categories/meta/eligible-rule-sets', { params })
    .then((r) => r.data)
}

export function listEligibleScorecards(params?: {
  borrowerType?: string
  loanProduct?: string
  amount?: number
}) {
  return http
    .get<EligibleScorecard[]>('/customer-categories/meta/eligible-scorecards', { params })
    .then((r) => r.data)
}
