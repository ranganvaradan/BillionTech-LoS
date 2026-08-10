import { Fragment, useCallback, useEffect, useState } from 'react'
import {
  calculateCollateralGoldValue,
  calculateCollateralLtv,
  completeCollateralValuation,
  createCollateralValuation,
  geoTagCollateralProperty,
  listCollateralValuations,
  verifyCollateralEc,
  verifyCollateralRc,
  type CollateralLtvResult,
  type CollateralType,
  type CollateralValuation,
  type CollateralValuationStatus,
  type PropertyEcVerifyRequest,
} from '@/api/collateral'
import {
  getCersaiRegistrationByApplication,
  registerCersaiSecurityInterest,
  searchCersaiCharges,
  type CersaiRegistration,
  type CersaiRegistrationStatus,
} from '@/api/cersai'
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

const GOLD_PURITY_OPTIONS = [
  { label: '24K (99.9%)', value: '99.9' },
  { label: '22K (91.6%)', value: '91.6' },
  { label: '18K (75%)', value: '75' },
  { label: 'Custom', value: 'custom' },
]

const CERSAI_ASSET_TYPES = ['IMMOVABLE', 'MOVABLE', 'INTANGIBLE'] as const

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

function cersaiStatusBadgeClass(status: CersaiRegistrationStatus | string): string {
  const s = String(status).toUpperCase()
  if (s === 'REGISTERED') return 'border-emerald-200 bg-emerald-50 text-emerald-950'
  if (s === 'SEARCHED') return 'border-sky-200 bg-sky-50 text-sky-950'
  if (s === 'FAILED') return 'border-rose-200 bg-rose-50 text-rose-950'
  return 'border-amber-200 bg-amber-50 text-amber-950'
}

function formatCollateralType(type: string): string {
  return type.replaceAll('_', ' ')
}

function detailString(details: Record<string, unknown> | null | undefined, ...keys: string[]): string {
  if (!details) return ''
  for (const key of keys) {
    const value = details[key]
    if (value != null && String(value).trim()) return String(value).trim()
  }
  const intake = details.intake as Record<string, unknown> | undefined
  if (intake) {
    for (const key of keys) {
      const value = intake[key]
      if (value != null && String(value).trim()) return String(value).trim()
    }
  }
  return ''
}

function mapCollateralToCersaiAssetType(collateralType: string): (typeof CERSAI_ASSET_TYPES)[number] {
  const t = collateralType.toUpperCase()
  if (t === 'PROPERTY') return 'IMMOVABLE'
  if (t === 'SHARES') return 'INTANGIBLE'
  return 'MOVABLE'
}

