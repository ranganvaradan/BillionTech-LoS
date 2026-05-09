import { useCallback, useEffect, useState } from 'react'
import { getCam } from '@/api/cam'
import { downloadKfsPdfBlob, getLatestKfs, type KfsDocumentView } from '@/api/kfsApi'
import { proceedToSanctionPendingFlow, rejectPostCreditFlow, sanctionApplicationFlow } from '@/api/flow'
import { ApiError } from '@/api/http'
import { downloadSanctionPdfBlob, getLatestSanction, type SanctionResponse } from '@/api/sanctionApi'
import { formatMoney } from '@/lib/format'
import type { ApplicationResponse } from '@/types/application'

export function SanctionKfsSection({
  applicationId,
  app,
  onRefetch,
}: {
  applicationId: string
  app: ApplicationResponse
  onRefetch: () => void | Promise<unknown>
}) {
  const [error, setError] = useState<string | null>(null)
  const [sanctionRec, setSanctionRec] = useState<SanctionResponse | null>(null)
  const [kfs, setKfs] = useState<KfsDocumentView | null>(null)
  const [loadErr, setLoadErr] = useState<string | null>(null)
  const [amount, setAmount] = useState<string>(() =>
    app.sanctionedAmount != null ? String(app.sanctionedAmount) : '',
  )
  const [tenure, setTenure] = useState<string>(() =>
    app.tenureMonths != null ? String(app.tenureMonths) : '',
  )
  const [rate, setRate] = useState<string>(() => {
    if (app.approvedRate != null) {
      return String(app.approvedRate)
    }
    if (app.interestRate != null) {
      return String(app.interestRate)
    }
    return ''
  })
  const [fee, setFee] = useState<string>('')
  const [conditions, setConditions] = useState<string>('')
  const [remarks, setRemarks] = useState<string>('')
  const [approvedBy, setApprovedBy] = useState<string>('')
  const [busy, setBusy] = useState(false)
  const [kfsLoad, setKfsLoad] = useState(false)
  const [camStatusLine, setCamStatusLine] = useState<string | null>(null)

  const canAct =
    app.status === 'CAM_REVIEWED' || app.status === 'SANCTION_PENDING' || app.status === 'APPROVED'

  const refetchAncillary = useCallback(async () => {
    setKfsLoad(true)
    setLoadErr(null)
    try {
      try {
        setSanctionRec(await getLatestSanction(applicationId))
      } catch {
        setSanctionRec(null)
      }
      try {
        setKfs(await getLatestKfs(applicationId))
      } catch {
        setKfs(null)
      }
    } catch (e) {
      setLoadErr(e instanceof Error ? e.message : 'Load failed')
    } finally {
      setKfsLoad(false)
    }
  }, [applicationId])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- optional sanction/KFS from API
    void refetchAncillary()
  }, [refetchAncillary, app.status])

  const loadCamLabel = useCallback(async () => {
    try {
      const c = await getCam(applicationId)
      if (c.camStatus === 'APPROVED' && c.approvedAt) {
        setCamStatusLine(
          `Credit Appraisal Memo is approved in the system (recorded at ${c.approvedAt}). You may move to sanction pending and issue terms.`,
        )
      } else {
        setCamStatusLine(
          c.camStatus
            ? `Credit Appraisal Memo status: ${c.camStatus}. Complete and approve the CAM on the CAM tab before final sanction.`
            : null,
        )
      }
    } catch {
      setCamStatusLine(
        'CAM is not on file for this case yet, or the application has not reached CAM ready. If you expect CAM here, check underwriting status first.',
      )
    }
  }, [applicationId])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async CAM status line for sanction gate
    void loadCamLabel()
  }, [loadCamLabel, app.status])

  async function onProceedPending() {
    setBusy(true)
    setError(null)
    try {
      await proceedToSanctionPendingFlow(applicationId)
      await onRefetch()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not move to sanction pending')
    } finally {
      setBusy(false)
    }
  }

  async function onApprove() {
    setBusy(true)
    setError(null)
    try {
      await sanctionApplicationFlow(applicationId, {
        sanctionedAmount: amount,
        interestRate: rate,
        tenureMonths: parseInt(tenure, 10),
        processingFee: fee || undefined,
        conditions,
        remarks,
        approvedBy: approvedBy || undefined,
      })
      await onRefetch()
      await refetchAncillary()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Sanction failed')
    } finally {
      setBusy(false)
    }
  }

  async function onReject() {
    if (!window.confirm('Reject this application at sanction stage?')) {
      return
    }
    setBusy(true)
    setError(null)
    try {
      await rejectPostCreditFlow(applicationId, remarks || 'Rejected at sanction')
      await onRefetch()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Reject failed')
    } finally {
      setBusy(false)
    }
  }

  async function openSanctionPdf() {
    setBusy(true)
    setError(null)
    try {
      const blob = await downloadSanctionPdfBlob(applicationId)
      const url = URL.createObjectURL(blob)
      window.open(url, '_blank', 'noopener,noreferrer')
      setTimeout(() => URL.revokeObjectURL(url), 60_000)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No sanction letter yet (complete sanction first)')
    } finally {
      setBusy(false)
    }
  }

  async function openKfsPdf() {
    setBusy(true)
    setError(null)
    try {
      const blob = await downloadKfsPdfBlob(applicationId)
      const url = URL.createObjectURL(blob)
      window.open(url, '_blank', 'noopener,noreferrer')
      setTimeout(() => URL.revokeObjectURL(url), 60_000)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'KFS PDF not available')
    } finally {
      setBusy(false)
    }
  }

  const sanctionUnlocked = [
    'CAM_REVIEWED',
    'SANCTION_PENDING',
    'SANCTIONED',
    'KFS_GENERATED',
    'SANCTION_ISSUED',
    'ESIGN_PENDING',
    'ESIGN_COMPLETED',
    'READY_FOR_DISBURSEMENT',
    'DISBURSEMENT_PENDING',
    'DISBURSED',
    'APPROVED',
  ].includes(app.status)

  return (
    <div className="space-y-8">
      {camStatusLine ? (
        <div className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800">{camStatusLine}</div>
      ) : null}
      {!sanctionUnlocked ? (
        <div className="rounded-lg border border-amber-200 bg-amber-50/60 px-3 py-2 text-sm text-amber-950">
          <span className="font-medium">Status:</span> {app.status}. Sanction and KFS actions unlock after the Credit
          Appraisal Memo is reviewed (CAM_REVIEWED or legacy APPROVED). You can still use this tab to read guidance and
          download PDFs if they already exist.
        </div>
      ) : null}
      {error && (
        <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-900">{error}</div>
      )}

      {canAct && app.status === 'CAM_REVIEWED' && (
        <div className="rounded-lg border border-slate-200 bg-white p-4">
          <p className="mb-2 text-sm text-slate-700">Optional: move to formal sanction-pending before entering terms.</p>
          <button
            type="button"
            disabled={busy}
            onClick={() => void onProceedPending()}
            className="rounded-md border border-slate-300 bg-slate-50 px-3 py-1.5 text-sm font-medium text-slate-800"
          >
            Proceed to sanction pending
          </button>
        </div>
      )}

      {canAct && (
        <div className="rounded-lg border border-indigo-200/80 bg-indigo-50/40 p-4">
          <h3 className="mb-2 text-sm font-semibold text-indigo-950">Sanction decision</h3>
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="text-xs font-medium text-slate-600">
              Approved amount
              <input
                className="mt-0.5 w-full rounded border border-slate-200 p-2 text-sm"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                inputMode="decimal"
              />
            </label>
            <label className="text-xs font-medium text-slate-600">
              Tenure (months)
              <input
                className="mt-0.5 w-full rounded border border-slate-200 p-2 text-sm"
                value={tenure}
                onChange={(e) => setTenure(e.target.value)}
                inputMode="numeric"
              />
            </label>
            <label className="text-xs font-medium text-slate-600">
              Interest rate (% p.a.)
              <input
                className="mt-0.5 w-full rounded border border-slate-200 p-2 text-sm"
                value={rate}
                onChange={(e) => setRate(e.target.value)}
                inputMode="decimal"
              />
            </label>
            <label className="text-xs font-medium text-slate-600">
              Processing fee
              <input
                className="mt-0.5 w-full rounded border border-slate-200 p-2 text-sm"
                value={fee}
                onChange={(e) => setFee(e.target.value)}
                inputMode="decimal"
              />
            </label>
          </div>
          <label className="mt-3 block text-xs font-medium text-slate-600">
            Conditions
            <textarea
              className="mt-0.5 w-full rounded border border-slate-200 p-2 text-sm"
              rows={2}
              value={conditions}
              onChange={(e) => setConditions(e.target.value)}
            />
          </label>
          <label className="mt-3 block text-xs font-medium text-slate-600">
            Remarks
            <textarea
              className="mt-0.5 w-full rounded border border-slate-200 p-2 text-sm"
              rows={2}
              value={remarks}
              onChange={(e) => setRemarks(e.target.value)}
            />
          </label>
          <label className="mt-3 block text-xs font-medium text-slate-600">
            Approved by (name / id)
            <input
              className="mt-0.5 w-full max-w-md rounded border border-slate-200 p-2 text-sm"
              value={approvedBy}
              onChange={(e) => setApprovedBy(e.target.value)}
            />
          </label>
          <div className="mt-4 flex flex-wrap gap-2">
            <button
              type="button"
              disabled={busy}
              onClick={() => void onApprove()}
              className="rounded-md bg-indigo-700 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
            >
              {busy ? 'Working…' : 'Approve & generate sanction + KFS'}
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={() => void onReject()}
              className="rounded-md border border-red-300 bg-red-50 px-3 py-1.5 text-sm font-medium text-red-900"
            >
              Reject
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={() => void openSanctionPdf()}
              className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm text-slate-800"
            >
              Sanction letter PDF
            </button>
          </div>
        </div>
      )}

      {(sanctionRec || kfs) && (
        <div className="rounded-lg border border-slate-200 bg-white p-4">
          <h3 className="mb-2 text-sm font-semibold text-slate-900">KFS (from sanction)</h3>
          {kfsLoad && <p className="text-sm text-slate-500">Refreshing KFS…</p>}
          {loadErr && <p className="text-sm text-amber-800">{loadErr}</p>}
          {kfs && (
            <dl className="mb-3 grid gap-2 sm:grid-cols-2 text-sm">
              <div>
                <dt className="text-xs uppercase text-slate-500">APR</dt>
                <dd className="font-medium text-slate-900">{kfs.apr != null ? `${kfs.apr}%` : '—'}</dd>
              </div>
              <div>
                <dt className="text-xs uppercase text-slate-500">EMI</dt>
                <dd className="font-medium text-slate-900">{formatMoney(kfs.emiAmount)}</dd>
              </div>
              <div>
                <dt className="text-xs uppercase text-slate-500">Total payable</dt>
                <dd className="font-medium text-slate-900">{formatMoney(kfs.totalRepayment)}</dd>
              </div>
              <div>
                <dt className="text-xs uppercase text-slate-500">Processing fee</dt>
                <dd className="font-medium text-slate-900">
                  {kfs.processingFee != null ? formatMoney(kfs.processingFee) : '—'}
                </dd>
              </div>
            </dl>
          )}
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              disabled={busy}
              onClick={() => void openKfsPdf()}
              className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm text-slate-800"
            >
              Preview KFS PDF
            </button>
            <button
              type="button"
              disabled={busy || kfsLoad}
              onClick={() => void refetchAncillary()}
              className="text-sm text-slate-600 underline"
            >
              Refresh
            </button>
          </div>
        </div>
      )}

      {sanctionRec && (
        <p className="text-xs text-slate-500">
          Latest sanction on file from {sanctionRec.createdAt ?? '—'}
        </p>
      )}
    </div>
  )
}
