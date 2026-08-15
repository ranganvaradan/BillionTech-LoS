import { http } from '@/api/http'

export type FulfilmentMode =
  | 'DIRECT_INPUT'
  | 'DOCUMENT_UPLOAD'
  | 'AUTOMATIC_SOURCE'
  | 'DERIVATION'
  | 'MANUAL_REVIEW'

export type CustomerFulfilmentState =
  | 'REQUIRED'
  | 'REQUESTED'
  | 'PROVIDED'
  | 'WAIVED'
  | 'NOT_APPLICABLE'
  | 'REUPLOAD_REQUIRED'

export type DataReadinessState =
  | 'NOT_AVAILABLE'
  | 'PROCESSING'
  | 'EXTRACTED'
  | 'VERIFIED'
  | 'READY_FOR_POLICY'
  | 'FAILED'
  | 'DATA_INSUFFICIENT'

export interface CustomerSummary {
  informationRequiredCount: number
  documentsRequiredCount: number
  providedCount: number
  processingCount: number
  remainingActionsCount: number
  reuploadRequiredCount: number
}

export interface FieldMeta {
  label: string
  helpText?: string | null
  datatype?: string | null
  unit?: string | null
  allowedValues?: string[]
  required: boolean
  inputType: string
}

export interface CustomerAction {
  actionKey: string
  section: string
  title: string
  description?: string | null
  whyNeeded?: string | null
  allowedModes: FulfilmentMode[]
  preferredMode?: FulfilmentMode | null
  chosenMode?: FulfilmentMode | null
  customerFulfilment: CustomerFulfilmentState
  customerStatusLabel: string
  dataReadiness: DataReadinessState
  processingLabel?: string | null
  actionable: boolean
  showChoice: boolean
  reuploadRequired: boolean
  extractionFailed: boolean
  documentGroup?: string | null
  documentRef?: string | null
  itemIds: string[]
  linkedLabels: string[]
  field?: FieldMeta | null
  draftValue?: Record<string, unknown>
}

export interface CustomerRequirementsView {
  applicationId: string
  planId?: string | null
  planVersion: number
  planPresent: boolean
  summary: CustomerSummary
  actions: CustomerAction[]
  providedOrProcessing: CustomerAction[]
  emptyMessage?: string | null
}

export async function getStaffCustomerRequirements(applicationId: string) {
  const { data } = await http.get<CustomerRequirementsView>(
    `/applications/${applicationId}/customer-requirements`,
  )
  return data
}

export async function getBorrowerCustomerRequirements(applicationId: string) {
  const { data } = await http.get<CustomerRequirementsView>(
    `/borrower/applications/${applicationId}/customer-requirements`,
  )
  return data
}

export async function submitDirectInput(
  applicationId: string,
  itemId: string,
  body: {
    value?: string
    verified?: boolean
    actor?: string
    actorRole?: string
    reason?: string
    saveDraftOnly?: boolean
  },
  borrower = false,
) {
  const base = borrower
    ? `/borrower/applications/${applicationId}/customer-requirements`
    : `/applications/${applicationId}/customer-requirements`
  const { data } = await http.post<CustomerRequirementsView>(`${base}/actions/${itemId}/direct-input`, body)
  return data
}

export async function submitRequirementDocument(
  applicationId: string,
  itemId: string,
  body: { documentRef: string; actor?: string; actorRole?: string; reason?: string },
  borrower = false,
) {
  const base = borrower
    ? `/borrower/applications/${applicationId}/customer-requirements`
    : `/applications/${applicationId}/customer-requirements`
  const { data } = await http.post<CustomerRequirementsView>(`${base}/actions/${itemId}/document`, body)
  return data
}

export async function chooseRequirementMode(
  applicationId: string,
  itemId: string,
  mode: FulfilmentMode,
  actorRole: string,
  borrower = false,
) {
  const base = borrower
    ? `/borrower/applications/${applicationId}/customer-requirements`
    : `/applications/${applicationId}/customer-requirements`
  const { data } = await http.post<CustomerRequirementsView>(`${base}/actions/${itemId}/choose-mode`, {
    mode,
    actorRole,
  })
  return data
}

export async function postDocumentOutcome(
  applicationId: string,
  itemId: string,
  outcome: 'DOCUMENT_REJECTED' | 'EXTRACTION_FAILED',
  actorRole = 'RM',
  reason?: string,
) {
  const { data } = await http.post<CustomerRequirementsView>(
    `/applications/${applicationId}/customer-requirements/actions/${itemId}/document-outcome`,
    { outcome, actorRole, reason },
  )
  return data
}
