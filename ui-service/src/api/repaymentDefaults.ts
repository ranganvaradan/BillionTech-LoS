import { http } from './http'

export interface LoanProductRepaymentDefaultResponse {
  loanProduct: string
  repaymentMechanism: string
  pgProviderCode: string | null
  enabled: boolean
  updatedAt: string | null
  updatedBy: string | null
  managedByPlp?: boolean
}

export interface LoanProductRepaymentDefaultUpdateRequest {
  repaymentMechanism?: string
  pgProviderCode?: string | null
  enabled?: boolean
}

export async function listRepaymentDefaults(): Promise<LoanProductRepaymentDefaultResponse[]> {
  const { data } = await http.get<LoanProductRepaymentDefaultResponse[]>('/admin/repayment-defaults')
  return data
}

export async function updateRepaymentDefault(
  loanProduct: string,
  request: LoanProductRepaymentDefaultUpdateRequest,
): Promise<LoanProductRepaymentDefaultResponse> {
  const { data } = await http.put<LoanProductRepaymentDefaultResponse>(
    `/admin/repayment-defaults/${encodeURIComponent(loanProduct)}`,
    request,
  )
  return data
}
