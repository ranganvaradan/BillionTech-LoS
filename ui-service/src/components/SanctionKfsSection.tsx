import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '@/auth/useAuth'
import { isL2SanctionRole } from '@/auth/types'
import { getCam } from '@/api/cam'
import { downloadKfsPdfBlob, getLatestKfs, type KfsDocumentView } from '@/api/kfsApi'
import { proceedToSanctionPendingFlow, rejectPostCreditFlow, sanctionApplicationFlow } from '@/api/flow'
import { ApiError } from '@/api/http'
import { downloadSanctionPdfBlob, getLatestSanction, type SanctionResponse } from '@/api/sanctionApi'
import { formatMoneyWithScale as formatMoney } from '@/lib/format'
import { AmountInputHint } from '@/components/ui/AmountInputHint'
import type { ApplicationResponse } from '@/types/application'
import { isInvoiceDiscountingProduct } from '@/catalog/loanProducts'
import { installmentPaymentLabel, tenureMagnitudeLabel } from '@/catalog/lmsTenureUnits'
import {
  idFlowSkipsKfsAtSanction,
  isInvoiceDiscountingAnchorApp,
  isInvoiceDiscountingBorrowerApp,
} from '@/lib/invoiceDiscountingFlow'
import { PlpBorrowerProgramSection } from '@/components/plp/PlpBorrowerProgramSection'
import { PlpProgramSetupSection } from '@/components/plp/PlpProgramSetupSection'
import { listPlpProgramsForAnchor, listSyncedAnchors } from '@/api/plp'

