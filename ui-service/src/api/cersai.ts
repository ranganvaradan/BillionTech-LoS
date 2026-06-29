import { http } from './http'

export type CersaiAssetType = 'IMMOVABLE' | 'MOVABLE' | 'INTANGIBLE'

export type CersaiRegistrationStatus = 'PENDING' | 'SEARCHED' | 'REGISTERED' | 'FAILED'

export type CersaiSecurityInterestType =
  | 'MORTGAGE'
  | 'HYPOTHECATION'
  | 'PLEDGE'
  | 'LIEN'
  | 'SEARCH'

export interface CersaiCharge {
  lenderName?: string
  chargeType?: string
  amount?: number
  chargeDate?: string
  assetIdentifier?: string
}

export interface CersaiSearchResponseData {
  simulated?: boolean
  assetType?: string
  assetIdentifier?: string
  chargeCount?: number
  charges?: CersaiCharge[]
  message?: string
}

export interface CersaiRegistration {
  id: string
  applicationId: string
  collateralValuationId?: string | null
  cersaiId?: string | null
  assetType: CersaiAssetType | string
  assetDescription?: string | null
  registrationStatus: CersaiRegistrationStatus | string
  securityInterestType: CersaiSecurityInterestType | string
  securedAmount?: number | null
  borrowerName?: string | null
  borrowerPan?: string | null
  lenderName?: string | null
  lenderCin?: string | null
  registrationDate?: string | null
  expiryDate?: string | null
  responseData?: CersaiSearchResponseData | Record<string, unknown> | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface CersaiRegistrationRequest {
  applicationId: string
  collateralValuationId?: string
  assetType?: CersaiAssetType | string
  assetDescription?: string
  assetIdentifier?: string
  securityInterestType?: CersaiSecurityInterestType | string
  securedAmount?: number
  borrowerName?: string
  borrowerPan?: string
  lenderName?: string
  lenderCin?: string
}

export interface SearchCersaiChargesParams {
  assetType: CersaiAssetType | string
  assetIdentifier: string
  applicationId: string
}

/** POST /api/v1/cersai/search — search CERSAI for existing security interests on an asset. */
export async function searchCersaiCharges(params: SearchCersaiChargesParams): Promise<CersaiRegistration> {
  const { data } = await http.post<CersaiRegistration>('/cersai/search', null, { params })
  return data
}

/** POST /api/v1/cersai/register — register a security interest with CERSAI. */
export async function registerCersaiSecurityInterest(
  request: CersaiRegistrationRequest,
): Promise<CersaiRegistration> {
  const { data } = await http.post<CersaiRegistration>('/cersai/register', request)
  return data
}

/** GET /api/v1/cersai/application/{applicationId} — latest CERSAI record for an application. */
export async function getCersaiRegistrationByApplication(applicationId: string): Promise<CersaiRegistration> {
  const { data } = await http.get<CersaiRegistration>(`/cersai/application/${applicationId}`)
  return data
}

/** GET /api/v1/cersai/application/{applicationId}/history — all CERSAI search/register records. */
export async function listCersaiRegistrationsByApplication(
  applicationId: string,
): Promise<CersaiRegistration[]> {
  const { data } = await http.get<CersaiRegistration[]>(`/cersai/application/${applicationId}/history`)
  return data
}

/** Convenience bundle for CollateralPanel and related UI. */
export const cersaiApi = {
  search: searchCersaiCharges,
  register: registerCersaiSecurityInterest,
  getByApplication: getCersaiRegistrationByApplication,
  listByApplication: listCersaiRegistrationsByApplication,
}
