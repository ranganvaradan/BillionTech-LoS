import { http } from './http'
import type { ApplicationPartyResponse } from '@/types/application'

export type ProgramApprovalStatus =
  | 'DRAFT'
  | 'PENDING_L2'
  | 'SENT_BACK'
  | 'APPROVED'
  | 'REJECTED'

export interface ProgramApprovalResponse {
  programId: string
  programName: string
  programCode: string
  approvalStatus: ProgramApprovalStatus
  assignedL1UserId: string | null
  assignedL1UserName: string | null
  assignedL2UserId: string | null
  assignedL2UserName: string | null
  approvalNotes: string | null
  anchorApplicationId: string | null
  approvedAt: string | null
  approvedByUserId: string | null
  plpOperationalStatus: string | null
}

export async function listPendingProgramApprovals(): Promise<ProgramApprovalResponse[]> {
  const { data } = await http.get<ProgramApprovalResponse[]>('/plp/programs/approvals/pending')
  return data
}

export async function getProgramApproval(programId: string): Promise<ProgramApprovalResponse> {
  const { data } = await http.get<ProgramApprovalResponse>(`/plp/programs/${programId}/approval`)
  return data
}

export async function refreshPlpProgramStatus(programId: string): Promise<ProgramApprovalResponse> {
  const { data } = await http.post<ProgramApprovalResponse>(
    `/plp/programs/${programId}/refresh-plp-status`,
  )
  return data
}

export async function submitProgramToL2(programId: string): Promise<ProgramApprovalResponse> {
  const { data } = await http.post<ProgramApprovalResponse>(
    `/plp/programs/${programId}/approval/submit-to-l2`,
  )
  return data
}

export async function sendBackProgram(programId: string, notes: string): Promise<ProgramApprovalResponse> {
  const { data } = await http.post<ProgramApprovalResponse>(
    `/plp/programs/${programId}/approval/send-back`,
    { notes },
  )
  return data
}

export async function approveProgram(programId: string): Promise<ProgramApprovalResponse> {
  const { data } = await http.post<ProgramApprovalResponse>(
    `/plp/programs/${programId}/approval/approve`,
  )
  return data
}

export async function notifyBorrowerToComplete(
  applicationId: string,
  completedStep = 1,
): Promise<void> {
  await http.post(`/applications/${applicationId}/notify-borrower`, null, {
    params: { completedStep },
  })
}

export async function acceptBorrowerSubmission(applicationId: string) {
  const { data } = await http.post(`/applications/${applicationId}/review/accept`)
  return data
}

export async function sendBackBorrowerSubmission(applicationId: string, notes?: string) {
  const { data } = await http.post(`/applications/${applicationId}/review/send-back`, {
    notes: notes?.trim() ? notes.trim() : undefined,
  })
  return data
}

export async function handOffToCreditOfficer(applicationId: string, notes?: string) {
  const { data } = await http.post(`/applications/${applicationId}/review/hand-off-to-co`, {
    notes: notes?.trim() ? notes.trim() : undefined,
  })
  return data
}

export async function sendBackToRelationshipManager(applicationId: string, notes?: string) {
  const { data } = await http.post(`/applications/${applicationId}/review/send-back-to-rm`, {
    notes: notes?.trim() ? notes.trim() : undefined,
  })
  return data
}

export async function submitDelegatedBorrowerIntake(applicationId: string): Promise<void> {
  await http.post(`/borrower/applications/${applicationId}/submit-delegated`)
}

/** GET /api/v1/applications/{id}/parties — PRIMARY + co-applicants. */
export async function listApplicationParties(applicationId: string): Promise<ApplicationPartyResponse[]> {
  const { data } = await http.get<ApplicationPartyResponse[]>(`/applications/${applicationId}/parties`)
  return data
}

export interface UpsertCoApplicantDraft {
  id?: string
  personalInfo: Record<string, unknown>
  requiredForDisbursement?: boolean
}

/** PUT /api/v1/applications/{id}/parties — full replacement of the co-applicant list (PRIMARY is always retained). */
export async function upsertApplicationParties(
  applicationId: string,
  payload: { coApplicants: UpsertCoApplicantDraft[] },
): Promise<ApplicationPartyResponse[]> {
  const { data } = await http.put<ApplicationPartyResponse[]>(`/applications/${applicationId}/parties`, payload)
  return data
}

/** POST /api/v1/applications/{id}/parties/{partyId}/submit — co-applicant submits their intake portion. */
export async function submitApplicationParty(
  applicationId: string,
  partyId: string,
): Promise<ApplicationPartyResponse> {
  const { data } = await http.post<ApplicationPartyResponse>(
    `/applications/${applicationId}/parties/${partyId}/submit`,
  )
  return data
}

/** PUT /api/v1/applications/{id}/parties/{partyId}/personal-info — co-applicant portal self-update. */
export async function updateApplicationPartyPersonalInfo(
  applicationId: string,
  partyId: string,
  personalInfo: Record<string, unknown>,
): Promise<ApplicationPartyResponse> {
  const { data } = await http.put<ApplicationPartyResponse>(
    `/applications/${applicationId}/parties/${partyId}/personal-info`,
    personalInfo,
  )
  return data
}