export function SanctionKfsSection({
  applicationId,
  app,
  onRefetch,
  suppressCamGuidance = false,
}: {
  applicationId: string
  app: ApplicationResponse
  onRefetch: () => void | Promise<unknown>
  /** When CAM is shown elsewhere on Decision, hide duplicate CAM status helper copy. */
  suppressCamGuidance?: boolean
}) {
  const [error, setError] = useState<string | null>(null)
  const [sanctionRec, setSanctionRec] = useState<SanctionResponse | null>(null)
  const [kfs, setKfs] = useState<KfsDocumentView | null>(null)
  const [loadErr, setLoadErr] = useState<string | null>(null)
  const [amount, setAmount] = useState<string>(() => {
    if (app.sanctionedAmount != null) {
      return String(app.sanctionedAmount)
    }
    if (isInvoiceDiscountingAnchorApp(app) && app.requestedAmount != null) {
      return String(app.requestedAmount)
    }
    return ''
  })
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
  const [bookType, setBookType] = useState<'' | 'OWN_BOOK' | 'COLENDING'>('')
  const [busy, setBusy] = useState(false)
  const [kfsLoad, setKfsLoad] = useState(false)
  const [camStatusLine, setCamStatusLine] = useState<string | null>(null)
  const { user } = useAuth()
  const canSanctionL2 = user ? isL2SanctionRole(user.role) : false

  const isAnchor = isInvoiceDiscountingAnchorApp(app)
  const isIdBorrower = isInvoiceDiscountingBorrowerApp(app)
  const skipKfsDoc = idFlowSkipsKfsAtSanction(app)

  // Anchor sanction / eSign start requires L2 (CM/Admin); RM still sees program setup below.
  const canAct = isAnchor
    ? app.status === 'SANCTION_PENDING' && canSanctionL2
    : (app.status === 'CAM_REVIEWED' || app.status === 'SANCTION_PENDING' || app.status === 'APPROVED') &&
      canSanctionL2

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
      if (c.recommendedAmount != null) {
        setAmount((prev) => prev || String(c.recommendedAmount))
      }
      if (c.recommendedTenureMonths != null) {
        setTenure((prev) => prev || String(c.recommendedTenureMonths))
      }
      if (c.recommendedRate != null) {
        setRate((prev) => prev || String(c.recommendedRate))
      }
    } catch {
      setCamStatusLine(
        'CAM is not on file for this case yet, or the application has not reached CAM ready. If you expect CAM here, check underwriting status first.',
      )
    }
  }, [applicationId])

  const loadAnchorProgramDefaults = useCallback(async () => {
    if (app.requestedAmount != null) {
      setAmount((prev) => prev || String(app.requestedAmount))
    }
    try {
      const anchors = await listSyncedAnchors()
      const linked = anchors.find((a) => a.sourceAnchorApplicationId === applicationId)
      if (!linked) return
      const programs = await listPlpProgramsForAnchor(linked.id)
      const program = programs[0]
      if (!program) return
      if (program.creditLimit != null) {
        setAmount((prev) => prev || String(program.creditLimit))
      }
      if (program.tenureDays != null) {
        const months = Math.max(1, Math.ceil(program.tenureDays / 30))
        setTenure((prev) => prev || String(months))
      }
    } catch {
      /* PLP program may not exist yet */
    }
  }, [applicationId, app.requestedAmount])

  useEffect(() => {
    if (isAnchor) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- PLP defaults for anchor sanction
      void loadAnchorProgramDefaults()
      return
    }
    // eslint-disable-next-line react-hooks/set-state-in-effect -- async CAM status line for sanction gate
    void loadCamLabel()
  }, [loadCamLabel, loadAnchorProgramDefaults, app.status, isAnchor])

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
      const body: Record<string, unknown> = {
        sanctionedAmount: amount,
        conditions,
        remarks,
        approvedBy: approvedBy || undefined,
      }
      if (!isAnchor) {
        body.interestRate = rate
        body.tenureMonths = parseInt(tenure, 10)
        body.processingFee = fee || undefined
      } else if (tenure) {
        body.tenureMonths = parseInt(tenure, 10)
      }
      if (!isAnchor && !isIdBorrower) {
        body.bookType = bookType
      }
      await sanctionApplicationFlow(applicationId, body)
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

  async function openTermsOrKfsPdf() {
    setBusy(true)
    setError(null)
    try {
      try {
        const blob = await downloadKfsPdfBlob(applicationId)
        const url = URL.createObjectURL(blob)
        window.open(url, '_blank', 'noopener,noreferrer')
        setTimeout(() => URL.revokeObjectURL(url), 60_000)
      } catch {
        const blob = await downloadSanctionPdfBlob(applicationId)
        const url = URL.createObjectURL(blob)
        window.open(url, '_blank', 'noopener,noreferrer')
        setTimeout(() => URL.revokeObjectURL(url), 60_000)
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Document preview not available')
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

  const idBorrowerTermsOnFile =
    isIdBorrower &&
    (sanctionRec != null ||
      kfs != null ||
      ['SANCTIONED', 'KFS_GENERATED', 'SANCTION_ISSUED', 'ESIGN_PENDING', 'ESIGN_COMPLETED'].includes(app.status))

  const tenureFieldLabel = tenureMagnitudeLabel(app.lmsTenureUnit)
  const installmentLabel =
    (kfs?.additionalTerms && typeof kfs.additionalTerms.installmentLabel === 'string'
      ? kfs.additionalTerms.installmentLabel
      : null) ?? installmentPaymentLabel(app.lmsTenureUnit)

  const sanctionUnlocked = isAnchor
    ? ['SANCTION_PENDING', 'SANCTIONED'].includes(app.status)
    : [
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
    <div className="space-y-8" data-testid="sanction-kfs-section">
      {!isAnchor && !suppressCamGuidance && camStatusLine ? (
        <div className="bt-section-card bt-section-card--default px-3 py-2 text-sm text-slate-800">{camStatusLine}</div>
      ) : null}
      {!sanctionUnlocked ? (
        <div className="rounded-lg border border-amber-200 bg-amber-50/60 px-3 py-2 text-sm text-amber-950">
          {isAnchor ? (
            <>
              Sanction terms will be available after due diligence approval. Complete program setup and PLP approval,
              then generate program terms.
            </>
          ) : (
            <>Sanction terms will be available after credit appraisal is completed.</>
          )}
        </div>
      ) : null}
      {error && (
        <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-900">{error}</div>
      )}

      {isInvoiceDiscountingProduct(app.loanProduct) ? (
        <div data-testid="plp-program-subsection" className="space-y-3">
          <div>
            <h3 className="text-sm font-semibold text-slate-900">Program / PLP</h3>
            <p className="text-xs text-slate-500">
              Program setup and borrower linkage stay with Sanction &amp; Terms (not CAM).
            </p>
          </div>
          {app.intakeSegment === 'ANCHOR' ? (
            <PlpProgramSetupSection app={app} />
          ) : (
            <PlpBorrowerProgramSection app={app} onRefetch={onRefetch} />
          )}
        </div>
      ) : null}

      {canAct && !isAnchor && app.status === 'CAM_REVIEWED' && (
        <div className="bt-section-card bt-section-card--default p-4">
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
          <h3 className="mb-2 text-sm font-semibold text-indigo-950">
            {isAnchor ? 'Anchor sanction (final step)' : 'Sanction decision'}
          </h3>
          {isAnchor ? (
            <p className="mb-3 text-sm text-slate-700">
              Set the program limit below and complete PLP program setup. Approve the program in PLP
              (Programs listing), refresh status on LOS, then sanction to generate program terms
              for anchor eSign — onboarding completes after eSign, not at this step.
            </p>
          ) : isIdBorrower ? (
            <p className="mb-3 text-sm text-slate-700">
              Issue sanction terms for this invoice discounting borrower. A terms &amp; conditions document is generated
              instead of a KFS; no LMS loan is created.
            </p>
          ) : null}
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="text-xs font-medium text-slate-600">
              {isAnchor ? 'Program / anchor limit (INR)' : 'Approved amount'}
              <input
                className="mt-0.5 w-full rounded border border-slate-200 p-2 text-sm"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                inputMode="decimal"
              />
              <AmountInputHint value={amount} />
            </label>
            {isAnchor ? (
              <label className="text-xs font-medium text-slate-600">
                Program validity (months, from PLP)
                <input
                  className="mt-0.5 w-full rounded border border-slate-200 p-2 text-sm"
                  value={tenure}
                  onChange={(e) => setTenure(e.target.value)}
                  inputMode="numeric"
                  placeholder="From PLP program tenure"
                />
              </label>
            ) : null}
            {!isAnchor ? (
              <>
                <label className="text-xs font-medium text-slate-600">
                  {tenureFieldLabel}
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
                {!isIdBorrower ? (
                  <label className="text-xs font-medium text-slate-600">
                    Own Book / Colending *
                    <select
                      className="mt-0.5 w-full rounded border border-slate-200 p-2 text-sm"
                      value={bookType}
                      onChange={(e) => setBookType(e.target.value as typeof bookType)}
                    >
                      <option value="">Select book type</option>
                      <option value="OWN_BOOK">Own Book</option>
                      <option value="COLENDING">Colending</option>
                    </select>
                  </label>
                ) : null}
              </>
            ) : null}
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
              disabled={busy || (!isAnchor && !isIdBorrower && !bookType)}
              onClick={() => void onApprove()}
              className="rounded-md bg-indigo-700 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
            >
              {busy
                ? 'Working…'
                : isAnchor
                  ? 'Generate program terms & start eSign'
                  : isIdBorrower
                    ? 'Approve & generate terms document'
                    : 'Approve & generate sanction + KFS'}
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
              {isIdBorrower ? 'Terms & conditions PDF' : 'Sanction letter PDF'}
            </button>
          </div>
        </div>
      )}

      {(idBorrowerTermsOnFile || (!skipKfsDoc && (sanctionRec || kfs))) && (
        <div className="bt-section-card bt-section-card--default p-4">
          <h3 className="mb-2 bt-card-title">
            {isIdBorrower ? 'Sanction terms' : 'KFS (from sanction)'}
          </h3>
          {kfsLoad && <p className="text-sm text-slate-500">Refreshing document…</p>}
          {loadErr && <p className="text-sm text-amber-800">{loadErr}</p>}
          {kfs && !isIdBorrower && (
            <dl className="mb-3 grid gap-2 sm:grid-cols-2 text-sm">
              <div>
                <dt className="text-xs uppercase text-slate-500">APR</dt>
                <dd className="font-medium text-slate-900">{kfs.apr != null ? `${kfs.apr}%` : '—'}</dd>
              </div>
              <div>
                <dt className="text-xs uppercase text-slate-500">{installmentLabel}</dt>
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
          {isIdBorrower && sanctionRec ? (
            <p className="mb-3 text-sm text-slate-700">
              Sanctioned limit: {formatMoney(sanctionRec.approvedAmount)}
              {sanctionRec.createdAt ? ` · Recorded ${sanctionRec.createdAt}` : ''}
            </p>
          ) : null}
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              disabled={busy}
              onClick={() => void openTermsOrKfsPdf()}
              className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm text-slate-800"
            >
              {isIdBorrower ? 'Preview terms document (PDF)' : 'Preview KFS PDF'}
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

      {skipKfsDoc && sanctionRec && isAnchor && (
        <div className="bt-section-card bt-section-card--default p-4">
          <h3 className="mb-2 bt-card-title">Anchor program terms</h3>
          <p className="text-sm text-slate-700">
            Limit: {formatMoney(sanctionRec.approvedAmount)} · Recorded {sanctionRec.createdAt ?? '—'}
            {app.status === 'ESIGN_PENDING'
              ? ' · Awaiting anchor eSign on the eSign tab — SANCTIONED after signing.'
              : app.status === 'SANCTIONED' || app.status === 'ESIGN_COMPLETED'
                ? ' · eSign complete — anchor onboarding is SANCTIONED.'
                : null}
          </p>
        </div>
      )}
    </div>
  )
}
