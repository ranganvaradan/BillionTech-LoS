/**
 * Client lender list visibility — hide platform seed / system reference from normal UX.
 * Does not delete rows; Internal surface sees everything.
 */

import { isClientLenderSurface } from '@/lib/runtimeEnv'

export type LenderObjectClass =
  | 'LENDER_CONFIG'
  | 'LEGACY'
  | 'DEMO_TEST'
  | 'SYSTEM_REFERENCE'
  | 'INTERNAL_RUNTIME'
  | 'HISTORICAL'

/** Day-1 seed Categories — not a new lender's configuration. */
export function isDay1SeedCategory(code: string | null | undefined): boolean {
  return String(code ?? '')
    .trim()
    .toUpperCase()
    .startsWith('CC_DAY1_')
}

/** Platform default workflow templates (foundation) — not lender-authored journeys. */
export function isPlatformDefaultWorkflow(name: string | null | undefined): boolean {
  const n = String(name ?? '').trim()
  return (
    /^Default Workflow\b/i.test(n) ||
    /^Anchor ID\b/i.test(n) ||
    /^Default\b/i.test(n)
  )
}

export function classifyCustomerCategory(row: {
  code?: string | null
  status?: string | null
}): LenderObjectClass {
  if (isDay1SeedCategory(row.code)) return 'DEMO_TEST'
  const st = String(row.status ?? '').toUpperCase()
  if (st === 'RETIRED') return 'HISTORICAL'
  return 'LENDER_CONFIG'
}

export function classifyWorkflow(row: {
  name?: string | null
  id?: string | null
}): LenderObjectClass {
  if (isPlatformDefaultWorkflow(row.name)) return 'SYSTEM_REFERENCE'
  return 'LENDER_CONFIG'
}

/**
 * Normal Client lender lists: LENDER_CONFIG only (plus optional linked system workflows).
 * When showPlatformCatalogue is true, show everything (diagnostics).
 */
export function filterCategoriesForLenderUi<T extends { code?: string | null; status?: string | null }>(
  rows: T[],
  opts?: { showPlatformCatalogue?: boolean },
): T[] {
  if (!isClientLenderSurface() || opts?.showPlatformCatalogue) return rows
  return rows.filter((r) => classifyCustomerCategory(r) === 'LENDER_CONFIG')
}

export function filterWorkflowsForLenderUi<
  T extends { name?: string | null; id?: string | null },
>(
  rows: T[],
  opts?: {
    showPlatformCatalogue?: boolean
    /** Workflow IDs referenced by visible lender Categories — keep for binding. */
    linkedWorkflowIds?: Set<string>
  },
): T[] {
  if (!isClientLenderSurface() || opts?.showPlatformCatalogue) return rows
  const linked = opts?.linkedWorkflowIds
  return rows.filter((r) => {
    if (classifyWorkflow(r) === 'LENDER_CONFIG') return true
    // Allow platform defaults that are actively bound to a lender Category
    if (linked && r.id && linked.has(String(r.id))) return true
    return false
  })
}

export function shouldDefaultHidePlatformCatalogue(): boolean {
  return isClientLenderSurface()
}
