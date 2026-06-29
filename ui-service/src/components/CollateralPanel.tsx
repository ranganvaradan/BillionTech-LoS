import { useCallback, useEffect, useState } from 'react'
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
} from '@/api/collateral'
import {
  cersaiApi,
  type CersaiRegistration,
} from '@/api/cersai'
import type { ApplicationStatus } from '@/types/application'
import { isSanctionIssued } from '@/lib/postCreditGates'
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

type DetailRecord = Record<string, unknown>

function asRecord(value: unknown): DetailRecord | null {
  return value != null && typeof value === 'object' && !Array.isArray(value) ? (value as DetailRecord) : null
}

function detailString(details: DetailRecord | null | undefined, ...keys: string[]): string | undefined {
  if (!details) return undefined
  for (const key of keys) {
    const value = details[key]
    if (value != null && String(value).trim()) return String(value).trim()
  }
  return undefined
}

function cersaiStatusBadgeClass(status: string): string {
  const s = status.toUpperCase()
  if (s === 'REGISTERED') return 'border-emerald-200 bg-emerald-50 text-emerald-950'
  if (s === 'SEARCHED') return 'border-sky-200 bg-sky-50 text-sky-950'
  if (s === 'PENDING') return 'border-amber-200 bg-amber-50 text-amber-950'
  return 'border-rose-200 bg-rose-50 text-rose-950'
}

function verificationSummary(row: CollateralValuation): string[] {
  const details = row.details ?? {}
  const intake = asRecord(details.intake)
  const lines: string[] = []

  if (row.collateralType === 'VEHICLE') {
    const rc = asRecord(details.rcVerification)
    if (rc?.success === true) lines.push('RC verified')
    else if (rc?.success === false) lines.push('RC failed')
  }

  if (row.collateralType === 'PROPERTY') {
    const ec = asRecord(details.ecVerification)
    if (ec?.success === true) lines.push('EC verified')
    else if (ec?.success === false) lines.push('EC failed')
    const geo = asRecord(details.geo)
    if (geo?.geoVerified === true) {
      const lat = geo.lat ?? geo.latitude
      const lng = geo.lng ?? geo.longitude
      lines.push(lat != null && lng != null ? `Geo: ${lat}, ${lng}` : 'Geo tagged')
    }
  }

  if (row.collateralType === 'GOLD') {
    const gold = asRecord(details.goldValuation)
    if (gold?.acceptedValue != null) {
      lines.push(`Gold: ${formatMoney(Number(gold.acceptedValue))}`)
    }
  }

  if (lines.length === 0 && intake) {
    const rc = detailString(intake, 'rcNumber', 'registrationNumber', 'vehicleRegistrationNumber')
    if (rc && row.collateralType === 'VEHICLE') lines.push(`RC: ${rc}`)
  }

  return lines
}

function defaultCersaiAsset(valuations: CollateralValuation[]): {
  assetType: 'IMMOVABLE' | 'MOVABLE'
  assetIdentifier: string
  collateralValuationId: string
} | null {
  const row =
    valuations.find((v) => v.collateralType === 'PROPERTY') ??
    valuations.find((v) => v.collateralType === 'VEHICLE')
  if (!row) return null

  const details = row.details ?? {}
  const intake = asRecord(details.intake)

  if (row.collateralType === 'PROPERTY') {
    const identifier =
      detailString(details, 'propertyRegNumber', 'registrationNumber') ??
      detailString(intake, 'propertyRegNumber', 'registrationNumber') ??
      row.description?.trim() ??
      row.id
    return { assetType: 'IMMOVABLE', assetIdentifier: identifier, collateralValuationId: row.id }
  }

  const identifier =
    detailString(details, 'rcNumber', 'registrationNumber', 'vehicleRegistrationNumber') ??
    detailString(intake, 'rcNumber', 'registrationNumber', 'vehicleRegistrationNumber') ??
    row.description?.trim() ??
    row.id
  return { assetType: 'MOVABLE', assetIdentifier: identifier, collateralValuationId: row.id }
}

