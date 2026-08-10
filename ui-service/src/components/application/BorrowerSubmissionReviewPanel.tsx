import { useState } from 'react'
import {
  acceptBorrowerSubmission,
  handOffToCreditOfficer,
  sendBackBorrowerSubmission,
  sendBackToRelationshipManager,
} from '@/api/workflow'
import { ApiError } from '@/api/http'
import { useAuth } from '@/auth/useAuth'
import {
  canAcceptBorrowerSubmission,
  canHandOffToCo,
  canSendBackToBorrower,
  canSendBackToRm,
} from '@/auth/types'
import {
  isInvoiceDiscountingAnchorApp,
  isInvoiceDiscountingBorrowerApp,
} from '@/lib/invoiceDiscountingFlow'
import { InvoiceDiscountingVintagePanel } from '@/components/plp/InvoiceDiscountingVintagePanel'
import type { ApplicationResponse } from '@/types/application'

/** Post-accept processing statuses until sanction / eSign path begins. */
const PRE_SANCTION_SEND_BACK_STATUSES = new Set([
  'KYC_IN_PROGRESS',
  'KYC_FAILED',
  'UNDERWRITING',
  'UNDERWRITING_COMPLETED',
  'CAM_READY',
  'CAM_REVIEWED',
  'SANCTION_PENDING',
  'APPROVED',
])

function reviewNotesFromApp(app: ApplicationResponse): string | null {
  const fi = app.financialInfo as Record<string, unknown> | null
  const rn = fi?.reviewNotes
  if (!rn || typeof rn !== 'object') return null
  const notes = (rn as Record<string, unknown>).notes
  return typeof notes === 'string' && notes.trim() ? notes.trim() : null
}

