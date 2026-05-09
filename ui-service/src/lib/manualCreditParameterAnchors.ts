/**
 * Maps scorecard parameter codes (underwriting parameter results) to stable DOM ids on
 * {@link ManualCreditInputsSection} rows, so "Add manual input" can deep-link.
 */
const SCORECARD_PARAM_TO_ROW_ID: Record<string, string> = {
  BUREAU_SCORE: 'manual-credit-bureau-score',
  MONTHLY_INCOME: 'manual-credit-monthly-income',
  GST_INCOME: 'manual-credit-gst-income',
  BANK_STATEMENT_INCOME: 'manual-credit-bank-statement-income',
  AVERAGE_BANK_BALANCE: 'manual-credit-average-bank-balance',
  OBLIGATION_RATIO: 'manual-credit-obligation-ratio',
  EMI_OBLIGATION: 'manual-credit-emi-obligation',
  MONTHLY_OBLIGATION: 'manual-credit-monthly-obligation',
  KYC_QUALITY: 'manual-credit-kyc-outcome',
  KYC_PASS: 'manual-credit-kyc-outcome',
  /** Loan terms — no dedicated row; scroll to manual credit section top. */
  REQUESTED_AMOUNT: 'manual-credit-inputs',
  TENURE_MONTHS: 'manual-credit-inputs',
  PROPERTY_VALUE: 'manual-credit-property-value',
  LTV: 'manual-credit-ltv',
  BUSINESS_VINTAGE_MONTHS: 'manual-credit-business-vintage',
  INDUSTRY_RISK: 'manual-credit-industry-risk',
  REPAYMENT_HISTORY: 'manual-credit-repayment-history',
  LEVERAGE_RATIO: 'manual-credit-leverage-ratio',
  EBITDA_PROXY: 'manual-credit-ebitda-proxy',
  STATE: 'manual-credit-state',
  CITY: 'manual-credit-city',
}

/** Hash including leading `#` for use in `href`. */
function normalizeParameterKey(raw: string): string {
  return raw
    .trim()
    .toUpperCase()
    .replace(/[\s-]+/g, '_')
    .replace(/_+/g, '_')
}

export function manualCreditHashForScorecardParameter(parameter: string | undefined | null): string {
  if (!parameter) return '#manual-credit-inputs'
  const k = normalizeParameterKey(parameter)
  const id = SCORECARD_PARAM_TO_ROW_ID[k]
  return id ? `#${id}` : '#manual-credit-inputs'
}
