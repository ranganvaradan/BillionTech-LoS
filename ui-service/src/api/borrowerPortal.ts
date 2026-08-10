import { http } from './http'
import type { ApplicationStatus } from '@/types/application'

/** DELETE unsubmitted (DRAFT or CONSENT_PENDING) application owned by the borrower. */
export async function deleteBorrowerDraftApplication(applicationId: string): Promise<void> {
  await http.delete(`/borrower/applications/${applicationId}/draft`)
}

export interface BorrowerDashboard {
  fullName: string
  email: string
  mobile: string | null
  activeLoanCount: number
  draftOrOpenApplicationCount: number
  hasIncompleteDraftHint: boolean
  recentApplications: BorrowerAppSummary[]
  secondLoanWarning: string | null
  primaryDisbursedApplicationId: string | null
  invoiceDiscountingLinked: boolean
  purchaseBillDiscountingLinked: boolean
  salesBillDiscountingLinked: boolean
  purchaseOrderDiscountingLinked: boolean
}

export interface BorrowerAppSummary {
  applicationId: string
  applicationNumber: string
  product: string
  friendlyStatus: string
  status: ApplicationStatus
  createdAt: string | null
  updatedAt: string | null
  partyId?: string | null
  partyRole?: string | null
}

export interface BorrowerTimelineStep {
  id: string
  label: string
  state: 'completed' | 'in_progress' | 'pending' | 'locked'
  description: string
  completedAt: string | null
}

export interface BorrowerApplicationDetail {
  applicationId: string
  applicationNumber: string
  customerName: string
  product: string
  status: ApplicationStatus
  friendlyStatusHeadline: string
  currentStageMessage: string
  estimatedProcessingHint: string
  requiredActions: string[]
  kycStatus: string
  documentStatus: string
  sanctionStatus: string
  kfsStatus: string
  eSignStatus: string
  disbursementStatus: string
  rejectionMessage: string | null
  reapplyVisible: boolean
  disbursedAmount: number | null
  disbursedAt: string | null
  disbursementAccountMask: string
  loanAccountNumber: string
  timeline: BorrowerTimelineStep[]
  /** Borrower-submitted collateral summary (intake) — no internal credit remarks. */
  collateralSummary?: { label: string; value: string }[]
  /** Invoice discounting borrower onboarding — overview only, no KFS/loan tabs. */
  invoiceDiscountingBorrower?: boolean
  sanctionedAmount?: number | null
  interestRate?: number | null
  tenureMonths?: number | null
  termsDocumentAvailable?: boolean
}

export async function getBorrowerDashboard(): Promise<BorrowerDashboard> {
  const { data } = await http.get<BorrowerDashboard>('/borrower/dashboard')
  return data
}

export async function listBorrowerApplications(page = 0, size = 20) {
  const { data } = await http.get<{
    content: BorrowerAppSummary[]
    totalElements: number
  }>('/borrower/applications', { params: { page, size, sort: 'updatedAt,desc' } })
  return { content: data.content ?? [], totalElements: data.totalElements ?? 0 }
}

export async function getBorrowerApplicationDetail(id: string): Promise<BorrowerApplicationDetail> {
  const { data } = await http.get<BorrowerApplicationDetail>(`/borrower/applications/${id}`)
  return data
}

export interface BorrowerDocumentItem {
  id: string
  source: 'UPLOAD' | 'ESIGN'
  category: 'KYC' | 'SIGNED'
  documentType: string
  fileName: string
  contentType: string
  fileSize: number
  createdAt: string | null
}

export async function listBorrowerDocuments(applicationId: string): Promise<BorrowerDocumentItem[]> {
  const { data } = await http.get<BorrowerDocumentItem[]>(`/borrower/applications/${applicationId}/documents`)
  return data ?? []
}

/** Inline preview for borrower-visible documents (upload or eSign signed PDF). */
export async function fetchBorrowerDocumentPreviewBlob(
  applicationId: string,
  doc: BorrowerDocumentItem,
): Promise<Blob> {
  const path =
    doc.source === 'ESIGN'
      ? `/borrower/applications/${applicationId}/documents/esign/${doc.id}/content`
      : `/borrower/applications/${applicationId}/documents/${doc.id}/content`
  const { data } = await http.get<Blob>(path, { responseType: 'blob' })
  return data
}

export async function downloadInvoiceDiscountingTermsPdf(applicationId: string): Promise<Blob> {
  const { data } = await http.get<Blob>(`/borrower/applications/${applicationId}/terms/pdf`, {
    responseType: 'blob',
  })
  return data
}

export interface BorrowerNotification {
  id: string
  title: string
  message: string
  kind: string
  createdAt: string
  applicationId: string
}

export async function getBorrowerNotifications(): Promise<BorrowerNotification[]> {
  const { data } = await http.get<BorrowerNotification[]>('/borrower/notifications')
  return data
}

export interface BorrowerKfsSummary {
  kfsId: string
  version: string
  sanctionedAmount: number
  interestRate: number
  apr: number
  tenureMonths: number
  emiAmount: number
  totalRepayment: number
  status: string
  signPending: boolean
  coolingOffComplete: boolean
}

