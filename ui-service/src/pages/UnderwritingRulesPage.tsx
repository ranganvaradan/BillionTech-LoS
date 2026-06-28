import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  activateUnderwritingRule,
  createUnderwritingRule,
  deactivateUnderwritingRule,
  deleteUnderwritingRule,
  listUnderwritingRules,
  updateUnderwritingRule,
  type UnderwritingRuleSetRequest,
  type UnderwritingRuleSetResponse,
} from '@/api/underwritingRules'
import { ApiError } from '@/api/http'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { PageHeader } from '@/components/PageHeader'
import {
  DetailEmptyState,
  DetailPanel,
  DetailSection,
  MasterDetailLayout,
  MasterListItem,
  MasterListPanel,
} from '@/components/ui/AdminLayout'
import { BORROWER_TYPE_LABELS, BORROWER_TYPE_ORDER } from '@/catalog/borrowerTypes'
import { isLoanProductCode, LOAN_PRODUCT_CODES, LOAN_PRODUCT_LABELS, loanProductLabel } from '@/catalog/loanProducts'
import type { BorrowerType } from '@/types/createApplication'

const BORROWER_TYPES: BorrowerType[] = [...BORROWER_TYPE_ORDER]
const DECISIONS = ['APPROVE', 'REJECT', 'MANUAL_REVIEW'] as const

const SCORECARD_PARAMS = [
  { value: 'GST_INCOME', label: 'GST income' },
  { value: 'BANK_STATEMENT_INCOME', label: 'Bank statement income' },
  { value: 'EMI_OBLIGATION', label: 'EMI / monthly obligation' },
  { value: 'AVERAGE_BANK_BALANCE', label: 'Average bank balance' },
  { value: 'OBLIGATION_RATIO', label: 'Obligation ratio' },
  { value: 'MONTHLY_INCOME', label: 'Monthly income' },
  { value: 'BUREAU_SCORE', label: 'Bureau score' },
] as const

type ScoreRow = {
  parameter: string
  weight: string
  mode: 'GTE' | 'LTE'
  approveAt: string
  manualAt: string
}

