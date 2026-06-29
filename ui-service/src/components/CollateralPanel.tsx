import { useCallback, useEffect, useState } from 'react'
import {
  calculateCollateralLtv,
  completeCollateralValuation,
  createCollateralValuation,
  listCollateralValuations,
  type CollateralLtvResult,
  type CollateralType,
  type CollateralValuation,
  type CollateralValuationStatus,
} from '@/api/collateral'
import { messageForKycAction } from '@/api/kycErrorMessage'
import { loadSessionUser } from '@/auth/types'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { formatInstant, formatMoney } from '@/lib/format'

const COLLATERAL_TYPES: CollateralType[] = [
  'PROPERTY',
  'VEHICLE',
  'GOLD',
  'FIXED_DEPOSIT',
  'SHARES',
  'MACHINERY',
]

function canManageCollateral(role: string): boolean {
  const r = String(role ?? '')
    .trim()
    .toUpperCase()
  return r === 'CREDIT_MANAGER' || r === 'ADMIN' || r === 'ADMINISTRATOR' || r === 'UNDERWRITER'
}

function statusBadgeClass(status: CollateralValuationStatus): string {
  if (status === 'PENDING') return 'border-amber-200 bg-amber-50 text-amber-950'
  if (status === 'IN_PROGRESS') return 'border-sky-200 bg-sky-50 text-sky-950'
  if (status === 'COMPLETED') return 'border-emerald-200 bg-emerald-50 text-emerald-950'
  return 'border-rose-200 bg-rose-50 text-rose-950'
}

function formatCollateralType(type: string): string {
  return type.replaceAll('_', ' ')
}

