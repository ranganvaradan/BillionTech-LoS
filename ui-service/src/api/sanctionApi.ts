import { http } from './http'

export interface SanctionResponse {
  id: string
  applicationId: string
  approvedAmount: number
  approvedTenure: number
  interestRate: number
  processingFee: number | null
  conditionsText: string | null
  remarks: string | null
  approvedBy: string | null
  sanctionPdfPath: string | null
  createdAt: string | null
}

export async function getLatestSanction(applicationId: string): Promise<SanctionResponse> {
  const { data } = await http.get<SanctionResponse>(`/applications/${applicationId}/sanction`)
  return data
}

export async function downloadSanctionPdfBlob(applicationId: string): Promise<Blob> {
  const { data } = await http.get<Blob>(`/applications/${applicationId}/sanction/pdf`, {
    responseType: 'blob',
  })
  return data
}
