import type { EligibilityResult } from '@/api/categorySelection'
import type { ApplicationResponse } from '@/types/application'
import { isCategoryGovernedApplication } from '@/lib/workflow/pinnedWorkflowDisplay'

export type CategoryPinnedSelection = NonNullable<EligibilityResult['selected']>

export function hasCategoryPin(
  selected: CategoryPinnedSelection | null | undefined,
): selected is CategoryPinnedSelection {
  return Boolean(
    selected?.categoryId &&
      selected.categoryCode &&
      selected.categoryVersion != null &&
      selected.workflowId &&
      selected.workflowVersion != null,
  )
}

export function buildCategorySelectedResult(
  selected: CategoryPinnedSelection,
): EligibilityResult {
  return {
    state: 'CATEGORY_SELECTED',
    eligible: [],
    selected,
  }
}

/** Build category pin display from persisted application GET — authoritative on revisit. */
export function categoryPinnedSelectionFromApplication(
  app: ApplicationResponse,
): CategoryPinnedSelection | null {
  if (!isCategoryGovernedApplication(app)) return null
  if (
    !app.selectedCustomerCategoryId ||
    !app.selectedCustomerCategoryCode ||
    app.selectedCustomerCategoryVersion == null ||
    !app.workflowId ||
    app.workflowVersion == null
  ) {
    return null
  }
  return {
    applicationId: app.id,
    categoryId: app.selectedCustomerCategoryId,
    categoryCode: app.selectedCustomerCategoryCode,
    categoryVersion: app.selectedCustomerCategoryVersion,
    categoryDisplayName:
      app.selectedCustomerCategoryDisplayName ?? app.selectedCustomerCategoryCode,
    policyDocumentId: app.selectedPolicyDocumentId ?? undefined,
    workflowId: app.workflowId,
    workflowVersion: app.workflowVersion,
    workflowName: app.workflowName ?? undefined,
    selectionSource: app.categorySelectionSource ?? undefined,
  }
}

/**
 * Apply async evaluate result without displacing a valid pin with loading/error/transient states.
 * Returns null when `incoming` is stale relative to `requestGeneration`.
 */
export function mergeCategoryEvaluateResult(
  current: EligibilityResult | null,
  incoming: EligibilityResult,
  requestGeneration: number,
  activeGeneration: number,
): EligibilityResult | null {
  if (requestGeneration !== activeGeneration) return null
  if (incoming.state === 'CATEGORY_SELECTED' && incoming.selected) {
    return incoming
  }
  if (current?.state === 'CATEGORY_SELECTED' && current.selected) {
    return current
  }
  return incoming
}

/** True when a background evaluate failure must not replace the pinned display. */
export function shouldPreservePinOnEvaluateError(
  pinnedSelection: CategoryPinnedSelection | null | undefined,
  current: EligibilityResult | null,
): boolean {
  return hasCategoryPin(pinnedSelection) || current?.state === 'CATEGORY_SELECTED'
}
