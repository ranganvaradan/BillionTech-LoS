import { http } from './http'
import type { CamResponse, CamUpdateRequest } from '@/types/cam'

export async function getCam(applicationId: string): Promise<CamResponse> {
  const { data } = await http.get<CamResponse>(`/applications/${applicationId}/cam`)
  return data
}

export async function updateCam(
  applicationId: string,
  body: CamUpdateRequest,
): Promise<CamResponse> {
  const { data } = await http.put<CamResponse>(`/applications/${applicationId}/cam`, body)
  return data
}

/** Opens save-as / preview via blob (caller creates object URL). */
export async function fetchCamPdfBlob(applicationId: string): Promise<Blob> {
  const { data } = await http.get<Blob>(`/applications/${applicationId}/cam/pdf`, {
    responseType: 'blob',
  })
  return data
}

export async function submitCam(applicationId: string): Promise<CamResponse> {
  const { data } = await http.post<CamResponse>(`/applications/${applicationId}/cam/submit`, {})
  return data
}

export async function sendBackCam(applicationId: string): Promise<CamResponse> {
  const { data } = await http.post<CamResponse>(`/applications/${applicationId}/cam/send-back`, {})
  return data
}

export async function rejectCamMemorandum(applicationId: string): Promise<CamResponse> {
  const { data } = await http.post<CamResponse>(`/applications/${applicationId}/cam/reject`, {})
  return data
}
