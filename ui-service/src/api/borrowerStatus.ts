import { http } from './http'
import type { ApplicationStatus } from '@/types/application'

export interface BorrowerApplicationStatus {
  applicationId: string
  applicationNumber: string
  customerName: string
  product: string
  status: ApplicationStatus
  requiredActions: string[]
  kycStatus: string
  documentStatus: string
  sanctionStatus: string
  kfsStatus: string
  eSignStatus: string
  disbursementStatus: string
}

export async function getBorrowerApplicationStatus(applicationId: string): Promise<BorrowerApplicationStatus> {
  const { data } = await http.get<BorrowerApplicationStatus>(`/borrower/applications/${applicationId}/status`)
  return data
}
