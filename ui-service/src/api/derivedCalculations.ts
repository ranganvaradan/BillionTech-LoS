/** Derived calculation authoring API (Data & Parameters). */
import { apiFetch } from '@/api/http'

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
  return apiFetch(
    `/api/v1/data-parameters/derived-calculations/by-parameter/${encodeURIComponent(canonicalParameterId)}${q}`,
  )
}

export async function saveDerivedCalculationDraft(
  body: DerivedCalculationDraftRequest,
  opts?: { tenantId?: string },
): Promise<Record<string, unknown>> {
  const q = opts?.tenantId ? `?tenantId=${encodeURIComponent(opts.tenantId)}` : ''
  return apiFetch(`/api/v1/data-parameters/derived-calculations${q}`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export async function testDerivedCalculation(
  id: string,
  sampleInputs: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  return apiFetch(`/api/v1/data-parameters/derived-calculations/${encodeURIComponent(id)}/test`, {
    method: 'POST',
    body: JSON.stringify(sampleInputs),
  })
}

export async function markDerivedCalculationProductionReady(
  id: string,
): Promise<Record<string, unknown>> {
  return apiFetch(
    `/api/v1/data-parameters/derived-calculations/${encodeURIComponent(id)}/mark-production-ready`,
    { method: 'POST', body: '{}' },
  )
}
