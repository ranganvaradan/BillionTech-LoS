/**
 * Application Category eligibility / disambiguation / selection APIs.
 * Does not trigger W4/W6 or underwriting.
 */
import { http } from './http'

export type CategorySelectionState =
  | 'NO_ELIGIBLE_CATEGORY'
  | 'AUTO_SINGLE_MATCH'
  | 'DISAMBIGUATION_REQUIRED'
  | 'EXPLICIT_PROPOSITION_SELECTION_REQUIRED'
  | 'CATEGORY_SELECTED'

export interface EligibilityResult {
  state: CategorySelectionState
  eligible: Array<{
    categoryId: string
    code: string
    versionNo: number
    name: string
    customerFacingName?: string
    shortDescription?: string
    requirementsSummary?: string
  }>
  noMatchReasons?: string[]
  nextQuestion?: {
    questionId: string
    prompt: string
    options: Array<{ value: string; label: string; retainsCategoryIds: string[] }>
    whySafe?: string
  }
  propositions?: Array<{
    categoryId: string
    name: string
    shortDescription?: string
    requirementsSummary?: string
    benefits?: string[]
  }>
  selected?: {
    applicationId: string
    categoryId: string
    categoryCode: string
    categoryVersion: number
    categoryDisplayName?: string
    policyDocumentId?: string
    policyVersionLabel?: string
    workflowId?: string
    workflowVersion?: number
    workflowName?: string
    selectionSource?: string
  }
  diagnostics?: Record<string, unknown>
}

const draftQ = (allowDraftSimulation?: boolean) =>
  allowDraftSimulation ? '?allowDraftSimulation=true' : ''

export async function evaluateCategorySelection(
  applicationId: string,
  allowDraftSimulation = false,
): Promise<EligibilityResult> {
  const { data } = await http.get<EligibilityResult>(
    `/applications/${applicationId}/category-selection${draftQ(allowDraftSimulation)}`,
  )
  return data
}

export async function answerCategoryDisambiguation(
  applicationId: string,
  body: { questionId: string; answerValue: string; actor?: string; actorRole?: string },
  allowDraftSimulation = false,
): Promise<EligibilityResult> {
  const { data } = await http.post<EligibilityResult>(
    `/applications/${applicationId}/category-selection/answers${draftQ(allowDraftSimulation)}`,
    body,
  )
  return data
}

export async function autoSelectCategory(
  applicationId: string,
  actor = 'SYSTEM',
  allowDraftSimulation = false,
): Promise<EligibilityResult> {
  const { data } = await http.post<EligibilityResult>(
    `/applications/${applicationId}/category-selection/auto-select${draftQ(allowDraftSimulation)}`,
    { actor },
  )
  return data
}

export async function selectCategory(
  applicationId: string,
  body: {
    categoryId: string
    actor?: string
    actorRole?: string
    reason?: string
    selectionSource?: string
  },
  allowDraftSimulation = false,
): Promise<EligibilityResult> {
  const { data } = await http.post<EligibilityResult>(
    `/applications/${applicationId}/category-selection/select${draftQ(allowDraftSimulation)}`,
    body,
  )
  return data
}

export async function updateCategoryPropositionConfig(
  categoryId: string,
  body: { proposition?: Record<string, unknown>; disambiguation?: Record<string, unknown> },
): Promise<Record<string, unknown>> {
  const { data } = await http.put<Record<string, unknown>>(
    `/customer-categories/${categoryId}/proposition-config`,
    body,
  )
  return data
}
