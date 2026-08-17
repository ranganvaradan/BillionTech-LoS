/**
 * Aligns with backend `WorkflowConfigResponse` / `WorkflowConfigRequest` JSON.
 */
export type WorkflowIntakeSegment = 'BORROWER' | 'ANCHOR'

export type WorkflowIntakePolicy = 'LEGACY' | 'WORKFLOW_DRIVEN'

export interface WorkflowPersonalFieldConfig {
  collect?: boolean
  required?: boolean
  allowedValues?: string[]
}

export interface WorkflowCustomFieldConfig {
  key: string
  label: string
  type: 'TEXT' | 'NUMBER' | 'BOOLEAN'
  required?: boolean
  helpText?: string
  defaultValue?: string | number | boolean | null
  usedForUnderwriting?: boolean
}

export interface WorkflowAgeRules {
  enabled?: boolean
  minAge?: number
  maxAge?: number
}

export interface WorkflowTenureOption {
  value: string
  label: string
  unit?: string
}

export interface WorkflowTenureRules {
  inputMode?: 'numeric' | 'dropdown'
  min?: number
  max?: number
  defaultValue?: string
  options?: WorkflowTenureOption[]
}

/** Allowed intake dropdown value — scores live only on underwriting scorecards. */
export interface WorkflowCodedOption {
  value: string
  label: string
}

export interface WorkflowCodedFieldRules {
  options?: WorkflowCodedOption[]
}

export interface WorkflowMandatoryFieldGroup {
  id: string
  label: string
  logic: 'ANY'
  steps: string[]
}

export interface WorkflowStandaloneDocument {
  documentType: string
  required?: boolean
  label?: string
}

/** Aligns with backend `intakeConfig.coApplicant` (nested JSON) — co-applicant / joint borrower rules. */
export interface WorkflowCoApplicantConfig {
  enabled?: boolean
  maxCoApplicants?: number
  minCoApplicants?: number
  captureAtRmCreate?: boolean
  notifyAllOnInvite?: boolean
  primaryKycSteps?: string[]
  coApplicantKycSteps?: string[]
  requireAllEsignBeforeDisbursement?: boolean
  underwritingParty?: 'PRIMARY'
  personalFields?: {
    dateOfBirth?: WorkflowPersonalFieldConfig
    gender?: WorkflowPersonalFieldConfig
    occupation?: WorkflowPersonalFieldConfig
  }
}

export interface WorkflowStepDocumentRequired {
  documentType: string
  required?: boolean
}

export interface WorkflowIntakeConfig {
  policy?: WorkflowIntakePolicy
  personalFields?: {
    dateOfBirth?: WorkflowPersonalFieldConfig
    gender?: WorkflowPersonalFieldConfig
    occupation?: WorkflowPersonalFieldConfig
    loanPurpose?: WorkflowPersonalFieldConfig
  }
  ageRules?: WorkflowAgeRules
  tenureRules?: WorkflowTenureRules
  occupationRules?: WorkflowCodedFieldRules
  loanPurposeRules?: WorkflowCodedFieldRules
  customFields?: WorkflowCustomFieldConfig[]
  mandatoryFieldGroups?: WorkflowMandatoryFieldGroup[]
  standaloneDocuments?: WorkflowStandaloneDocument[]
  /**
   * Optional Indian state names allowed for this workflow (geo master stateName values).
   * Empty / omitted / `["ALL"]` = all states (default). Non-empty list = only those states.
   */
  allowedStates?: string[]
  /** When enabled, staff/borrower intake can capture co-applicants for this workflow. */
  coApplicant?: WorkflowCoApplicantConfig
}

export interface WorkflowConfigResponse {
  id: string
  name: string
  borrowerType: string
  loanProduct: string
  /** Encore LMS product code default for this workflow. */
  lmsProductCode?: string | null
  /** Encore tenure unit default (Day, Month, Week). */
  lmsTenureUnit?: string | null
  /** Omitted in older API payloads — treat as BORROWER. */
  intakeSegment?: WorkflowIntakeSegment | null
  intakeIdentitySchema?: Record<string, unknown>[] | null
  intakeConfig?: WorkflowIntakeConfig | null
  bureauEnabled?: boolean
  autoPullBureauAfterKycSuccess?: boolean
  steps: Record<string, unknown>[]
  processNotificationMappings?: Record<string, unknown>[] | null
  manualOverridePolicies?: Record<string, unknown>[] | null
  conditionalRules?: Record<string, unknown>[] | null
  vkycTriggerCondition?: Record<string, unknown>[] | null
  workflowPosition?: string | null
  active: boolean
  version: number
  workflowFamilyId?: string | null
  publicationStatus?: 'DRAFT' | 'ACTIVE' | 'SUPERSEDED' | 'RETIRED' | string | null
  createdAt: string | null
}

export interface WorkflowConfigRequest {
  name: string
  borrowerType: string
  loanProduct: string
  lmsProductCode?: string
  lmsTenureUnit?: string
  intakeSegment?: WorkflowIntakeSegment
  intakeIdentitySchema?: Record<string, unknown>[]
  intakeConfig?: WorkflowIntakeConfig
  bureauEnabled?: boolean
  autoPullBureauAfterKycSuccess?: boolean
  steps: Record<string, unknown>[]
  processNotificationMappings?: Record<string, unknown>[]
  manualOverridePolicies?: Record<string, unknown>[]
  conditionalRules?: Record<string, unknown>[]
  vkycTriggerCondition?: Record<string, unknown>[]
  workflowPosition?: string
}