export async function getBorrowerKfsSummary(applicationId: string): Promise<BorrowerKfsSummary> {
  const { data } = await http.get<BorrowerKfsSummary>(`/borrower/applications/${applicationId}/kfs`)
  return data
}

export interface RepaymentRow {
  installmentNo: number
  dueDate: string
  emi: number
  principal: number
  interest: number
  outstandingPrincipal: number
}

export interface StatementRow {
  valueDate: string
  description: string
  credit: number | null
  debit: number | null
  balance: number | null
}

export interface TransactionRow {
  postedAt: string
  description: string
  reference: string
  amount: number
  type: string
}

export interface BorrowerLoanAccount {
  loanAccountNumber: string
  loanStatus: string
  sanctionedAmount: number | null
  disbursedAmount: number | null
  outstandingPrincipal: number | null
  totalPaid: number | null
  overdueAmount: number | null
  totalEmis: number
  paidEmis: number
  overdueEmis: number
  nextEmiDate: string | null
  nextEmiAmount: number | null
  lastPaymentDate: string | null
  dpd: number
  servicingActive: boolean
  loanProduct?: string | null
  repaymentMechanism?: string | null
  payuCheckoutAvailable?: boolean
}

/** Loan account summary after disbursement (LMS-backed with local fallback). */
export async function getLoanAccount(loanId: string): Promise<BorrowerLoanAccount> {
  const { data } = await http.get<BorrowerLoanAccount>(`/borrower/loans/${loanId}/account`)
  return data
}

/** Make a repayment against a disbursed loan; returns the refreshed account. */
export async function postRepayment(loanId: string, amount: number): Promise<BorrowerLoanAccount> {
  const { data } = await http.post<BorrowerLoanAccount>(`/borrower/loans/${loanId}/repay`, { amount })
  return data
}

export interface LoanPayuInitiatePayload {
  baseUrl: string
  key: string
  txnid: string
  amount: string
  productinfo: string
  firstname: string
  email: string
  phone?: string | null
  udf1?: string
  udf2?: string
  surl: string
  furl: string
  hash: string
  transactionId: string
  applicationId?: string
}

export async function initiateLoanPayuPayment(
  loanId: string,
  amount: number,
): Promise<LoanPayuInitiatePayload> {
  const { data } = await http.post<LoanPayuInitiatePayload>(
    `/borrower/loans/${loanId}/payments/payu/initiate`,
    { amount },
  )
  return data
}

/** Provenance of post-disbursement servicing data. */
export type ServicingSource = 'LMS' | 'LOCAL'

export interface ServicingData<T> {
  source: ServicingSource
  rows: T[]
}

export async function getRepaymentSchedule(loanId: string): Promise<ServicingData<RepaymentRow>> {
  const { data } = await http.get<ServicingData<RepaymentRow>>(
    `/borrower/loans/${loanId}/repayment-schedule`,
  )
  return data
}

/** Submit ITD username/password for Karza ITR pull (password never stored server-side). */
export async function submitBorrowerItrReturnForms(
  applicationId: string,
  body: { username: string; password: string; consent: boolean },
): Promise<{
  outcome: string
  errorMessage?: string | null
  parsedData?: Record<string, unknown> | null
  transactionId?: string | null
}> {
  // Align with backend Karza ITR + GST analysis HTTP wait (5 minutes).
  const { data } = await http.post(`/borrower/applications/${applicationId}/itr-return-forms`, body, {
    timeout: 300_000,
  })
  return data as {
    outcome: string
    errorMessage?: string | null
    parsedData?: Record<string, unknown> | null
    transactionId?: string | null
  }
}

export async function getBorrowerItrReturnFormsStatus(applicationId: string): Promise<{
  required: boolean
  success: boolean
  status: string
  errorMessage?: string | null
  mappedMetrics?: Record<string, unknown>
}> {
  const { data } = await http.get<{
    required: boolean
    success: boolean
    status: string
    errorMessage?: string | null
    mappedMetrics?: Record<string, unknown>
  }>(`/borrower/applications/${applicationId}/itr-return-forms/status`)
  return data
}

export type GstAnalysisStatus = {
  applicationId?: string
  onWorkflow?: boolean
  required?: boolean
  gstin?: string | null
  consent?: boolean
  documentCount?: number
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
}

export async function prepareBorrowerGstAnalysis(
  applicationId: string,
  body: { gstin: string; consent: boolean },
): Promise<GstAnalysisStatus> {
  const { data } = await http.post<GstAnalysisStatus>(
    `/borrower/applications/${applicationId}/gst-analysis/prepare`,
    body,
  )
  return data
}

export async function getBorrowerGstAnalysisStatus(applicationId: string): Promise<GstAnalysisStatus> {
  const { data } = await http.get<GstAnalysisStatus>(
    `/borrower/applications/${applicationId}/gst-analysis/status`,
  )
  return data
}

export async function getStatement(loanId: string): Promise<ServicingData<StatementRow>> {
  const { data } = await http.get<ServicingData<StatementRow>>(`/borrower/loans/${loanId}/statement`)
  return data
}

export async function getTransactions(loanId: string): Promise<ServicingData<TransactionRow>> {
  const { data } = await http.get<ServicingData<TransactionRow>>(
    `/borrower/loans/${loanId}/transactions`,
  )
  return data
}
