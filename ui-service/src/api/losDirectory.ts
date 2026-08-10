import { http } from './http'

export const ASSIGNMENT_ROLE_CODES = [
  'SALES_OFFICER',
  'RELATIONSHIP_MANAGER',
  'CREDIT_OFFICER',
  'CREDIT_MANAGER',
  'OPERATIONS',
  'ADMINISTRATOR',
  'ACCOUNTS',
] as const

export type AssignmentRoleCode = (typeof ASSIGNMENT_ROLE_CODES)[number]

export const ASSIGNMENT_ROLE_LABELS: Record<AssignmentRoleCode, string> = {
  SALES_OFFICER: 'Sales Officer',
  RELATIONSHIP_MANAGER: 'Relationship Manager',
  CREDIT_OFFICER: 'Credit Officer',
  CREDIT_MANAGER: 'Credit Manager',
  OPERATIONS: 'Operations',
  ADMINISTRATOR: 'Administrator',
  ACCOUNTS: 'Accounts',
}

export interface LosUserResponse {
  id: string
  name: string
  email: string
  mobile: string | null
  active: boolean
  createdAt: string | null
  updatedAt: string | null
}

export interface LosUserRequest {
  name: string
  email: string
  mobile: string
  active: boolean
}

export interface UserRoleMappingResponse {
  id: string
  userId: string
  userName: string | null
  role: string
  loanProduct: string | null
  borrowerType: string | null
  minAmount: number | null
  maxAmount: number | null
  geography: Record<string, unknown> | null
  priority: number
  active: boolean
  createdAt: string | null
  updatedAt: string | null
}

export interface UserRoleMappingRequest {
  userId: string
  role: AssignmentRoleCode
  loanProduct?: string | null
  borrowerType?: string | null
  minAmount?: number | null
  maxAmount?: number | null
  geography?: Record<string, unknown> | null
  priority: number
  active: boolean
}

export async function listLosUsers(role?: string): Promise<LosUserResponse[]> {
  const { data } = await http.get<LosUserResponse[]>('/los/users', {
    params: role ? { role } : undefined,
  })
  return data
}

export async function createLosUser(request: LosUserRequest): Promise<LosUserResponse> {
  const { data } = await http.post<LosUserResponse>('/los/users', request)
  return data
}

export async function updateLosUser(id: string, request: LosUserRequest): Promise<LosUserResponse> {
  const { data } = await http.put<LosUserResponse>(`/los/users/${id}`, request)
  return data
}

export async function deactivateLosUser(id: string): Promise<void> {
  await http.post(`/los/users/${id}/deactivate`)
}

export async function listUserRoleMappings(): Promise<UserRoleMappingResponse[]> {
  const { data } = await http.get<UserRoleMappingResponse[]>('/los/user-role-mappings')
  return data
}

export async function createUserRoleMapping(
  request: UserRoleMappingRequest,
): Promise<UserRoleMappingResponse> {
  const { data } = await http.post<UserRoleMappingResponse>('/los/user-role-mappings', request)
  return data
}

export async function updateUserRoleMapping(
  id: string,
  request: UserRoleMappingRequest,
): Promise<UserRoleMappingResponse> {
  const { data } = await http.put<UserRoleMappingResponse>(`/los/user-role-mappings/${id}`, request)
  return data
}

export async function deleteUserRoleMapping(id: string): Promise<void> {
  await http.delete(`/los/user-role-mappings/${id}`)
}
