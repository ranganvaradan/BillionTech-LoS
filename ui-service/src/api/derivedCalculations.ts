/** Derived calculation authoring + research/approval API (Data & Parameters / Policy Studio). */
import { http } from '@/api/http'

/**
 * Relative to http baseURL (`/api/v1` on staging). Must NOT include `/api/v1`.
 */
const BASE = 'data-parameters/derived-calculations'

export type DerivedCalculationDraftRequest = {
  canonicalParameterId: string
  scope?: 'PLATFORM' | 'LENDER'
  expression: Record<string, unknown>
  resultType?: string
  unit?: string
  description?: string
}

export async function getDerivedCalculationLatest(
  canonicalParameterId: string,
  opts?: { tenantId?: string },
): Promise<Record<string, unknown>> {
  const q = opts?.tenantId ? `?tenantId=${encodeURIComponent(opts.tenantId)}` : ''
  const { data } = await http.get<Record<string, unknown>>(
    `${BASE}/by-parameter/${encodeURIComponent(canonicalParameterId)}${q}`,
  )
  return data
}

export async function saveDerivedCalculationDraft(
  body: DerivedCalculationDraftRequest,
  opts?: { tenantId?: string },
): Promise<Record<string, unknown>> {
  const q = opts?.tenantId ? `?tenantId=${encodeURIComponent(opts.tenantId)}` : ''
  const { data } = await http.post<Record<string, unknown>>(`${BASE}${q}`, body)
  return data
}

export async function testDerivedCalculation(
  id: string,
  sampleInputs: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `${BASE}/${encodeURIComponent(id)}/test`,
    sampleInputs,
  )
  return data
}

export async function markDerivedCalculationProductionReady(
  id: string,
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `${BASE}/${encodeURIComponent(id)}/mark-production-ready`,
    {},
  )
  return data
}

export async function suggestDerivedCalculation(
  canonicalParameterId: string,
  opts?: { tenantId?: string },
): Promise<Record<string, unknown>> {
  const q = opts?.tenantId ? `?tenantId=${encodeURIComponent(opts.tenantId)}` : ''
  const { data } = await http.post<Record<string, unknown>>(`${BASE}/research/suggest${q}`, {
    targetParameterId: canonicalParameterId,
    canonicalParameterId,
  })
  return data
}

export async function acceptDerivedCalculationProposal(
  proposalId: string,
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `${BASE}/research/proposals/${encodeURIComponent(proposalId)}/accept`,
    {},
  )
  return data
}

export async function rejectDerivedCalculationProposal(
  proposalId: string,
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `${BASE}/research/proposals/${encodeURIComponent(proposalId)}/reject`,
    {},
  )
  return data
}

export async function editDerivedCalculationProposal(
  proposalId: string,
  expression: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `${BASE}/research/proposals/${encodeURIComponent(proposalId)}/edit`,
    { expression },
  )
  return data
}
