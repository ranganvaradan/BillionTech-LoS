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
  intakeSegment?: string
  sort?: string
}

export async function listApplications(
  params: ListApplicationsParams = {},
): Promise<ApplicationPage> {
  const { page = 0, size = 20, status, borrowerType, intakeSegment, sort = 'createdAt,desc' } = params
  const { data } = await http.get<ApplicationPage>('/applications', {
    params: {
      page,
      size,
      sort,
      ...(status ? { status } : {}),
      ...(borrowerType ? { borrowerType } : {}),
      ...(intakeSegment ? { intakeSegment } : {}),
    },
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
  avgDailyBalance3m?: number
  avgMonthlyTransactions3m?: number
  avgMonthlySettlements3m?: number
  monthlyTransactions3m?: number
  inwardChequeReturns3m?: number
  avgDailySettlements3m?: number
  noOfTxns60days?: number
  txnMth1?: number
  txnMth2?: number
  txnMth3?: number
  avgGmv3m?: number
  active90days?: number
  annualGstTurnover?: number
  annualBankingTurnover?: number
  itrIncome?: number
  bankingTurnoverPctGst?: number
  abbObligationMultiple?: number
  ccUtilisationPct?: number
  chequeBounces12m?: number
  chequeBounces3m?: number
  liveUnsecuredLoanCount?: number
  bureauEnquiries3m?: number
  ntcFlag?: string
  residenceOwned?: string
  residenceStability?: number
  businessStability?: number
  existingLoanTrackRecordAll?: string
  existingLoanTrackRecord15d?: string
  qrTxnEDI?: string
  eligibleOnePointFiveX?: string
  pat?: number
  existingFbLimits?: number
  existingNfbLimits?: number
  debtToEquity?: number
  ebitda?: number
  debtService?: number
  tolTnw?: number
  officeOwned?: string
  dscr?: number
  interestCoverage?: number
  scorecardMetrics?: Record<string, string | number>
}

export async function saveManualCreditInputs(
  applicationId: string,
  payload: ManualCreditInputsPayload,
): Promise<ApplicationResponse> {
  const { data } = await http.post<ApplicationResponse>(`/applications/${applicationId}/manual-credit-inputs`, payload)
  return data
}

/** Scorecard OTHER/GST fields — available to operations and underwriting staff (not only credit managers). */
export async function saveScorecardInputs(
  applicationId: string,
  payload: ManualCreditInputsPayload,
): Promise<ApplicationResponse> {
  const { data } = await http.post<ApplicationResponse>(`/applications/${applicationId}/scorecard-inputs`, payload)
  return data
}

export interface AiLosOpenRequest {
  returnUrl: string
  mode?: 'REVIEW' | 'WHAT_IF'
}

export interface AiLosOpenResponse {
  loanId: string
  status: string
  message: string
  reviewUrl: string
  finalRedirectUrl: string
  mode: string
}

export async function openAiLosReview(
  applicationId: string,
  payload: AiLosOpenRequest,
): Promise<AiLosOpenResponse> {
  const { data } = await http.post<AiLosOpenResponse>(`/applications/${applicationId}/ai-los/open`, payload)
  return data
}

export interface ApplicationDeletionPreview {
  applicationId: string
  applicationNumber: string
  loanProduct: string
  intakeSegment: string
  status: string | null
  borrowerApplication: boolean
  invoiceDiscountingBorrower: boolean
  plpLinked: boolean
  requiresDoubleConfirm: boolean
  warningMessage: string | null
  summaryMessage: string | null
}

export interface ApplicationDeletionLogEntry {
  id: string
  applicationId: string
  applicationNumber: string
  loanProduct: string
  intakeSegment: string
  applicationStatus: string
  borrowerEmail?: string | null
  deletedByEmail?: string | null
  deletedByRole?: string | null
  reason?: string | null
  plpCleanupAttempted: boolean
  plpCleanupSummary?: string | null
  deletedAt: string
}

export async function getApplicationDeletionPreview(applicationId: string): Promise<ApplicationDeletionPreview> {
  const { data } = await http.get<ApplicationDeletionPreview>(`/applications/${applicationId}/deletion-preview`)
  return data
}

export async function deleteApplication(
  applicationId: string,
  body: { reason?: string; confirmActiveLoan?: boolean },
): Promise<void> {
  await http.delete(`/applications/${applicationId}`, { data: body })
}

export async function listApplicationDeletionLogs(page = 0, size = 20): Promise<{
  content: ApplicationDeletionLogEntry[]
  totalElements: number
  totalPages: number
}> {
  const { data } = await http.get<{
    content: ApplicationDeletionLogEntry[]
    totalElements: number
    totalPages: number
  }>('/admin/application-deletions', { params: { page, size } })
  return data
}

export type AdminGstAnalysisStatus = {
  applicationId?: string
  onWorkflow?: boolean
  required?: boolean
  gstin?: string | null
  consent?: boolean
  documentCount?: number
  documents?: Array<{
    id?: string
    fileName?: string
    fileSize?: number
    contentType?: string
    documentType?: string
    kycStepType?: string | null
    createdAt?: string | null
  }>
  status?: string
  phase?: string | null
  success?: boolean
  uploadSuccess?: boolean
  reportSuccess?: boolean
  errorMessage?: string | null
  requestId?: string | null
  fullResponse?: unknown
  uploadResult?: unknown
  mappedMetrics?: Record<string, unknown>
  attemptNumber?: number
  completedAt?: string | null
}

const GST_ANALYSIS_TIMEOUT_MS = 300_000

export async function getAdminGstAnalysisStatus(applicationId: string): Promise<AdminGstAnalysisStatus> {
  const { data } = await http.get<AdminGstAnalysisStatus>(`/applications/${applicationId}/gst-analysis/status`)
  return data
}

export async function retryAdminGstAnalysisUpload(applicationId: string): Promise<{
  outcome: string
  errorMessage?: string | null
  parsedData?: Record<string, unknown> | null
  transactionId?: string | null
}> {
  const { data } = await http.post(`/applications/${applicationId}/gst-analysis/retry-upload`, null, {
    timeout: GST_ANALYSIS_TIMEOUT_MS,
  })
  return data as {
    outcome: string
    errorMessage?: string | null
    parsedData?: Record<string, unknown> | null
    transactionId?: string | null
  }
}

export async function generateAdminGstAnalysisReport(applicationId: string): Promise<{
  outcome: string
  errorMessage?: string | null
  parsedData?: Record<string, unknown> | null
  transactionId?: string | null
}> {
  const { data } = await http.post(`/applications/${applicationId}/gst-analysis/generate-report`, null, {
    timeout: GST_ANALYSIS_TIMEOUT_MS,
  })
  return data as {
    outcome: string
    errorMessage?: string | null
    parsedData?: Record<string, unknown> | null
    transactionId?: string | null
  }
}

