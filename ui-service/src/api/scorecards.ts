import { http } from './http'
import type { BorrowerType } from '@/types/createApplication'

export interface ScorecardRow {
  id: string
  parameter: string
  source: string
  condition: string
  weight: number
  score: number
  attachment?: string
}

export interface HardRuleRow {
  id: string
  parameter: string
  source: string
  condition: string
  decision: 'REJECT' | 'MANUAL_REVIEW'
  message?: string
}

export interface UnderwritingScorecardResponse {
  id: string
  name: string
  borrowerType: string
  loanProduct: string
  version: number
  priority: number
  minAmount: number | null
  maxAmount: number | null
  geography: Record<string, unknown> | null
  scorecardJson: Record<string, unknown>
  thresholdsJson: Record<string, unknown>
  hardRulesJson: Record<string, unknown>
  active: boolean
  createdAt: string | null
  updatedAt: string | null
}

export interface UnderwritingScorecardRequest {
  name: string
  borrowerType: BorrowerType
  loanProduct: string
  version: number
  priority: number
  minAmount: number | null
  maxAmount: number | null
  geography: Record<string, unknown> | null
  scorecardJson: Record<string, unknown>
  thresholdsJson: Record<string, unknown>
  hardRulesJson: Record<string, unknown>
  active: boolean
}

export async function listScorecards(): Promise<UnderwritingScorecardResponse[]> {
  const { data } = await http.get<UnderwritingScorecardResponse[]>('/underwriting/scorecards')
  return data
}

export async function createScorecard(
  request: UnderwritingScorecardRequest,
): Promise<UnderwritingScorecardResponse> {
  const { data } = await http.post<UnderwritingScorecardResponse>('/underwriting/scorecards', request)
  return data
}

export async function updateScorecard(
  id: string,
  request: UnderwritingScorecardRequest,
): Promise<UnderwritingScorecardResponse> {
  const { data } = await http.put<UnderwritingScorecardResponse>(`/underwriting/scorecards/${id}`, request)
  return data
}

export async function deleteScorecard(id: string): Promise<void> {
  await http.delete(`/underwriting/scorecards/${id}`)
}
