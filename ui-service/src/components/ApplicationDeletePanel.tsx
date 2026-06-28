import { useEffect, useState } from 'react'
import { useAuth } from '@/auth/useAuth'
import { canDeleteApplication } from '@/auth/types'
import { useNavigate } from 'react-router-dom'
import {
  deleteApplication,
  getApplicationDeletionPreview,
  type ApplicationDeletionPreview,
} from '@/api/applications'
import { ApiError } from '@/api/http'
import type { ApplicationResponse } from '@/types/application'

type Props = {
  applicationId: string
  app: ApplicationResponse
}

type ModalStep = 'details' | 'confirm' | 'activeLoanWarning'

export function ApplicationDeletePanel({ applicationId, app }: Props) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [modalOpen, setModalOpen] = useState(false)
  const [step, setStep] = useState<ModalStep>('details')
  const [preview, setPreview] = useState<ApplicationDeletionPreview | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [reason, setReason] = useState('')
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    if (!modalOpen) {
      return
    }
    let cancelled = false
    setLoading(true)
    setError(null)
    void getApplicationDeletionPreview(applicationId)
      .then((data) => {
        if (!cancelled) setPreview(data)
      })
      .catch((e: unknown) => {
        if (!cancelled) {
          setPreview(null)
          setError(e instanceof Error ? e.message : 'Unable to load deletion preview')
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [applicationId, modalOpen])

  if (app.intakeSegment === 'ANCHOR' || !user || !canDeleteApplication(user.role)) {
    return null
  }

  function closeModal() {
    if (deleting) return
    setModalOpen(false)
    setStep('details')
    setError(null)
    setReason('')
    setPreview(null)
  }

  function openModal() {
    setModalOpen(true)
    setStep('details')
    setError(null)
  }

  async function runDelete(confirmActiveLoan: boolean) {
    setDeleting(true)
    setError(null)
    try {
      await deleteApplication(applicationId, {
        reason: reason.trim() || undefined,
        confirmActiveLoan,
      })
      navigate('/applications', { replace: true })
    } catch (e: unknown) {
      if (e instanceof ApiError) {
        setError(e.serverMessage || e.message || 'Delete failed')
      } else if (e instanceof Error) {
        setError(e.message)
      } else {
        setError('Delete failed')
      }
    } finally {
      setDeleting(false)
    }
  }

  function onProceedToConfirm() {
    if (!preview) return
    if (preview.requiresDoubleConfirm) {
      setStep('activeLoanWarning')
      return
    }
    setStep('confirm')
  }

  return (
    <>
      <button
        type="button"
        className="bt-btn bt-btn-danger"
        onClick={openModal}
      >
        Delete
      </button>

      {modalOpen ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="delete-application-modal-title"
          onClick={closeModal}
        >
          <div
            className="w-full max-w-lg rounded-lg border border-slate-200 bg-white p-5 shadow-lg"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 id="delete-application-modal-title" className="text-base font-semibold text-slate-900">
              Delete application
            </h3>
            <p className="mt-1 text-xs text-slate-600">
              Permanently removes this borrower application and related LOS data
              {preview?.plpLinked ? ', including linked PLP borrower and program enrollment' : ''}.
            </p>

            {loading ? <p className="mt-4 text-sm text-slate-600">Loading deletion impact…</p> : null}
            {error ? <p className="mt-4 text-sm text-red-700">{error}</p> : null}

            {step === 'details' && preview && !loading ? (
              <>
                <p className="mt-4 text-sm text-slate-800">{preview.summaryMessage}</p>
                {preview.warningMessage ? (
                  <p className="mt-2 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-950">
                    {preview.warningMessage}
                  </p>
                ) : null}
                <label className="mt-4 block text-xs font-medium text-slate-700">
                  Reason (optional)
                  <textarea
                    value={reason}
                    onChange={(e) => setReason(e.target.value)}
                    rows={3}
                    className="mt-1 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-800"
                    placeholder="Why is this application being removed?"
                  />
                </label>
                <div className="mt-5 flex flex-wrap justify-end gap-2">
                  <button
                    type="button"
                    className="bt-btn bt-btn-secondary"
                    disabled={deleting}
                    onClick={closeModal}
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    className="bt-btn bt-btn-danger"
                    disabled={deleting}
                    onClick={onProceedToConfirm}
                  >
                    Delete application
                  </button>
                </div>
              </>
            ) : null}

            {step === 'confirm' && preview ? (
              <>
                <p className="mt-4 text-sm text-slate-800">
                  Delete application <strong>{app.applicationNumber}</strong>? This cannot be undone.
                </p>
                <div className="mt-5 flex flex-wrap justify-end gap-2">
                  <button
                    type="button"
                    className="bt-btn bt-btn-secondary"
                    disabled={deleting}
                    onClick={() => setStep('details')}
                  >
                    Back
                  </button>
                  <button
                    type="button"
                    className="bt-btn bt-btn-danger"
                    disabled={deleting}
                    onClick={() => void runDelete(false)}
                  >
                    {deleting ? 'Deleting…' : 'Yes, delete'}
                  </button>
                </div>
              </>
            ) : null}

            {step === 'activeLoanWarning' && preview ? (
              <>
                <p className="mt-4 text-sm font-semibold text-amber-950">Active loan warning</p>
                <p className="mt-2 text-sm text-amber-950">
                  This application is in <strong>{preview.status}</strong> status and may have an active loan,
                  repayments, or LMS records. Deleting will remove the borrower user and all related financial data.
                </p>
                <p className="mt-2 text-sm text-amber-950">Do you really want to delete this application?</p>
                <div className="mt-5 flex flex-wrap justify-end gap-2">
                  <button
                    type="button"
                    className="bt-btn bt-btn-secondary"
                    disabled={deleting}
                    onClick={() => setStep('details')}
                  >
                    Back
                  </button>
                  <button
                    type="button"
                    className="bt-btn bt-btn-danger"
                    disabled={deleting}
                    onClick={() => void runDelete(true)}
                  >
                    {deleting ? 'Deleting…' : 'Yes, delete anyway'}
                  </button>
                </div>
              </>
            ) : null}

            {!loading && !preview && !error ? (
              <div className="mt-5 flex justify-end">
                <button type="button" className="bt-btn bt-btn-secondary" onClick={closeModal}>
                  Close
                </button>
              </div>
            ) : null}
          </div>
        </div>
      ) : null}
    </>
  )
}