export function CollateralPanel({
  applicationId,
  loanAmount,
}: {
  applicationId: string
  loanAmount: number | null | undefined
}) {
  const userRole = loadSessionUser()?.role ?? ''
  const canManage = canManageCollateral(userRole)

  const [valuations, setValuations] = useState<CollateralValuation[]>([])
  const [ltv, setLtv] = useState<CollateralLtvResult | null>(null)
  const [loading, setLoading] = useState(true)
  const [ltvLoading, setLtvLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showAddForm, setShowAddForm] = useState(false)
  const [addBusy, setAddBusy] = useState(false)
  const [completeTarget, setCompleteTarget] = useState<CollateralValuation | null>(null)
  const [completeBusy, setCompleteBusy] = useState(false)

  const [collateralType, setCollateralType] = useState<CollateralType>('PROPERTY')
  const [description, setDescription] = useState('')
  const [address, setAddress] = useState('')
  const [estimatedMarketValue, setEstimatedMarketValue] = useState('')

  const [marketValue, setMarketValue] = useState('')
  const [forcedSaleValue, setForcedSaleValue] = useState('')
  const [valuerId, setValuerId] = useState('')
  const [valuerName, setValuerName] = useState('')

  const reload = useCallback(async () => {
    setError(null)
    setLoading(true)
    try {
      const rows = await listCollateralValuations(applicationId)
      setValuations(rows)
    } catch (e) {
      setError(messageForKycAction(e))
      setValuations([])
    } finally {
      setLoading(false)
    }
  }, [applicationId])

  const reloadLtv = useCallback(async () => {
    const amount = loanAmount != null ? Number(loanAmount) : NaN
    if (!Number.isFinite(amount) || amount <= 0) {
      setLtv(null)
      return
    }
    setLtvLoading(true)
    try {
      const result = await calculateCollateralLtv(applicationId, amount)
      setLtv(result)
    } catch {
      setLtv(null)
    } finally {
      setLtvLoading(false)
    }
  }, [applicationId, loanAmount])

  useEffect(() => {
    void reload()
  }, [reload])

  useEffect(() => {
    void reloadLtv()
  }, [reloadLtv, valuations])

  async function onCreateCollateral(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    const est = estimatedMarketValue.trim() ? Number.parseFloat(estimatedMarketValue) : undefined
    if (estimatedMarketValue.trim() && (!Number.isFinite(est!) || est! <= 0)) {
      setError('Enter a valid estimated market value.')
      return
    }
    setAddBusy(true)
    try {
      await createCollateralValuation({
        applicationId,
        collateralType,
        description: description.trim() || undefined,
        address:
          collateralType === 'PROPERTY' || collateralType === 'VEHICLE'
            ? address.trim() || undefined
            : undefined,
        details: est != null ? { estimatedMarketValue: est } : undefined,
      })
      setShowAddForm(false)
      setDescription('')
      setAddress('')
      setEstimatedMarketValue('')
      setCollateralType('PROPERTY')
      await reload()
      await reloadLtv()
    } catch (err) {
      setError(messageForKycAction(err))
    } finally {
      setAddBusy(false)
    }
  }

  async function onCompleteValuation(e: React.FormEvent) {
    e.preventDefault()
    if (!completeTarget) return
    setError(null)
    const mv = Number.parseFloat(marketValue)
    if (!Number.isFinite(mv) || mv <= 0) {
      setError('Enter a valid market value.')
      return
    }
    const fsvRaw = forcedSaleValue.trim()
    let fsv: number | undefined
    if (fsvRaw) {
      fsv = Number.parseFloat(fsvRaw)
      if (!Number.isFinite(fsv) || fsv <= 0) {
        setError('Forced sale value must be a positive number when provided.')
        return
      }
    }
    if (!valuerId.trim() || !valuerName.trim()) {
      setError('Valuer ID and valuer name are required.')
      return
    }
    setCompleteBusy(true)
    try {
      await completeCollateralValuation(completeTarget.id, {
        marketValue: mv,
        forcedSaleValue: fsv,
        valuerId: valuerId.trim(),
        valuerName: valuerName.trim(),
      })
      setCompleteTarget(null)
      setMarketValue('')
      setForcedSaleValue('')
      setValuerId('')
      setValuerName('')
      await reload()
      await reloadLtv()
    } catch (err) {
      setError(messageForKycAction(err))
    } finally {
      setCompleteBusy(false)
    }
  }

  function openCompleteModal(row: CollateralValuation) {
    setCompleteTarget(row)
    setMarketValue('')
    setForcedSaleValue('')
    setValuerId('')
    setValuerName('')
    setError(null)
  }

  const showAddress = collateralType === 'PROPERTY' || collateralType === 'VEHICLE'

  return (
    <div className="space-y-5">
      <div className="bt-section-card bt-section-card--hero p-4 text-sm text-slate-800">
        <h3 className="text-base font-semibold text-slate-900">LTV summary</h3>
        {ltvLoading ? (
          <p className="mt-2 text-xs text-slate-500">Calculating LTVΓÇª</p>
        ) : ltv ? (
          <dl className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-4 text-xs">
            <div>
              <dt className="text-slate-500">Total collateral value</dt>
              <dd className="font-medium text-slate-900">{formatMoney(ltv.totalCollateralValue)}</dd>
            </div>
            <div>
              <dt className="text-slate-500">LTV ratio</dt>
              <dd className="font-medium tabular-nums text-slate-900">{ltv.ltvRatio}%</dd>
            </div>
            <div>
              <dt className="text-slate-500">LTV acceptable</dt>
              <dd className="font-medium">
                {ltv.ltvAcceptable ? (
                  <span className="text-emerald-800">Γ£ô Within policy</span>
                ) : (
                  <span className="text-rose-800">Γ£ù Exceeds limit</span>
                )}
              </dd>
            </div>
            <div>
              <dt className="text-slate-500">Max allowed LTV</dt>
              <dd className="font-medium text-slate-900">{ltv.maxAllowedLtv}%</dd>
            </div>
          </dl>
        ) : (
          <p className="mt-2 text-xs text-slate-500">
            LTV is calculated from completed valuations against the requested loan amount.
            {loanAmount == null || loanAmount <= 0 ? ' No loan amount on file yet.' : null}
          </p>
        )}
      </div>

      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h3 className="bt-card-title">Collateral valuations</h3>
          <p className="text-xs text-slate-500">Official valuations used for secured lending and LTV checks.</p>
        </div>
        {canManage ? (
          <button
            type="button"
            onClick={() => {
              setShowAddForm((v) => !v)
              setError(null)
            }}
            className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-800"
          >
            {showAddForm ? 'Cancel' : 'Add collateral'}
          </button>
        ) : null}
      </div>

      {showAddForm && canManage ? (
        <form
          onSubmit={(e) => void onCreateCollateral(e)}
          className="bt-section-card bt-section-card--default space-y-4 p-4 text-sm"
        >
          <h4 className="font-semibold text-slate-900">New collateral valuation</h4>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">Collateral type *</span>
              <select
                className="bt-input w-full"
                value={collateralType}
                onChange={(e) => setCollateralType(e.target.value as CollateralType)}
              >
                {COLLATERAL_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {formatCollateralType(t)}
                  </option>
                ))}
              </select>
            </label>
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">Estimated market value (INR)</span>
              <input
                type="number"
                min={0}
                step="1"
                className="bt-input w-full tabular-nums"
                value={estimatedMarketValue}
                onChange={(e) => setEstimatedMarketValue(e.target.value)}
              />
            </label>
            <label className="block text-sm text-slate-700 sm:col-span-2">
              <span className="mb-1 block text-xs font-medium text-slate-500">Description</span>
              <input
                className="bt-input w-full"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Brief description of the asset"
              />
            </label>
            {showAddress ? (
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">Address</span>
                <textarea
                  className="bt-input w-full"
                  rows={2}
                  value={address}
                  onChange={(e) => setAddress(e.target.value)}
                />
              </label>
            ) : null}
          </div>
          <button
            type="submit"
            disabled={addBusy}
            className="rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
          >
            {addBusy ? 'SavingΓÇª' : 'Create valuation request'}
          </button>
        </form>
      ) : null}

      {error ? <ErrorState message={error} /> : null}

      {loading ? (
        <LoadingState label="Loading collateral valuationsΓÇª" />
      ) : valuations.length === 0 ? (
        <div className="bt-card bt-empty-state p-4 text-sm text-slate-600">No collateral valuations on file yet.</div>
      ) : (
        <div className="bt-card overflow-x-auto">
          <table className="bt-table min-w-full text-sm">
            <thead>
              <tr>
                <th>Type</th>
                <th>Description</th>
                <th>Status</th>
                <th>Market value</th>
                <th>FSV</th>
                <th>Accepted value</th>
                <th>Valuer</th>
                <th>Expiry</th>
                {canManage ? <th>Action</th> : null}
              </tr>
            </thead>
            <tbody>
              {valuations.map((row) => (
                <tr key={row.id}>
                  <td className="text-slate-900">{formatCollateralType(row.collateralType)}</td>
                  <td className="max-w-[12rem] truncate text-slate-700" title={row.description ?? undefined}>
                    {row.description ?? 'ΓÇö'}
                  </td>
                  <td>
                    <span
                      className={`inline-block rounded border px-2 py-0.5 text-[11px] font-medium ${statusBadgeClass(row.status)}`}
                    >
                      {row.status.replaceAll('_', ' ')}
                    </span>
                  </td>
                  <td className="tabular-nums text-slate-800">{formatMoney(row.marketValue)}</td>
                  <td className="tabular-nums text-slate-800">{formatMoney(row.forcedSaleValue)}</td>
                  <td className="tabular-nums text-slate-800">{formatMoney(row.valuationAmount)}</td>
                  <td className="text-slate-700">{row.valuerName ?? 'ΓÇö'}</td>
                  <td className="text-slate-600 tabular-nums">{formatInstant(row.valuationExpiry)}</td>
                  {canManage ? (
                    <td>
                      {row.status === 'PENDING' || row.status === 'IN_PROGRESS' ? (
                        <button
                          type="button"
                          onClick={() => openCompleteModal(row)}
                          className="text-xs font-medium text-indigo-700 underline hover:text-indigo-900"
                        >
                          Complete valuation
                        </button>
                      ) : (
                        'ΓÇö'
                      )}
                    </td>
                  ) : null}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {completeTarget ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="complete-valuation-title"
        >
          <form
            onSubmit={(e) => void onCompleteValuation(e)}
            className="w-full max-w-lg rounded-lg border border-slate-200 bg-white p-5 shadow-lg"
          >
            <div className="flex items-start justify-between gap-2">
              <div>
                <h3 id="complete-valuation-title" className="text-base font-semibold text-slate-900">
                  Complete valuation
                </h3>
                <p className="mt-1 text-xs text-slate-500">
                  {formatCollateralType(completeTarget.collateralType)}
                  {completeTarget.description ? ` ┬╖ ${completeTarget.description}` : ''}
                </p>
              </div>
              <button
                type="button"
                className="rounded border border-slate-300 bg-white px-2 py-1 text-xs text-slate-700"
                onClick={() => setCompleteTarget(null)}
              >
                Close
              </button>
            </div>
            <div className="mt-4 grid gap-3 sm:grid-cols-2">
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Market value (INR) *</span>
                <input
                  type="number"
                  min={0}
                  step="1"
                  required
                  className="bt-input w-full tabular-nums"
                  value={marketValue}
                  onChange={(e) => setMarketValue(e.target.value)}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Forced sale value (INR)</span>
                <input
                  type="number"
                  min={0}
                  step="1"
                  className="bt-input w-full tabular-nums"
                  value={forcedSaleValue}
                  onChange={(e) => setForcedSaleValue(e.target.value)}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Valuer ID *</span>
                <input
                  className="bt-input w-full"
                  value={valuerId}
                  onChange={(e) => setValuerId(e.target.value)}
                  required
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Valuer name *</span>
                <input
                  className="bt-input w-full"
                  value={valuerName}
                  onChange={(e) => setValuerName(e.target.value)}
                  required
                />
              </label>
            </div>
            <div className="mt-4 flex flex-wrap gap-2">
              <button
                type="submit"
                disabled={completeBusy}
                className="rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
              >
                {completeBusy ? 'SavingΓÇª' : 'Save valuation'}
              </button>
              <button
                type="button"
                onClick={() => setCompleteTarget(null)}
                className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-800"
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      ) : null}
    </div>
  )
}
