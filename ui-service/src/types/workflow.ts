/**
 * Aligns with backend `WorkflowConfigResponse` / `WorkflowConfigRequest` JSON.
 */
export type WorkflowIntakeSegment = 'BORROWER' | 'ANCHOR'

export interface WorkflowConfigResponse {
  id: string
  name: string
  borrowerType: string
  loanProduct: string
  /** Omitted in older API payloads — treat as BORROWER. */
  intakeSegment?: WorkflowIntakeSegment | null
  intakeIdentitySchema?: Record<string, unknown>[] | null
  steps: Record<string, unknown>[]
  processNotificationMappings?: Record<string, unknown>[] | null
  manualOverridePolicies?: Record<string, unknown>[] | null
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
  intakeSegment?: WorkflowIntakeSegment
  intakeIdentitySchema?: Record<string, unknown>[]
  steps: Record<string, unknown>[]
  processNotificationMappings?: Record<string, unknown>[]
  manualOverridePolicies?: Record<string, unknown>[]
  conditionalRules?: Record<string, unknown>[]
  vkycTriggerCondition?: Record<string, unknown>[]
  workflowPosition?: string
}
