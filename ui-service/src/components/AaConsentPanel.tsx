import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  approveAaConsent,
  fetchAaData,
  initiateAaConsent,
  listAaConsents,
  revokeAaConsent,
  type AaConsent,
  type AaConsentStatus,
  type AaFetchedData,
} from '@/api/accountAggregator'
import { messageForKycAction } from '@/api/kycErrorMessage'
import { loadSessionUser } from '@/auth/types'
import { ErrorState } from '@/components/ErrorState'
import { LoadingState } from '@/components/LoadingState'
import { formatInstant, formatMoney } from '@/lib/format'

const FI_TYPES = [
  'DEPOSIT',
  'TERM_DEPOSIT',
  'RECURRING_DEPOSIT',
  'SIP',
  'CP',
  'GOVT_SECURITIES',
  'EQUITIES',
  'BONDS',
  'DEBENTURES',
  'MF',
  'ETF',
  'IDR',
  'CIS',
  'AIF',
  'INSURANCE_POLICIES',
  'NPS',
  'INVIT',
  'REIT',
  'OTHER',
] as const

const DEFAULT_FI_TYPES = ['DEPOSIT', 'TERM_DEPOSIT']

function canManageAa(role: string): boolean {
  const r = String(role ?? '')
    .trim()
    .toUpperCase()
  return r === 'CREDIT_MANAGER' || r === 'ADMIN' || r === 'ADMINISTRATOR' || r === 'UNDERWRITER'
}

function statusBadgeClass(status: AaConsentStatus): string {
  if (status === 'PENDING') return 'border-amber-200 bg-amber-50 text-amber-950'
  if (status === 'APPROVED') return 'border-sky-200 bg-sky-50 text-sky-950'
  if (status === 'DATA_FETCHED') return 'border-emerald-200 bg-emerald-50 text-emerald-950'
  return 'border-rose-200 bg-rose-50 text-rose-950'
}

function computeFoir(data: AaFetchedData): number | null {
  if (!data.avgMonthlyInflow || data.avgMonthlyInflow <= 0) return null
  return (data.regularEmiOutflows / data.avgMonthlyInflow) * 100
}

function foirTone(pct: number): { label: string; className: string } {
  if (pct <= 40) return { label: 'Healthy FOIR', className: 'text-emerald-800 bg-emerald-50 border-emerald-200' }
  if (pct <= 50) return { label: 'Elevated FOIR', className: 'text-amber-900 bg-amber-50 border-amber-200' }
  return { label: 'High FOIR', className: 'text-rose-900 bg-rose-50 border-rose-200' }
}

function hasActiveAaState(consents: AaConsent[]): boolean {
  return consents.some((c) => c.status === 'PENDING' || c.status === 'APPROVED' || c.status === 'DATA_FETCHED')
}

