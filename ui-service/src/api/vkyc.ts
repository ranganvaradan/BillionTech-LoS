import { http } from './http'

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
