import { http } from './http'
import type { BorrowerType } from '@/types/createApplication'

export interface UnderwritingRuleSetResponse {
  id: string
  name: string
  borrowerType: string
  loanProduct: string
  minAmount: number | null
  maxAmount: number | null
  geography: Record<string, unknown> | null
  minTenureMonths: number | null
  maxTenureMonths: number | null
  priority: number
  active: boolean
  rulesJson: Record<string, unknown>
  createdAt: string | null
  updatedAt: string | null
}

export interface UnderwritingRuleSetRequest {
  name: string
  borrowerType: BorrowerType
  loanProduct: string
  minAmount: number | null
  maxAmount: number | null
  geography: Record<string, unknown> | null
  minTenureMonths: number | null
  maxTenureMonths: number | null
  priority: number
  rulesJson: Record<string, unknown>
}

export async function listUnderwritingRules(): Promise<UnderwritingRuleSetResponse[]> {
  const { data } = await http.get<UnderwritingRuleSetResponse[]>('/underwriting/rules')
  return data
}

export async function createUnderwritingRule(
  request: UnderwritingRuleSetRequest,
): Promise<UnderwritingRuleSetResponse> {
  const { data } = await http.post<UnderwritingRuleSetResponse>('/underwriting/rules', request)
  return data
}

export async function updateUnderwritingRule(
  id: string,
  request: UnderwritingRuleSetRequest,
): Promise<UnderwritingRuleSetResponse> {
  const { data } = await http.put<UnderwritingRuleSetResponse>(`/underwriting/rules/${id}`, request)
  return data
}

export async function deleteUnderwritingRule(id: string): Promise<void> {
  await http.delete(`/underwriting/rules/${id}`)
}

export async function activateUnderwritingRule(id: string): Promise<void> {
  await http.post(`/underwriting/rules/${id}/activate`)
}

export async function deactivateUnderwritingRule(id: string): Promise<void> {
  await http.post(`/underwriting/rules/${id}/deactivate`)
}
