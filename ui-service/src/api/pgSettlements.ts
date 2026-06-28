import { http } from './http'

export interface LosPaymentInProgressRow {
  id: string
  applicationId: string
  applicationNumber: string | null
  loanProduct: string | null
  borrowerUserId: string
  principalAmount: number
  pipStatus: string
  createdAt: string | null
}

export async function listOpenLosPip(): Promise<LosPaymentInProgressRow[]> {
  const { data } = await http.get<LosPaymentInProgressRow[]>('/admin/pg-settlements/pip')
  return data
}

export async function createLosSettlementBatch(payload: {
  settlementDate: string
  settlementUtr: string
  pipIds: string[]
  remarks?: string
}): Promise<unknown> {
  const { data } = await http.post('/admin/pg-settlements/batches', payload)
  return data
}
