import { useCallback, useEffect, useState } from 'react'
import {
  createScorecard,
  deleteScorecard,
  listScorecards,
  updateScorecard,
  type HardRuleRow,
  type ScorecardRow,
  type UnderwritingScorecardRequest,
  type UnderwritingScorecardResponse,
} from '@/api/scorecards'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import { BORROWER_TYPE_LABELS, BORROWER_TYPE_ORDER } from '@/catalog/borrowerTypes'
import { isLoanProductCode, LOAN_PRODUCT_CODES, LOAN_PRODUCT_LABELS, loanProductLabel } from '@/catalog/loanProducts'
import type { BorrowerType } from '@/types/createApplication'

const BORROWER_TYPES: BorrowerType[] = [...BORROWER_TYPE_ORDER]
const PARAM_OPTIONS = [
  'BUREAU_SCORE',
  'KYC_PASS',
  'REQUESTED_AMOUNT',
  'TENURE_MONTHS',
  'MONTHLY_INCOME',
  'MONTHLY_OBLIGATION',
  'DTI_RATIO',
] as const
const SOURCE_OPTIONS = ['BUREAU', 'KYC', 'APPLICATION', 'CONTEXT', 'SCORECARD'] as const
const EMPTY_PARAM = '— custom —'
let rid = 0
function newRow(): ScorecardRow {
  rid += 1
  return { id: `r${Date.now()}-${rid}`, parameter: 'BUREAU_SCORE', source: 'BUREAU', condition: 'GTE:650', weight: 1, score: 20 }
}
function newHard(): HardRuleRow {
  rid += 1
  return { id: `h${Date.now()}-${rid}`, parameter: 'BUREAU_SCORE', source: 'BUREAU', condition: 'LT:500', decision: 'REJECT' }
}

