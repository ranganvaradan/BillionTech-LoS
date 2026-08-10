import { useMemo, useState } from 'react'
import type { ScorecardParamDef } from '@/lib/credit/scorecardConfig'
import { parametersForSource, SCORECARD_SOURCES } from '@/lib/credit/scorecardConfig'

function readManualValue(manual: Record<string, unknown> | undefined, key: string): string {
  const cell = manual?.[key] as { value?: unknown } | undefined
  if (cell?.value == null) return ''
  return String(cell.value)
}

export function readMetricsMap(manual: Record<string, unknown> | undefined): Record<string, string> {
  const raw = manual?.scorecardMetrics as Record<string, { value?: unknown }> | undefined
  if (!raw) return {}
  const out: Record<string, string> = {}
  for (const [k, v] of Object.entries(raw)) {
    if (v?.value != null) out[k] = String(v.value)
  }
  return out
}

/** Top-level manual keys that buildScorecardMetricsPayload reads from the values map. */
export const KNOWN_SCORECARD_MANUAL_KEYS = new Set([
  'avgDailyBalance3m',
  'avgMonthlyTransactions3m',
  'avgMonthlySettlements3m',
  'monthlyTransactions3m',
  'inwardChequeReturns3m',
  'avgDailySettlements3m',
  'noOfTxns60days',
  'txnMth1',
  'txnMth2',
  'txnMth3',
  'annualBankingTurnover',
  'bankingTurnoverPctGst',
  'abbObligationMultiple',
  'ccUtilisationPct',
  'chequeBounces12m',
  'chequeBounces3m',
  'avgGmv3m',
  'active90days',
  'annualGstTurnover',
  'itrIncome',
  'liveUnsecuredLoanCount',
  'bureauEnquiries3m',
  'ntcFlag',
  'residenceOwned',
  'residenceStability',
  'businessStability',
  'existingLoanTrackRecordAll',
  'existingLoanTrackRecord15d',
  'qrTxnEDI',
  'eligibleOnePointFiveX',
  'pat',
  'existingFbLimits',
  'existingNfbLimits',
  'debtToEquity',
  'ebitda',
  'debtService',
  'tolTnw',
  'officeOwned',
  'dscr',
  'interestCoverage',
])

export function buildCustomMetricsFromRequirements(
  values: Record<string, string>,
  requirements: { manualKey: string }[],
): Record<string, string> {
  const out: Record<string, string> = {}
  for (const req of requirements) {
    if (KNOWN_SCORECARD_MANUAL_KEYS.has(req.manualKey)) continue
    const v = values[req.manualKey]?.trim()
    if (v) out[req.manualKey] = v
  }
  return out
}

export function readScorecardManualInputValue(
  manual: Record<string, unknown> | undefined,
  manualKey: string,
): string {
  const fromMetrics = readMetricsMap(manual)[manualKey]
  if (fromMetrics != null && fromMetrics !== '') return fromMetrics
  return readManualValue(manual, manualKey)
}

const COLLECTION_SOURCES = ['OTHER'] as const

type Props = {
  manual: Record<string, unknown> | undefined
  values: Record<string, string>
  onChange: (key: string, value: string) => void
  customMetrics: Record<string, string>
  onCustomChange: (key: string, value: string) => void
  onAddCustom: (key: string) => void
  onRemoveCustom: (key: string) => void
}

function FieldInput({
  param,
  value,
  onChange,
}: {
  param: ScorecardParamDef
  value: string
  onChange: (v: string) => void
}) {
  if (param.type === 'yesno') {
    return (
      <select className="bt-input w-full text-sm" value={value || ''} onChange={(e) => onChange(e.target.value)}>
        <option value="">—</option>
        <option value="Y">Yes</option>
        <option value="N">No</option>
      </select>
    )
  }
  return (
    <input
      type="number"
      className="bt-input w-full text-sm"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      step="any"
    />
  )
}