function integrationSummary(row: CollateralValuation): string[] {
  const details = row.details ?? {}
  const lines: string[] = []
  const rc = details.rcVerification as Record<string, unknown> | undefined
  if (rc?.verified === true || rc?.rcStatus) {
    lines.push(`RC: ${String(rc.rcStatus ?? 'verified')} · ${String(rc.ownerName ?? '—')}`)
  }
  const ec = details.ecVerification as Record<string, unknown> | undefined
  if (ec?.encumbranceStatus) {
    lines.push(`EC: ${String(ec.encumbranceStatus)}`)
  }
  const geo = details.geo as Record<string, unknown> | undefined
  if (geo?.lat != null && geo?.lng != null) {
    lines.push(`Geo: ${String(geo.lat)}, ${String(geo.lng)} · ${String(geo.pinCode ?? '—')}`)
  }
  const gold = details.goldValuation as Record<string, unknown> | undefined
  if (gold?.marketValue != null) {
    lines.push(`Gold: ${formatMoney(Number(gold.marketValue))} @ ₹${String(gold.liveRatePerGram ?? '—')}/g`)
  }
  const cersai = details.cersai as Record<string, unknown> | undefined
  if (cersai?.cersaiId) {
    lines.push(`CERSAI: ${String(cersai.cersaiId)}`)
  }
  return lines
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
  const [busyKey, setBusyKey] = useState<string | null>(null)

  const [collateralType, setCollateralType] = useState<CollateralType>('PROPERTY')
  const [description, setDescription] = useState('')
  const [address, setAddress] = useState('')
  const [estimatedMarketValue, setEstimatedMarketValue] = useState('')

  const [marketValue, setMarketValue] = useState('')
  const [forcedSaleValue, setForcedSaleValue] = useState('')
  const [valuerId, setValuerId] = useState('')
  const [valuerName, setValuerName] = useState('')

  const [rcTarget, setRcTarget] = useState<CollateralValuation | null>(null)
  const [rcNumber, setRcNumber] = useState('')

  const [ecTarget, setEcTarget] = useState<CollateralValuation | null>(null)
  const [propertyRegNumber, setPropertyRegNumber] = useState('')
  const [district, setDistrict] = useState('')
  const [stateName, setStateName] = useState('')
  const [surveyNumber, setSurveyNumber] = useState('')

  const [geoTarget, setGeoTarget] = useState<CollateralValuation | null>(null)
  const [geoAddress, setGeoAddress] = useState('')

  const [goldTarget, setGoldTarget] = useState<CollateralValuation | null>(null)
  const [goldWeight, setGoldWeight] = useState('')
  const [goldPurityOption, setGoldPurityOption] = useState('91.6')
  const [goldPurityCustom, setGoldPurityCustom] = useState('')
  const [goldArticle, setGoldArticle] = useState('')

  const [cersaiRegistration, setCersaiRegistration] = useState<CersaiRegistration | null>(null)
  const [cersaiAssetType, setCersaiAssetType] = useState<(typeof CERSAI_ASSET_TYPES)[number]>('IMMOVABLE')
  const [cersaiAssetIdentifier, setCersaiAssetIdentifier] = useState('')
  const [cersaiRegisterValuationId, setCersaiRegisterValuationId] = useState('')
  const [cersaiBusy, setCersaiBusy] = useState<'search' | 'register' | null>(null)

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

  const reloadCersai = useCallback(async () => {
    try {
      const row = await getCersaiRegistrationByApplication(applicationId)
      setCersaiRegistration(row)
    } catch {
      setCersaiRegistration(null)
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
    void reloadCersai()
  }, [reload, reloadCersai])

  useEffect(() => {
    void reloadLtv()
  }, [reloadLtv, valuations])

  useEffect(() => {
    const first = valuations[0]
    if (first && !cersaiRegisterValuationId) {
      setCersaiRegisterValuationId(first.id)
      setCersaiAssetType(mapCollateralToCersaiAssetType(first.collateralType))
    }
  }, [valuations, cersaiRegisterValuationId])

  async function refreshAfterIntegration() {
    await reload()
    await reloadLtv()
    await reloadCersai()
  }

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
      await refreshAfterIntegration()
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
      await refreshAfterIntegration()
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

  function openRcModal(row: CollateralValuation) {
    setRcTarget(row)
    setRcNumber(detailString(row.details, 'rcNumber', 'registrationNumber', 'vehicleRegistrationNumber'))
    setError(null)
  }

  function openEcModal(row: CollateralValuation) {
    setEcTarget(row)
    setPropertyRegNumber(detailString(row.details, 'propertyRegNumber', 'registrationNumber'))
    setDistrict(detailString(row.details, 'district'))
    setStateName(detailString(row.details, 'state'))
    setSurveyNumber(detailString(row.details, 'surveyNumber'))
    setError(null)
  }

  function openGeoModal(row: CollateralValuation) {
    setGeoTarget(row)
    setGeoAddress(row.address?.trim() || detailString(row.details, 'formattedAddress', 'address') || '')
    setError(null)
  }

  function openGoldModal(row: CollateralValuation) {
    setGoldTarget(row)
    setGoldWeight(detailString(row.details, 'weightGrams', 'goldWeight'))
    setGoldPurityOption('91.6')
    setGoldPurityCustom('')
    setGoldArticle(row.description?.trim() || '')
    setError(null)
  }

  async function runRcVerify(row: CollateralValuation, rc?: string) {
    const key = `${row.id}:rc`
    setBusyKey(key)
    setError(null)
    try {
      await verifyCollateralRc(row.id, rc?.trim() || undefined)
      setRcTarget(null)
      await refreshAfterIntegration()
    } catch (err) {
      setError(messageForKycAction(err))
    } finally {
      setBusyKey(null)
    }
  }

  async function onSubmitRcVerify(e: React.FormEvent) {
    e.preventDefault()
    if (!rcTarget) return
    if (!rcNumber.trim()) {
      setError('RC number is required.')
      return
    }
    await runRcVerify(rcTarget, rcNumber)
  }

  async function onSubmitEcVerify(e: React.FormEvent) {
    e.preventDefault()
    if (!ecTarget) return
    const payload: PropertyEcVerifyRequest = {
      propertyRegNumber: propertyRegNumber.trim() || undefined,
      district: district.trim() || undefined,
      state: stateName.trim() || undefined,
      surveyNumber: surveyNumber.trim() || undefined,
    }
    setBusyKey(`${ecTarget.id}:ec`)
    setError(null)
    try {
      await verifyCollateralEc(ecTarget.id, payload)
      setEcTarget(null)
      await refreshAfterIntegration()
    } catch (err) {
      setError(messageForKycAction(err))
    } finally {
      setBusyKey(null)
    }
  }

  async function onSubmitGeoTag(e: React.FormEvent) {
    e.preventDefault()
    if (!geoTarget) return
    if (!geoAddress.trim()) {
      setError('Property address is required for geo-tagging.')
      return
    }
    setBusyKey(`${geoTarget.id}:geo`)
    setError(null)
    try {
      await geoTagCollateralProperty(geoTarget.id, geoAddress.trim())
      setGeoTarget(null)
      await refreshAfterIntegration()
    } catch (err) {
      setError(messageForKycAction(err))
    } finally {
      setBusyKey(null)
    }
  }

  async function onSubmitGoldCalc(e: React.FormEvent) {
    e.preventDefault()
    if (!goldTarget) return
    const weight = Number.parseFloat(goldWeight)
    if (!Number.isFinite(weight) || weight <= 0) {
      setError('Enter a valid gold weight in grams.')
      return
    }
    const purity =
      goldPurityOption === 'custom'
        ? Number.parseFloat(goldPurityCustom)
        : Number.parseFloat(goldPurityOption)
    if (!Number.isFinite(purity) || purity <= 0 || purity > 100) {
      setError('Enter a valid purity percentage.')
      return
    }
    setBusyKey(`${goldTarget.id}:gold`)
    setError(null)
    try {
      await calculateCollateralGoldValue(goldTarget.id, {
        weightGrams: weight,
        purityPercent: purity,
        articleDescription: goldArticle.trim() || undefined,
      })
      setGoldTarget(null)
      await refreshAfterIntegration()
    } catch (err) {
      setError(messageForKycAction(err))
    } finally {
      setBusyKey(null)
    }
  }

  async function onCersaiSearch(e: React.FormEvent) {
    e.preventDefault()
    if (!cersaiAssetIdentifier.trim()) {
      setError('Asset identifier is required for CERSAI search.')
      return
    }
    setCersaiBusy('search')
    setError(null)
    try {
      const result = await searchCersaiCharges({
        applicationId,
        assetType: cersaiAssetType,
        assetIdentifier: cersaiAssetIdentifier.trim(),
      })
      setCersaiRegistration(result)
    } catch (err) {
      setError(messageForKycAction(err))
    } finally {
      setCersaiBusy(null)
    }
  }

  async function onCersaiRegister() {
    const valuation = valuations.find((v) => v.id === cersaiRegisterValuationId)
    if (!valuation) {
      setError('Select a collateral valuation to register with CERSAI.')
      return
    }
    setCersaiBusy('register')
    setError(null)
    try {
      const result = await registerCersaiSecurityInterest({
        applicationId,
        collateralValuationId: valuation.id,
        assetType: mapCollateralToCersaiAssetType(valuation.collateralType),
        assetDescription: valuation.description ?? undefined,
        assetIdentifier:
          cersaiAssetIdentifier.trim() ||
          detailString(valuation.details, 'propertyRegNumber', 'registrationNumber', 'rcNumber') ||
          valuation.address ||
          undefined,
        securedAmount: valuation.valuationAmount ?? undefined,
      })
      setCersaiRegistration(result)
      await refreshAfterIntegration()
    } catch (err) {
      setError(messageForKycAction(err))
    } finally {
      setCersaiBusy(null)
    }
  }

  const showAddress = collateralType === 'PROPERTY' || collateralType === 'VEHICLE'
  const hasCompletedValuation = valuations.some((v) => v.status === 'COMPLETED')

  return (
    <div className="space-y-5">
      <div className="bt-section-card bt-section-card--hero p-4 text-sm text-slate-800">
        <h3 className="text-base font-semibold text-slate-900">LTV summary</h3>
        {ltvLoading ? (
          <p className="mt-2 text-xs text-slate-500">Calculating LTV…</p>
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
                  <span className="text-emerald-800">✓ Within policy</span>
                ) : (
                  <span className="text-rose-800">✗ Exceeds limit</span>
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
            {addBusy ? 'Saving…' : 'Create valuation request'}
          </button>
        </form>
      ) : null}

      {error ? <ErrorState message={error} /> : null}

      {loading ? (
        <LoadingState label="Loading collateral valuations…" />
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
                <th>Verification</th>
                {canManage ? <th>Actions</th> : null}
              </tr>
            </thead>
            <tbody>
              {valuations.map((row) => {
                const summaries = integrationSummary(row)
                const isPending = row.status === 'PENDING' || row.status === 'IN_PROGRESS'
                return (
                  <Fragment key={row.id}>
                    <tr>
                      <td className="text-slate-900">{formatCollateralType(row.collateralType)}</td>
                      <td className="max-w-[12rem] truncate text-slate-700" title={row.description ?? undefined}>
                        {row.description ?? '—'}
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
                      <td className="text-slate-700">{row.valuerName ?? '—'}</td>
                      <td className="text-slate-600 tabular-nums">{formatInstant(row.valuationExpiry)}</td>
                      <td className="min-w-[10rem] text-xs text-slate-600">
                        {summaries.length === 0 ? '—' : summaries.map((line) => <div key={line}>{line}</div>)}
                      </td>
                      {canManage ? (
                        <td className="min-w-[9rem]">
                          <div className="flex flex-col gap-1">
                            {row.collateralType === 'VEHICLE' && isPending ? (
                              <button
                                type="button"
                                disabled={busyKey === `${row.id}:rc`}
                                onClick={() => {
                                  const rc = detailString(
                                    row.details,
                                    'rcNumber',
                                    'registrationNumber',
                                    'vehicleRegistrationNumber',
                                  )
                                  if (rc) void runRcVerify(row, rc)
                                  else openRcModal(row)
                                }}
                                className="text-left text-xs font-medium text-indigo-700 underline hover:text-indigo-900 disabled:opacity-50"
                              >
                                {busyKey === `${row.id}:rc` ? 'Verifying RC…' : 'Verify RC'}
                              </button>
                            ) : null}
                            {row.collateralType === 'PROPERTY' ? (
                              <>
                                <button
                                  type="button"
                                  disabled={busyKey === `${row.id}:ec`}
                                  onClick={() => openEcModal(row)}
                                  className="text-left text-xs font-medium text-indigo-700 underline hover:text-indigo-900 disabled:opacity-50"
                                >
                                  {busyKey === `${row.id}:ec` ? 'Verifying EC…' : 'Verify EC'}
                                </button>
                                <button
                                  type="button"
                                  disabled={busyKey === `${row.id}:geo`}
                                  onClick={() => openGeoModal(row)}
                                  className="text-left text-xs font-medium text-indigo-700 underline hover:text-indigo-900 disabled:opacity-50"
                                >
                                  {busyKey === `${row.id}:geo` ? 'Geo-tagging…' : 'Geo-tag property'}
                                </button>
                              </>
                            ) : null}
                            {row.collateralType === 'GOLD' && isPending ? (
                              <button
                                type="button"
                                disabled={busyKey === `${row.id}:gold`}
                                onClick={() => openGoldModal(row)}
                                className="text-left text-xs font-medium text-indigo-700 underline hover:text-indigo-900 disabled:opacity-50"
                              >
                                {busyKey === `${row.id}:gold` ? 'Calculating…' : 'Calculate value'}
                              </button>
                            ) : null}
                            {isPending && row.collateralType !== 'GOLD' ? (
                              <button
                                type="button"
                                onClick={() => openCompleteModal(row)}
                                className="text-left text-xs font-medium text-slate-700 underline hover:text-slate-900"
                              >
                                Complete valuation
                              </button>
                            ) : null}
                          </div>
                        </td>
                      ) : null}
                    </tr>
                  </Fragment>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      <div className="bt-section-card bt-section-card--default space-y-4 p-4 text-sm">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div>
            <h3 className="text-base font-semibold text-slate-900">CERSAI</h3>
            <p className="text-xs text-slate-500">
              Search existing security interests before sanction and register after valuation is complete.
            </p>
          </div>
          {cersaiRegistration ? (
            <span
              className={`inline-block rounded border px-2 py-0.5 text-[11px] font-medium ${cersaiStatusBadgeClass(cersaiRegistration.registrationStatus)}`}
            >
              {cersaiRegistration.registrationStatus}
            </span>
          ) : null}
        </div>

        {cersaiRegistration?.cersaiId ? (
          <p className="text-xs text-slate-700">
            CERSAI ID: <span className="font-mono font-medium">{cersaiRegistration.cersaiId}</span>
            {cersaiRegistration.assetType ? ` · ${cersaiRegistration.assetType}` : null}
          </p>
        ) : null}

        {cersaiRegistration?.responseData &&
        typeof cersaiRegistration.responseData === 'object' &&
        'charges' in cersaiRegistration.responseData &&
        Array.isArray(cersaiRegistration.responseData.charges) &&
        cersaiRegistration.responseData.charges.length > 0 ? (
          <div className="rounded border border-slate-200 bg-white p-3 text-xs">
            <p className="font-medium text-slate-800">Existing charges found</p>
            <ul className="mt-2 space-y-1 text-slate-600">
              {cersaiRegistration.responseData.charges.map((charge, idx) => (
                <li key={`${charge.lenderName ?? 'charge'}-${idx}`}>
                  {charge.lenderName ?? 'Lender'} · {charge.chargeType ?? 'Charge'} ·{' '}
                  {charge.amount != null ? formatMoney(charge.amount) : '—'}
                </li>
              ))}
            </ul>
          </div>
        ) : null}

        {canManage ? (
          <>
            <form onSubmit={(e) => void onCersaiSearch(e)} className="grid gap-3 sm:grid-cols-3">
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Asset type</span>
                <select
                  className="bt-input w-full"
                  value={cersaiAssetType}
                  onChange={(e) =>
                    setCersaiAssetType(e.target.value as (typeof CERSAI_ASSET_TYPES)[number])
                  }
                >
                  {CERSAI_ASSET_TYPES.map((t) => (
                    <option key={t} value={t}>
                      {t}
                    </option>
                  ))}
                </select>
              </label>
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">Asset identifier</span>
                <input
                  className="bt-input w-full"
                  value={cersaiAssetIdentifier}
                  onChange={(e) => setCersaiAssetIdentifier(e.target.value)}
                  placeholder="Survey no / RC no / account reference"
                />
              </label>
              <button
                type="submit"
                disabled={cersaiBusy === 'search'}
                className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-800 disabled:opacity-50 sm:col-span-1"
              >
                {cersaiBusy === 'search' ? 'Searching…' : 'Search charges'}
              </button>
            </form>

            {hasCompletedValuation ? (
              <div className="flex flex-wrap items-end gap-3 border-t border-slate-200 pt-3">
                <label className="block min-w-[12rem] flex-1 text-sm text-slate-700">
                  <span className="mb-1 block text-xs font-medium text-slate-500">Register for valuation</span>
                  <select
                    className="bt-input w-full"
                    value={cersaiRegisterValuationId}
                    onChange={(e) => setCersaiRegisterValuationId(e.target.value)}
                  >
                    {valuations
                      .filter((v) => v.status === 'COMPLETED')
                      .map((v) => (
                        <option key={v.id} value={v.id}>
                          {formatCollateralType(v.collateralType)}
                          {v.description ? ` · ${v.description}` : ''}
                        </option>
                      ))}
                  </select>
                </label>
                <button
                  type="button"
                  disabled={cersaiBusy === 'register'}
                  onClick={() => void onCersaiRegister()}
                  className="rounded-md bg-slate-900 px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50"
                >
                  {cersaiBusy === 'register' ? 'Registering…' : 'Register security interest'}
                </button>
              </div>
            ) : (
              <p className="text-xs text-slate-500">
                Complete at least one collateral valuation to register a security interest with CERSAI.
              </p>
            )}
          </>
        ) : null}
      </div>

      {completeTarget ? (
        <ModalShell title="Complete valuation" onClose={() => setCompleteTarget(null)}>
          <form onSubmit={(e) => void onCompleteValuation(e)} className="space-y-4">
            <p className="text-xs text-slate-500">
              {formatCollateralType(completeTarget.collateralType)}
              {completeTarget.description ? ` · ${completeTarget.description}` : ''}
            </p>
            <div className="grid gap-3 sm:grid-cols-2">
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
            <ModalActions
              submitLabel={completeBusy ? 'Saving…' : 'Save valuation'}
              submitDisabled={completeBusy}
              onCancel={() => setCompleteTarget(null)}
            />
          </form>
        </ModalShell>
      ) : null}

      {rcTarget ? (
        <ModalShell title="Verify RC" onClose={() => setRcTarget(null)}>
          <form onSubmit={(e) => void onSubmitRcVerify(e)} className="space-y-4">
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">RC number *</span>
              <input
                className="bt-input w-full uppercase"
                value={rcNumber}
                onChange={(e) => setRcNumber(e.target.value)}
                required
              />
            </label>
            <ModalActions
              submitLabel={busyKey === `${rcTarget.id}:rc` ? 'Verifying…' : 'Verify RC'}
              submitDisabled={busyKey === `${rcTarget.id}:rc`}
              onCancel={() => setRcTarget(null)}
            />
          </form>
        </ModalShell>
      ) : null}

      {ecTarget ? (
        <ModalShell title="Verify encumbrance certificate" onClose={() => setEcTarget(null)}>
          <form onSubmit={(e) => void onSubmitEcVerify(e)} className="space-y-4">
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">Property registration number</span>
                <input
                  className="bt-input w-full"
                  value={propertyRegNumber}
                  onChange={(e) => setPropertyRegNumber(e.target.value)}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">District</span>
                <input className="bt-input w-full" value={district} onChange={(e) => setDistrict(e.target.value)} />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">State</span>
                <input className="bt-input w-full" value={stateName} onChange={(e) => setStateName(e.target.value)} />
              </label>
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">Survey number</span>
                <input
                  className="bt-input w-full"
                  value={surveyNumber}
                  onChange={(e) => setSurveyNumber(e.target.value)}
                />
              </label>
            </div>
            <ModalActions
              submitLabel={busyKey === `${ecTarget.id}:ec` ? 'Verifying…' : 'Verify EC'}
              submitDisabled={busyKey === `${ecTarget.id}:ec`}
              onCancel={() => setEcTarget(null)}
            />
          </form>
        </ModalShell>
      ) : null}

      {geoTarget ? (
        <ModalShell title="Geo-tag property" onClose={() => setGeoTarget(null)}>
          <form onSubmit={(e) => void onSubmitGeoTag(e)} className="space-y-4">
            <label className="block text-sm text-slate-700">
              <span className="mb-1 block text-xs font-medium text-slate-500">Property address *</span>
              <textarea
                className="bt-input w-full"
                rows={3}
                value={geoAddress}
                onChange={(e) => setGeoAddress(e.target.value)}
                required
              />
            </label>
            <ModalActions
              submitLabel={busyKey === `${geoTarget.id}:geo` ? 'Geo-tagging…' : 'Geo-tag'}
              submitDisabled={busyKey === `${geoTarget.id}:geo`}
              onCancel={() => setGeoTarget(null)}
            />
          </form>
        </ModalShell>
      ) : null}

      {goldTarget ? (
        <ModalShell title="Calculate gold value" onClose={() => setGoldTarget(null)}>
          <form onSubmit={(e) => void onSubmitGoldCalc(e)} className="space-y-4">
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Weight (grams) *</span>
                <input
                  type="number"
                  min={0}
                  step="0.01"
                  required
                  className="bt-input w-full tabular-nums"
                  value={goldWeight}
                  onChange={(e) => setGoldWeight(e.target.value)}
                />
              </label>
              <label className="block text-sm text-slate-700">
                <span className="mb-1 block text-xs font-medium text-slate-500">Purity *</span>
                <select
                  className="bt-input w-full"
                  value={goldPurityOption}
                  onChange={(e) => setGoldPurityOption(e.target.value)}
                >
                  {GOLD_PURITY_OPTIONS.map((opt) => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
              </label>
              {goldPurityOption === 'custom' ? (
                <label className="block text-sm text-slate-700 sm:col-span-2">
                  <span className="mb-1 block text-xs font-medium text-slate-500">Custom purity (%)</span>
                  <input
                    type="number"
                    min={0}
                    max={100}
                    step="0.1"
                    className="bt-input w-full tabular-nums"
                    value={goldPurityCustom}
                    onChange={(e) => setGoldPurityCustom(e.target.value)}
                  />
                </label>
              ) : null}
              <label className="block text-sm text-slate-700 sm:col-span-2">
                <span className="mb-1 block text-xs font-medium text-slate-500">Article description</span>
                <input
                  className="bt-input w-full"
                  value={goldArticle}
                  onChange={(e) => setGoldArticle(e.target.value)}
                />
              </label>
            </div>
            <ModalActions
              submitLabel={busyKey === `${goldTarget.id}:gold` ? 'Calculating…' : 'Calculate & complete'}
              submitDisabled={busyKey === `${goldTarget.id}:gold`}
              onCancel={() => setGoldTarget(null)}
            />
          </form>
        </ModalShell>
      ) : null}
    </div>
  )
}

function ModalShell({
  title,
  onClose,
  children,
}: {
  title: string
  onClose: () => void
  children: React.ReactNode
}) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      role="dialog"
      aria-modal="true"
    >
      <div className="w-full max-w-lg rounded-lg border border-slate-200 bg-white p-5 shadow-lg">
        <div className="mb-4 flex items-start justify-between gap-2">
          <h3 className="text-base font-semibold text-slate-900">{title}</h3>
          <button
            type="button"
            className="rounded border border-slate-300 bg-white px-2 py-1 text-xs text-slate-700"
            onClick={onClose}
          >
            Close
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}

function ModalActions({
  submitLabel,
  submitDisabled,
  onCancel,
}: {
  submitLabel: string
  submitDisabled?: boolean
  onCancel: () => void
}) {
  return (
    <div className="flex flex-wrap gap-2">
      <button
        type="submit"
        disabled={submitDisabled}
        className="rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
      >
        {submitLabel}
      </button>
      <button
        type="button"
        onClick={onCancel}
        className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-800"
      >
        Cancel
      </button>
    </div>
  )
}
