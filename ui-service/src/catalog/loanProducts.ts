/**
 * Master loan product codes (API / DB) vs display labels (UI only).
 * All creates/updates must send `LoanProductCode` only — never free-text product.
 */

export const LOAN_PRODUCT_CODES = [
  'PERSONAL_LOAN',
  'BUSINESS_TERM_LOAN',
  'BUSINESS_WC_OD',
  'BUSINESS_WC_INVOICE_DISCOUNTING',
  'TERM_LOAN',
  'LOAN_AGAINST_PROPERTY',
  'LOAN_AGAINST_SECURITIES',
  'LOAN_AGAINST_GOLD',
] as const

export type LoanProductCode = (typeof LOAN_PRODUCT_CODES)[number]

/** Invoice discounting (working capital) — only product that may branch to anchor onboarding in staff intake. */
export const INVOICE_DISCOUNTING_PRODUCT_CODE: LoanProductCode = 'BUSINESS_WC_INVOICE_DISCOUNTING'

export function isInvoiceDiscountingProduct(code: string | null | undefined): boolean {
  return (code ?? '').trim() === INVOICE_DISCOUNTING_PRODUCT_CODE
}

export const LOAN_PRODUCT_LABELS: Record<LoanProductCode, string> = {
  PERSONAL_LOAN: 'Personal Loan',
  BUSINESS_TERM_LOAN: 'Business Term Loan',
  BUSINESS_WC_OD: 'Business Working Capital Loan -- Overdraft',
  BUSINESS_WC_INVOICE_DISCOUNTING: 'Business Working Capital Loan -- Invoice Discounting',
  TERM_LOAN: 'Term Loan',
  LOAN_AGAINST_PROPERTY: 'Loan Against Property',
  LOAN_AGAINST_SECURITIES: 'Loan Against Securities',
  LOAN_AGAINST_GOLD: 'Loan Against Gold',
}

const LEGACY_LABEL_TO_CODE: Record<string, LoanProductCode> = {
  Personal: 'PERSONAL_LOAN',
  'Personal Loan': 'PERSONAL_LOAN',
  PERSONAL_LOAN: 'PERSONAL_LOAN',
  LAP: 'LOAN_AGAINST_PROPERTY',
  'Loan Against Property': 'LOAN_AGAINST_PROPERTY',
  'Loan Against Shares': 'LOAN_AGAINST_SECURITIES',
  LAS: 'LOAN_AGAINST_SECURITIES',
  'Loan Against Securities': 'LOAN_AGAINST_SECURITIES',
  'Gold Loan': 'LOAN_AGAINST_GOLD',
  'Loan Against Gold': 'LOAN_AGAINST_GOLD',
  'Term Loan': 'TERM_LOAN',
  TERM_LOAN: 'TERM_LOAN',
  'Business Term Loan': 'BUSINESS_TERM_LOAN',
  BUSINESS_LOAN: 'BUSINESS_TERM_LOAN',
  BUSINESS_TERM_LOAN: 'BUSINESS_TERM_LOAN',
  WORKING_CAPITAL: 'BUSINESS_WC_OD',
  'Business Working Capital Loan -- Overdraft': 'BUSINESS_WC_OD',
  'Business Working Capital Loan -- Invoice Discounting': 'BUSINESS_WC_INVOICE_DISCOUNTING',
  SME: 'BUSINESS_TERM_LOAN',
  MSME: 'BUSINESS_TERM_LOAN',
}

export function isLoanProductCode(s: string): s is LoanProductCode {
  return (LOAN_PRODUCT_CODES as readonly string[]).includes(s)
}

export function loanProductLabel(code: string | null | undefined): string {
  if (code == null || code === '') return '—'
  if (isLoanProductCode(code)) return LOAN_PRODUCT_LABELS[code]
  if (code in LEGACY_LABEL_TO_CODE) return LOAN_PRODUCT_LABELS[LEGACY_LABEL_TO_CODE[code]!]
  return code
}

/** Normalize a stored or legacy product string to a standard code, if known. */
export function toLoanProductCodeOrOriginal(raw: string): string {
  const t = raw.trim()
  if (t === '') return t
  if (isLoanProductCode(t)) return t
  return LEGACY_LABEL_TO_CODE[t] ?? t
}

export const SECURED_LOAN_PRODUCT_CODES: ReadonlySet<LoanProductCode> = new Set([
  'LOAN_AGAINST_PROPERTY',
  'LOAN_AGAINST_SECURITIES',
  'LOAN_AGAINST_GOLD',
])

export function isSecuredLoanProductCode(code: string): boolean {
  return isLoanProductCode(code) && SECURED_LOAN_PRODUCT_CODES.has(code)
}
