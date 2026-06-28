import { http } from './http'

export interface EsignRequestView {
  id: string
  documentType: string
  provider: string
  providerRequestId: string | null
  signingUrl: string | null
  status: string
  signerName: string | null
  signedDocumentUrl: string | null
  createdAt: string | null
  signedAt: string | null
}

export async function listEsignRequests(applicationId: string): Promise<EsignRequestView[]> {
  const { data } = await http.get<EsignRequestView[]>(
    `/applications/${applicationId}/esign-requests`,
  )
  return data
}

export async function resendEsignLink(applicationId: string): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `/applications/${applicationId}/esign-requests/resend-link`,
    {},
  )
  return data
}
