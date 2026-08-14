import { http } from './http'
import type {
  ActivationReadiness,
  LifecycleAction,
  LifecycleActionRequest,
  LifecycleStatus,
} from './customerCategories'

export interface PolicySet {
  id: string
  code: string
  versionNo: number
  name: string
  description: string | null
  status: LifecycleStatus
  primaryRuleSetId: string
  additionalRuleSetIds: string[]
  scorecardId: string | null
  seedSourceRuleSetId?: string | null
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
  replacesPolicySetId?: string | null
  usedByCategoryCount?: number
  allowedActions?: LifecycleAction[]
  history?: Record<string, unknown>[]
}

export interface PolicySetRequest {
  code: string
  name: string
  description?: string | null
  primaryRuleSetId: string
  additionalRuleSetIds?: string[] | null
  scorecardId: string
  effectiveFrom?: string | null
  effectiveUntil?: string | null
  reasonForChange?: string | null
}

export function listPolicySets() {
  return http.get<PolicySet[]>('/policy-sets').then((r) => r.data)
}

export function getPolicySet(id: string) {
  return http.get<PolicySet>(`/policy-sets/${id}`).then((r) => r.data)
}

export function createPolicySet(body: PolicySetRequest) {
  return http.post<PolicySet>('/policy-sets', body).then((r) => r.data)
}

export function updatePolicySet(id: string, body: PolicySetRequest) {
  return http.put<PolicySet>(`/policy-sets/${id}`, body).then((r) => r.data)
}

export function submitPolicySet(id: string, body?: LifecycleActionRequest) {
  return http.post<PolicySet>(`/policy-sets/${id}/submit`, body ?? {}).then((r) => r.data)
}

export function approvePolicySet(id: string, body?: LifecycleActionRequest) {
  return http.post<PolicySet>(`/policy-sets/${id}/approve`, body ?? {}).then((r) => r.data)
}

export function returnPolicySet(id: string, body?: LifecycleActionRequest) {
  return http.post<PolicySet>(`/policy-sets/${id}/return`, body ?? {}).then((r) => r.data)
}

export function activatePolicySet(id: string) {
  return http.post<PolicySet>(`/policy-sets/${id}/activate`).then((r) => r.data)
}

export function retirePolicySet(id: string, body: LifecycleActionRequest) {
  return http.post<PolicySet>(`/policy-sets/${id}/retire`, body).then((r) => r.data)
}

export function copyPolicySet(id: string, body?: LifecycleActionRequest) {
  return http.post<PolicySet>(`/policy-sets/${id}/copy`, body ?? {}).then((r) => r.data)
}

export function policySetHistory(id: string) {
  return http.get<Record<string, unknown>[]>(`/policy-sets/${id}/history`).then((r) => r.data)
}

export function policySetActivationReadiness(id: string) {
  return http.get<ActivationReadiness>(`/policy-sets/${id}/activation-readiness`).then((r) => r.data)
}
