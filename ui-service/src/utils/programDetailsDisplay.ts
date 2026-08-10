export type ProgramDetailRow = { label: string; value: string }

function parseBool(raw: unknown): boolean {
  return raw === true || raw === 'true' || raw === 'YES'
}

function yesNo(raw: unknown): string {
  return parseBool(raw) ? 'Yes' : 'No'
}

function fmtNum(raw: unknown, suffix = ''): string | null {
  if (raw == null || raw === '') return null
  const n = Number(raw)
  if (Number.isNaN(n)) return null
  return `${n}${suffix}`
}

function pushRow(rows: ProgramDetailRow[], label: string, value: string | null | undefined) {
  if (value == null || value === '') return
  rows.push({ label, value })
}

export function buildProgramConfigurationRowsFromEnrollment(row: {
  productType?: string | null
  programConfig?: Record<string, unknown> | null
  programParameters?: Record<string, unknown> | null
  lmsEntryIn?: string | null
  encoreProductCode?: string | null
}): ProgramDetailRow[] {
  const rows: ProgramDetailRow[] = []
  const cfg = row.programConfig ?? {}
  const params = row.programParameters ?? {}
  const isId = row.productType === 'INVOICE_DISCOUNTING'

  if (isId) {
    pushRow(rows, 'Age of invoice (days)', fmtNum(cfg.maxInvoiceAgeDays))
    pushRow(
      rows,
      'Min invoice amount',
      cfg.minInvoiceAmount != null ? `₹${Number(cfg.minInvoiceAmount).toLocaleString('en-IN')}` : null,
    )
    pushRow(rows, 'Min days to due date', fmtNum(cfg.minDaysToDueDate))
  }

  pushRow(rows, 'Enable payment for borrower', yesNo(params.enablePaymentForBorrower))
  pushRow(rows, 'Auto payment (borrower)', yesNo(params.autoPaymentBorrower))
  pushRow(rows, 'Auto discounting', yesNo(params.autoDiscounting))
  pushRow(rows, 'Discounting day (of month)', fmtNum(params.discountingDay))
  pushRow(rows, 'Gap b/w discounting (days)', fmtNum(params.gapBetweenDiscountingDays))
  pushRow(rows, 'Gap b/w previous invoice (days)', fmtNum(params.gapBetweenPreviousInvoiceDays))
  pushRow(rows, 'Auto pull option', yesNo(params.autoPullOption))
  pushRow(rows, 'Auto accept invoices', yesNo(params.autoAcceptInvoices))
  pushRow(rows, 'Sanction type', params.sanctionType ? String(params.sanctionType).toUpperCase() : null)
  pushRow(rows, 'Gap b/w sanction & disbursement (days)', fmtNum(params.gapBetweenSanctionAndDisbursementDays))
  pushRow(rows, 'Partial discount', yesNo(params.partialDiscount))
  pushRow(rows, 'Invoice delete allowed', yesNo(params.invoiceDelete))
  pushRow(rows, 'Interest-free credit period', yesNo(params.intFreeCreditPeriod))
  pushRow(rows, 'Interest-free period (days)', fmtNum(params.intFreePeriodDays))
  pushRow(rows, 'LMS entry', row.lmsEntryIn === 'YES' ? 'Yes' : row.lmsEntryIn === 'NO' ? 'No' : null)
  if (row.lmsEntryIn === 'YES') {
    pushRow(rows, 'LMS product code', row.encoreProductCode?.trim() || null)
  }
  return rows
}

export function buildSubProgramConfigurationRowsFromEnrollment(row: {
  flowType?: string | null
  subProgramInterestRate?: number | null
  subProgramMarginPercent?: number | null
  subProgramMaxTenureDays?: number | null
}): ProgramDetailRow[] {
  const rows: ProgramDetailRow[] = []
  pushRow(rows, 'Flow type', row.flowType?.replace(/_/g, ' ') ?? null)
  pushRow(rows, 'Interest rate', row.subProgramInterestRate != null ? `${row.subProgramInterestRate}%` : null)
  pushRow(rows, 'Discount margin', row.subProgramMarginPercent != null ? `${row.subProgramMarginPercent}%` : null)
  pushRow(rows, 'Max tenure (days)', fmtNum(row.subProgramMaxTenureDays))
  return rows
}

export function buildBorrowerTermsRowsFromEnrollment(row: {
  borrowerInterestRate?: number | null
  borrowerDiscountMarginPercent?: number | null
  borrowerCreditPeriodDays?: number | null
  borrowerDiscountHold?: string | null
  borrowerPaymentMethod?: string | null
  borrowerOverdueInterestRate?: number | null
}): ProgramDetailRow[] {
  const rows: ProgramDetailRow[] = []
  pushRow(rows, 'Interest rate', row.borrowerInterestRate != null ? `${row.borrowerInterestRate}%` : null)
  pushRow(
    rows,
    'Discount margin',
    row.borrowerDiscountMarginPercent != null ? `${row.borrowerDiscountMarginPercent}%` : null,
  )
  pushRow(rows, 'Credit period (days)', fmtNum(row.borrowerCreditPeriodDays))
  pushRow(
    rows,
    'Discount hold',
    row.borrowerDiscountHold === 'YES' ? 'Yes' : row.borrowerDiscountHold === 'NO' ? 'No' : null,
  )
  pushRow(rows, 'Payment method', row.borrowerPaymentMethod?.replace(/_/g, ' ') ?? null)
  pushRow(
    rows,
    'Overdue interest rate',
    row.borrowerOverdueInterestRate != null ? `${row.borrowerOverdueInterestRate}%` : null,
  )
  return rows
}
