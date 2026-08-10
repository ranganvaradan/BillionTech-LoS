import { http } from './http'

export interface KfsDocumentView {
  id: string
  applicationId: string
  version: string
  sanctionedAmount: number
  interestRate: number
  apr: number
  tenureMonths: number
  emiAmount: number
  totalInterest: number
  totalRepayment: number
  processingFee: number | null
  stampDuty: number | null
  insurancePremium: number | null
  otherCharges: number | null
  totalCostOfCredit: number | null
  coolingOffHours: number
  status: string
  createdAt: string | null
  updatedAt: string | null
  additionalTerms?: Record<string, unknown> | null
}

export async function getLatestKfs(applicationId: string): Promise<KfsDocumentView> {
  const { data } = await http.get<KfsDocumentView>(`/kfs/application/${applicationId}/latest`)
  return data
}

export async function downloadKfsPdfBlob(applicationId: string): Promise<Blob> {
  const { data } = await http.get<Blob>(`/kfs/application/${applicationId}/pdf`, {
    responseType: 'blob',
  })
  return data
}
