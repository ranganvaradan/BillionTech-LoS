import { http } from './http'
import type { KycStepResultResponse } from '@/types/kyc'

/**
 * KYC read APIs — `KycController` at `/api/v1/kyc`.
 * Write path for running KYC: prefer {@link import('./flow') runKycFlow} so step execution and flow state stay aligned.
 */
export async function getKycResults(applicationId: string): Promise<KycStepResultResponse[]> {
  const { data } = await http.get<KycStepResultResponse[]>(`/kyc/${applicationId}/results`)
  return data
}

export async function getKycOutcome(applicationId: string): Promise<Record<string, unknown>> {
  const { data } = await http.get<Record<string, unknown>>(`/kyc/${applicationId}/outcome`)
  return data
}
