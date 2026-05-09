import type { BorrowerType } from '@/types/createApplication'

export const BORROWER_TYPE_ORDER: readonly BorrowerType[] = [
  'INDIVIDUAL',
  'PROPRIETOR',
  'PARTNERSHIP',
  'COMPANY',
] as const

export const BORROWER_TYPE_LABELS: Record<BorrowerType, string> = {
  INDIVIDUAL: 'Individual',
  PROPRIETOR: 'Proprietor',
  PARTNERSHIP: 'Partnership',
  COMPANY: 'Company',
}

export function borrowerTypeLabel(code: string | null | undefined): string {
  if (code == null || code === '') return '—'
  if (code in BORROWER_TYPE_LABELS) return BORROWER_TYPE_LABELS[code as BorrowerType]
  return code
}
