import { isSecuredLoanProductCode, loanProductLabel } from '@/catalog/loanProducts'
import type { WorkflowConfigResponse } from '@/types/workflow'

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

/** Active workflows for a concrete borrower type, deduped by loan product. */
export function productsForBorrowerType(
  activeWorkflows: WorkflowConfigResponse[],
  borrowerType: string,
): WorkflowConfigResponse[] {
  return dedupeByLoanProduct(activeWorkflows.filter((w) => w.borrowerType === borrowerType))
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
