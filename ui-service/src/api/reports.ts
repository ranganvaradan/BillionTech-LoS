import { http } from '@/api/http'

export type DashboardAnalytics = {
  totalApplications: number
  activePipeline: number
  approvedCount: number
  rejectedCount: number
  disbursedCount: number
  todayApplications: number
  totalDisbursedAmount?: number
  totalRequestedAmount?: number
  averageLoanSize?: number
  approvalRate?: number
  rejectionRate?: number
  conversionRate?: number
  statusDistribution?: { status: string; count: number; percentage: number }[]
  productDistribution?: { product: string; count: number; totalAmount?: number }[]
  borrowerTypeDistribution?: { borrowerType: string; count: number; percentage: number }[]
  monthlyTrends?: {
    month: string
    applications: number
    approved: number
    disbursed: number
    disbursedAmount?: number
  }[]
}

export type OperationsReport = {
  period?: string
  statusCounts: { status: string; count: number }[]
  productCounts: { product: string; count: number }[]
  intakeSegmentCounts: { segment: string; count: number }[]
  handoffQueue: { status: string; count: number }[]
  kycAndUnderwriting: { status: string; count: number }[]
  sanctionAndEsign: { status: string; count: number }[]
}

export type MisReport = {
  reportType: string
  period: string
  generatedAt: string
  summary: {
    totalApplications: number
    approved: number
    rejected: number
    disbursed: number
    pending: number
    totalDisbursedAmount: number
    totalRequestedAmount: number
    approvalRate: number
    avgProcessingDays: number
  }
  rows: {
    applicationNumber: string
    borrowerName: string
    borrowerType: string
    loanProduct: string
    requestedAmount: number
    approvedAmount: number
    status: string
    kycStatus: string
    createdAt: string
    submittedAt: string
    processingDays: number
  }[]
}

export type RegulatoryReport = {
  reportType: string
  period: string
  generatedAt: string
  digitalLending: {
    totalDigitalLoans: number
    kfsIssued: number
    kfsSigned: number
    coolingOffComplied: number
    esignCompleted: number
    digitalComplianceRate: number
    rbiCircularRef: string
  }
  kyc: {
    totalKycInitiated: number
    kycCompleted: number
    kycFailed: number
    aadhaarVerified: number
    panVerified: number
    cKycVerified: number
    faceMatchCompleted: number
    kycCompletionRate: number
  }
  dpdAnalysis: {
    totalActiveLoans: number
    dpd0: number
    dpd1to30: number
    dpd31to60: number
    dpd61to90: number
    dpd90plus: number
    npaCount: number
    totalNpaAmount: number
    npaPercentage: number
  }
}

export async function fetchDashboardAnalytics() {
  const { data } = await http.get<DashboardAnalytics>('/reports/dashboard')
  return data
}

export async function fetchMisReport(period?: string) {
  const { data } = await http.get<MisReport>('/reports/mis', {
    params: period ? { period } : undefined,
  })
  return data
}

export async function fetchRegulatoryReport(period?: string) {
  const { data } = await http.get<RegulatoryReport>('/reports/regulatory', {
    params: period ? { period } : undefined,
  })
  return data
}

export async function fetchOperationsReport(period?: string) {
  const { data } = await http.get<OperationsReport>('/reports/operations', {
    params: period ? { period } : undefined,
  })
  return data
}

export async function downloadReportExport(
  reportType: string,
  format: 'csv' | 'pdf' | 'xlsx',
  startDate?: string,
  endDate?: string,
) {
  const { data } = await http.get<Blob>(`/reports/export/download/${reportType}.${format}`, {
    params: { startDate, endDate },
    responseType: 'blob',
  })
  const url = URL.createObjectURL(data)
  const a = document.createElement('a')
  a.href = url
  a.download = `${reportType.toLowerCase()}-report.${format}`
  a.click()
  URL.revokeObjectURL(url)
}
