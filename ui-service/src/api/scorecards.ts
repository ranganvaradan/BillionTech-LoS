import { http } from './http'
import type { BorrowerType } from '@/types/createApplication'

export type ScorecardInputType = 'number' | 'text' | 'dropdown' | 'formula'

export interface ScorecardParamOption {
  value: string
  label: string
  score: number
}

export interface FormulaOperand {
  parameter: string
  source: string
}

export interface FormulaDefinition {
  expression: string
  operands: FormulaOperand[]
}

export interface DependencyCondition {
  source: string
  parameter: string
  condition: string
}

export interface DependencyGroup {
  logic?: 'ALL' | 'ANY'
  conditions: DependencyCondition[]
}

export interface ScorecardParameterDef {
  inputType: ScorecardInputType
  options?: ScorecardParamOption[]
  formula?: FormulaDefinition
}

export interface ScorecardRow {
  id: string
  parameter: string
  source: string
  condition: string
  weight: number
  score: number
  attachment?: string
  inputType?: ScorecardInputType
  options?: ScorecardParamOption[]
  formula?: FormulaDefinition
  dependsOn?: DependencyGroup
  /** SCORECARD-CONVERGENCE-1 */
  canonicalParameterId?: string
  canonicalDefinitionVersion?: number
  mappingStatus?: string
  legacyParameterKey?: string
  factorLabel?: string
  missingData?: 'REQUIRED' | 'OPTIONAL_DEPRESS' | 'OPTIONAL_SKIP'
  legacyCustomJustified?: boolean
}

export interface HardRuleRow {
  id: string
  parameter: string
  source: string
  condition: string
  decision: 'REJECT' | 'MANUAL_REVIEW'
  message?: string
  dependsOn?: DependencyGroup
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
  status?: string
  lineageId?: string | null
  parentScorecardId?: string | null
  activatedAt?: string | null
  safetyJson?: Record<string, unknown>
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
  status?: string
  lineageId?: string | null
  parentScorecardId?: string | null
  activatedAt?: string | null
  safetyJson?: Record<string, unknown>
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

/** SCORECARD-SAFETY-FOUNDATION-1 — clone ACTIVE/DRAFT into editable DRAFT vN+1 */
export async function createScorecardNewVersion(id: string): Promise<UnderwritingScorecardResponse> {
  const { data } = await http.post<UnderwritingScorecardResponse>(
    `/underwriting/scorecards/${id}/new-version`,
  )
  return data
}

/** SCORECARD-CONVERGENCE-1 — GACAT-backed factor picker */
export async function listGacatScorecardFactors(q?: string): Promise<{
  parameters: Array<Record<string, unknown>>
  count: number
  catalogueAuthority?: string
}> {
  const { data } = await http.get<{
    parameters: Array<Record<string, unknown>>
    count: number
    catalogueAuthority?: string
  }>('/underwriting/scorecards/gacat-factors', { params: q ? { q } : {} })
  return data
}

export async function previewScorecard(body: Record<string, unknown>): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>('/underwriting/scorecards/preview', body)
  return data
}

export async function suggestScorecardFactorsFromPolicy(
  parameterIds: string[],
): Promise<{ suggestions: Array<Record<string, unknown>>; note?: string }> {
  const { data } = await http.post<{ suggestions: Array<Record<string, unknown>>; note?: string }>(
    '/underwriting/scorecards/suggest-from-policy',
    { parameterIds },
  )
  return data
}

export async function confirmScorecardMissingDataPolicies(
  id: string,
): Promise<UnderwritingScorecardResponse> {
  const { data } = await http.post<UnderwritingScorecardResponse>(
    `/underwriting/scorecards/${id}/confirm-missing-data-policies`,
  )
  return data
}