export function UnderwritingRulesPage() {
  const [rows, setRows] = useState<UnderwritingRuleSetResponse[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState<UnderwritingRuleSetResponse | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [toggling, setToggling] = useState(false)
  const [listSearch, setListSearch] = useState('')

  const [name, setName] = useState('')
  const [borrowerType, setBorrowerType] = useState<BorrowerType>('INDIVIDUAL')
  const [loanProduct, setLoanProduct] = useState('PERSONAL_LOAN')
  const [minAmount, setMinAmount] = useState('')
  const [maxAmount, setMaxAmount] = useState('')
  const [minTenure, setMinTenure] = useState('')
  const [maxTenure, setMaxTenure] = useState('')
  const [geoState, setGeoState] = useState('')
  const [geoCity, setGeoCity] = useState('')
  const [priority, setPriority] = useState('50')
  const [minBureau, setMinBureau] = useState('650')
  const [maxLoan, setMaxLoan] = useState('')
  const [requireKyc, setRequireKyc] = useState(true)
  const [decision, setDecision] = useState<(typeof DECISIONS)[number]>('MANUAL_REVIEW')
  const [reasonsText, setReasonsText] = useState('')
  const [scorecardRows, setScorecardRows] = useState<ScoreRow[]>([])

  const load = useCallback(async () => {
    setLoadError(null)
    setLoading(true)
    try {
      const rules = await listUnderwritingRules()
      setRows(rules)
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

  const filteredRows = useMemo(() => {
    const items = rows ?? []
    const q = listSearch.trim().toLowerCase()
    if (!q) return items
    return items.filter((r) => {
      const borrowerLabel = (BORROWER_TYPE_LABELS[r.borrowerType as BorrowerType] ?? r.borrowerType).toLowerCase()
      const productLabel = loanProductLabel(r.loanProduct).toLowerCase()
      return (
        r.name.toLowerCase().includes(q)
        || r.borrowerType.toLowerCase().includes(q)
        || borrowerLabel.includes(q)
        || r.loanProduct.toLowerCase().includes(q)
        || productLabel.includes(q)
        || String(r.priority).includes(q)
      )
    })
  }, [rows, listSearch])

  function applyRule(r: UnderwritingRuleSetResponse) {
    setSelected(r)
    setIsCreating(false)
    setName(r.name)
    setBorrowerType(r.borrowerType as BorrowerType)
    setLoanProduct(r.loanProduct)
    setMinAmount(r.minAmount != null ? String(r.minAmount) : '')
    setMaxAmount(r.maxAmount != null ? String(r.maxAmount) : '')
    setMinTenure(r.minTenureMonths != null ? String(r.minTenureMonths) : '')
    setMaxTenure(r.maxTenureMonths != null ? String(r.maxTenureMonths) : '')
    const g = r.geography
    setGeoState(g && typeof g.state === 'string' ? g.state : '')
    setGeoCity(g && typeof g.city === 'string' ? g.city : '')
    setPriority(String(r.priority))
    const j = r.rulesJson || {}
    setMinBureau(j.minBureauScore != null ? String(j.minBureauScore) : '650')
    setMaxLoan(j.maxLoanAmount != null ? String(j.maxLoanAmount) : '')
    setRequireKyc(j.requireKycSuccess !== false)
    const d = typeof j.decision === 'string' ? j.decision.toUpperCase() : 'MANUAL_REVIEW'
    setDecision(DECISIONS.includes(d as (typeof DECISIONS)[number]) ? (d as (typeof DECISIONS)[number]) : 'MANUAL_REVIEW')
    const rs = j.reasons
    setReasonsText(Array.isArray(rs) ? rs.map(String).join('\n') : '')
    const sc = (j as { scorecardRules?: unknown }).scorecardRules
    if (Array.isArray(sc) && sc.length > 0) {
      setScorecardRows(
        (sc as Record<string, unknown>[]).map((row) => ({
          parameter: String(row.parameter ?? 'OBLIGATION_RATIO'),
          weight: String(row.weight ?? 10),
          mode: (String(row.mode ?? 'GTE').toUpperCase() === 'LTE' ? 'LTE' : 'GTE') as 'GTE' | 'LTE',
          approveAt: row.approveAt != null ? String(row.approveAt) : '',
          manualAt: row.manualAt != null ? String(row.manualAt) : '',
        })),
      )
    } else {
      setScorecardRows([])
    }
    setActionError(null)
  }

  function startNew() {
    setSelected(null)
    setIsCreating(true)
    setName('New rule set')
    setBorrowerType('INDIVIDUAL')
    setLoanProduct(LOAN_PRODUCT_CODES[0] ?? 'PERSONAL_LOAN')
    setMinAmount('')
    setMaxAmount('')
    setMinTenure('')
    setMaxTenure('')
    setGeoState('')
    setGeoCity('')
    setPriority('50')
    setMinBureau('650')
    setMaxLoan('')
    setRequireKyc(true)
    setDecision('MANUAL_REVIEW')
    setReasonsText('Policy check')
    setScorecardRows([])
    setActionError(null)
  }

  function buildRequest(): UnderwritingRuleSetRequest {
    const scRules = scorecardRows
      .filter((r) => r.parameter && r.weight.trim() && (r.approveAt.trim() || r.manualAt.trim()))
      .map((r) => {
        const w = Number.parseInt(r.weight, 10)
        return {
          parameter: r.parameter,
          weight: Number.isNaN(w) ? 0 : w,
          mode: r.mode,
          approveAt: Number(r.approveAt) || 0,
          manualAt: Number(r.manualAt) || 0,
        }
      })
    const reasons = reasonsText
      .split('\n')
      .map((s) => s.trim())
      .filter(Boolean)
    const geo: Record<string, unknown> | null =
      geoState.trim() || geoCity.trim()
        ? { ...(geoState.trim() ? { state: geoState.trim() } : {}), ...(geoCity.trim() ? { city: geoCity.trim() } : {}) }
        : null
    return {
      name: name.trim() || 'Rule set',
      borrowerType,
      loanProduct: loanProduct.trim(),
      minAmount: minAmount.trim() ? Number(minAmount) : null,
      maxAmount: maxAmount.trim() ? Number(maxAmount) : null,
      geography: geo && Object.keys(geo).length ? geo : null,
      minTenureMonths: minTenure.trim() ? Number.parseInt(minTenure, 10) : null,
      maxTenureMonths: maxTenure.trim() ? Number.parseInt(maxTenure, 10) : null,
      priority: Number.parseInt(priority, 10) || 0,
      rulesJson: {
        minBureauScore: minBureau.trim() ? Number.parseInt(minBureau, 10) : null,
        maxLoanAmount: maxLoan.trim() ? Number(maxLoan) : null,
        requireKycSuccess: requireKyc,
        decision,
        reasons,
        scorecardRules: scRules,
      },
    }
  }

  async function onSave() {
    setActionError(null)
    setSaving(true)
    try {
      const body = buildRequest()
      if (isCreating) {
        const c = await createUnderwritingRule(body)
        setRows((prev) => (prev ? [c, ...prev] : [c]))
        setIsCreating(false)
        applyRule(c)
      } else if (selected) {
        const u = await updateUnderwritingRule(selected.id, body)
        setRows((prev) => (prev ? prev.map((x) => (x.id === u.id ? u : x)) : [u]))
        applyRule(u)
      }
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : e instanceof Error ? e.message : 'Save failed')
    } finally {
      setSaving(false)
    }
  }

  async function onDelete() {
    if (!selected || selected.active) return
    if (!globalThis.confirm('Delete this inactive rule set?')) return
    setActionError(null)
    try {
      await deleteUnderwritingRule(selected.id)
      setRows((prev) => (prev ? prev.filter((x) => x.id !== selected.id) : []))
      setSelected(null)
      setIsCreating(false)
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : 'Delete failed')
    }
  }

  async function onActivate() {
    if (!selected) return
    setToggling(true)
    try {
      await activateUnderwritingRule(selected.id)
      const fresh = await listUnderwritingRules()
      setRows(fresh)
      const u = fresh.find((x) => x.id === selected.id)
      if (u) applyRule(u)
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Activate failed')
    } finally {
      setToggling(false)
    }
  }

  async function onDeactivate() {
    if (!selected) return
    setToggling(true)
    try {
      await deactivateUnderwritingRule(selected.id)
      const fresh = await listUnderwritingRules()
      setRows(fresh)
      const u = fresh.find((x) => x.id === selected.id)
      if (u) applyRule(u)
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Deactivate failed')
    } finally {
      setToggling(false)
    }
  }

  const showForm = selected !== null || isCreating

  return (
    <div>
      <PageHeader
        title="Underwriting rules"
        description="Credit Manager setup — multiple active rules may match the same application; each is evaluated and results are aggregated (any reject → reject; any manual review → manual review; all approve → approve). Higher priority is still used for ordering."
      />
      {loading && <LoadingState label="Loading…" />}
      {loadError && <ErrorState message={loadError} />}
      {rows && !loading && (
        <MasterDetailLayout>
          <MasterListPanel
            title="Rule sets"
            count={filteredRows.length}
            search={listSearch}
            onSearchChange={setListSearch}
            searchPlaceholder="Search rule sets…"
            action={
              <button type="button" onClick={startNew} className="bt-btn bt-btn-primary bt-btn-sm">
                New rule set
              </button>
            }
            empty={
              filteredRows.length === 0 && !isCreating ? (
                <div className="bt-master-list-empty">
                  {rows.length === 0 ? 'No rule sets yet.' : 'No rule sets match your search.'}
                </div>
              ) : undefined
            }
          >
            {filteredRows.map((r) => (
              <MasterListItem
                key={r.id}
                active={selected?.id === r.id && !isCreating}
                onClick={() => applyRule(r)}
                avatar={r.name}
                title={r.name}
                subtitle={`Priority ${r.priority}`}
                meta={
                  r.active ? (
                    <span className="bt-badge bt-badge-green">Active</span>
                  ) : (
                    <span className="bt-badge bt-badge-gray">Inactive</span>
                  )
                }
                tags={
                  <>
                    <span className="bt-tag">{BORROWER_TYPE_LABELS[r.borrowerType as BorrowerType] ?? r.borrowerType}</span>
                    <span className="bt-tag">{loanProductLabel(r.loanProduct)}</span>
                  </>
                }
              />
            ))}
          </MasterListPanel>
          <div>
            {showForm ? (
              <DetailPanel
                title={isCreating ? 'New rule set' : name}
                description="Define match criteria and decision rules. Multiple active sets may apply; results are aggregated."
                badge={
                  !isCreating && selected ? (
                    selected.active ? (
                      <span className="bt-badge bt-badge-green">Active</span>
                    ) : (
                      <span className="bt-badge bt-badge-gray">Inactive</span>
                    )
                  ) : undefined
                }
              >
                {actionError ? (
                  <p className="bt-alert bt-alert-warning mb-4">{actionError}</p>
                ) : null}
                <DetailSection title="Match criteria">
                <div className="grid gap-2 sm:grid-cols-2">
                  <label className="sm:col-span-2 text-sm text-slate-700">
                    <span className="mb-0.5 block text-xs text-slate-500">Name</span>
                    <input
                      className="bt-input w-full"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                    />
                  </label>
                  <label className="text-sm text-slate-700">
                    <span className="mb-0.5 block text-xs text-slate-500">Borrower type</span>
                    <select
                      className="w-full rounded border border-slate-300 bg-white px-2 py-1.5"
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
                  <label className="text-sm text-slate-700">
                    <span className="mb-0.5 block text-xs text-slate-500">Loan product</span>
                    <select
                      className="w-full rounded border border-slate-300 bg-white px-2 py-1.5"
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
                  <label className="text-sm text-slate-700">
                    <span className="mb-0.5 block text-xs text-slate-500">Min amount</span>
                    <input
                      className="bt-input w-full"
                      value={minAmount}
                      onChange={(e) => setMinAmount(e.target.value)}
                      inputMode="decimal"
                    />
                  </label>
                  <label className="text-sm text-slate-700">
                    <span className="mb-0.5 block text-xs text-slate-500">Max amount</span>
                    <input
                      className="bt-input w-full"
                      value={maxAmount}
                      onChange={(e) => setMaxAmount(e.target.value)}
                      inputMode="decimal"
                    />
                  </label>
                  <label className="text-sm text-slate-700">
                    <span className="mb-0.5 block text-xs text-slate-500">Min tenure (months)</span>
                    <input
                      className="bt-input w-full"
                      value={minTenure}
                      onChange={(e) => setMinTenure(e.target.value.replace(/\D/g, ''))}
                    />
                  </label>
                  <label className="text-sm text-slate-700">
                    <span className="mb-0.5 block text-xs text-slate-500">Max tenure (months)</span>
                    <input
                      className="bt-input w-full"
                      value={maxTenure}
                      onChange={(e) => setMaxTenure(e.target.value.replace(/\D/g, ''))}
                    />
                  </label>
                  <label className="text-sm text-slate-700">
                    <span className="mb-0.5 block text-xs text-slate-500">Geography — state (optional)</span>
                    <input
                      className="bt-input w-full"
                      value={geoState}
                      onChange={(e) => setGeoState(e.target.value)}
                      placeholder="e.g. KA"
                    />
                  </label>
                  <label className="text-sm text-slate-700">
                    <span className="mb-0.5 block text-xs text-slate-500">Geography — city (optional)</span>
                    <input
                      className="bt-input w-full"
                      value={geoCity}
                      onChange={(e) => setGeoCity(e.target.value)}
                    />
                  </label>
                  <label className="text-sm text-slate-700 sm:col-span-2">
                    <span className="mb-0.5 block text-xs text-slate-500">Priority (higher = first)</span>
                    <input
                      className="w-full max-w-xs rounded border border-slate-300 px-2 py-1.5"
                      value={priority}
                      onChange={(e) => setPriority(e.target.value.replace(/\D/g, ''))}
                    />
                  </label>
                </div>
                </DetailSection>

                <DetailSection title="Policy rules">
                <div className="rounded border border-slate-200 bg-slate-50/80 p-3">
                  <p className="mb-2 text-xs font-medium text-slate-600">Policy (rulesJson)</p>
                  <div className="grid gap-2 sm:grid-cols-2">
                    <label className="text-sm text-slate-700">
                      <span className="mb-0.5 block text-xs text-slate-500">Min bureau score</span>
                      <input
                        className="bt-input w-full"
                        value={minBureau}
                        onChange={(e) => setMinBureau(e.target.value.replace(/\D/g, ''))}
                      />
                    </label>
                    <label className="text-sm text-slate-700">
                      <span className="mb-0.5 block text-xs text-slate-500">Max loan amount (cap)</span>
                      <input
                        className="bt-input w-full"
                        value={maxLoan}
                        onChange={(e) => setMaxLoan(e.target.value)}
                        inputMode="decimal"
                      />
                    </label>
                    <label className="flex items-center gap-2 text-sm text-slate-700 sm:col-span-2">
                      <input
                        type="checkbox"
                        checked={requireKyc}
                        onChange={(e) => setRequireKyc(e.target.checked)}
                      />
                      Require KYC PASS
                    </label>
                    <label className="text-sm text-slate-700 sm:col-span-2">
                      <span className="mb-0.5 block text-xs text-slate-500">Decision (if policy checks pass)</span>
                      <select
                        className="w-full max-w-sm rounded border border-slate-300 bg-white px-2 py-1.5"
                        value={decision}
                        onChange={(e) => setDecision(e.target.value as (typeof DECISIONS)[number])}
                      >
                        {DECISIONS.map((d) => (
                          <option key={d} value={d}>
                            {d}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label className="text-sm text-slate-700 sm:col-span-2">
                      <span className="mb-0.5 block text-xs text-slate-500">Reasons (one per line)</span>
                      <textarea
                        className="h-20 bt-input w-full font-mono text-xs"
                        value={reasonsText}
                        onChange={(e) => setReasonsText(e.target.value)}
                      />
                    </label>
                  </div>
                </div>
                <div className="rounded border border-indigo-200 bg-indigo-50/50 p-3">
                  <p className="mb-2 text-xs font-medium text-slate-700">Scorecard (optional, overrides policy decision if non-empty)</p>
                  <p className="mb-2 text-xs text-slate-500">
                    Per row: <strong>parameter</strong> from effective scorecard, <strong>weight</strong>, <strong>mode</strong>{' '}
                    (GTE = higher is better, LTE = lower is better, e.g. ratio), and numeric{' '}
                    <strong>approve</strong> / <strong>manual</strong> thresholds. Highest weighted bucket (approve, manual, reject)
                    wins.
                  </p>
                  <div className="mb-2 space-y-2">
                    {scorecardRows.map((row, idx) => (
                      <div
                        key={idx}
                        className="grid gap-1 rounded border border-indigo-100 bg-white/90 p-2 sm:grid-cols-2 lg:grid-cols-5"
                      >
                        <label className="text-xs sm:col-span-2">
                          Parameter
                          <select
                            className="mt-0.5 w-full rounded border border-slate-300 bg-white px-1 py-1"
                            value={row.parameter}
                            onChange={(e) => {
                              const v = e.target.value
                              setScorecardRows((p) => p.map((x, i) => (i === idx ? { ...x, parameter: v } : x)))
                            }}
                          >
                            {SCORECARD_PARAMS.map((o) => (
                              <option key={o.value} value={o.value}>
                                {o.label}
                              </option>
                            ))}
                          </select>
                        </label>
                        <label className="text-xs">
                          Weight
                          <input
                            className="mt-0.5 w-full rounded border border-slate-300 px-1 py-1"
                            value={row.weight}
                            onChange={(e) =>
                              setScorecardRows((p) => p.map((x, i) => (i === idx ? { ...x, weight: e.target.value } : x)))
                            }
                          />
                        </label>
                        <label className="text-xs">
                          Mode
                          <select
                            className="mt-0.5 w-full rounded border border-slate-300 bg-white px-1 py-1"
                            value={row.mode}
                            onChange={(e) =>
                              setScorecardRows((p) =>
                                p.map((x, i) =>
                                  i === idx ? { ...x, mode: e.target.value as 'GTE' | 'LTE' } : x,
                                ),
                              )
                            }
                          >
                            <option value="GTE">GTE (≥ = better)</option>
                            <option value="LTE">LTE (≤ = better)</option>
                          </select>
                        </label>
                        <label className="text-xs">
                          Approve at
                          <input
                            className="mt-0.5 w-full rounded border border-slate-300 px-1 py-1"
                            value={row.approveAt}
                            onChange={(e) =>
                              setScorecardRows((p) => p.map((x, i) => (i === idx ? { ...x, approveAt: e.target.value } : x)))
                            }
                          />
                        </label>
                        <label className="text-xs">
                          Manual at
                          <input
                            className="mt-0.5 w-full rounded border border-slate-300 px-1 py-1"
                            value={row.manualAt}
                            onChange={(e) =>
                              setScorecardRows((p) => p.map((x, i) => (i === idx ? { ...x, manualAt: e.target.value } : x)))
                            }
                          />
                        </label>
                        <div className="sm:col-span-2 flex items-end lg:col-span-1">
                          <button
                            type="button"
                            className="text-xs text-rose-700 underline"
                            onClick={() => setScorecardRows((p) => p.filter((_, i) => i !== idx))}
                          >
                            Remove row
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                  <button
                    type="button"
                    className="rounded border border-indigo-400 bg-indigo-50 px-2 py-1 text-xs text-indigo-900"
                    onClick={() =>
                      setScorecardRows((p) => [
                        ...p,
                        {
                          parameter: 'OBLIGATION_RATIO',
                          weight: '20',
                          mode: 'LTE',
                          approveAt: '0.4',
                          manualAt: '0.55',
                        },
                      ])
                    }
                  >
                    + Add scorecard row
                  </button>
                </div>
                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    onClick={() => void onSave()}
                    disabled={saving}
                    className="bt-btn bt-btn-primary disabled:opacity-50"
                  >
                    {saving ? 'Saving…' : isCreating ? 'Create' : 'Save'}
                  </button>
                  {isCreating ? (
                    <button
                      type="button"
                      onClick={() => {
                        setIsCreating(false)
                        setSelected(null)
                      }}
                      className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm"
                    >
                      Cancel
                    </button>
                  ) : null}
                  {!isCreating && selected && (
                    <>
                      {!selected.active ? (
                        <button
                          type="button"
                          disabled={toggling}
                          onClick={() => void onActivate()}
                          className="rounded-md border border-emerald-600 bg-emerald-50 px-3 py-1.5 text-sm text-emerald-900"
                        >
                          Activate
                        </button>
                      ) : (
                        <button
                          type="button"
                          disabled={toggling}
                          onClick={() => void onDeactivate()}
                          className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm"
                        >
                          Deactivate
                        </button>
                      )}
                      {!selected.active ? (
                        <button
                          type="button"
                          onClick={() => void onDelete()}
                          className="rounded-md border border-rose-300 bg-rose-50 px-3 py-1.5 text-sm text-rose-900"
                        >
                          Delete
                        </button>
                      ) : null}
                    </>
                  )}
                </div>
                {!isCreating && selected ? (
                  <p className="mt-3 text-xs text-slate-500">Id: {selected.id}</p>
                ) : null}
                </DetailSection>
              </DetailPanel>
            ) : (
              <DetailEmptyState
                title="Select a rule set"
                description="Choose a rule set from the list to edit its match criteria and policy rules, or create a new one."
                action={
                  <button type="button" onClick={startNew} className="bt-btn bt-btn-primary">
                    New rule set
                  </button>
                }
              />
            )}
          </div>
        </MasterDetailLayout>
      )}
    </div>
  )
}
