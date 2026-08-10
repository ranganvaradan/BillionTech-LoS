import { http } from './http'
import type { ApplicationResponse } from '@/types/application'
import type {
  AnchorMasterView,
  CreatePlpProgramRequest,
  PlpLinkedSubProgramSummary,
  PlpProgramDetail,
  PlpProgramSetupResponse,
  PlpProgramSummary,
  PlpSyncResult,
  VintageEligibility,
} from '@/types/plp'

export async function listPlpPrograms(): Promise<PlpProgramSummary[]> {
  const { data } = await http.get<PlpProgramSummary[]>('/plp/programs')
  return data
}

export async function listSelectablePlpPrograms(): Promise<PlpProgramSummary[]> {
  const { data } = await http.get<PlpProgramSummary[]>('/plp/programs/selectable')
  return data
}

export async function getPlpProgram(programId: string): Promise<PlpProgramDetail> {
  const { data } = await http.get<PlpProgramDetail>(`/plp/programs/${programId}`)
  return data
}

export async function getLinkedSubProgramSummary(
  subProgramId: string,
): Promise<PlpLinkedSubProgramSummary> {
  const { data } = await http.get<PlpLinkedSubProgramSummary>(
    `/plp/sub-programs/${subProgramId}/summary`,
  )
  return data
}

export async function getVintageEligibility(applicationId: string): Promise<VintageEligibility | null> {
  const res = await http.get<VintageEligibility>(`/plp/applications/${applicationId}/vintage-eligibility`, {
    validateStatus: (s) => s === 200 || s === 204,
  })
  if (res.status === 204) return null
  return res.data
}

export async function listPlpProgramsForAnchor(
  anchorId: string,
  syncedOnly = false,
): Promise<PlpProgramSummary[]> {
  const { data } = await http.get<PlpProgramSummary[]>(`/plp/programs/anchor/${anchorId}`, {
    params: { syncedOnly },
  })
  return data
}

export async function createPlpProgram(
  request: CreatePlpProgramRequest,
): Promise<PlpProgramSetupResponse> {
  const { data } = await http.post<PlpProgramSetupResponse>('/plp/programs', request)
  return data
}

export async function linkApplicationToProgram(
  applicationId: string,
  subProgramId: string,
): Promise<ApplicationResponse> {
  const { data } = await http.post<ApplicationResponse>(
    `/plp/applications/${applicationId}/link-program`,
    { subProgramId },
  )
  return data
}

export async function listAnchors(): Promise<AnchorMasterView[]> {
  const { data } = await http.get<AnchorMasterView[]>('/anchors')
  return data
}

export async function listSyncedAnchors(): Promise<AnchorMasterView[]> {
  const { data } = await http.get<AnchorMasterView[]>('/plp/anchors/synced')
  return data
}

export async function retryPlpAnchor(anchorId: string): Promise<PlpSyncResult> {
  const { data } = await http.post<PlpSyncResult>(`/plp/retry/anchor/${anchorId}`)
  return data
}

export async function retryPlpProgram(programId: string): Promise<PlpSyncResult> {
  const { data } = await http.post<PlpSyncResult>(`/plp/retry/program/${programId}`)
  return data
}

export async function retryPlpSubProgram(subProgramId: string): Promise<PlpSyncResult> {
  const { data } = await http.post<PlpSyncResult>(`/plp/retry/sub-program/${subProgramId}`)
  return data
}

export async function retryPlpBorrower(applicationId: string): Promise<PlpSyncResult> {
  const { data } = await http.post<PlpSyncResult>(`/plp/retry/borrower/${applicationId}`)
  return data
}

export async function retryPlpApplication(applicationId: string): Promise<PlpSyncResult> {
  const { data } = await http.post<PlpSyncResult>(`/plp/retry/application/${applicationId}`)
  return data
}
