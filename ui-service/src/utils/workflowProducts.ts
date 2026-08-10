import { isSecuredLoanProductCode, loanProductLabel } from '@/catalog/loanProducts'
import type { WorkflowConfigResponse } from '@/types/workflow'

export type WorkflowIntakeSegmentUi = 'BORROWER' | 'ANCHOR'

/** Defaults to BORROWER when absent (older API payloads). */
export function workflowIntakeSegment(w: WorkflowConfigResponse): WorkflowIntakeSegmentUi {
  return w.intakeSegment === 'ANCHOR' ? 'ANCHOR' : 'BORROWER'
}

/** Loan products from **active** workflow rows only (same list used on Assignment / Underwriting rules UIs). */
export function uniqueActiveWorkflowLoanProducts(workflows: WorkflowConfigResponse[]): string[] {
  const u = new Set<string>()
  for (const w of workflows) {
    if (w.active && w.loanProduct) u.add(w.loanProduct)
  }
  return [...u].sort()
}

/** Deduplicate by loan product, keeping the highest version per product (same rules as new-application intakes). */
export function dedupeByLoanProduct(workflows: WorkflowConfigResponse[]): WorkflowConfigResponse[] {
  const byKey = new Map<string, WorkflowConfigResponse>()
  for (const w of workflows) {
    const prev = byKey.get(w.loanProduct)
    if (!prev || w.version > prev.version) byKey.set(w.loanProduct, w)
  }
  return Array.from(byKey.values()).sort((a, b) => a.loanProduct.localeCompare(b.loanProduct))
}

export function matchingWorkflowsForBorrowerType(
  activeWorkflows: WorkflowConfigResponse[],
  borrowerType: string,
): WorkflowConfigResponse[] {
  return activeWorkflows
    .filter((w) => w.active && w.borrowerType === borrowerType && workflowIntakeSegment(w) === 'BORROWER')
    .sort((a, b) => {
      const productCmp = a.loanProduct.localeCompare(b.loanProduct)
      if (productCmp !== 0) return productCmp
      return b.version - a.version
    })
}

/** All active BORROWER-segment workflows for a borrower type + loan product (highest version first). */
export function matchingWorkflowsForProduct(
  activeWorkflows: WorkflowConfigResponse[],
  borrowerType: string,
  loanProduct: string,
  intakeSegment: WorkflowIntakeSegmentUi = 'BORROWER',
): WorkflowConfigResponse[] {
  if (!loanProduct) return []
  const segment = intakeSegment === 'ANCHOR' ? 'ANCHOR' : 'BORROWER'
  return activeWorkflows
    .filter(
      (w) =>
        w.active &&
        w.borrowerType === borrowerType &&
        w.loanProduct === loanProduct &&
        workflowIntakeSegment(w) === segment,
    )
    .sort((a, b) => b.version - a.version)
}

/**
 * Prefer the current binding when still valid; otherwise the sole match or highest version.
 * Empty when there is no active workflow for the product.
 */
export function resolveWorkflowIdForProduct(
  activeWorkflows: WorkflowConfigResponse[],
  borrowerType: string,
  loanProduct: string,
  currentWorkflowId?: string | null,
  intakeSegment: WorkflowIntakeSegmentUi = 'BORROWER',
): string {
  const matches = matchingWorkflowsForProduct(activeWorkflows, borrowerType, loanProduct, intakeSegment)
  if (matches.length === 0) return ''
  const current = (currentWorkflowId ?? '').trim()
  if (current && matches.some((w) => w.id === current)) return current
  return matches[0]!.id
}

/**
 * Active workflows for borrower loan origination only (excludes {@code ANCHOR} intake rows
 * so anchor invoice-discounting configs do not appear in the standard new-application wizard).
 */
export function productsForBorrowerType(
  activeWorkflows: WorkflowConfigResponse[],
  borrowerType: string,
): WorkflowConfigResponse[] {
  return dedupeByLoanProduct(matchingWorkflowsForBorrowerType(activeWorkflows, borrowerType))
}

/** Active workflows for a borrower class and intake segment (e.g. anchor onboarding). */
export function productsForIntakeSegment(
  activeWorkflows: WorkflowConfigResponse[],
  segment: WorkflowIntakeSegmentUi,
  borrowerType: string,
): WorkflowConfigResponse[] {
  return dedupeByLoanProduct(
    activeWorkflows.filter((w) => w.active && w.borrowerType === borrowerType && workflowIntakeSegment(w) === segment),
  )
}

/** True if at least one active workflow for this borrower class maps to a secured (collateral) product in the UI. */
export function activeCatalogHasSecuredProduct(
  activeWorkflows: WorkflowConfigResponse[],
  borrowerType: string,
): boolean {
  return productsForBorrowerType(activeWorkflows, borrowerType).some((w) => isSecuredLoanProductCode(w.loanProduct))
}

/** Display label for a workflow’s loan product code (for dropdowns, tables, review). */
export function workflowLoanProductDisplayName(loanProduct: string): string {
  return loanProductLabel(loanProduct)
}
