import { useEffect, useMemo, useState } from 'react'
import {
  getLifecycleSettings,
  saveLifecycleDraft,
  type StagingPolicyStudio,
} from '@/api/creditIntelligence'
import { ApiError } from '@/api/http'
import { CiExecutiveSummary, CiSection } from '@/components/creditIntelligence/CiSection'
import { loanProductLabel } from '@/catalog/loanProducts'

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

type Opt = { value: string; label: string }

function optionList(raw: unknown): Opt[] {
  return asList(raw).map((row) => {
    const r = asRecord(row)
    return { value: String(r.value ?? ''), label: String(r.label ?? r.value ?? '') }
  }).filter((o) => o.value)
}

function formatInrInput(n: string): string {
  const digits = n.replace(/[^\d]/g, '')
  if (!digits) return ''
  try {
    return Number(digits).toLocaleString('en-IN')
  } catch {
    return digits
  }
}

function parseAmount(raw: string): string | null {
  const digits = raw.replace(/[^\d]/g, '')
  return digits ? digits : null
}

export function CiPolicyScopeTab({
  documentId,
  busy,
  setBusy,
  onError,
  onSessionRefresh,
  onScopeDirty,
  session,
}: {
  documentId: string
  busy: boolean
  setBusy: (v: boolean) => void
  onError: (msg: string | null) => void
  onSessionRefresh?: (next?: StagingPolicyStudio) => void
  onScopeDirty?: (dirty: boolean) => void
  session?: StagingPolicyStudio | null
}) {
  const [loading, setLoading] = useState(true)
  const [settings, setSettings] = useState<Record<string, unknown> | null>(null)
  const [productMode, setProductMode] = useState<'ALL' | 'INCLUDE'>('ALL')
  const [selectedProducts, setSelectedProducts] = useState<string[]>([])
  const [borrowerType, setBorrowerType] = useState('')
  const [customerSegment, setCustomerSegment] = useState('')
  const [minAmount, setMinAmount] = useState('')
  const [maxAmount, setMaxAmount] = useState('')
  const [effectiveFrom, setEffectiveFrom] = useState('')
  const [effectiveUntil, setEffectiveUntil] = useState('')
  const [noEndDate, setNoEndDate] = useState(true)
  const [localDirty, setLocalDirty] = useState(false)
  const [savedMsg, setSavedMsg] = useState<string | null>(null)

  const markDirty = () => {
    setLocalDirty(true)
    onScopeDirty?.(true)
    setSavedMsg(null)
  }

  const hydrate = (s: Record<string, unknown>) => {
    const hdr = asRecord(s.policySettings)
    const app = asRecord(s.applicability ?? hdr)
    const products = asList(app.products ?? hdr.products).map(String).filter(Boolean)
    if (products.length === 0) {
      setProductMode('ALL')
      setSelectedProducts([])
    } else {
      setProductMode('INCLUDE')
      setSelectedProducts(products)
    }
    setBorrowerType(app.borrowerType ? String(app.borrowerType) : '')
    setCustomerSegment(app.customerSegment ? String(app.customerSegment) : '')
    setMinAmount(app.minLoanAmount != null && String(app.minLoanAmount) !== ''
      ? formatInrInput(String(app.minLoanAmount)) : '')
    setMaxAmount(app.maxLoanAmount != null && String(app.maxLoanAmount) !== ''
      ? formatInrInput(String(app.maxLoanAmount)) : '')
    setEffectiveFrom(app.effectiveFrom ? String(app.effectiveFrom).slice(0, 10) : '')
    if (app.effectiveUntil) {
      setEffectiveUntil(String(app.effectiveUntil).slice(0, 10))
      setNoEndDate(false)
    } else {
      setEffectiveUntil('')
      setNoEndDate(true)
    }
  }

  const reload = async () => {
    setLoading(true)
    onError(null)
    try {
      const s = await getLifecycleSettings(documentId)
      setSettings(s)
      hydrate(s)
      setLocalDirty(false)
      onScopeDirty?.(false)
    } catch (e) {
      onError(e instanceof ApiError ? e.message : 'Could not load policy scope')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void reload()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [documentId])

  const options = asRecord(settings?.scopeOptions)
  const productOpts = optionList(options.products)
  const borrowerOpts = optionList(options.borrowerTypes)
  const segmentOpts = optionList(options.customerSegments)
  const summary = asRecord(settings?.scopeSummary)
  const overlap = asRecord(settings?.overlapPreview)
  const coverage = asRecord(summary.coveragePreview)
  const advanced = asRecord(options.advanced)

  const body = () => ({
    products: productMode === 'ALL' ? [] : selectedProducts,
    borrowerType: borrowerType || null,
    customerSegment: customerSegment || null,
    minLoanAmount: parseAmount(minAmount),
    maxLoanAmount: parseAmount(maxAmount),
    effectiveFrom: effectiveFrom || null,
    effectiveUntil: noEndDate ? null : effectiveUntil || null,
    reasonForChange: 'Credit Manager scope draft',
  })

  const saveScope = async () => {
    const min = parseAmount(minAmount)
    const max = parseAmount(maxAmount)
    if (min && max && Number(min) > Number(max)) {
      onError('Minimum requested amount must be less than or equal to maximum.')
      return
    }
    setBusy(true)
    onError(null)
    try {
      const data = await saveLifecycleDraft(documentId, body())
      const life = asRecord(asRecord(data).lifecycle)
      if (Object.keys(life).length) {
        setSettings(life)
        hydrate(life)
      } else {
        await reload()
      }
      onSessionRefresh?.(data)
      setLocalDirty(false)
      onScopeDirty?.(false)
      setSavedMsg(String(asRecord(data).message ?? 'Scope saved as draft. Rules unchanged.'))
    } catch (e) {
      onError(e instanceof ApiError ? e.message : 'Could not save scope draft')
    } finally {
      setBusy(false)
    }
  }

  const liveSummary = useMemo(() => {
    if (summary.appliesTo) return String(summary.appliesTo)
    const parts: string[] = []
    if (productMode === 'ALL' || selectedProducts.length === 0) parts.push('All products')
    else parts.push(selectedProducts.map((p) => loanProductLabel(p) === '—' ? p : loanProductLabel(p)).join(', '))
    if (customerSegment) {
      parts.push(customerSegment === 'ANCHOR' ? 'Anchor applications' : 'Borrower applications')
    }
    parts.push(borrowerType
      ? (borrowerOpts.find((b) => b.value === borrowerType)?.label ?? borrowerType)
      : 'All borrower types')
    if (!minAmount && !maxAmount) parts.push('Any amount')
    else if (!minAmount) parts.push(`Up to ₹${maxAmount}`)
    else if (!maxAmount) parts.push(`Above ₹${minAmount}`)
    else parts.push(`₹${minAmount} – ₹${maxAmount}`)
    return parts.join(' · ')
  }, [summary.appliesTo, productMode, selectedProducts, customerSegment, borrowerType, borrowerOpts, minAmount, maxAmount])

  if (loading && !settings) {
    return <p className="text-sm text-slate-600">Loading policy scope…</p>
  }

  const toggleProduct = (code: string) => {
    markDirty()
    setProductMode('INCLUDE')
    setSelectedProducts((prev) =>
      prev.includes(code) ? prev.filter((p) => p !== code) : [...prev, code],
    )
  }

  return (
    <div className="space-y-4">
      <CiExecutiveSummary title="Policy scope">
        <p className="text-sm text-slate-700">Which applications should use this policy?</p>
        <div className="mt-3 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3">
          <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">Applies to</div>
          <div className="mt-1 text-base font-medium text-slate-900">{liveSummary}</div>
          {summary.effective ? (
            <div className="mt-1 text-sm text-slate-600">{String(summary.effective)}</div>
          ) : effectiveFrom ? (
            <div className="mt-1 text-sm text-slate-600">
              Effective {effectiveFrom}{noEndDate ? ' onward' : effectiveUntil ? ` to ${effectiveUntil}` : ''}
            </div>
          ) : (
            <div className="mt-1 text-sm text-slate-500">Draft scope — set an effective date before activation.</div>
          )}
        </div>
      </CiExecutiveSummary>

      {Boolean(overlap.potentialOverlap) ? (
        <div className="rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-950">
          <div className="font-semibold">Potential policy overlap</div>
          <p className="mt-1">{String(overlap.message)}</p>
          <ul className="mt-2 list-disc pl-5">
            {asList(overlap.conflicts).map((c, i) => {
              const row = asRecord(c)
              const existing = asRecord(row.existing)
              return (
                <li key={i}>
                  {String(existing.policyName ?? 'Policy')} {String(existing.policyVersion ?? '')}
                  {' — '}
                  {String(row.message ?? '')}
                </li>
              )
            })}
          </ul>
          <p className="mt-2 text-xs">Overlaps are not silently resolved. EXACTLY_ONE safety remains.</p>
        </div>
      ) : null}

      <CiSection title="Policy applies to" description="Include-only. Unset dimensions mean All. Save Draft anytime.">
        <div className="space-y-4">
          <fieldset>
            <legend className="text-sm font-medium text-slate-800">Product</legend>
            <div className="mt-2 flex flex-wrap gap-3 text-sm">
              <label className="inline-flex items-center gap-2">
                <input
                  type="radio"
                  name="productMode"
                  checked={productMode === 'ALL'}
                  disabled={busy}
                  onChange={() => {
                    markDirty()
                    setProductMode('ALL')
                    setSelectedProducts([])
                  }}
                />
                All products
              </label>
              <label className="inline-flex items-center gap-2">
                <input
                  type="radio"
                  name="productMode"
                  checked={productMode === 'INCLUDE'}
                  disabled={busy}
                  onChange={() => {
                    markDirty()
                    setProductMode('INCLUDE')
                  }}
                />
                Selected product(s)
              </label>
            </div>
            {productMode === 'INCLUDE' ? (
              <div className="mt-3 grid gap-2 sm:grid-cols-2">
                {productOpts.map((p) => (
                  <label key={p.value} className="inline-flex items-center gap-2 text-sm text-slate-700">
                    <input
                      type="checkbox"
                      checked={selectedProducts.includes(p.value)}
                      disabled={busy}
                      onChange={() => toggleProduct(p.value)}
                    />
                    {p.label}
                  </label>
                ))}
              </div>
            ) : null}
          </fieldset>

          <div className="grid gap-3 sm:grid-cols-2">
            <label className="text-sm">
              <span className="text-slate-600">Customer / borrower type</span>
              <select
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                value={borrowerType}
                disabled={busy}
                onChange={(e) => {
                  markDirty()
                  setBorrowerType(e.target.value)
                }}
              >
                <option value="">All</option>
                {borrowerOpts.map((o) => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
            </label>

            <label className="text-sm">
              <span className="text-slate-600">Application relationship</span>
              <select
                className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                value={customerSegment}
                disabled={busy}
                onChange={(e) => {
                  markDirty()
                  setCustomerSegment(e.target.value)
                }}
              >
                <option value="">All</option>
                {segmentOpts.map((o) => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
              <span className="mt-1 block text-xs text-slate-500">
                Anchor / Borrower intake role — not MSME/Corporate commercial segment.
              </span>
            </label>
          </div>

          <fieldset>
            <legend className="text-sm font-medium text-slate-800">Requested amount</legend>
            <div className="mt-2 grid gap-3 sm:grid-cols-2">
              <label className="text-sm">
                <span className="text-slate-600">Minimum (₹)</span>
                <input
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                  inputMode="numeric"
                  placeholder="Any"
                  value={minAmount}
                  disabled={busy}
                  onChange={(e) => {
                    markDirty()
                    setMinAmount(formatInrInput(e.target.value))
                  }}
                />
              </label>
              <label className="text-sm">
                <span className="text-slate-600">Maximum (₹)</span>
                <input
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                  inputMode="numeric"
                  placeholder="Any"
                  value={maxAmount}
                  disabled={busy}
                  onChange={(e) => {
                    markDirty()
                    setMaxAmount(formatInrInput(e.target.value))
                  }}
                />
              </label>
            </div>
          </fieldset>

          <fieldset>
            <legend className="text-sm font-medium text-slate-800">Effective period</legend>
            <div className="mt-2 grid gap-3 sm:grid-cols-2">
              <label className="text-sm">
                <span className="text-slate-600">From</span>
                <input
                  type="date"
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                  value={effectiveFrom}
                  disabled={busy}
                  onChange={(e) => {
                    markDirty()
                    setEffectiveFrom(e.target.value)
                  }}
                />
              </label>
              <label className="text-sm">
                <span className="text-slate-600">Until</span>
                <input
                  type="date"
                  className="mt-1 w-full rounded border border-slate-300 px-3 py-2"
                  value={effectiveUntil}
                  disabled={busy || noEndDate}
                  onChange={(e) => {
                    markDirty()
                    setEffectiveUntil(e.target.value)
                  }}
                />
                <label className="mt-2 inline-flex items-center gap-2 text-xs text-slate-600">
                  <input
                    type="checkbox"
                    checked={noEndDate}
                    disabled={busy}
                    onChange={(e) => {
                      markDirty()
                      setNoEndDate(e.target.checked)
                      if (e.target.checked) setEffectiveUntil('')
                    }}
                  />
                  No end date
                </label>
              </label>
            </div>
          </fieldset>
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-3">
          <button
            type="button"
            className="bt-btn bt-btn-primary bt-btn-sm"
            disabled={busy || !localDirty}
            onClick={() => void saveScope()}
          >
            Save Draft
          </button>
          {localDirty ? (
            <span className="text-xs text-amber-800">Scope has unsaved changes (rules are separate).</span>
          ) : null}
          {savedMsg ? <span className="text-xs text-emerald-800">{savedMsg}</span> : null}
        </div>
      </CiSection>

      <CiSection title="Coverage preview" description="Qualitative — no population counts invented.">
        <dl className="grid gap-2 text-sm sm:grid-cols-2">
          <div>
            <dt className="text-slate-500">Product</dt>
            <dd className="font-medium text-slate-900">
              {Array.isArray(coverage.product)
                ? (coverage.product as unknown[]).map(String).join(', ')
                : String(coverage.product ?? liveSummary)}
            </dd>
          </div>
          <div>
            <dt className="text-slate-500">Borrower type</dt>
            <dd className="font-medium text-slate-900">{String(coverage.borrowerType ?? 'All')}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Application relationship</dt>
            <dd className="font-medium text-slate-900">{String(coverage.applicationRelationship ?? 'All')}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Amount</dt>
            <dd className="font-medium text-slate-900">{String(coverage.amount ?? 'Any amount')}</dd>
          </div>
        </dl>
      </CiSection>

      <details className="rounded border border-slate-200 bg-white px-4 py-3 text-sm">
        <summary className="cursor-pointer font-medium text-slate-800">Advanced scope (not enabled)</summary>
        <ul className="mt-2 list-disc space-y-1 pl-5 text-slate-600">
          <li>
            Facility type — {String(asRecord(advanced.facilityType).reason ?? 'unavailable')}
          </li>
          <li>
            Secured / Unsecured — {String(asRecord(advanced.securedUnsecured).reason ?? 'unavailable')}
          </li>
          <li>
            Program / Scheme — {String(asRecord(advanced.programScheme).reason ?? 'unavailable')}. Left as All.
          </li>
        </ul>
        <p className="mt-2 text-xs text-slate-500">
          Not exposed for editing because application mapping cannot populate these dimensions reliably.
          allowCanonicalAuthority=false.
        </p>
      </details>

      {session ? null : null}
    </div>
  )
}
