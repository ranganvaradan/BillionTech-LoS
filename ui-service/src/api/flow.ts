import { http } from './http'
import type { ApplicationResponse } from '@/types/application'

/**
 * Flow lifecycle — `LoanApplicationFlowController` under `/api/v1/flow`.
 * POST /flow/{id}/kyc is the main entry: runs KYC_WORKFLOW with `kycPayload` in context
 * (same map forwarded to each KYC step / provider).
 */
export async function submitApplicationForKyc(applicationId: string): Promise<ApplicationResponse> {
  const { data } = await http.post<ApplicationResponse>(`/flow/${applicationId}/submit`)
  return data
}

export async function runKycFlow(
  applicationId: string,
  kycPayload: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(`/flow/${applicationId}/kyc`, kycPayload)
  return data
}

/** KYC_FAILED → KYC_IN_PROGRESS; prior step/KYC results stay on the server. Then call {@link runKycFlow}. */
export async function retryKycFlow(applicationId: string): Promise<ApplicationResponse> {
  const { data } = await http.post<ApplicationResponse>(`/flow/${applicationId}/kyc/retry`)
  return data
}

export async function pullBureauFlow(applicationId: string): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(`/flow/${applicationId}/bureau`, {})
  return data
}

export async function underwriteApplicationFlow(applicationId: string): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(`/flow/${applicationId}/underwrite`, {})
  return data
}

export async function approveManualUnderwritingFlow(applicationId: string): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(`/flow/${applicationId}/underwriting/approve`)
  return data
}

export async function rejectManualUnderwritingFlow(applicationId: string): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(`/flow/${applicationId}/underwriting/reject`)
  return data
}

export async function getAnchorDueDiligenceFlow(applicationId: string): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(`/flow/${applicationId}/anchor/due-diligence`)
  return data
}

export async function saveAnchorDueDiligenceFlow(
  applicationId: string,
  payload: { answers: Record<string, string>; comments?: Record<string, string> },
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(`/flow/${applicationId}/anchor/due-diligence`, payload)
  return data
}

export async function completeAnchorUnderwritingFlow(applicationId: string): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(`/flow/${applicationId}/anchor/underwrite`)
  return data
}

export async function approveAnchorManualUnderwritingFlow(applicationId: string): Promise<ApplicationResponse> {
  const { data } = await http.post<ApplicationResponse>(`/flow/${applicationId}/anchor/underwriting/approve`)
  return data
}

export async function rejectAnchorManualUnderwritingFlow(applicationId: string): Promise<ApplicationResponse> {
  const { data } = await http.post<ApplicationResponse>(`/flow/${applicationId}/anchor/underwriting/reject`)
  return data
}

export async function markCamReviewedFlow(applicationId: string): Promise<ApplicationResponse> {
  const { data } = await http.post<ApplicationResponse>(`/flow/${applicationId}/cam/reviewed`)
  return data
}

export async function proceedToSanctionPendingFlow(applicationId: string): Promise<ApplicationResponse> {
  const { data } = await http.post<ApplicationResponse>(`/flow/${applicationId}/sanction-pending`)
  return data
}

export async function rejectPostCreditFlow(
  applicationId: string,
  remarks?: string,
): Promise<ApplicationResponse> {
  const { data } = await http.post<ApplicationResponse>(`/flow/${applicationId}/sanction/reject`, {
    remarks: remarks ?? '',
  })
  return data
}

export async function sanctionApplicationFlow(
  applicationId: string,
  body: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `/flow/${applicationId}/sanction`,
    body,
  )
  return data
}

export async function initiateEsignFlow(
  applicationId: string,
  signerInfo: Record<string, unknown> = {},
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `/flow/${applicationId}/esign`,
    signerInfo,
  )
  return data
}

export async function completeEsignFlow(
  applicationId: string,
  transactionId?: string,
): Promise<ApplicationResponse> {
  const { data } = await http.post<ApplicationResponse>(
    `/flow/${applicationId}/esign-complete`,
    {},
    {
      params:
        transactionId != null && transactionId !== ''
          ? { transactionId }
          : undefined,
    },
  )
  return data
}

export async function markReadyForDisbursementFlow(applicationId: string): Promise<ApplicationResponse> {
  const { data } = await http.post<ApplicationResponse>(
    `/flow/${applicationId}/ready-for-disbursement`,
  )
  return data
}

export async function disburseApplicationFlow(
  applicationId: string,
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(
    `/flow/${applicationId}/disburse`,
  )
  return data
}

export interface ManualOverrideRequest {
  processCode: string
  failureCode: string
  overrideReason: string
  remarks?: string
  approvalReference?: string
  manualBureauScore?: number
  creditRiskScore?: number
}

export async function getManualOverrideEligibility(
  applicationId: string,
  processCode: string,
  failureCode: string,
): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(`/flow/${applicationId}/override/eligibility`, {
    params: { processCode, failureCode },
  })
  return data
}

export async function applyManualOverride(
  applicationId: string,
  payload: ManualOverrideRequest,
): Promise<Record<string, unknown>> {
  const { data } = await http.post<Record<string, unknown>>(`/flow/${applicationId}/override`, payload)
  return data
}