export function ScorecardsPage() {
  const [rows, setRows] = useState<UnderwritingScorecardResponse[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState<UnderwritingScorecardResponse | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [saving, setSaving] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  const [name, setName] = useState('')
  const [borrowerType, setBorrowerType] = useState<BorrowerType>('INDIVIDUAL')
  const [loanProduct, setLoanProduct] = useState('PERSONAL_LOAN')
  const [version, setVersion] = useState(1)
  const [priority, setPriority] = useState(200)
  const [minAmount, setMinAmount] = useState('')
  const [maxAmount, setMaxAmount] = useState('')
  const [geoState, setGeoState] = useState('')
  const [geoCity, setGeoCity] = useState('')
  const [active, setActive] = useState(true)
  const [approveMin, setApproveMin] = useState(70)
  const [manualMin, setManualMin] = useState(40)
  const [grid, setGrid] = useState<ScorecardRow[]>([newRow()])
  const [hards, setHards] = useState<HardRuleRow[]>([])

  const load = useCallback(async () => {
    setLoadError(null)
    setLoading(true)
    try {
      const s = await listScorecards()
      setRows(s)
    } catch (e) {
      setRows(null)
      setLoadError(e instanceof Error ? e.message : 'Failed to load')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async load
    void load()
  }, [load])

  function apply(r: UnderwritingScorecardResponse) {
    setSelected(r)
    setIsCreating(false)
    setName(r.name)
    setBorrowerType(r.borrowerType as BorrowerType)
    setLoanProduct(r.loanProduct)
    setVersion(r.version)
    setPriority(r.priority)
    setMinAmount(r.minAmount != null ? String(r.minAmount) : '')
    setMaxAmount(r.maxAmount != null ? String(r.maxAmount) : '')
    const g = r.geography
    setGeoState(g && typeof g.state === 'string' ? g.state : '')
    setGeoCity(g && typeof g.city === 'string' ? g.city : '')
    setActive(r.active)
    const t = r.thresholdsJson || {}
    setApproveMin(typeof t.approveMinPercent === 'number' ? t.approveMinPercent : 70)
    setManualMin(typeof t.manualMinPercent === 'number' ? t.manualMinPercent : 40)
    const sc = r.scorecardJson || {}
    const rawRows = (sc as { rows?: unknown }).rows
    if (Array.isArray(rawRows) && rawRows.length) {
      setGrid(
        (rawRows as Record<string, unknown>[]).map((x, i) => ({
          id: String(x.id ?? `e${i}`),
          parameter: String(x.parameter ?? 'BUREAU_SCORE'),
          source: String(x.source ?? 'BUREAU'),
          condition: String(x.condition ?? 'GTE:650'),
          weight: Number(x.weight) || 1,
          score: Number(x.score) || 0,
          attachment: x.attachment != null ? String(x.attachment) : undefined,
        })),
      )
    } else {
      setGrid([newRow()])
    }
    const hr = (r.hardRulesJson as { rules?: unknown })?.rules
    if (Array.isArray(hr) && hr.length) {
      setHards(
        (hr as Record<string, unknown>[]).map((x, i) => ({
          id: String(x.id ?? `h${i}`),
          parameter: String(x.parameter ?? 'BUREAU_SCORE'),
          source: String(x.source ?? 'BUREAU'),
          condition: String(x.condition ?? 'LT:500'),
          decision: String(x.decision) === 'MANUAL_REVIEW' ? 'MANUAL_REVIEW' : 'REJECT',
          message: x.message != null ? String(x.message) : undefined,
        })),
      )
    } else {
      setHards([])
    }
    setActionError(null)
  }

  function startNew() {
    setSelected(null)
    setIsCreating(true)
    setName('New scorecard')
    setBorrowerType('INDIVIDUAL')
    setLoanProduct(LOAN_PRODUCT_CODES[0] ?? 'PERSONAL_LOAN')
    setVersion(1)
    setPriority(200)
    setMinAmount('')
    setMaxAmount('')
    setGeoState('')
    setGeoCity('')
    setActive(true)
    setApproveMin(70)
    setManualMin(40)
    setGrid([newRow(), newRow()])
    setHards([newHard()])
    setActionError(null)
  }

  function toRequest(): UnderwritingScorecardRequest {
    const minA = minAmount.trim() ? Number.parseFloat(minAmount) : null
    const maxA = maxAmount.trim() ? Number.parseFloat(maxAmount) : null
    const geo: Record<string, unknown> | null =
      geoState.trim() || geoCity.trim()
        ? { ...(geoState.trim() ? { state: geoState.trim() } : {}), ...(geoCity.trim() ? { city: geoCity.trim() } : {}) }
        : null
    return {
      name: name.trim() || 'Scorecard',
      borrowerType,
      loanProduct: loanProduct.trim(),
      version: Math.max(1, version),
      priority,
      minAmount: minA != null && !Number.isNaN(minA) ? minA : null,
      maxAmount: maxA != null && !Number.isNaN(maxA) ? maxA : null,
      geography: geo,
      scorecardJson: {
        rows: grid.map((r) => ({
          id: r.id,
          parameter: r.parameter,
          source: r.source,
          condition: r.condition.trim(),
          weight: r.weight,
          score: r.score,
          ...(r.attachment?.trim() ? { attachment: r.attachment.trim() } : {}),
        })),
      },
      thresholdsJson: { approveMinPercent: approveMin, manualMinPercent: manualMin },
      hardRulesJson: {
        rules: hards.map((h) => ({
          parameter: h.parameter,
          source: h.source,
          condition: h.condition,
          decision: h.decision,
          ...(h.message?.trim() ? { message: h.message.trim() } : {}),
        })),
      },
      active,
    }
  }

  async function onSave() {
    setActionError(null)
    setSaving(true)
    try {
      const body = toRequest()
      if (isCreating) {
        const c = await createScorecard(body)
        setIsCreating(false)
        setSelected(c)
        setRows((await listScorecards()) ?? null)
      } else if (selected) {
        const u = await updateScorecard(selected.id, body)
        setSelected(u)
        setRows((await listScorecards()) ?? null)
      }
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Save failed')
    } finally {
      setSaving(false)
    }
  }

  async function onDelete() {
    if (!selected) return
    if (!window.confirm('Delete this scorecard? It must be inactive.')) return
    setActionError(null)
    try {
      await deleteScorecard(selected.id)
      setSelected(null)
      setRows((await listScorecards()) ?? null)
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Delete failed')
    }
  }

  return (
    <div>
      <PageHeader
        title="Underwriting scorecards"
        description="Structured parameter scoring, hard rules, and thresholds. Matching scorecards run before rule sets for the same borrower type and loan product (priority: higher first)."
      />
      <p className="mb-2 text-sm text-slate-600">
        <button type="button" onClick={() => void load()} className="font-medium text-slate-800 underline">
          Refresh
        </button>
        {' · '}
        <button type="button" onClick={startNew} className="font-medium text-slate-800 underline">
          New scorecard
        </button>
      </p>

      {loading && <LoadingState label="Loading…" />}
      {loadError && <ErrorState message={loadError} />}
      {actionError && <p className="text-sm text-rose-700">{actionError}</p>}

      {rows && !loading && (
        <div className="mb-4 overflow-x-auto rounded border border-slate-200">
          <table className="bt-table min-w-full">
            <thead className="bg-slate-50 text-xs text-slate-500">
              <tr>
                <th className="p-2">Name</th>
                <th className="p-2">Segment</th>
                <th className="p-2">Prio</th>
                <th className="p-2">V</th>
                <th className="p-2">Active</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr
                  key={r.id}
                  onClick={() => apply(r)}
                  onKeyDown={(e) => e.key === 'Enter' && apply(r)}
                  role="button"
                  tabIndex={0}
                  className={
                    r.id === selected?.id
                      ? 'cursor-pointer bg-indigo-50/80'
                      : 'cursor-pointer border-t border-slate-100 '
                  }
                >
                  <td className="p-2 font-medium text-slate-900">{r.name}</td>
                  <td className="p-2 text-slate-700">
                    {BORROWER_TYPE_LABELS[r.borrowerType as BorrowerType] ?? r.borrowerType} /{' '}
                    {loanProductLabel(r.loanProduct)}
                  </td>
                  <td className="p-2 font-mono">{r.priority}</td>
                  <td className="p-2 font-mono">{r.version}</td>
                  <td className="p-2">{r.active ? 'yes' : 'no'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {(isCreating || selected) && (
        <div className="space-y-4 rounded-lg border border-slate-200 bg-white p-4 text-sm">
          <div className="grid gap-2 sm:grid-cols-2">
            <label className="block">
              <span className="text-xs text-slate-500">Name</span>
              <input className="mt-0.5 bt-input w-full" value={name} onChange={(e) => setName(e.target.value)} />
            </label>
            <div className="grid grid-cols-2 gap-2">
              <label className="block">
                <span className="text-xs text-slate-500">Version</span>
                <input
                  type="number"
                  className="mt-0.5 bt-input w-full"
                  value={version}
                  onChange={(e) => setVersion(Number(e.target.value) || 1)}
                />
              </label>
              <label className="block">
                <span className="text-xs text-slate-500">Priority</span>
                <input
                  type="number"
                  className="mt-0.5 bt-input w-full"
                  value={priority}
                  onChange={(e) => setPriority(Number(e.target.value) || 0)}
                />
              </label>
            </div>
            <label className="block">
              <span className="text-xs text-slate-500">Borrower</span>
              <select
                className="mt-0.5 bt-input w-full"
                value={borrowerType}
                onChange={(e) => setBorrowerType(e.target.value as BorrowerType)}
              >
                {BORROWER_TYPES.map((b) => (
                  <option key={b} value={b}>
                    {BORROWER_TYPE_LABELS[b]}
                  </option>
                ))}
              </select>
            </label>
            <label className="block">
              <span className="text-xs text-slate-500">Loan product</span>
              <select
                className="mt-0.5 w-full border bg-white px-2 py-1"
                value={loanProduct}
                onChange={(e) => setLoanProduct(e.target.value)}
              >
                {LOAN_PRODUCT_CODES.map((c) => (
                  <option key={c} value={c}>
                    {LOAN_PRODUCT_LABELS[c]}
                  </option>
                ))}
                {loanProduct && !isLoanProductCode(loanProduct) ? (
                  <option value={loanProduct}>{loanProductLabel(loanProduct)} (legacy)</option>
                ) : null}
              </select>
            </label>
            <label className="block">
              <span className="text-xs text-slate-500">Min amount</span>
              <input className="mt-0.5 bt-input w-full" value={minAmount} onChange={(e) => setMinAmount(e.target.value)} />
            </label>
            <label className="block">
              <span className="text-xs text-slate-500">Max amount</span>
              <input className="mt-0.5 bt-input w-full" value={maxAmount} onChange={(e) => setMaxAmount(e.target.value)} />
            </label>
            <label className="block sm:col-span-2">
              <span className="text-xs text-slate-500">Geography (optional)</span>
              <div className="mt-0.5 flex flex-wrap gap-2">
                <input className="border px-2 py-1" placeholder="State" value={geoState} onChange={(e) => setGeoState(e.target.value)} />
                <input className="border px-2 py-1" placeholder="City" value={geoCity} onChange={(e) => setGeoCity(e.target.value)} />
              </div>
            </label>
            <label className="inline-flex items-center gap-2 sm:col-span-2">
              <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
              Active
            </label>
          </div>

          <div>
            <h3 className="bt-card-title">Thresholds (% of max points, 0–100)</h3>
            <div className="mt-2 flex flex-wrap gap-4">
              <label>
                <span className="text-xs text-slate-500">Approve at ≥</span>
                <input
                  type="number"
                  className="ml-1 w-20 border px-2 py-1"
                  value={approveMin}
                  onChange={(e) => setApproveMin(Number(e.target.value) || 0)}
                />
              </label>
              <label>
                <span className="text-xs text-slate-500">Manual at ≥</span>
                <input
                  type="number"
                  className="ml-1 w-20 border px-2 py-1"
                  value={manualMin}
                  onChange={(e) => setManualMin(Number(e.target.value) || 0)}
                />
              </label>
            </div>
          </div>

          <div>
            <div className="flex items-center justify-between">
              <h3 className="bt-card-title">Parameters</h3>
              <button
                type="button"
                className="text-xs text-indigo-800 underline"
                onClick={() => setGrid((g) => [...g, newRow()])}
              >
                + Add row
              </button>
            </div>
            <div className="mt-2 overflow-x-auto">
              <table className="min-w-full text-xs">
                <thead>
                  <tr className="text-left text-slate-500">
                    <th className="p-1">Parameter</th>
                    <th className="p-1">Source</th>
                    <th className="p-1">Condition</th>
                    <th className="p-1">W</th>
                    <th className="p-1">Pts</th>
                    <th className="p-1">Attachment</th>
                    <th className="p-1" />
                  </tr>
                </thead>
                <tbody>
                  {grid.map((r, i) => (
                    <tr key={r.id} className="border-t border-slate-100">
                      <td className="p-1">
                        <select
                          className="w-full min-w-[8rem] border px-1 py-0.5"
                          value={
                            PARAM_OPTIONS.includes(r.parameter as (typeof PARAM_OPTIONS)[number])
                              ? r.parameter
                              : EMPTY_PARAM
                          }
                          onChange={(e) => {
                            const v = e.target.value
                            setGrid((g) =>
                              g.map((x, j) => (j === i ? { ...x, parameter: v === EMPTY_PARAM ? '' : v } : x)),
                            )
                          }}
                        >
                          <option value={EMPTY_PARAM}>— custom —</option>
                          {PARAM_OPTIONS.map((p) => (
                            <option key={p} value={p}>
                              {p}
                            </option>
                          ))}
                        </select>
                        {(!PARAM_OPTIONS.includes(r.parameter as (typeof PARAM_OPTIONS)[number]) || r.parameter === '') && (
                          <input
                            className="mt-0.5 w-full border px-1 py-0.5 font-mono"
                            value={r.parameter}
                            onChange={(e) =>
                              setGrid((g) => g.map((x, j) => (j === i ? { ...x, parameter: e.target.value } : x)))
                            }
                            placeholder="e.g. GST_TURNOVER for SCORECARD"
                          />
                        )}
                      </td>
                      <td className="p-1">
                        <select
                          className="w-full min-w-[6rem] border px-1 py-0.5"
                          value={r.source}
                          onChange={(e) => setGrid((g) => g.map((x, j) => (j === i ? { ...x, source: e.target.value } : x)))}
                        >
                          {SOURCE_OPTIONS.map((s) => (
                            <option key={s} value={s}>
                              {s}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td className="p-1">
                        <input
                          className="w-full min-w-[6rem] border px-1 py-0.5 font-mono"
                          value={r.condition}
                          onChange={(e) =>
                            setGrid((g) => g.map((x, j) => (j === i ? { ...x, condition: e.target.value } : x)))
                          }
                          title="GTE:650, LT:500, EQ:1, BETWEEN:1:10"
                        />
                      </td>
                      <td className="p-1">
                        <input
                          type="number"
                          className="w-12 border px-1 py-0.5"
                          value={r.weight}
                          onChange={(e) =>
                            setGrid((g) => g.map((x, j) => (j === i ? { ...x, weight: Number(e.target.value) || 0 } : x)))
                          }
                        />
                      </td>
                      <td className="p-1">
                        <input
                          type="number"
                          className="w-12 border px-1 py-0.5"
                          value={r.score}
                          onChange={(e) =>
                            setGrid((g) => g.map((x, j) => (j === i ? { ...x, score: Number(e.target.value) || 0 } : x)))
                          }
                        />
                      </td>
                      <td className="p-1">
                        <input
                          className="w-full min-w-[6rem] border px-1 py-0.5"
                          value={r.attachment ?? ''}
                          onChange={(e) =>
                            setGrid((g) => g.map((x, j) => (j === i ? { ...x, attachment: e.target.value } : x)))
                          }
                          placeholder="e.g. BUREAU_REPORT"
                        />
                      </td>
                      <td className="p-1">
                        <button
                          type="button"
                          className="text-rose-700"
                          onClick={() => setGrid((g) => g.filter((_, j) => j !== i))}
                        >
                          ×
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="mt-1 text-xs text-slate-500">Conditions: GTE:n, GT:n, LTE:n, LT:n, EQ:1, BETWEEN:a:b. Source SCORECARD uses parameter as context key.</p>
          </div>

          <div>
            <div className="flex items-center justify-between">
              <h3 className="bt-card-title">Hard rules (override first)</h3>
              <button
                type="button"
                className="text-xs text-indigo-800 underline"
                onClick={() => setHards((h) => [...h, newHard()])}
              >
                + Add
              </button>
            </div>
            <div className="mt-2 space-y-1">
              {hards.map((h, i) => (
                <div key={h.id} className="flex flex-wrap items-center gap-1 text-xs">
                  <select
                    className="border px-1 py-0.5"
                    value={h.parameter}
                    onChange={(e) => setHards((a) => a.map((x, j) => (j === i ? { ...x, parameter: e.target.value } : x)))}
                  >
                    {PARAM_OPTIONS.map((p) => (
                      <option key={p} value={p}>
                        {p}
                      </option>
                    ))}
                  </select>
                  <select
                    className="border px-1 py-0.5"
                    value={h.source}
                    onChange={(e) => setHards((a) => a.map((x, j) => (j === i ? { ...x, source: e.target.value } : x)))}
                  >
                    {SOURCE_OPTIONS.map((s) => (
                      <option key={s} value={s}>
                        {s}
                      </option>
                    ))}
                  </select>
                  <input
                    className="w-32 border px-1 py-0.5 font-mono"
                    value={h.condition}
                    onChange={(e) => setHards((a) => a.map((x, j) => (j === i ? { ...x, condition: e.target.value } : x)))}
                  />
                  <select
                    className="border px-1 py-0.5"
                    value={h.decision}
                    onChange={(e) =>
                      setHards((a) =>
                        a.map((x, j) =>
                          j === i
                            ? { ...x, decision: e.target.value as HardRuleRow['decision'] }
                            : x,
                        ),
                      )
                    }
                  >
                    <option value="REJECT">REJECT</option>
                    <option value="MANUAL_REVIEW">MANUAL</option>
                  </select>
                  <input
                    className="min-w-[8rem] flex-1 border px-1 py-0.5"
                    value={h.message ?? ''}
                    onChange={(e) => setHards((a) => a.map((x, j) => (j === i ? { ...x, message: e.target.value } : x)))}
                    placeholder="message"
                  />
                  <button type="button" className="text-rose-700" onClick={() => setHards((a) => a.filter((_, j) => j !== i))}>
                    ×
                  </button>
                </div>
              ))}
            </div>
          </div>

          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => void onSave()}
              disabled={saving}
              className="bt-btn bt-btn-primary disabled:opacity-50"
            >
              {saving ? 'Saving…' : 'Save'}
            </button>
            {selected && !isCreating ? (
              <button type="button" onClick={() => void onDelete()} className="rounded-md border border-rose-300 px-3 py-1.5 text-rose-800">
                Delete
              </button>
            ) : null}
          </div>
        </div>
      )}
    </div>
  )
}
