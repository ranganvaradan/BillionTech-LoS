import { http } from './http'

/** Upload PKYC evidence via {@code POST /documents/{id}/upload?documentType=PHYSICAL_KYC_EVIDENCE}. */
export const PHYSICAL_KYC_DOCUMENT_TYPE = 'PHYSICAL_KYC_EVIDENCE'

export type VkycPkycReasonCode =
  | 'TECHNICAL_ISSUE'
  | 'CUSTOMER_REFUSED_VKYC'
  | 'CAMERA_NETWORK_FAILURE'
  | 'VKYC_VENDOR_FAILURE'
  | 'MANUAL_VERIFICATION_APPROVED'
  | 'OTHER'

export async function getVkycConfig(applicationId: string): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(`/vkyc/${applicationId}/config`)
  return data
}

export async function getVkycEligibility(applicationId: string): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(`/vkyc/${applicationId}/eligibility`)
  return data
}

export async function generateVkycUrl(applicationId: string): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(`/vkyc/${applicationId}/generate-url`)
  return data
}

export async function updateVkycStage(
  applicationId: string,
  status:
    | 'CUSTOMER_JOINED'
    | 'URL_GENERATED'
    | 'AGENT_APPROVED'
    | 'AUDITOR_APPROVED'
    | 'COMPLETED'
    | 'REJECTED'
    | 'EXPIRED'
    | 'FAILED',
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(`/vkyc/${applicationId}/stage`, null, { params: { status } })
  return data
}

export async function resendVkycLink(applicationId: string): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(`/vkyc/${applicationId}/resend-link`)
  return data
}

export async function getVkycTimeline(applicationId: string): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(`/vkyc/${applicationId}/timeline`)
  return data
}

export async function completePhysicalVkyc(
  applicationId: string,
  payload: { reason: VkycPkycReasonCode; comments: string; documentId: string },
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(`/vkyc/${applicationId}/complete-physical-kyc`, payload)
  return data
}
