import { http } from './http'
import type { CreateApplicationRequest } from '@/types/createApplication'
import type { ApplicationPage, DashboardSummary } from '@/types/api'
import type { ApplicationResponse, ApplicationStatus } from '@/types/application'
import type { UpdateApplicationRequest } from '@/types/updateApplication'

export interface ListApplicationsParams {
  page?: number
  size?: number
  status?: ApplicationStatus
  borrowerType?: string
  sort?: string
}

export async function listApplications(
  params: ListApplicationsParams = {},
): Promise<ApplicationPage> {
  const { page = 0, size = 20, status, borrowerType, sort = 'createdAt,desc' } = params
  const { data } = await http.get<ApplicationPage>('/applications', {
    params: { page, size, sort, ...(status ? { status } : {}), ...(borrowerType ? { borrowerType } : {}) },
  })
  return data
}

export async function getApplication(applicationId: string): Promise<ApplicationResponse> {
  const { data } = await http.get<ApplicationResponse>(`/applications/${applicationId}`)
  return data
}

/** PUT /api/v1/applications/{id} — merge maps into personal/business/financial info (not terminal status). */
export async function updateApplication(
  applicationId: string,
  request: UpdateApplicationRequest,
): Promise<ApplicationResponse> {
  const { data } = await http.put<ApplicationResponse>(`/applications/${applicationId}`, request)
  return data
}

export async function getDashboardSummary(): Promise<DashboardSummary> {
  const { data } = await http.get<DashboardSummary>('/applications/dashboard/summary')
  return data
}

/**
 * POST /api/v1/applications — 201 with {@link ApplicationResponse} body.
 */
export async function createApplication(request: CreateApplicationRequest): Promise<ApplicationResponse> {
  const { data } = await http.post<ApplicationResponse>('/applications', request)
  return data
}

export interface ManualBureauPayload {
  manualBureauScore?: number
  manualBureauRemarks?: string
  manualBureauDocumentId?: string
}

export async function saveManualBureau(
  applicationId: string,
  payload: ManualBureauPayload,
): Promise<ApplicationResponse> {
  const { data } = await http.post<ApplicationResponse>(`/applications/${applicationId}/bureau/manual`, payload)
  return data
}

export interface ManualCreditInputsPayload {
  panName?: string
  panStatus?: string
  aadhaarName?: string
  aadhaarStatus?: string
  mobileVerified?: boolean
  manualBureauScore?: number
  manualBureauRemarks?: string
  monthlyIncome?: number
  monthlyObligation?: number
  state?: string
  city?: string
  creditRemarks?: string
  manualKycOutcome?: string
  gstIncome?: number
  bankStatementIncome?: number
  averageBankBalance?: number
  obligationRatio?: number
  emiObligation?: number
  propertyValue?: number
  ltv?: number
  businessVintageMonths?: number
  industryRisk?: string
  repaymentHistory?: string
  ebitdaProxy?: number
  leverageRatio?: number
  supportingDocumentIds?: string[]
  decisionSources?: {
    bureauScoreSource?: string
    incomeSource?: string
    kycSource?: string
  }
}

export async function saveManualCreditInputs(
  applicationId: string,
  payload: ManualCreditInputsPayload,
): Promise<ApplicationResponse> {
  const { data } = await http.post<ApplicationResponse>(`/applications/${applicationId}/manual-credit-inputs`, payload, {
    headers: { 'X-User-Role': 'CREDIT_MANAGER' },
  })
  return data
}
