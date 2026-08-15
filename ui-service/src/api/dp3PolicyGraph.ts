import { http } from '@/api/http'

/**
 * Relative to http baseURL (`/api/v1` on staging). Must NOT include `/api/v1` —
 * axios joins baseURL + path and would produce `/api/v1/api/v1/...`.
 */
const BASE = 'internal/credit-intelligence/dp3'

export { BASE as DP3_POLICY_GRAPH_API_BASE }

export async function fetchPolicyParameterInventory(policyDocumentId: string) {
  const { data } = await http.get<Record<string, unknown>>(
    `${BASE}/policies/${encodeURIComponent(policyDocumentId)}/parameter-inventory`,
  )
  return data
}

export async function fetchScorecardFactorPicker(policyDocumentId: string) {
  const { data } = await http.get<Record<string, unknown>>(
    `${BASE}/policies/${encodeURIComponent(policyDocumentId)}/scorecard-factor-picker`,
  )
  return data
}

export async function previewScorecardWeights(body: {
  factors: Array<{
    canonicalParameterId: string
    rawWeight: number | string
    required?: boolean
    dataState?: string
  }>
}) {
  const { data } = await http.post<Record<string, unknown>>(`${BASE}/scorecards/weight-preview`, body)
  return data
}

export async function materializePolicyGraph(policyDocumentId: string) {
  const { data } = await http.post<Record<string, unknown>>(
    `${BASE}/policies/${encodeURIComponent(policyDocumentId)}/materialize-graph`,
  )
  return data
}
