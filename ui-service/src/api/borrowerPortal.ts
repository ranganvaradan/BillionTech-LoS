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
}

export interface BorrowerAppSummary {
  applicationId: string
  applicationNumber: string
  product: string
  friendlyStatus: string
  status: ApplicationStatus
  createdAt: string | null
  updatedAt: string | null
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

export async function getRepaymentScheduleDemo(loanId: string): Promise<RepaymentRow[]> {
  const { data } = await http.get<RepaymentRow[]>(`/borrower/loans/${loanId}/repayment-schedule`)
  return data
}

export async function getStatementDemo(loanId: string): Promise<StatementRow[]> {
  const { data } = await http.get<StatementRow[]>(`/borrower/loans/${loanId}/statement`)
  return data
}

export async function getTransactionsDemo(loanId: string): Promise<TransactionRow[]> {
  const { data } = await http.get<TransactionRow[]>(`/borrower/loans/${loanId}/transactions`)
  return data
}