export function AaConsentPanel({ applicationId }: { applicationId: string }) {
  const userRole = loadSessionUser()?.role ?? ''
  const canManage = canManageAa(userRole)

  const [consents, setConsents] = useState<AaConsent[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busyHandle, setBusyHandle] = useState<string | null>(null)
  const [initiateBusy, setInitiateBusy] = useState(false)
  const [showInitiateForm, setShowInitiateForm] = useState(false)

  const [selectedFiTypes, setSelectedFiTypes] = useState<string[]>([...DEFAULT_FI_TYPES])
  const [aaName, setAaName] = useState('DEFAULT_AA')

  const reload = useCallback(async () => {
    setError(null)
    setLoading(true)
    try {
      const rows = await listAaConsents(applicationId)
      setConsents(rows)
    } catch (e) {
      setError(messageForKycAction(e))
      setConsents([])
    } finally {
      setLoading(false)
    }
  }, [applicationId])

  useEffect(() => {
    void reload()
  }, [reload])

  const latestConsent = consents[0] ?? null
  const fetchedConsent = useMemo(
    () => consents.find((c) => c.status === 'DATA_FETCHED' && c.fetchedDataSummary) ?? null,
    [consents],
  )
  const fetchedData = fetchedConsent?.fetchedDataSummary ?? null
  const foir = fetchedData ? computeFoir(fetchedData) : null

  const showForm =
    canManage &&
    !hasActiveAaState(consents) &&
    (showInitiateForm || consents.length === 0)

  async function onInitiate(e: React.FormEvent) {
    e.preventDefault()
    if (selectedFiTypes.length === 0) {
      setError('Select at least one FI type.')
      return
    }
    setInitiateBusy(true)
    setError(null)
    try {
      await initiateAaConsent(applicationId, {
        fiTypes: selectedFiTypes,
        aaName: aaName.trim() || 'DEFAULT_AA',
      })
      setShowInitiateForm(false)
      await reload()
    } catch (err) {
      setError(messageForKycAction(err))
    } finally {
      setInitiateBusy(false)
    }
  }

  async function onFetch(consentHandle: string) {
    setBusyHandle(consentHandle)
    setError(null)
    try {
      await fetchAaData(consentHandle)
      await reload()
    } catch (err) {
      setError(messageForKycAction(err))
    } finally {
      setBusyHandle(null)
    }
  }

  async function onRevoke(consent: AaConsent) {
    const reason = window.prompt('Reason for revoking this consent:', 'Borrower requested revocation')
    if (!reason?.trim()) return
    setBusyHandle(consent.consentHandle)
    setError(null)
    try {
      await revokeAaConsent(consent.id, reason.trim())
      await reload()
    } catch (err) {
      setError(messageForKycAction(err))
    } finally {
      setBusyHandle(null)
    }
  }

  async function onSimulateApprove(consent: AaConsent) {
    setBusyHandle(consent.consentHandle)
    setError(null)
    try {
      await approveAaConsent(consent.consentHandle, `SIM-${consent.consentHandle}`)
      await reload()
    } catch (err) {
      setError(messageForKycAction(err))
    } finally {
      setBusyHandle(null)
    }
  }

  function toggleFiType(type: string) {
    setSelectedFiTypes((prev) =>
      prev.includes(type) ? prev.filter((t) => t !== type) : [...prev, type],
    )
  }

  function renderStatusBanner() {
    if (loading) return null
    if (!latestConsent) {
      return (
        <div className="bt-section-card bt-section-card--default flex flex-wrap items-center justify-between gap-3 p-4 text-sm">
          <div>
            <p className="font-medium text-slate-900">No AA consent initiated</p>
            <p className="mt-1 text-xs text-slate-500">
              Request borrower consent to fetch bank statements via Account Aggregator.
            </p>
          </div>
          {canManage ? (
            <button
              type="button"
              onClick={() => setShowInitiateForm(true)}
              className="rounded-md bg-slate-900 px-3 py-1.5 text-xs font-medium text-white"
            >
              Initiate consent
            </button>
          ) : null}
        </div>
      )
    }

    const s = latestConsent.status
    if (s === 'PENDING') {
      return (
        <div className="bt-section-card bt-section-card--warning p-4 text-sm text-amber-950">
          <p className="font-medium">Waiting for borrower approval</p>
          <p className="mt-1 text-xs">
            Consent handle: <span className="font-mono">{latestConsent.consentHandle}</span>
          </p>
        </div>
      )
    }
    if (s === 'APPROVED') {
      return (
        <div className="bt-section-card bt-section-card--info flex flex-wrap items-center justify-between gap-3 p-4 text-sm text-sky-950">
          <div>
            <p className="font-medium">Consent approved — ready to fetch data</p>
            <p className="mt-1 text-xs">
              Handle: <span className="font-mono">{latestConsent.consentHandle}</span>
            </p>
          </div>
          {canManage ? (
            <button
              type="button"
              disabled={busyHandle === latestConsent.consentHandle}
              onClick={() => void onFetch(latestConsent.consentHandle)}
              className="rounded-md border border-sky-700 bg-sky-800 px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50"
            >
              {busyHandle === latestConsent.consentHandle ? 'Fetching…' : 'Fetch data'}
            </button>
          ) : null}
        </div>
      )
    }
    if (s === 'DATA_FETCHED') {
      return (
        <div className="bt-section-card bt-section-card--success p-4 text-sm text-emerald-950">
          <p className="font-medium">Bank data available</p>
          <p className="mt-1 text-xs">
            Fetched {formatInstant(latestConsent.dataFetchedAt)} · handle{' '}
            <span className="font-mono">{latestConsent.consentHandle}</span>
          </p>
        </div>
      )
    }
    return (
      <div className="bt-section-card bt-section-card--default border-rose-200 bg-rose-50 p-4 text-sm text-rose-950">
        <p className="font-medium">Consent revoked</p>
        {latestConsent.revokeReason ? (
          <p className="mt-1 text-xs">Reason: {latestConsent.revokeReason}</p>
        ) : null}
        {canManage && !hasActiveAaState(consents.filter((c) => c.id !== latestConsent.id)) ? (
          <button
            type="button"
            onClick={() => setShowInitiateForm(true)}
            className="mt-3 rounded-md bg-slate-900 px-3 py-1.5 text-xs font-medium text-white"
          >
            Initiate new consent
          </button>
        ) : null}
      </div>
    )
  }

  return (
    <div className="space-y-5">
      {renderStatusBanner()}

      {showForm ? (
        <form
          onSubmit={(e) => void onInitiate(e)}
          className="bt-section-card bt-section-card--default space-y-4 p-4 text-sm"
        >
          <h3 className="font-semibold text-slate-900">Initiate AA consent</h3>
          <div>
            <span className="mb-2 block text-xs font-medium text-slate-500">FI types</span>
            <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
              {FI_TYPES.map((type) => (
                <label key={type} className="inline-flex items-center gap-2 text-xs text-slate-800">
                  <input
                    type="checkbox"
                    checked={selectedFiTypes.includes(type)}
                    onChange={() => toggleFiType(type)}
                  />
                  {type.replaceAll('_', ' ')}
                </label>
              ))}
            </div>
          </div>
          <label className="block max-w-md text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">AA provider</span>
            <input className="bt-input w-full" value={aaName} onChange={(e) => setAaName(e.target.value)} />
          </label>
          <button
            type="submit"
            disabled={initiateBusy}
            className="rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
          >
            {initiateBusy ? 'Sending…' : 'Send consent request'}
          </button>
        </form>
      ) : null}

      {error ? <ErrorState message={error} /> : null}

      {loading ? (
        <LoadingState label="Loading AA consents…" />
      ) : (
        <>
          <div>
            <h3 className="bt-card-title">Consent history</h3>
            <p className="text-xs text-slate-500">RBI AA consent lifecycle for this application.</p>
          </div>
          {consents.length === 0 ? (
            <div className="bt-card bt-empty-state p-4 text-sm text-slate-600">No consent records yet.</div>
          ) : (
            <div className="bt-card overflow-x-auto">
              <table className="bt-table min-w-full text-sm">
                <thead>
                  <tr>
                    <th>Handle</th>
                    <th>Status</th>
                    <th>AA provider</th>
                    <th>FI types</th>
                    <th>Created</th>
                    <th>Approved at</th>
                    <th>Data fetched at</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {consents.map((row) => (
                    <tr key={row.id}>
                      <td className="font-mono text-xs text-slate-900">{row.consentHandle}</td>
                      <td>
                        <span
                          className={`inline-block rounded border px-2 py-0.5 text-[11px] font-medium ${statusBadgeClass(row.status)}`}
                        >
                          {row.status.replaceAll('_', ' ')}
                        </span>
                      </td>
                      <td className="text-slate-700">{row.aaName}</td>
                      <td className="max-w-[10rem] truncate text-xs text-slate-600" title={row.fiTypes.join(', ')}>
                        {row.fiTypes.join(', ')}
                      </td>
                      <td className="tabular-nums text-slate-600">{formatInstant(row.createdAt)}</td>
                      <td className="tabular-nums text-slate-600">{formatInstant(row.approvedAt)}</td>
                      <td className="tabular-nums text-slate-600">{formatInstant(row.dataFetchedAt)}</td>
                      <td>
                        <div className="flex flex-col gap-1 text-xs">
                          {row.status === 'APPROVED' && canManage ? (
                            <button
                              type="button"
                              disabled={busyHandle === row.consentHandle}
                              onClick={() => void onFetch(row.consentHandle)}
                              className="text-left font-medium text-indigo-700 underline hover:text-indigo-900 disabled:opacity-50"
                            >
                              Fetch data
                            </button>
                          ) : null}
                          {(row.status === 'PENDING' || row.status === 'APPROVED') && canManage ? (
                            <button
                              type="button"
                              disabled={busyHandle === row.consentHandle}
                              onClick={() => void onRevoke(row)}
                              className="text-left font-medium text-rose-700 underline hover:text-rose-900 disabled:opacity-50"
                            >
                              Revoke
                            </button>
                          ) : null}
                          {row.status === 'PENDING' && canManage ? (
                            <button
                              type="button"
                              disabled={busyHandle === row.consentHandle}
                              onClick={() => void onSimulateApprove(row)}
                              className="text-left text-slate-600 underline hover:text-slate-900 disabled:opacity-50"
                            >
                              Simulate approval
                            </button>
                          ) : null}
                          {row.status !== 'PENDING' && row.status !== 'APPROVED' ? '—' : null}
                          {!canManage && (row.status === 'PENDING' || row.status === 'APPROVED') ? '—' : null}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {fetchedData ? (
            <div className="bt-section-card bt-section-card--success space-y-4 p-4 text-sm text-slate-800">
              <h3 className="font-semibold text-emerald-950">Bank accounts summary</h3>
              <dl className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3 text-xs">
                <div>
                  <dt className="text-slate-500">Total balance</dt>
                  <dd className="font-medium tabular-nums text-slate-900">{formatMoney(fetchedData.totalBalance)}</dd>
                </div>
                <div>
                  <dt className="text-slate-500">Avg monthly inflow</dt>
                  <dd className="font-medium tabular-nums text-slate-900">
                    {formatMoney(fetchedData.avgMonthlyInflow)}
                  </dd>
                </div>
                <div>
                  <dt className="text-slate-500">Avg monthly outflow</dt>
                  <dd className="font-medium tabular-nums text-slate-900">
                    {formatMoney(fetchedData.avgMonthlyOutflow)}
                  </dd>
                </div>
                <div>
                  <dt className="text-slate-500">Regular EMI outflows</dt>
                  <dd className="font-medium tabular-nums text-slate-900">
                    {formatMoney(fetchedData.regularEmiOutflows)}
                  </dd>
                </div>
                <div>
                  <dt className="text-slate-500">Bounce count (6M)</dt>
                  <dd className="font-medium text-slate-900">{fetchedData.bounceCount6Months}</dd>
                </div>
                {foir != null ? (
                  <div>
                    <dt className="text-slate-500">FOIR (from AA)</dt>
                    <dd>
                      <span
                        className={`inline-block rounded border px-2 py-0.5 text-[11px] font-medium ${foirTone(foir).className}`}
                      >
                        {foir.toFixed(1)}% · {foirTone(foir).label}
                      </span>
                    </dd>
                  </div>
                ) : null}
              </dl>
              <div>
                <h4 className="text-xs font-semibold uppercase tracking-wide text-emerald-900">
                  Accounts ({fetchedData.accountCount})
                </h4>
                <ul className="mt-2 space-y-2 text-xs">
                  {fetchedData.accounts.map((acct, i) => (
                    <li key={i} className="rounded border border-emerald-100 bg-white/70 p-2">
                      <span className="font-medium text-slate-900">
                        {acct.bank} {acct.type}
                      </span>
                      <span className="text-slate-600">
                        {' '}
                        · Balance: {formatMoney(acct.balance)} · Avg balance:{' '}
                        {formatMoney(acct.avgMonthlyBalance)} · Txns: {acct.txnCount6Months}
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          ) : null}
        </>
      )}
    </div>
  )
}
