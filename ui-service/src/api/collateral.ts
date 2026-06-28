import { http } from './http'

export type CollateralType =
  | 'PROPERTY'
  | 'VEHICLE'
  | 'GOLD'
  | 'FIXED_DEPOSIT'
  | 'SHARES'
  | 'MACHINERY'

export type CollateralValuationStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'EXPIRED'

export interface CollateralValuation {
  id: string
  applicationId: string
  collateralType: CollateralType
  description?: string | null
  address?: string | null
  marketValue?: number | null
  forcedSaleValue?: number | null
  valuationAmount?: number | null
  valuerId?: string | null
  valuerName?: string | null
  valuationDate?: string | null
  valuationExpiry?: string | null
  status: CollateralValuationStatus
  details?: Record<string, unknown> | null
  createdAt?: string | null
}

export interface CreateCollateralValuationRequest {
  applicationId: string
  collateralType: CollateralType
  description?: string
  address?: string
  details?: Record<string, unknown>
}

export interface CompleteCollateralValuationParams {
  marketValue: number
  forcedSaleValue?: number
  valuerId: string
  valuerName: string
}

export interface CollateralLtvResult {
  applicationId: string
  loanAmount: number
  totalCollateralValue: number
  ltvRatio: number
  ltvAcceptable: boolean
  maxAllowedLtv: number
  valuationCount: number
}

/** POST /api/v1/collateral — create a collateral valuation request. */
export async function createCollateralValuation(
  request: CreateCollateralValuationRequest,
): Promise<CollateralValuation> {
  const { data } = await http.post<CollateralValuation>('/collateral', request)
  return data
}

/** POST /api/v1/collateral/{valuationId}/complete — record valuation results. */
export async function completeCollateralValuation(
  valuationId: string,
  params: CompleteCollateralValuationParams,
): Promise<CollateralValuation> {
  const { data } = await http.post<CollateralValuation>(`/collateral/${valuationId}/complete`, null, {
    params,
  })
  return data
}

/** GET /api/v1/collateral/application/{applicationId} — list valuations for an application. */
export async function listCollateralValuations(applicationId: string): Promise<CollateralValuation[]> {
  const { data } = await http.get<CollateralValuation[]>(`/collateral/application/${applicationId}`)
  return data
}

/** GET /api/v1/collateral/ltv/{applicationId}?loanAmount= — calculate LTV for secured lending. */
export async function calculateCollateralLtv(
  applicationId: string,
  loanAmount: number,
): Promise<CollateralLtvResult> {
  const { data } = await http.get<CollateralLtvResult>(`/collateral/ltv/${applicationId}`, {
    params: { loanAmount },
  })
  return data
}