const GOLD_PURITY_OPTIONS = [
  { label: '24K (99.9%)', value: '99.9' },
  { label: '22K (91.6%)', value: '91.6' },
  { label: '18K (75%)', value: '75' },
  { label: 'Other (custom %)', value: 'custom' },
] as const

export function CollateralPanel({
  applicationId,
  loanAmount,
  applicationStatus,
}: {
  applicationId: string
  loanAmount: number | null | undefined
  applicationStatus?: ApplicationStatus | string | null
}) {
  const userRole = loadSessionUser()?.role ?? ''
  const canManage = canManageCollateral(userRole)
  const sanctionIssued = applicationStatus ? isSanctionIssued(applicationStatus as ApplicationStatus) : false

  const [valuations, setValuations] = useState<CollateralValuation[]>([])
  const [ltv, setLtv] = useState<CollateralLtvResult | null>(null)
  const [loading, setLoading] = useState(true)
  const [ltvLoading, setLtvLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showAddForm, setShowAddForm] = useState(false)
  const [addBusy, setAddBusy] = useState(false)
  const [completeTarget, setCompleteTarget] = useState<CollateralValuation | null>(null)
  const [completeBusy, setCompleteBusy] = useState(false)
  const [actionBusy, setActionBusy] = useState<string | null>(null)

  const [goldTarget, setGoldTarget] = useState<CollateralValuation | null>(null)
  const [goldWeight, setGoldWeight] = useState('')
  const [goldPurityOption, setGoldPurityOption] = useState<string>(GOLD_PURITY_OPTIONS[1].value)
  const [goldCustomPurity, setGoldCustomPurity] = useState('')
  const [goldBusy, setGoldBusy] = useState(false)

  const [cersaiRecord, setCersaiRecord] = useState<CersaiRegistration | null>(null)
  const [cersaiHistory, setCersaiHistory] = useState<CersaiRegistration[]>([])
  const [cersaiLoading, setCersaiLoading] = useState(false)
  const [cersaiBusy, setCersaiBusy] = useState<'search' | 'register' | null>(null)

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

  const reloadCersai = useCallback(async () => {
    setCersaiLoading(true)
    try {
      const [latest, history] = await Promise.all([
        cersaiApi.getByApplication(applicationId).catch(() => null),
        cersaiApi.listByApplication(applicationId).catch(() => [] as CersaiRegistration[]),
      ])
      setCersaiRecord(latest)
      setCersaiHistory(history)
    } catch {
      setCersaiRecord(null)
      setCersaiHistory([])
    } finally {
      setCersaiLoading(false)
    }
  }, [applicationId])

  useEffect(() => {
    void reload()
  }, [reload])

  useEffect(() => {
    void reloadLtv()
  }, [reloadLtv, valuations])

  useEffect(() => {
    void reloadCersai()
  }, [reloadCersai])

  function resolveRcNumber(row: CollateralValuation): string | undefined {
    const details = row.details ?? {}
    const intake = asRecord(details.intake)
    return (
      detailString(details, 'rcNumber', 'registrationNumber', 'vehicleRegistrationNumber') ??
      detailString(intake, 'rcNumber', 'registrationNumber', 'vehicleRegistrationNumber')
    )
  }

  function resolvePropertyAddress(row: CollateralValuation): string | undefined {
    return row.address?.trim() || undefined
  }

  async function onVerifyRc(row: CollateralValuation) {
    setError(null)
    const busyKey = `${row.id}:rc`
    setActionBusy(busyKey)
    try {
      let rcNumber = resolveRcNumber(row)
      if (!rcNumber) {
        const entered = window.prompt('Enter vehicle RC / registration number')
        if (!entered?.trim()) return
        rcNumber = entered.trim()
      }
      await verifyCollateralRc(row.id, rcNumber)
      await reload()
      await reloadLtv()
    } catch (err) {
      setError(messageForKycAction(err))
    } finally {
      setActionBusy(null)
    }
  }

  async function onVerifyEc(row: CollateralValuation) {
    setError(null)
    setActionBusy(`${row.id}:ec`)
    try {
      await verifyCollateralEc(row.id)
      await reload()
    } catch (err) {
      setError(messageForKycAction(err))
    } finally {
      setActionBusy(null)
    }
  }

  async function onGeoTag(row: CollateralValuation) {
    setError(null)
    setActionBusy(`${row.id}:geo`)
    try {
      let address = resolvePropertyAddress(row)
      if (!address) {
        const entered = window.prompt('Enter property address for geo-tagging')
        if (!entered?.trim()) return
        address = entered.trim()
      }
      await geoTagCollateralProperty(row.id, address)
      await reload()
    } catch (err) {
      setError(messageForKycAction(err))
    } finally {
      setActionBusy(null)
    }
  }

  function openGoldModal(row: CollateralValuation) {
    setGoldTarget(row)
    setGoldWeight('')
    setGoldPurityOption(GOLD_PURITY_OPTIONS[1].value)
    setGoldCustomPurity('')
    setError(null)
  }

  async function onCalculateGold(e: React.FormEvent) {
    e.preventDefault()
    if (!goldTarget) return
    setError(null)
    const weight = Number.parseFloat(goldWeight)
    if (!Number.isFinite(weight) || weight <= 0) {
      setError('Enter a valid gold weight in grams.')
      return
    }
    const purity =
      goldPurityOption === 'custom'
        ? Number.parseFloat(goldCustomPurity)
        : Number.parseFloat(goldPurityOption)
    if (!Number.isFinite(purity) || purity <= 0 || purity > 100) {
      setError('Enter a valid purity percentage.')
      return
    }
    setGoldBusy(true)
    try {
      await calculateCollateralGoldValue(goldTarget.id, {
        weightGrams: weight,
        purityPercent: purity,
        articleDescription: goldTarget.description ?? undefined,
      })
      setGoldTarget(null)
      await reload()
      await reloadLtv()
    } catch (err) {
      setError(messageForKycAction(err))
    } finally {
      setGoldBusy(false)
    }
  }

  async function onSearchCersaiCharges() {
    setError(null)
    const asset = defaultCersaiAsset(valuations)
    if (!asset) {
      setError('Add a property or vehicle collateral valuation before searching CERSAI.')
      return
    }
    setCersaiBusy('search')
    try {
      await cersaiApi.search({
        applicationId,
        assetType: asset.assetType,
        assetIdentifier: asset.assetIdentifier,
      })
      await reloadCersai()
    } catch (err) {
      setError(messageForKycAction(err))
    } finally {
      setCersaiBusy(null)
    }
  }

  async function onRegisterCersai() {
    setError(null)
    const asset = defaultCersaiAsset(valuations)
    if (!asset) {
      setError('Add a property or vehicle collateral valuation before registering with CERSAI.')
      return
    }
    setCersaiBusy('register')
    try {
      const securedAmount = loanAmount != null && loanAmount > 0 ? loanAmount : undefined
      await cersaiApi.register({
        applicationId,
        collateralValuationId: asset.collateralValuationId,
        assetType: asset.assetType,
        assetIdentifier: asset.assetIdentifier,
        assetDescription: valuations.find((v) => v.id === asset.collateralValuationId)?.description ?? undefined,
        securityInterestType: asset.assetType === 'IMMOVABLE' ? 'MORTGAGE' : 'HYPOTHECATION',
        securedAmount,
      })
      await reloadCersai()
    } catch (err) {
      setError(messageForKycAction(err))
    } finally {
      setCersaiBusy(null)
    }
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
                <th>Verification</th>
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
                    {row.description ?? '—'}
                  </td>
                  <td>
                    <span
                      className={`inline-block rounded border px-2 py-0.5 text-[11px] font-medium ${statusBadgeClass(row.status)}`}
                    >
                      {row.status.replaceAll('_', ' ')}
                    </span>
                  </td>
                  <td className="max-w-[10rem] text-xs text-slate-600">
                    {verificationSummary(row).length > 0 ? (
                      <ul className="space-y-0.5">
                        {verificationSummary(row).map((line) => (
                          <li key={line}>{line}</li>
                        ))}
                      </ul>
                    ) : (
                      '—'
                    )}
                  </td>
                  <td className="tabular-nums text-slate-800">{formatMoney(row.marketValue)}</td>
                  <td className="tabular-nums text-slate-800">{formatMoney(row.forcedSaleValue)}</td>
                  <td className="tabular-nums text-slate-800">{formatMoney(row.valuationAmount)}</td>
                  <td className="text-slate-700">{row.valuerName ?? '—'}</td>
                  <td className="text-slate-600 tabular-nums">{formatInstant(row.valuationExpiry)}</td>
                  {canManage ? (
                    <td>
                      <div className="flex flex-col gap-1 text-xs">
                        {row.status === 'PENDING' || row.status === 'IN_PROGRESS' ? (
                          <button
                            type="button"
                            onClick={() => openCompleteModal(row)}
                            className="text-left font-medium text-indigo-700 underline hover:text-indigo-900"
                          >
                            Complete valuation
                          </button>
                        ) : null}
                        {row.collateralType === 'VEHICLE' && canManage ? (
                          <button
                            type="button"
                            disabled={actionBusy === `${row.id}:rc`}
                            onClick={() => void onVerifyRc(row)}
                            className="text-left font-medium text-sky-800 underline hover:text-sky-950 disabled:opacity-50"
                          >
                            {actionBusy === `${row.id}:rc` ? 'Verifying RC…' : 'Verify RC'}
                          </button>
                        ) : null}
                        {row.collateralType === 'PROPERTY' && canManage ? (
                          <>
                            <button
                              type="button"
                              disabled={actionBusy === `${row.id}:ec`}
                              onClick={() => void onVerifyEc(row)}
                              className="text-left font-medium text-sky-800 underline hover:text-sky-950 disabled:opacity-50"
                            >
                              {actionBusy === `${row.id}:ec` ? 'Verifying EC…' : 'Verify EC'}
                            </button>
                            <button
                              type="button"
                              disabled={actionBusy === `${row.id}:geo`}
                              onClick={() => void onGeoTag(row)}
                              className="text-left font-medium text-teal-800 underline hover:text-teal-950 disabled:opacity-50"
                            >
                              {actionBusy === `${row.id}:geo` ? 'Geo-tagging…' : 'Geo-tag property'}
                            </button>
                          </>
                        ) : null}
                        {row.collateralType === 'GOLD' && canManage ? (
                          <button
                            type="button"
                            onClick={() => openGoldModal(row)}
                            className="text-left font-medium text-amber-900 underline hover:text-amber-950"
                          >
                            Calculate value
                          </button>
                        ) : null}
                        {row.status !== 'PENDING' &&
                        row.status !== 'IN_PROGRESS' &&
                        row.collateralType !== 'VEHICLE' &&
                        row.collateralType !== 'PROPERTY' &&
                        row.collateralType !== 'GOLD'
                          ? '—'
                          : null}
                      </div>
                    </td>
                  ) : null}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="bt-section-card bt-section-card--default space-y-4 p-4">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div>
            <h3 className="bt-card-title">CERSAI</h3>
            <p className="text-xs text-slate-500">
              Search existing security interests before sanction; register after sanction is issued.
            </p>
          </div>
          {canManage ? (
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                disabled={cersaiBusy != null || valuations.length === 0}
                onClick={() => void onSearchCersaiCharges()}
                className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-800 disabled:opacity-50"
              >
                {cersaiBusy === 'search' ? 'Searching…' : 'Search charges'}
              </button>
              {sanctionIssued ? (
                <button
                  type="button"
                  disabled={cersaiBusy != null || valuations.length === 0}
                  onClick={() => void onRegisterCersai()}
                  className="rounded-md bg-slate-900 px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50"
                >
                  {cersaiBusy === 'register' ? 'Registering…' : 'Register security interest'}
                </button>
              ) : null}
            </div>
          ) : null}
        </div>

        {cersaiLoading ? (
          <p className="text-xs text-slate-500">Loading CERSAI records…</p>
        ) : cersaiRecord ? (
          <dl className="grid gap-3 text-xs sm:grid-cols-2 lg:grid-cols-4">
            <div>
              <dt className="text-slate-500">CERSAI ID</dt>
              <dd className="font-mono text-slate-900">{cersaiRecord.cersaiId ?? '—'}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Status</dt>
              <dd>
                <span
                  className={`inline-block rounded border px-2 py-0.5 text-[11px] font-medium ${cersaiStatusBadgeClass(String(cersaiRecord.registrationStatus))}`}
                >
                  {String(cersaiRecord.registrationStatus).replaceAll('_', ' ')}
                </span>
              </dd>
            </div>
            <div>
              <dt className="text-slate-500">Asset type</dt>
              <dd className="text-slate-800">{String(cersaiRecord.assetType).replaceAll('_', ' ')}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Updated</dt>
              <dd className="tabular-nums text-slate-600">{formatInstant(cersaiRecord.updatedAt ?? cersaiRecord.createdAt)}</dd>
            </div>
          </dl>
        ) : (
          <p className="text-xs text-slate-500">No CERSAI search or registration on file yet.</p>
        )}

        {cersaiHistory.length > 1 ? (
          <div className="overflow-x-auto">
            <table className="bt-table min-w-full text-xs">
              <thead>
                <tr>
                  <th>Status</th>
                  <th>CERSAI ID</th>
                  <th>Asset</th>
                  <th>Created</th>
                </tr>
              </thead>
              <tbody>
                {cersaiHistory.map((row) => (
                  <tr key={row.id}>
                    <td>
                      <span
                        className={`inline-block rounded border px-2 py-0.5 text-[11px] font-medium ${cersaiStatusBadgeClass(String(row.registrationStatus))}`}
                      >
                        {String(row.registrationStatus).replaceAll('_', ' ')}
                      </span>
                    </td>
                    <td className="font-mono text-slate-800">{row.cersaiId ?? '—'}</td>
                    <td className="text-slate-700">{row.assetDescription ?? String(row.assetType)}</td>
                    <td className="tabular-nums text-slate-600">{formatInstant(row.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        {canManage && !sanctionIssued ? (
          <p className="text-xs text-amber-900">
            Security interest registration is available after sanction is issued.
          </p>
        ) : null}
      </div>

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
                  {completeTarget.description ? ` · ${completeTarget.description}` : ''}
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
                {completeBusy ? 'Saving…' : 'Save valuation'}
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

      {goldTarget ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="gold-value-title"
        >
          <form
            onSubmit={(e) => void onCalculateGold(e)}
            className="w-full max-w-md rounded-lg border border-slate-200 bg-white p-5 shadow-lg"
          >
            <div className="flex items-start justify-between gap-2">
              <div>
                <h3 id="gold-value-title" className="text-base font-semibold text-slate-900">
                  Calculate gold value
                </h3>
                <p className="mt-1 text-xs text-slate-500">
                  Uses live/simulated gold rate with configured haircut and auto-completes the valuation.
                </p>
              </div>
              <button
                type="button"
                className="rounded border border-slate-300 bg-white px-2 py-1 text-xs text-slate-700"
                onClick={() => setGoldTarget(null)}
              >
                Close
              </button>
            </div>
            <div className="mt-4 grid gap-3">
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
                <label className="block text-sm text-slate-700">
                  <span className="mb-1 block text-xs font-medium text-slate-500">Custom purity (%) *</span>
                  <input
                    type="number"
                    min={0}
                    max={100}
                    step="0.1"
                    required
                    className="bt-input w-full tabular-nums"
                    value={goldCustomPurity}
                    onChange={(e) => setGoldCustomPurity(e.target.value)}
                  />
                </label>
              ) : null}
            </div>
            <div className="mt-4 flex flex-wrap gap-2">
              <button
                type="submit"
                disabled={goldBusy}
                className="rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
              >
                {goldBusy ? 'Calculating…' : 'Calculate'}
              </button>
              <button
                type="button"
                onClick={() => setGoldTarget(null)}
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
