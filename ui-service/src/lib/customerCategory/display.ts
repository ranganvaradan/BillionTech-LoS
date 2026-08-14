import type { LifecycleAction, LifecycleStatus } from '@/api/customerCategories'
import { BORROWER_TYPE_LABELS } from '@/catalog/borrowerTypes'
import { loanProductLabel } from '@/catalog/loanProducts'
import type { BorrowerType } from '@/types/createApplication'

export const ANY_TOKEN = 'ANY'

export const INTAKE_OPTIONS = [
  { value: 'ANY', label: 'Any' },
  { value: 'BORROWER', label: 'Borrower' },
  { value: 'ANCHOR', label: 'Anchor' },
] as const

export function displayMatchDimension(value: string | null | undefined): string {
  if (value == null || value.trim() === '' || value.trim().toUpperCase() === ANY_TOKEN) {
    return 'Any'
  }
  return value.trim()
}

export function displayBorrowerType(value: string | null | undefined): string {
  const raw = displayMatchDimension(value)
  if (raw === 'Any') return 'Any'
  return BORROWER_TYPE_LABELS[raw as BorrowerType] ?? raw
}

export function displayLoanProduct(value: string | null | undefined): string {
  const raw = displayMatchDimension(value)
  if (raw === 'Any') return 'Any'
  return loanProductLabel(raw)
}

export function displayIntake(value: string | null | undefined): string {
  const raw = displayMatchDimension(value)
  if (raw === 'Any') return 'Any'
  const hit = INTAKE_OPTIONS.find((o) => o.value === raw.toUpperCase())
  return hit?.label ?? raw
}

/** Inclusive bounds; blank/null = unbounded. */
export function displayAmountRange(
  minAmount: number | null | undefined,
  maxAmount: number | null | undefined,
): string {
  const minLabel = minAmount == null ? 'unbounded below' : formatAmount(minAmount)
  const maxLabel = maxAmount == null ? 'unbounded above' : formatAmount(maxAmount)
  if (minAmount == null && maxAmount == null) return 'Any amount (inclusive)'
  return `${minLabel} – ${maxLabel} (inclusive)`
}

export function formatAmount(n: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 0,
  }).format(n)
}

export function formatInstant(value: string | null | undefined): string {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  return d.toLocaleString()
}

export function statusLabel(status: LifecycleStatus | string): string {
  switch (status) {
    case 'IN_REVIEW':
      return 'In review'
    case 'DRAFT':
      return 'Draft'
    case 'APPROVED':
      return 'Approved'
    case 'ACTIVE':
      return 'Active'
    case 'RETIRED':
      return 'Retired'
    default:
      return status
  }
}

export function hasAction(
  allowed: LifecycleAction[] | null | undefined,
  action: LifecycleAction,
): boolean {
  return (allowed ?? []).includes(action)
}

export function isEditableStatus(status: LifecycleStatus | string): boolean {
  return status === 'DRAFT'
}

export function historyEventLabel(event: unknown): string {
  const e = String(event ?? '')
  switch (e) {
    case 'CREATED':
      return 'Created'
    case 'UPDATED':
      return 'Updated'
    case 'SUBMITTED':
      return 'Submitted for review'
    case 'APPROVED':
      return 'Approved'
    case 'RETURNED':
      return 'Returned to draft'
    case 'ACTIVATED':
      return 'Activated'
    case 'RETIRED':
      return 'Retired'
    case 'COPIED':
      return 'Copied / new version'
    default:
      return e || 'Event'
  }
}

export function parseOptionalAmount(raw: string): number | null {
  const t = raw.trim()
  if (!t) return null
  const n = Number(t.replace(/,/g, ''))
  if (!Number.isFinite(n)) throw new Error('Amount must be a number')
  return n
}

export function toApiMatchValue(value: string): string {
  const t = value.trim()
  if (!t || t.toUpperCase() === ANY_TOKEN) return ANY_TOKEN
  return t
}

export function overlapPeerName(row: Record<string, unknown>, selfCodeVersion: string): string {
  const left = String(row.left ?? '')
  const right = String(row.right ?? '')
  const leftName = String(row.leftName ?? left)
  const rightName = String(row.rightName ?? right)
  if (left === selfCodeVersion) return rightName
  if (right === selfCodeVersion) return leftName
  return `${leftName} ↔ ${rightName}`
}
