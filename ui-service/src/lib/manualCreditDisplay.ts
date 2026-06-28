import type { ApplicationResponse } from '@/types/application'
import { DEMO_BANK_INCOME, shouldShowDemoBankIncomePreview } from '@/lib/credit/demoBankIncomeDisplay'

function str(x: unknown): string {
  if (x == null) return ''
  if (typeof x === 'string' || typeof x === 'number') return String(x)
  if (typeof x === 'object' && 'value' in (x as object)) {
    const v = (x as { value?: unknown }).value
    return v != null ? String(v) : ''
  }
  return String(x)
}

function money(n: unknown): string {
  if (n == null || n === '') return '—'
  const d = Number(n)
  if (Number.isNaN(d)) return '—'
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(d)
}

/**
 * Read-only "provider / application" snapshot for the manual credit form (not raw JSON keys).
 */
export function providerSnapshotForManualForm(
  app: ApplicationResponse,
  opts?: { bankStatementOnFile?: boolean },
) {
  const pi = app.personalInfo ?? {}
  const fi = app.financialInfo ?? {}
  const view = (app.creditControlView ?? {}) as {
    effective?: Record<string, unknown>
    providerBureauScore?: number | null
  }
  const eff = view.effective ?? {}
  const bankOnFile = opts?.bankStatementOnFile ?? false
  const demoIncome = shouldShowDemoBankIncomePreview(app, bankOnFile)

  let monthlyIncome = money(fi.monthlyIncome)
  if (demoIncome && (!monthlyIncome || monthlyIncome === '—')) {
    monthlyIncome = money(DEMO_BANK_INCOME.monthlyIncome)
  }
  let monthlyObligation = money(fi.monthlyObligation ?? fi.obligation)
  if (demoIncome && (!monthlyObligation || monthlyObligation === '—')) {
    monthlyObligation = money(DEMO_BANK_INCOME.emiObligations)
  }

  return {
    panName: str(pi.panName ?? pi.name ?? pi.fullName),
    aadhaarName: str(pi.name ?? pi.fullName),
    monthlyIncome,
    monthlyObligation,
    bureauScore:
      app.bureauScore != null && app.bureauScore > 0
        ? String(app.bureauScore)
        : view.providerBureauScore != null
          ? String(view.providerBureauScore)
          : '—',
    city: str(pi.city ?? (eff.effectiveCity as string | undefined)),
    state: str(pi.state ?? (eff.effectiveState as string | undefined)),
    effectiveBureau: str(eff.effectiveBureauScore),
    effectiveIncome: str(eff.effectiveIncome),
    effectiveObligation: str(eff.effectiveObligation),
  }
}
