import type { EligibilityResult } from '@/api/categorySelection'
import type { ApplicationResponse } from '@/types/application'

/** Frozen workflow + category labels for UI display — sourced from application pin, never catalog latest. */
export type PinnedWorkflowDisplay = {
  workflowId: string
  workflowName: string
  workflowVersion: number
  categoryDisplayName?: string
  categoryCode?: string
  categoryVersion?: number
}

export function isCategoryGovernedApplication(
  app: Pick<ApplicationResponse, 'workflowResolutionSource' | 'categorySelectionState'>,
): boolean {
  return (
    app.workflowResolutionSource === 'CATEGORY_SELECTION' ||
    app.categorySelectionState === 'CATEGORY_SELECTED'
  )
}

export function pinnedWorkflowDisplayFromCategoryHandoff(
  selected: NonNullable<EligibilityResult['selected']>,
): PinnedWorkflowDisplay | null {
  if (!selected.workflowId || !selected.workflowName || selected.workflowVersion == null) {
    return null
  }
  return {
    workflowId: selected.workflowId,
    workflowName: selected.workflowName,
    workflowVersion: selected.workflowVersion,
    categoryDisplayName: selected.categoryDisplayName ?? selected.categoryCode,
    categoryCode: selected.categoryCode,
    categoryVersion: selected.categoryVersion,
  }
}

export function pinnedWorkflowDisplayFromApplication(
  app: ApplicationResponse,
): PinnedWorkflowDisplay | null {
  if (!isCategoryGovernedApplication(app)) {
    return null
  }
  if (!app.workflowId || !app.workflowName || app.workflowVersion == null) {
    return null
  }
  return {
    workflowId: app.workflowId,
    workflowName: app.workflowName,
    workflowVersion: app.workflowVersion,
    categoryDisplayName: app.selectedCustomerCategoryDisplayName ?? app.selectedCustomerCategoryCode ?? undefined,
    categoryCode: app.selectedCustomerCategoryCode ?? undefined,
    categoryVersion: app.selectedCustomerCategoryVersion ?? undefined,
  }
}
