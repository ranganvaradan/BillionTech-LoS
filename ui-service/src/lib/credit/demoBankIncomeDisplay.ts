import type { ApplicationResponse } from '@/types/application'

/** Safe demo figures for underwriting preview only (not persisted). FOIR &lt; 40%, positive surplus. */
export const DEMO_BANK_INCOME = {
  monthlyIncome: 85_000,
  averageBankBalance: 120_000,
  emiObligations: 15_000,
  netDisposableIncome: 70_000,
  cashFlowStability: 'STABLE',
  incomeType: 'SALARIED',
  bankingVintageMonths: 24,
  foirPercent: 25,
  creditWorthiness: 'GOOD',
} as const

function str(v: unknown): string | null {
  if (v == null) return null
  const s = String(v).trim()
  return s === '' ? null : s
}

function numFromUnknown(v: unknown): number | null {
  if (v == null) return null
  if (typeof v === 'number' && !Number.isNaN(v)) return v
  const s = String(v).trim().replace(/,/g, '')
  if (s === '') return null
  const n = Number.parseFloat(s)
  return Number.isFinite(n) ? n : null
}

function manualCellValue(manual: Record<string, unknown> | null | undefined, key: string): string | null {
  if (!manual) return null
  const cell = manual[key]
  if (cell && typeof cell === 'object' && 'value' in (cell as object)) {
    return str((cell as { value?: unknown }).value)
  }
  return str(cell)
}

function hasPositiveManualOrFi(
  fi: Record<string, unknown> | null,
  manual: Record<string, unknown> | null | undefined,
  manualKey: string,
  fiKey: string,
): boolean {
  const m = numFromUnknown(manualCellValue(manual, manualKey))
  if (m != null && m > 0) return true
  const f = numFromUnknown(fi?.[fiKey])
  return f != null && f > 0
}

/** True when a bank-statement-derived income figure exists (manual layer or financialInfo). */
export function hasBankStatementIncomeSignal(app: ApplicationResponse): boolean {
  const fi = (app.financialInfo ?? null) as Record<string, unknown> | null
  const cc = fi?.creditControl as Record<string, unknown> | undefined
  const manual =
    cc && typeof cc === 'object' && cc.manual && typeof cc.manual === 'object'
      ? (cc.manual as Record<string, unknown>)
      : null
  return hasPositiveManualOrFi(fi, manual, 'bankStatementIncome', 'bankStatementIncome')
}

/**
 * When a bank statement is on file but no extracted / entered bank income line exists,
 * the UI may show read-only demo values for policy preview (does not replace persisted data).
 */
export function shouldShowDemoBankIncomePreview(app: ApplicationResponse, bankStatementOnFile: boolean): boolean {
  if (!bankStatementOnFile) return false
  return !hasBankStatementIncomeSignal(app)
}

function inr(n: number): string {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(n)
}

/**
 * Merges non-destructive demo lines into {@code incomeSummary} for missing bank / ratio context.
 */
export function mergeDemoIncomeSummaryLines(
  incomeSummary: Record<string, string>,
  app: ApplicationResponse,
  bankStatementOnFile: boolean,
): void {
  if (!shouldShowDemoBankIncomePreview(app, bankStatementOnFile)) return

  const d = DEMO_BANK_INCOME
  const demoTag = ' (demo preview — bank extraction unavailable)'

  const hasLine = (fragment: string) => {
    const f = fragment.toLowerCase()
    return Object.keys(incomeSummary).some(
      (k) => k.toLowerCase().includes(f) && !k.toLowerCase().includes('demo preview'),
    )
  }

  if (!hasLine('monthly income') && !incomeSummary['Declared monthly income']) {
    incomeSummary[`Estimated monthly income${demoTag}`] = inr(d.monthlyIncome)
  }
  if (!hasLine('Bank statement income')) {
    incomeSummary[`Bank statement income${demoTag}`] = inr(d.monthlyIncome)
  }
  if (!hasLine('Average bank balance')) {
    incomeSummary[`Average bank balance${demoTag}`] = inr(d.averageBankBalance)
  }
  if (!hasLine('Obligation') && !incomeSummary['Obligation / ratio (layer)']) {
    incomeSummary[`EMI / obligations (est.)${demoTag}`] = inr(d.emiObligations)
    incomeSummary[`Net disposable income (est.)${demoTag}`] = inr(d.netDisposableIncome)
    incomeSummary[`FOIR % (est.)${demoTag}`] = `${d.foirPercent}%`
  }
  incomeSummary[`Cashflow stability${demoTag}`] = d.cashFlowStability
  incomeSummary[`Income type (est.)${demoTag}`] = d.incomeType
  incomeSummary[`Banking vintage (months, est.)${demoTag}`] = String(d.bankingVintageMonths)
  incomeSummary[`Credit worthiness (demo label)${demoTag}`] = d.creditWorthiness
}