export function ScorecardMetricsManualSection({
  manual,
  values,
  onChange,
  customMetrics,
  onCustomChange,
  onAddCustom,
  onRemoveCustom,
}: Props) {
  const [newCustomKey, setNewCustomKey] = useState('')

  const groups = useMemo(
    () =>
      COLLECTION_SOURCES.map((source) => ({
        source,
        label: SCORECARD_SOURCES.find((s) => s.value === source)?.label ?? source,
        params: parametersForSource(source),
      })),
    [],
  )

  return (
    <div className="space-y-4">
      {groups.map((g) => (
        <div key={g.source} className="rounded-md border border-slate-200 bg-white p-3">
          <h4 className="text-sm font-semibold text-slate-800">{g.label}</h4>
          <p className="mb-2 text-xs text-slate-500">Used when scorecard rows reference source {g.source}.</p>
          <div className="grid gap-2 sm:grid-cols-2">
            {g.params.map((p) => {
              const key = p.manualKey ?? p.value
              return (
                <label
                  key={`${g.source}-${p.value}`}
                  id={`manual-credit-metric-${key}`}
                  className="block scroll-mt-28 text-xs text-slate-600"
                >
                  {p.label}
                  <FieldInput param={p} value={values[key] ?? readManualValue(manual, key)} onChange={(v) => onChange(key, v)} />
                </label>
              )
            })}
          </div>
        </div>
      ))}

      <div className="rounded-md border border-dashed border-slate-300 bg-white p-3">
        <h4 className="text-sm font-semibold text-slate-800">Custom scorecard parameters</h4>
        <p className="mb-2 text-xs text-slate-500">
          Add any extra parameter codes defined on the scorecard (OTHER source). Values are used at underwriting.
        </p>
        <div className="mb-2 flex flex-wrap gap-2">
          <input
            className="bt-input text-sm"
            placeholder="Parameter code"
            value={newCustomKey}
            onChange={(e) => setNewCustomKey(e.target.value)}
          />
          <button
            type="button"
            className="bt-btn bt-btn-ghost bt-btn-sm"
            onClick={() => {
              const k = newCustomKey.trim()
              if (!k) return
              onAddCustom(k)
              setNewCustomKey('')
            }}
          >
            Add field
          </button>
        </div>
        <div className="space-y-2">
          {Object.entries({ ...readMetricsMap(manual), ...customMetrics }).map(([key, val]) => (
            <div key={key} className="flex flex-wrap items-center gap-2">
              <span className="min-w-[8rem] font-mono text-xs text-slate-700">{key}</span>
              <input
                className="bt-input flex-1 text-sm"
                value={customMetrics[key] ?? val}
                onChange={(e) => onCustomChange(key, e.target.value)}
              />
              <button type="button" className="text-xs text-rose-600" onClick={() => onRemoveCustom(key)}>
                Remove
              </button>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

export function buildScorecardMetricsPayload(
  values: Record<string, string>,
  customMetrics: Record<string, string>,
): Record<string, unknown> {
  const num = (k: string) => {
    const v = values[k]?.trim()
    if (!v) return undefined
    const n = Number.parseFloat(v)
    return Number.isNaN(n) ? undefined : n
  }
  const yn = (k: string) => {
    const v = values[k]?.trim()
    return v ? v.toUpperCase() : undefined
  }
  const metrics: Record<string, string | number> = {}
  for (const [k, v] of Object.entries(customMetrics)) {
    if (!k.trim() || !v.trim()) continue
    const n = Number.parseFloat(v)
    metrics[k.trim()] = Number.isNaN(n) ? v.trim() : n
  }
  return {
    avgDailyBalance3m: num('avgDailyBalance3m'),
    avgMonthlyTransactions3m: num('avgMonthlyTransactions3m'),
    avgMonthlySettlements3m: num('avgMonthlySettlements3m'),
    monthlyTransactions3m: num('monthlyTransactions3m'),
    inwardChequeReturns3m: num('inwardChequeReturns3m'),
    avgDailySettlements3m: num('avgDailySettlements3m'),
    noOfTxns60days: num('noOfTxns60days'),
    txnMth1: num('txnMth1'),
    txnMth2: num('txnMth2'),
    txnMth3: num('txnMth3'),
    annualBankingTurnover: num('annualBankingTurnover'),
    bankingTurnoverPctGst: num('bankingTurnoverPctGst'),
    abbObligationMultiple: num('abbObligationMultiple'),
    ccUtilisationPct: num('ccUtilisationPct'),
    chequeBounces12m: num('chequeBounces12m'),
    chequeBounces3m: num('chequeBounces3m'),
    avgGmv3m: num('avgGmv3m'),
    active90days: num('active90days'),
    annualGstTurnover: num('annualGstTurnover'),
    itrIncome: num('itrIncome'),
    liveUnsecuredLoanCount: num('liveUnsecuredLoanCount'),
    bureauEnquiries3m: num('bureauEnquiries3m'),
    ntcFlag: yn('ntcFlag'),
    residenceOwned: yn('residenceOwned'),
    residenceStability: num('residenceStability'),
    businessStability: num('businessStability'),
    existingLoanTrackRecordAll: yn('existingLoanTrackRecordAll'),
    existingLoanTrackRecord15d: yn('existingLoanTrackRecord15d'),
    qrTxnEDI: yn('qrTxnEDI'),
    eligibleOnePointFiveX: yn('eligibleOnePointFiveX'),
    pat: num('pat'),
    existingFbLimits: num('existingFbLimits'),
    existingNfbLimits: num('existingNfbLimits'),
    debtToEquity: num('debtToEquity'),
    ebitda: num('ebitda'),
    debtService: num('debtService'),
    tolTnw: num('tolTnw'),
    officeOwned: yn('officeOwned'),
    dscr: num('dscr'),
    interestCoverage: num('interestCoverage'),
    ...(Object.keys(metrics).length ? { scorecardMetrics: metrics } : {}),
  }
}
