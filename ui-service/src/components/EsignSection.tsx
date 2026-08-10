import { useCallback, useEffect, useState } from 'react'
import { listEsignRequests, resendEsignLink, type EsignRequestView } from '@/api/esignRequests'
import { completeEsignFlow, initiateEsignFlow } from '@/api/flow'
import { ApiError } from '@/api/http'
import { formatInstant } from '@/lib/format'
import { esignSignerFromApplication } from '@/lib/intake/applicationPartyResolve'
import { idBorrowerSkipsDisbursement } from '@/lib/invoiceDiscountingFlow'
import type { ApplicationResponse } from '@/types/application'

export function EsignSection({
  applicationId,
  app,
  onRefetch,
}: {
  applicationId: string
  app: ApplicationResponse
  onRefetch: () => void | Promise<unknown>
}) {
  const [rows, setRows] = useState<EsignRequestView[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [txId, setTxId] = useState('')

  const canInit =
    app.status === 'KFS_GENERATED' ||
    app.status === 'SANCTION_ISSUED' ||
    app.status === 'SANCTIONED' ||
    (app.status === 'ESIGN_PENDING' && (rows?.length ?? 0) === 0)

  const canComplete = app.status === 'ESIGN_PENDING'

  const load = useCallback(async () => {
    setError(null)
    try {
      setRows(await listEsignRequests(applicationId))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to load eSign')
    }
  }, [applicationId])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- eSign list from API
    void load()
  }, [load, app.status])

  async function onSend() {
    setBusy(true)
    setError(null)
    setInfo(null)
    try {
      await initiateEsignFlow(applicationId, esignSignerFromApplication(app))
      await onRefetch()
      await load()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'eSign initiation failed')
    } finally {
      setBusy(false)
    }
  }

  const latest = rows?.[0] ?? null
  const resendBlockedByStatus = latest
    ? ['SIGNED', 'EXPIRED', 'FAILED', 'CANCELLED'].includes((latest.status ?? '').toUpperCase())
    : true
  const resendBlockedByUrl = !(latest?.signingUrl && latest.signingUrl.trim().length > 0)
  const canResendLink = !busy && !resendBlockedByStatus && !resendBlockedByUrl

  async function onResend() {
    setBusy(true)
    setError(null)
    setInfo(null)
    try {
      const r = await resendEsignLink(applicationId)
      const ok = Boolean(r.success)
      if (!ok) {
        setError(typeof r.message === 'string' ? r.message : 'Signing link unavailable')
      } else {
        setInfo('Signing link resent successfully')
      }
      await load()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Signing link unavailable')
    } finally {
      setBusy(false)
    }
  }

  async function onComplete() {
    setBusy(true)
    setError(null)
    setInfo(null)
    try {
      await completeEsignFlow(
        applicationId,
        txId || app.esignTransactionId || undefined,
      )
      setTxId('')
      await onRefetch()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Complete eSign failed')
    } finally {
      setBusy(false)
    }
  }

  const esignPhase = [
    'KFS_GENERATED',
    'SANCTION_ISSUED',
    'SANCTIONED',
    'ESIGN_PENDING',
    'ESIGN_COMPLETED',
    'READY_FOR_DISBURSEMENT',
    'DISBURSEMENT_PENDING',
    'DISBURSED',
  ].includes(app.status)

  const idBorrowerOnboarding = idBorrowerSkipsDisbursement(app)
  const onboardingComplete =
    idBorrowerOnboarding &&
    (app.status === 'ESIGN_COMPLETED' ||
      app.status === 'READY_FOR_DISBURSEMENT' ||
      app.status === 'DISBURSEMENT_PENDING')

  return (
    <div className="space-y-4">
      {onboardingComplete ? (
        <div className="rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-950">
          <p className="font-medium">Borrower onboarding complete</p>
          <p className="mt-2 leading-relaxed">
            Terms are signed and the borrower is linked in PLP. No term-loan disbursement step — finance happens per
            invoice in the invoice discounting module.
          </p>
        </div>
      ) : null}
      {!esignPhase ? (
        <div className="bt-section-card bt-section-card--warning px-3 py-2 text-sm text-amber-950">
          <span className="font-medium">Status:</span> {app.status}. eSign is typically run after the Key Fact Statement
          is generated (KFS_GENERATED or equivalent). Requests already recorded are listed below.
        </div>
      ) : null}
      {error && (
        <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-900">{error}</div>
      )}
      {info && (
        <div className="rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-900">{info}</div>
      )}

      <div className="flex flex-wrap gap-2">
        {canInit && (
          <button
            type="button"
            disabled={busy}
            onClick={() => void onSend()}
            className="rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white"
          >
            {busy ? 'Working…' : 'Send for eSign'}
          </button>
        )}
        {(rows?.length ?? 0) > 0 && (
          <button
            type="button"
            disabled={!canResendLink}
            onClick={() => void onResend()}
            className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm text-slate-800"
            title={
              resendBlockedByStatus
                ? 'Document already signed/expired/cancelled'
                : resendBlockedByUrl
                  ? 'Signing link unavailable'
                  : 'Resend signing link email'
            }
          >
            Resend Link
          </button>
        )}
        {canComplete && (
          <div className="flex flex-wrap items-end gap-2">
            <label className="text-xs text-slate-600">
              Transaction id (if different)
              <input
                className="ml-1 rounded border border-slate-200 px-2 py-1 text-sm"
                value={txId}
                onChange={(e) => setTxId(e.target.value)}
                placeholder={app.esignTransactionId ?? ''}
              />
            </label>
            <button
              type="button"
              disabled={busy}
              onClick={() => void onComplete()}
              className="bt-btn bt-btn-primary"
            >
              Mark eSign complete
            </button>
          </div>
        )}
      </div>

      {rows && rows.length > 0 && (
        <div className="overflow-x-auto bt-section-card bt-section-card--default">
          <table className="bt-table min-w-full">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs font-medium text-slate-600">
              <tr>
                <th className="px-3 py-2">Document</th>
                <th className="px-3 py-2">Provider</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2">Created</th>
                <th className="px-3 py-2">Link</th>
                <th className="px-3 py-2">Signed doc</th>
              </tr>
            </thead>
            <tbody className="">
              {rows.map((r) => (
                <tr key={r.id}>
                  <td className="px-3 py-2 font-mono text-xs text-slate-800">{r.documentType}</td>
                  <td className="px-3 py-2 text-slate-700">{r.provider}</td>
                  <td className="px-3 py-2 text-slate-800">{r.status}</td>
                  <td className="px-3 py-2 text-slate-600 tabular-nums">{formatInstant(r.createdAt)}</td>
                  <td className="px-3 py-2">
                    {r.signingUrl ? (
                      <a
                        href={r.signingUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="text-indigo-700 underline"
                      >
                        Open
                      </a>
                    ) : (
                      '—'
                    )}
                  </td>
                  <td className="px-3 py-2">
                    {r.signedDocumentUrl ? (
                      <a
                        href={r.signedDocumentUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="text-indigo-700 underline"
                      >
                        View
                      </a>
                    ) : (
                      '—'
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {rows && rows.length === 0 && (
        <p className="text-sm text-slate-500">No eSign requests recorded yet. Initiate eSign to create a request.</p>
      )}
    </div>
  )
}
