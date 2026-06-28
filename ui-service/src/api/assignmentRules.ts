import { http } from './http'

export interface AssignmentRuleSetResponse {
  id: string
  name: string
  borrowerType: string
  loanProduct: string
  minAmount: number | null
  maxAmount: number | null
  geography: Record<string, unknown> | null
  minTenureMonths: number | null
  maxTenureMonths: number | null
  assignedRole: string
  assignedUserId: string | null
  priority: number
  active: boolean
  createdAt: string | null
  updatedAt: string | null
}

export interface AssignmentRuleSetRequest {
  name: string
  borrowerType: string
  loanProduct: string
  minAmount?: number | null
  maxAmount?: number | null
  geography?: Record<string, unknown> | null
  minTenureMonths?: number | null
  maxTenureMonths?: number | null
  assignedRole: string
  assignedUserId?: string | null
  priority: number
}

export async function listAssignmentRules(): Promise<AssignmentRuleSetResponse[]> {
  const { data } = await http.get<AssignmentRuleSetResponse[]>('/assignment/rules')
  return data
}

export async function createAssignmentRule(request: AssignmentRuleSetRequest) {
  const { data } = await http.post<AssignmentRuleSetResponse>('/assignment/rules', request)
  return data
}

export async function updateAssignmentRule(id: string, request: AssignmentRuleSetRequest) {
  const { data } = await http.put<AssignmentRuleSetResponse>(`/assignment/rules/${id}`, request)
  return data
}

export async function deleteAssignmentRule(id: string) {
  await http.delete(`/assignment/rules/${id}`)
}

export async function activateAssignmentRule(id: string) {
  await http.post(`/assignment/rules/${id}/activate`)
}

export async function deactivateAssignmentRule(id: string) {
  await http.post(`/assignment/rules/${id}/deactivate`)
}
