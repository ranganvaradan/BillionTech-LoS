/**
 * Aligns with backend `WorkflowConfigResponse` / `WorkflowConfigRequest` JSON.
 */
export interface WorkflowConfigResponse {
  id: string
  name: string
  borrowerType: string
  loanProduct: string
  steps: Record<string, unknown>[]
  conditionalRules?: Record<string, unknown>[] | null
  vkycTriggerCondition?: Record<string, unknown>[] | null
  workflowPosition?: string | null
  active: boolean
  version: number
  createdAt: string | null
}

export interface WorkflowConfigRequest {
  name: string
  borrowerType: string
  loanProduct: string
  steps: Record<string, unknown>[]
  conditionalRules?: Record<string, unknown>[]
  vkycTriggerCondition?: Record<string, unknown>[]
  workflowPosition?: string
}
