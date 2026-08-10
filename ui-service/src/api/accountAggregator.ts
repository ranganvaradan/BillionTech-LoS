import { http } from './http'

export type AaConsentStatus = 'PENDING' | 'APPROVED' | 'DATA_FETCHED' | 'REVOKED'

export interface AaFetchedData {
  fetchTimestamp: string
  accountCount: number
  accounts: Array<{
    type: string
    bank: string
    balance: number
    avgMonthlyBalance: number
    txnCount6Months: number
  }>
  totalBalance: number
  avgMonthlyInflow: number
  avgMonthlyOutflow: number
  regularEmiOutflows: number
  bounceCount6Months: number
}

export interface AaConsent {
  id: string
  applicationId: string
  customerId: string
  consentHandle: string
  consentId?: string | null
  status: AaConsentStatus
  fiTypes: string[]
  consentStartDate: string
  consentExpiryDate: string
  fetchFrequency: string
  consentMode: string
  purposeInfo: Record<string, unknown>
  aaName: string
  approvedAt?: string | null
  revokedAt?: string | null
  revokeReason?: string | null
  fetchedDataSummary?: AaFetchedData | null
  dataFetchedAt?: string | null
  createdAt: string
}

export interface CreateAaConsentParams {
  applicationId: string
  customerId: string
  fiTypes?: string[]
  aaName?: string
  purpose?: Record<string, unknown>
}

export interface InitiateAaConsentParams {
  fiTypes?: string[]
  aaName?: string
  purpose?: Record<string, unknown>
}

/** GET /api/v1/aa/consent/application/{applicationId} — list consents for an application. */
export async function listAaConsents(applicationId: string): Promise<AaConsent[]> {
  const { data } = await http.get<AaConsent[]>(`/aa/consent/application/${applicationId}`)
  return data
}

/** POST /api/v1/aa/consent — create an AA consent request. */
export async function createAaConsent(params: CreateAaConsentParams): Promise<AaConsent> {
  const { purpose, ...queryParams } = params
  const { data } = await http.post<AaConsent>('/aa/consent', purpose ?? null, { params: queryParams })
  return data
}

/** POST /api/v1/aa/consent/application/{applicationId}/initiate — initiate consent (customer resolved server-side). */
export async function initiateAaConsent(
  applicationId: string,
  params: InitiateAaConsentParams = {},
): Promise<AaConsent> {
  const { purpose, ...queryParams } = params
  const { data } = await http.post<AaConsent>(
    `/aa/consent/application/${applicationId}/initiate`,
    purpose ?? null,
    { params: queryParams },
  )
  return data
}

/** POST /api/v1/aa/consent/{consentHandle}/approve — process consent approval. */
export async function approveAaConsent(consentHandle: string, consentId: string): Promise<AaConsent> {
  const { data } = await http.post<AaConsent>(`/aa/consent/${consentHandle}/approve`, null, {
    params: { consentId },
  })
  return data
}

/** POST /api/v1/aa/consent/{consentHandle}/fetch — fetch financial data after approval. */
export async function fetchAaData(consentHandle: string): Promise<AaConsent> {
  const { data } = await http.post<AaConsent>(`/aa/consent/${consentHandle}/fetch`)
  return data
}

/** POST /api/v1/aa/consent/{consentId}/revoke — revoke a consent. */
export async function revokeAaConsent(consentId: string, reason: string): Promise<AaConsent> {
  const { data } = await http.post<AaConsent>(`/aa/consent/${consentId}/revoke`, null, {
    params: { reason },
  })
  return data
}

/** GET /api/v1/aa/consent/{consentHandle} — get consent by handle. */
export async function getAaConsentByHandle(consentHandle: string): Promise<AaConsent> {
  const { data } = await http.get<AaConsent>(`/aa/consent/${consentHandle}`)
  return data
}

/** GET /api/v1/aa/consent/application/{applicationId}/active — check for active AA consent. */
export async function hasActiveAaConsent(
  applicationId: string,
): Promise<{ hasActiveConsent: boolean }> {
  const { data } = await http.get<{ hasActiveConsent: boolean }>(
    `/aa/consent/application/${applicationId}/active`,
  )
  return data
}
