import { useCallback, useEffect, useState } from 'react'
import { deleteDocument, listDocuments, uploadDocument } from '@/api/documents'
import { prepareBorrowerGstAnalysis } from '@/api/borrowerPortal'
import { notifySuccess } from '@/lib/notify'
import { intakeErrorMessage } from '@/lib/intake/checkIntakeIdentity'
import type { DocumentResponse } from '@/types/document'

function isGstAnalysisDoc(d: DocumentResponse): boolean {
  const t = String(d.documentType ?? '').toUpperCase()
  if (t !== 'GST_RETURN' && t !== 'GST_RETURNS') return false
  const step = String(d.kycStepType ?? '').toUpperCase()
  // Prefer tagged analysis PDFs; fall back to plain GST_RETURN when untagged
  return step === 'GST_ANALYSIS' || !step
}

function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / (1024 * 1024)).toFixed(1)} MB`
}

function IconPdf({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <polyline points="14 2 14 8 20 8" />
      <path d="M9 13h6M9 17h4" />
    </svg>
  )
}

function IconPlus({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
      <path d="M12 5v14M5 12h14" />
    </svg>
  )
}

function IconTrash({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
      <polyline points="3 6 5 6 21 6" />
      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
    </svg>
  )
}

function IconFolder({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
      <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
    </svg>
  )
}

type Props = {
  applicationId: string | null
  variant: 'borrower' | 'staff'
  gstin: string
  consent: boolean
  prepared: boolean
  statusLabel: string | null
  reportSuccess?: boolean
  busy: boolean
  disabled?: boolean
  error: string | null
  onGstinChange: (v: string) => void
  onConsentChange: (v: boolean) => void
  onError: (msg: string | null) => void
  onBusy: (v: boolean) => void
  onPreparedChange: (prepared: boolean, status?: string | null, docCount?: number) => void
}

export function GstAnalysisIntakePanel({
  applicationId,
  variant,
  gstin,
  consent,
  prepared,
  statusLabel,
  reportSuccess,
  busy,
  disabled,
  error,
  onGstinChange,
  onConsentChange,
  onError,
  onBusy,
  onPreparedChange,
}: Props) {
  const [docs, setDocs] = useState<DocumentResponse[]>([])
  const [modalOpen, setModalOpen] = useState(false)
  const [modalError, setModalError] = useState<string | null>(null)

  const reloadDocs = useCallback(async () => {
    if (!applicationId) {
      setDocs([])
      return
    }
    const all = await listDocuments(applicationId)
    const analysis = all.filter((d) => String(d.kycStepType ?? '').toUpperCase() === 'GST_ANALYSIS')
    const list = analysis.length > 0 ? analysis : all.filter(isGstAnalysisDoc)
    setDocs(list)
    return list
  }, [applicationId])

  useEffect(() => {
    void (async () => {
      try {
        const list = await reloadDocs()
        if (list) onPreparedChange(prepared, statusLabel, list.length)
      } catch {
        /* ignore */
      }
    })()
    // only re-load when application changes
    // eslint-disable-next-line react-hooks/exhaustive-deps -- parent tracks prepared separately
  }, [applicationId, reloadDocs])

  async function uploadFiles(files: FileList | File[]) {
    if (!applicationId || !files.length) return
    onError(null)
    setModalError(null)
    onBusy(true)
    try {
      const list = Array.from(files)
      for (const file of list) {
        await uploadDocument(applicationId, file, 'GST_RETURN', 'GST_ANALYSIS')
      }
      const refreshed = await reloadDocs()
      onPreparedChange(false, statusLabel, refreshed?.length ?? 0)
      notifySuccess(`${list.length} GST file(s) uploaded.`)
    } catch (err) {
      const msg = intakeErrorMessage(err, 'Could not upload GST PDFs.')
      onError(msg)
      setModalError(msg)
    } finally {
      onBusy(false)
    }
  }

  async function removeDoc(id: string) {
    if (!window.confirm('Remove this GST return PDF?')) return
    onError(null)
    onBusy(true)
    try {
      await deleteDocument(id)
      const list = await reloadDocs()
      onPreparedChange(false, statusLabel, list?.length ?? 0)
      notifySuccess('File removed.')
    } catch (err) {
      onError(intakeErrorMessage(err, 'Could not remove file.'))
    } finally {
      onBusy(false)
    }
  }

  async function savePrepare() {
    if (!applicationId) return
    onError(null)
    if (!gstin.trim() || gstin.trim().length < 15) {
      onError('Enter a valid 15-character GSTIN.')
      return
    }
    if (!consent) {
      onError('Consent is required.')
      return
    }
    if (docs.length < 1) {
      onError('Upload at least one GST return PDF.')
      return
    }
    onBusy(true)
    try {
      const res = await prepareBorrowerGstAnalysis(applicationId, {
        gstin: gstin.trim(),
        consent: true,
      })
      const ok = Boolean(res.consent) && Boolean(res.gstin) && (res.documentCount ?? 0) > 0
      onPreparedChange(ok, res.status ?? res.phase ?? 'PREPARED', res.documentCount ?? docs.length)
      notifySuccess('GST analysis details saved. Analysis runs after review by the lender.')
    } catch (err) {
      onError(intakeErrorMessage(err, 'Could not save GST analysis prepare.'))
    } finally {
      onBusy(false)
    }
  }

  if (variant === 'staff') {
    return (
      <div className="bt-section-card mt-4">
        <div className="bt-section-card__header">
          <div className="bt-section-card__header-text">
            <h3 className="bt-section-card__title">GST analysis</h3>
            <p className="bt-section-card__subtitle">
              Borrower uploads GST return PDFs and saves GSTIN + consent on the portal.
            </p>
          </div>
          <span className={`bt-section-card__chip ${prepared ? 'bt-section-card__chip--success' : 'bt-section-card__chip--warning'}`}>
            {prepared ? 'Prepared' : statusLabel || 'Pending borrower'}
          </span>
        </div>
        <div className="bt-section-card__body">
          <p className="text-sm text-slate-600">
            Even when staff fill other details, the borrower must complete GST analysis on the borrower portal
            {statusLabel ? ` · current: ${statusLabel}` : ''}.
          </p>
        </div>
      </div>
    )
  }

  return (
    <>
      <div className={`bt-section-card mt-4 ${prepared ? 'bt-section-card--success' : ''}`}>
        <div className="bt-section-card__header">
          <div className="bt-section-card__header-text">
            <h3 className="bt-section-card__title">GST analysis</h3>
            <p className="bt-section-card__subtitle">
              Upload GST return PDFs and save GSTIN with consent. Your lender runs the analysis later —
              nothing is sent to the analysis provider until then.
            </p>
          </div>
          <div className="bt-section-card__badge-row flex flex-wrap gap-1">
            {prepared ? (
              <span className="bt-section-card__chip bt-section-card__chip--success">Ready</span>
            ) : (
              <span className="bt-section-card__chip bt-section-card__chip--warning">Required</span>
            )}
            {reportSuccess ? (
              <span className="bt-section-card__chip bt-section-card__chip--info">Report done</span>
            ) : null}
          </div>
        </div>
        <div className="bt-section-card__body space-y-4">
          {reportSuccess ? (
            <p className="rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-600">
              An analysis report already exists. You can still update GSTIN or replace PDFs if the application was sent
              back for corrections — your lender will re-run analysis after re-submit.
            </p>
          ) : null}

          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">GSTIN *</span>
            <input
              className="bt-input w-full font-mono uppercase"
              value={gstin}
              onChange={(e) => onGstinChange(e.target.value.toUpperCase())}
              maxLength={15}
              disabled={disabled || busy}
            />
          </label>

          <div>
            <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
              <span className="text-xs font-medium text-slate-500">GST return PDFs *</span>
              <button
                type="button"
                className="bt-btn bt-btn-secondary bt-btn-sm inline-flex items-center gap-1.5"
                disabled={!applicationId || busy || disabled}
                onClick={() => setModalOpen(true)}
              >
                <IconFolder className="h-3.5 w-3.5" />
                Manage files ({docs.length})
              </button>
            </div>

            {docs.length === 0 ? (
              <button
                type="button"
                disabled={!applicationId || busy || disabled}
                onClick={() => setModalOpen(true)}
                className="flex w-full flex-col items-center justify-center gap-2 rounded-lg border border-dashed border-slate-300 bg-slate-50 px-4 py-8 text-center transition hover:border-slate-400 hover:bg-white disabled:opacity-50"
              >
                <span className="flex h-10 w-10 items-center justify-center rounded-full bg-white text-slate-500 ring-1 ring-slate-200">
                  <IconPlus className="h-5 w-5" />
                </span>
                <span className="text-sm font-medium text-slate-800">Add GST return PDFs</span>
                <span className="text-xs text-slate-500">You can upload multiple PDFs. Open the manager to add or remove files.</span>
              </button>
            ) : (
              <ul className="divide-y divide-slate-100 overflow-hidden rounded-lg border border-slate-200 bg-white">
                {docs.map((d) => (
                  <li key={d.id} className="flex items-center gap-3 px-3 py-2.5">
                    <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-slate-100 text-slate-600">
                      <IconPdf className="h-5 w-5" />
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-slate-900">{d.fileName}</p>
                      <p className="text-xs text-slate-500">{formatBytes(d.fileSize ?? 0)}</p>
                    </div>
                    <button
                      type="button"
                      className="inline-flex h-8 w-8 items-center justify-center rounded-md text-slate-500 hover:bg-rose-50 hover:text-rose-700 disabled:opacity-50"
                      title="Remove file"
                      aria-label={`Remove ${d.fileName}`}
                      disabled={busy || disabled}
                      onClick={() => void removeDoc(d.id)}
                    >
                      <IconTrash className="h-4 w-4" />
                    </button>
                  </li>
                ))}
              </ul>
            )}
            {docs.length > 0 ? (
              <div className="mt-2 flex flex-wrap gap-2">
                <button
                  type="button"
                  className="bt-btn bt-btn-secondary bt-btn-sm inline-flex items-center gap-1.5"
                  disabled={!applicationId || busy || disabled}
                  onClick={() => setModalOpen(true)}
                >
                  <IconPlus className="h-3.5 w-3.5" />
                  Add more
                </button>
              </div>
            ) : null}
          </div>

          <label className="flex items-start gap-2 text-sm text-slate-800">
            <input
              type="checkbox"
              className="mt-1"
              checked={consent}
              disabled={disabled || busy}
              onChange={(e) => onConsentChange(e.target.checked)}
            />
            <span>I consent to analysis of my uploaded GST return PDFs for this loan application.</span>
          </label>

          {error ? <p className="text-sm text-rose-700">{error}</p> : null}

          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              className="bt-btn bt-btn-primary bt-btn-sm"
              disabled={busy || disabled || !applicationId}
              onClick={() => void savePrepare()}
            >
              {busy ? 'Saving…' : prepared ? 'Update & save GST details' : 'Save GST analysis details'}
            </button>
            {prepared && statusLabel ? (
              <span className="text-xs text-slate-500">Saved · {statusLabel} · {docs.length} file(s)</span>
            ) : null}
          </div>
        </div>
      </div>

      {modalOpen ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/45 p-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="gst-files-modal-title"
        >
          <div className="flex max-h-[90vh] w-full max-w-lg flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xl">
            <div className="flex items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
              <div>
                <h4 id="gst-files-modal-title" className="text-sm font-semibold text-slate-900">
                  GST return PDFs
                </h4>
                <p className="mt-0.5 text-xs text-slate-500">Add or remove PDFs used for GST analysis.</p>
              </div>
              <button
                type="button"
                className="rounded-md px-2 py-1 text-sm text-slate-600 hover:bg-slate-100"
                onClick={() => setModalOpen(false)}
              >
                Close
              </button>
            </div>
            <div className="flex-1 overflow-y-auto px-5 py-4">
              {modalError ? <p className="mb-3 text-sm text-rose-700">{modalError}</p> : null}
              {docs.length === 0 ? (
                <p className="text-sm text-slate-600">No files yet. Add at least one GST return PDF.</p>
              ) : (
                <ul className="space-y-2">
                  {docs.map((d) => (
                    <li
                      key={d.id}
                      className="flex items-center gap-3 rounded-lg border border-slate-200 bg-slate-50/80 px-3 py-2.5"
                    >
                      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-white text-slate-600 ring-1 ring-slate-200">
                        <IconPdf className="h-5 w-5" />
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium text-slate-900">{d.fileName}</p>
                        <p className="text-xs text-slate-500">{formatBytes(d.fileSize ?? 0)}</p>
                      </div>
                      <button
                        type="button"
                        className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-rose-700 hover:bg-rose-50 disabled:opacity-50"
                        disabled={busy}
                        onClick={() => void removeDoc(d.id)}
                      >
                        <IconTrash className="h-3.5 w-3.5" />
                        Remove
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
            <div className="flex flex-wrap items-center justify-between gap-2 border-t border-slate-100 bg-slate-50 px-5 py-3">
              <label
                className={`bt-btn bt-btn-primary bt-btn-sm inline-flex cursor-pointer items-center gap-1.5 ${
                  busy || !applicationId ? 'pointer-events-none opacity-50' : ''
                }`}
              >
                <IconPlus className="h-3.5 w-3.5" />
                {busy ? 'Uploading…' : 'Add PDF files'}
                <input
                  type="file"
                  accept="application/pdf,.pdf"
                  multiple
                  className="sr-only"
                  disabled={busy || !applicationId}
                  onChange={(e) => {
                    // FileList is live — copy before clearing the input or the list becomes empty.
                    const files = Array.from(e.target.files ?? [])
                    e.target.value = ''
                    if (files.length > 0) void uploadFiles(files)
                  }}
                />
              </label>
              <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" onClick={() => setModalOpen(false)}>
                Done
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  )
}
