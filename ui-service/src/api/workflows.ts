import { http } from './http'
import type { WorkflowConfigRequest, WorkflowConfigResponse } from '@/types/workflow'

export async function listWorkflows(): Promise<WorkflowConfigResponse[]> {
  const { data } = await http.get<WorkflowConfigResponse[]>('/workflows')
  return data
}

export async function getActiveWorkflow(
  borrowerType: string,
  loanProduct: string,
): Promise<WorkflowConfigResponse> {
  const { data } = await http.get<WorkflowConfigResponse>('/workflows/active', {
    params: { borrowerType, loanProduct },
  })
  return data
}

export async function createWorkflow(request: WorkflowConfigRequest): Promise<WorkflowConfigResponse> {
  const { data } = await http.post<WorkflowConfigResponse>('/workflows', request)
  return data
}

export async function updateWorkflow(
  workflowId: string,
  request: WorkflowConfigRequest,
): Promise<WorkflowConfigResponse> {
  const { data } = await http.put<WorkflowConfigResponse>(`/workflows/${workflowId}`, request)
  return data
}

export async function activateWorkflow(workflowId: string): Promise<void> {
  await http.post(`/workflows/${workflowId}/activate`)
}

export async function deactivateWorkflow(workflowId: string): Promise<void> {
  await http.post(`/workflows/${workflowId}/deactivate`)
}

export async function deleteWorkflow(workflowId: string): Promise<void> {
  await http.delete(`/workflows/${workflowId}`)
}
