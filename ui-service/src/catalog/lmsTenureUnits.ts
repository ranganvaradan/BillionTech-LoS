/** Encore LMS tenure unit options for workflow and application intake UI. */

export const LMS_TENURE_UNIT_OPTIONS = [
  { value: 'Day', label: 'Day' },
  { value: 'Month', label: 'Month' },
  { value: 'Week', label: 'Week' },
] as const

export const DEFAULT_LMS_PRODUCT_CODE = 'IPPOPAYM01'
export const DEFAULT_LMS_TENURE_UNIT = 'Month'

export function lmsTenureUnitLabel(value: string | null | undefined): string {
  if (!value) return '—'
  const match = LMS_TENURE_UNIT_OPTIONS.find((o) => o.value.toLowerCase() === value.toLowerCase())
  return match?.label ?? value
}

/** Input label for tenure magnitude based on LMS tenure unit (e.g. Tenure (days)). */
export function tenureMagnitudeLabel(unit: string | null | undefined): string {
  const u = (unit || DEFAULT_LMS_TENURE_UNIT).trim().toLowerCase()
  if (u === 'day') return 'Tenure (days)'
  if (u === 'week') return 'Tenure (weeks)'
  return 'Tenure (months)'
}

/** Short unit suffix for review lines (e.g. "12 days"). */
export function tenureMagnitudeShortUnit(unit: string | null | undefined): string {
  const u = (unit || DEFAULT_LMS_TENURE_UNIT).trim().toLowerCase()
  if (u === 'day') return 'days'
  if (u === 'week') return 'weeks'
  return 'months'
}

/** Installment label for KFS / sanction display (EDI for daily, EMI for monthly, EWI for weekly). */
export function installmentPaymentLabel(unit: string | null | undefined): string {
  const u = (unit || DEFAULT_LMS_TENURE_UNIT).trim().toLowerCase()
  if (u === 'day') return 'EDI'
  if (u === 'week') return 'EWI'
  return 'EMI'
}

/** True when LMS workflow fields should be hidden (invoice discounting uses PLP program config). */
export function workflowUsesPlpLmsConfig(loanProduct: string, intakeSegment?: string | null): boolean {
  return loanProduct === 'BUSINESS_WC_INVOICE_DISCOUNTING' || intakeSegment === 'ANCHOR'
}
