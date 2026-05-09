import { http } from '@/api/http'

export type IntegrationProviderMatrixRow = {
  id: string
  integrationType: string
  kycStepType: string | null
  esignStepType: string | null
  providerName: string
  priority: number
  allowFallback: boolean
  active: boolean
  metadata: { purpose?: string; appliesTo?: string } | null
}

export async function getProviderMatrix(): Promise<IntegrationProviderMatrixRow[]> {
  const { data } = await http.get<IntegrationProviderMatrixRow[]>('/integrations/provider-matrix')
  return data
}