export function BorrowerSubmissionReviewPanel({
  app,
  onRefetch,
}: {
  app: ApplicationResponse
  onRefetch: () => void | Promise<unknown>
}) {
  const { user } = useAuth()
  const role = user?.role ?? ''
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const status = app.status
  const inPreSanctionWindow = PRE_SANCTION_SEND_BACK_STATUSES.has(status)
  /** Anchor apps are staff-owned; there is no RM → borrower portal send-back. CO → RM still applies. */
  const isAnchorApp = isInvoiceDiscountingAnchorApp(app)
  const camSubmittedToManager = app.camStatus === 'SUBMITTED'

  const showHandoffRm =
    (status === 'BORROWER_SUBMITTED' || status === 'SENT_BACK_TO_RM') && canHandOffToCo(role)
  const showSendBackToBorrower =
    !isAnchorApp &&
    canSendBackToBorrower(role) &&
    (status === 'BORROWER_SUBMITTED' || status === 'SENT_BACK_TO_RM')
  const showAcceptCo = status === 'PENDING_CREDIT_OFFICER' && canAcceptBorrowerSubmission(role)
  const showSendBackToRm =
    canSendBackToRm(role) &&
    !camSubmittedToManager &&
    (status === 'PENDING_CREDIT_OFFICER' ||
      status === 'KYC_IN_PROGRESS' ||
      status === 'KYC_FAILED' ||
      status === 'UNDERWRITING' ||
      status === 'UNDERWRITING_COMPLETED' ||
      status === 'CAM_READY')
  const showAdminAcceptFromSubmitted =
    status === 'BORROWER_SUBMITTED' &&
    (role === 'ADMIN' || role === 'ADMINISTRATOR') &&
    canAcceptBorrowerSubmission(role)

  if (
    !showHandoffRm &&
    !showSendBackToBorrower &&
    !showAcceptCo &&
    !showSendBackToRm &&
    !showAdminAcceptFromSubmitted
  ) {
    return null
  }

  const priorNotes = reviewNotesFromApp(app)

  async function run(fn: () => Promise<unknown>) {
    setBusy(true)
    setError(null)
    try {
      await fn()
      await onRefetch()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Action failed')
    } finally {
      setBusy(false)
    }
  }

  const headline =
    status === 'PENDING_CREDIT_OFFICER'
      ? 'Pending Credit Officer review'
      : status === 'SENT_BACK_TO_RM'
        ? 'Sent back to Relationship Manager'
        : inPreSanctionWindow
          ? camSubmittedToManager
            ? 'Submitted to Credit Manager'
            : isAnchorApp
              ? 'Application in processing — CO can send back to RM'
              : 'Application in processing — send-back available'
          : 'Borrower submitted — review required'

  const blurb =
    status === 'PENDING_CREDIT_OFFICER'
      ? 'Accept to start KYC, or send back to the Relationship Manager with optional notes.'
      : status === 'SENT_BACK_TO_RM'
        ? isAnchorApp
          ? 'Credit Officer returned this file. Hand off again to Credit Officer when ready.'
          : 'Credit Officer returned this file. Hand off again to Credit Officer when ready, or send back to the borrower.'
        : inPreSanctionWindow
          ? camSubmittedToManager
            ? 'This case is already submitted to the Credit Manager. Further send-back to RM stays hidden until the manager sends the CAM back.'
            : isAnchorApp
              ? 'Optional send-back to the Relationship Manager remains available until CAM submission or sanction. Anchor applications are not sent back to a borrower portal.'
              : 'Optional send-back remains available while the case is still with RM/CO. RM can return it to the borrower before handoff, and CO can return it to the RM before CAM submission.'
          : 'The borrower completed the delegated intake. Hand off to Credit Officer, or send back to the borrower.'

  return (
    <div className="bt-section-card bt-section-card--warning mb-4 space-y-4 p-4 text-sm text-amber-950">
      <div>
        <h3 className="bt-section-card__title">{headline}</h3>
        <p className="mt-1 text-sm text-amber-950/90">{blurb}</p>
      </div>
      {priorNotes ? (
        <div className="rounded-lg border border-amber-200/80 bg-amber-100/60 px-3 py-2 whitespace-pre-wrap">
          <span className="font-medium">Previous notes: </span>
          {priorNotes}
        </div>
      ) : null}
      {isInvoiceDiscountingBorrowerApp(app) ? (
        <div className="rounded-lg border border-amber-200/60 bg-white/70 p-3">
          <InvoiceDiscountingVintagePanel applicationId={app.id} />
        </div>
      ) : null}
      {error ? (
        <p className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>
      ) : null}
      <label className="block text-xs font-medium text-amber-950/80">
        Optional notes
        <textarea
          className="bt-input mt-1 w-full text-sm"
          rows={3}
          placeholder="Notes for send-back or handoff"
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
        />
      </label>
      <div className="flex flex-wrap gap-2">
        {showHandoffRm ? (
          <button
            type="button"
            className="bt-btn bt-btn--primary"
            disabled={busy}
            onClick={() => void run(() => handOffToCreditOfficer(app.id, notes))}
          >
            Hand off to Credit Officer
          </button>
        ) : null}
        {showSendBackToBorrower ? (
          <button
            type="button"
            className="bt-btn bt-btn--secondary"
            disabled={busy}
            onClick={() => void run(() => sendBackBorrowerSubmission(app.id, notes))}
          >
            Send back to borrower
          </button>
        ) : null}
        {showAcceptCo ? (
          <button
            type="button"
            className="bt-btn bt-btn--primary"
            disabled={busy}
            onClick={() => void run(() => acceptBorrowerSubmission(app.id))}
          >
            Accept for processing
          </button>
        ) : null}
        {showSendBackToRm ? (
          <button
            type="button"
            className="bt-btn bt-btn--secondary"
            disabled={busy}
            onClick={() => void run(() => sendBackToRelationshipManager(app.id, notes))}
          >
            Send back to RM
          </button>
        ) : null}
        {showAdminAcceptFromSubmitted ? (
          <button
            type="button"
            className="bt-btn bt-btn--primary"
            disabled={busy}
            onClick={() => void run(() => acceptBorrowerSubmission(app.id))}
          >
            Accept for processing
          </button>
        ) : null}
      </div>
    </div>
  )
}
