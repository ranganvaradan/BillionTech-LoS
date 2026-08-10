/**
 * Human-readable map: scorecard parameter → how LOS can source it.
 * Shown in Application → Underwriting for credit manager orientation (not a second policy engine).
 */
export const SCORECARD_PARAMETER_SOURCE_HELP: Record<
  string,
  { label: string; sources: string[] }
> = {
  BUREAU_SCORE: {
    label: 'Bureau score',
    sources: ['Equifax (or configured bureau) pull', 'Manual bureau override', 'Bureau report document'],
  },
  MONTHLY_INCOME: {
    label: 'Monthly income',
    sources: [
      'Application / borrower financials',
      'Manual credit input (income = MANUAL)',
      'Bank statement (declared in manual inputs)',
    ],
  },
  ANNUAL_GST_TURNOVER: {
    label: 'Annual GST turnover',
    sources: [
      'GST analysis report (Karza docs-upload-advance)',
      'GST return / GSTR OCR extract',
      'Server gap default when no GST document',
    ],
  },
  ITR_INCOME: {
    label: 'ITR income',
    sources: ['ITR document OCR extract', 'Server gap default when no ITR document'],
  },
  GST_INCOME: {
    label: 'GST / assessed business income',
    sources: [
      'GST analysis report mapped metrics',
      'GST return OCR extract',
      'Server gap default when no GST document',
    ],
  },
  BANK_STATEMENT_INCOME: {
    label: 'Bank statement income',
    sources: ['Account aggregator (when wired)', 'Bank statement document', 'Manual bankStatementIncome'],
  },
  AVERAGE_BANK_BALANCE: {
    label: 'Average bank balance',
    sources: ['Bank statement / AA', 'Manual averageBankBalance'],
  },
  OBLIGATION_RATIO: {
    label: 'Obligation / FOIR ratio',
    sources: [
      'System: monthly obligation ÷ income (from effective credit control)',
      'Manual obligationRatio (overrides ratio when set)',
    ],
  },
  EMI_OBLIGATION: {
    label: 'Monthly EMI / obligation',
    sources: ['Application financials', 'Manual emiObligation / monthlyObligation'],
  },
  PROPERTY_VALUE: {
    label: 'Property / collateral value',
    sources: ['Valuation report document', 'Manual propertyValue (collateral section when available)'],
  },
  LTV: {
    label: 'Loan-to-value',
    sources: [
      'System: requested amount ÷ property value (when both present)',
      'Manual ltv',
    ],
  },
  KYC_QUALITY: {
    label: 'KYC quality / outcome',
    sources: [
      'KYC workflow outcome (provider)',
      'Manual KYC outcome when KYC source = MANUAL',
    ],
  },
  KYC_PASS: {
    label: 'KYC pass (legacy row)',
    sources: ['Provider KYC outcome', 'Manual KYC when selected'],
  },
  BUSINESS_VINTAGE_MONTHS: {
    label: 'Business vintage (months)',
    sources: ['Business proof / GST', 'Manual businessVintageMonths'],
  },
  DEPENDENCY_VINTAGE_PERCENT: {
    label: 'Dependency vintage (%)',
    sources: ['Invoice discounting borrower intake', 'Program inputs scorecard source'],
  },
  ANCHOR_RELATIONSHIP_VINTAGE_MONTHS: {
    label: 'Anchor relationship vintage (months)',
    sources: ['Invoice discounting borrower intake', 'Program inputs scorecard source'],
  },
  PROGRAM_DEPENDENCY_VINTAGE_PERCENT: {
    label: 'Program min dependency vintage (%)',
    sources: ['PLP / LOS program configuration'],
  },
  PROGRAM_ANCHOR_RELATIONSHIP_VINTAGE_MONTHS: {
    label: 'Program min anchor vintage (months)',
    sources: ['PLP / LOS program configuration'],
  },
  INDUSTRY_RISK: {
    label: 'Industry risk band',
    sources: ['Credit note / policy', 'Manual industryRisk (LOW / MED / HIGH)'],
  },
  LEVERAGE_RATIO: {
    label: 'Leverage ratio',
    sources: ['Financial statements', 'Manual leverageRatio'],
  },
  EBITDA_PROXY: {
    label: 'EBITDA (proxy)',
    sources: ['Financials upload', 'Manual ebitdaProxy'],
  },
  REPAYMENT_HISTORY: {
    label: 'Repayment / conduct',
    sources: ['Bank statement / bureau', 'Manual repaymentHistory (CLEAN / …)'],
  },
  REQUESTED_AMOUNT: { label: 'Requested loan amount', sources: ['Application'] },
  TENURE_MONTHS: { label: 'Tenure', sources: ['Application'] },
  AGE: {
    label: 'Applicant age (years)',
    sources: ['Date of birth collected at application intake (computed at underwriting)'],
  },
  OCCUPATION: {
    label: 'Occupation',
    sources: [
      'Occupation code collected at application intake',
      'Scorecard rows: EQ / MATCH_OPTION against that code',
    ],
  },
  LOAN_PURPOSE: {
    label: 'Loan purpose',
    sources: [
      'Loan purpose code collected at application intake',
      'Scorecard rows: EQ / MATCH_OPTION against that code',
    ],
  },
  avgDailyBalance3m: {
    label: 'Avg daily balance (3m)',
    sources: ['Bank statement analytics', 'Manual credit → Scorecard metrics'],
  },
  avgGmv3m: {
    label: 'Avg GMV (3m)',
    sources: ['GST return OCR extract', 'Server gap default when no GST document'],
  },
  ANNUAL_BANKING_TURNOVER: {
    label: 'Annual banking turnover',
    sources: ['Bank statement analytics / OCR extract', 'Server gap default when no bank statement document'],
  },
  BANKING_TURNOVER_PCT_GST: {
    label: 'Banking turnover % of GST',
    sources: ['Bank statement analytics', 'Manual credit → bankingTurnoverPctGst'],
  },
  ABB_OBLIGATION_MULTIPLE: {
    label: 'ABB / obligation multiple',
    sources: ['Bank statement analytics', 'Manual credit → abbObligationMultiple'],
  },
  CC_UTILISATION_PCT: {
    label: 'CC utilisation (%)',
    sources: ['Bank / CC statement', 'Manual credit → ccUtilisationPct'],
  },
  CHEQUE_BOUNCES_12M: {
    label: 'Cheque bounces (12m)',
    sources: ['Bank statement analytics', 'Manual credit → chequeBounces12m'],
  },
  CHEQUE_BOUNCES_3M: {
    label: 'Cheque bounces (3m)',
    sources: ['Bank statement analytics', 'Manual credit → chequeBounces3m'],
  },
  LIVE_UNSECURED_LOAN_COUNT: {
    label: 'Live unsecured loan count',
    sources: ['Bureau pull', 'Manual credit → liveUnsecuredLoanCount'],
  },
  BUREAU_ENQUIRIES_3M: {
    label: 'Bureau enquiries (3m)',
    sources: ['Bureau pull', 'Manual credit → bureauEnquiries3m'],
  },
  NTC_FLAG: {
    label: 'NTC flag',
    sources: ['Bureau pull', 'Manual credit → ntcFlag'],
  },
  PAT: {
    label: 'PAT',
    sources: ['Financial statements / ITR', 'Manual credit → pat'],
  },
  DSCR: {
    label: 'DSCR',
    sources: ['Computed / financials', 'Manual credit → dscr'],
  },
  INTEREST_COVERAGE: {
    label: 'Interest coverage',
    sources: ['Computed / financials', 'Manual credit → interestCoverage'],
  },
  DEBT_TO_EQUITY: {
    label: 'Debt to equity',
    sources: ['Financial statements', 'Manual credit → debtToEquity'],
  },
  EBITDA: {
    label: 'EBITDA',
    sources: ['ITR / financial statements', 'Manual credit → ebitda'],
  },
  DEBT_SERVICE: {
    label: 'Debt service',
    sources: ['ITR / financial statements', 'Manual credit → debtService'],
  },
  TOL: {
    label: 'Total outside liability (TOL)',
    sources: ['ITR OCR extract'],
  },
  TNW: {
    label: 'Tangible net worth (TNW)',
    sources: ['ITR OCR extract'],
  },
  TOL_TNW: {
    label: 'TOL / TNW',
    sources: ['Financial statements', 'Manual credit → tolTnw'],
  },
  residenceOwned: {
    label: 'Residence owned',
    sources: ['Manual credit → Scorecard metrics (OTHER)'],
  },
  officeOwned: {
    label: 'Office owned',
    sources: ['Manual credit → Scorecard metrics (OTHER)'],
  },
  eligibleOnePointFiveX: {
    label: 'Eligible 1.5×',
    sources: ['Manual credit → Scorecard metrics (OTHER)'],
  },
  SCF_STANDARD_LIMIT: {
    label: 'SCF standard limit',
    sources: ['System: min(25% of annual GST turnover, ₹50L)'],
  },
  SCF_AMOUNT_OVER_STANDARD: {
    label: 'Amount over standard SCF limit',
    sources: ['System: requested amount − standard limit (0 when within policy)'],
  },
  SCF_MAX_DEVIATION_LIMIT: {
    label: 'SCF max deviation limit',
    sources: ['Policy constant: ₹1 Cr special deviation cap'],
  },
}

export function helpForParameter(parameter: string | undefined | null) {
  if (!parameter) {
    return { label: '—', sources: [] as string[] }
  }
  return (
    SCORECARD_PARAMETER_SOURCE_HELP[parameter] ?? {
      label: parameter.replace(/_/g, ' '),
      sources: ['See effective scorecard context (credit control) or add manual under Manual credit inputs'],
    }
  )
}
