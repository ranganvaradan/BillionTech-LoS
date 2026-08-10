import { useCallback, useEffect, useState } from 'react'
import {
  generateAdminGstAnalysisReport,
  getAdminGstAnalysisStatus,
  retryAdminGstAnalysisUpload,
  type AdminGstAnalysisStatus,
} from '@/api/applications'
import { fetchDocumentPreviewBlob } from '@/api/documents'
import { notifyError, notifyErrorMessage, notifySuccess } from '@/lib/notify'
import { intakeErrorMessage } from '@/lib/intake/checkIntakeIdentity'
import { useLongRunningAction } from '@/lib/hooks/useLongRunningAction'
import { BackgroundProcessingNotice } from '@/components/ui/BackgroundProcessingNotice'

type Props = {
  applicationId: string
  onUpdated?: () => void
}

type PreviewState = {
  documentId: string
  fileName: string
  contentType: string
  url: string
}

function formatBytes(n?: number): string {
  if (n == null || n < 0) return ''
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / (1024 * 1024)).toFixed(1)} MB`
}

function isPdfContentType(ct: string): boolean {
  return ct.toLowerCase().includes('pdf')
}

function isImageContentType(ct: string): boolean {
  return ct.toLowerCase().startsWith('image/')
}

function IconChevron({ open, className }: { open: boolean; className?: string }) {
  return (
    <svg
      className={`${className ?? ''} transition-transform ${open ? 'rotate-180' : ''}`}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      aria-hidden
    >
      <polyline points="6 9 12 15 18 9" />
    </svg>
  )
}

function IconEye({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  )
}

function IconPdf({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden>
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <polyline points="14 2 14 8 20 8" />
    </svg>
  )
}

export function GstAnalysisAdminPanel({ applicationId, onUpdated }: Props) {
  const [status, setStatus] = useState<AdminGstAnalysisStatus | null>(null)
  const [loading, setLoading] = useState(true)
  const longRun = useLongRunningAction()
  const [showResponse, setShowResponse] = useState(false)
  const [error, setError] = useState<string | null>(null)
  /** After report success, provider actions stay collapsed unless the user expands them. */
  const [advancedOpen, setAdvancedOpen] = useState(false)
  /** Source PDFs list collapsed by default — only count shown until expand. */
  const [docsOpen, setDocsOpen] = useState(false)
  const [metricsOpen, setMetricsOpen] = useState(false)
  const [previewLoading, setPreviewLoading] = useState(false)
  const [preview, setPreview] = useState<PreviewState | null>(null)

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      const st = await getAdminGstAnalysisStatus(applicationId)
      setStatus(st)
      setError(null)
      if (!st.reportSuccess) setAdvancedOpen(false)
    } catch (err) {
      setError(intakeErrorMessage(err, 'Could not load GST analysis status.'))
    } finally {
      setLoading(false)
    }
  }, [applicationId])

  useEffect(() => {
    void reload()
  }, [reload])

  useEffect(() => {
    return () => {
      if (preview?.url) URL.revokeObjectURL(preview.url)
    }
  }, [preview])

  async function openPreview(doc: { id?: string; fileName?: string; contentType?: string }) {
    if (!doc.id) return
    setPreviewLoading(true)
    setError(null)
    try {
      const blob = await fetchDocumentPreviewBlob(doc.id)
      const ct =
        blob.type && blob.type !== 'application/octet-stream'
          ? blob.type
          : doc.contentType || 'application/pdf'
      const viewBlob = blob.type === ct ? blob : new Blob([blob], { type: ct })
      const url = URL.createObjectURL(viewBlob)
      if (preview?.url) URL.revokeObjectURL(preview.url)
      setPreview({
        documentId: doc.id,
        fileName: doc.fileName || `document-${doc.id}`,
        contentType: ct,
        url,
      })
    } catch (err) {
      setError(intakeErrorMessage(err, 'Could not preview document.'))
      notifyError(err, 'Could not preview document.')
    } finally {
      setPreviewLoading(false)
    }
  }

  function closePreview() {
    if (preview?.url) URL.revokeObjectURL(preview.url)
    setPreview(null)
  }

  if (loading && !status) {
    return (
      <div className="bt-section-card p-4 text-sm text-slate-600">Loading GST analysis…</div>
    )
  }

  // Hide when step not on workflow and never started
  if (status && status.onWorkflow === false && status.status === 'NOT_STARTED' && !status.required) {
    return null
  }

  const uploadOk = Boolean(status?.uploadSuccess)
  const reportOk = Boolean(status?.reportSuccess)
  const prepared = Boolean(status?.consent && status?.gstin && (status?.documentCount ?? 0) > 0)
  const phase = status?.phase ?? '—'
  const outcome = status?.status ?? 'NOT_STARTED'
  const docs = status?.documents ?? []
  const docCount = docs.length || status?.documentCount || 0
  const hasMetrics = Boolean(status?.mappedMetrics && typeof status.mappedMetrics === 'object')

  const actionBusy = longRun.state.blocking
  const actionInFlight = longRun.state.inFlight

  const runUpload = () => {
    void (async () => {
      if (actionInFlight) return
      setError(null)
      try {
        await longRun.run(async () => {
          const res = await retryAdminGstAnalysisUpload(applicationId)
          const ok = String(res.outcome).toUpperCase() === 'SUCCESS'
          if (ok) {
            notifySuccess('GST documents uploaded to provider successfully.')
            longRun.markOutcome(true, res.outcome)
          } else {
            const msg = res.errorMessage || 'GST upload finished with errors.'
            notifyErrorMessage(msg)
            setError(msg)
            longRun.markOutcome(false, res.outcome, msg)
          }
          await reload()
          onUpdated?.()
          return res
        })
      } catch (err) {
        setError(intakeErrorMessage(err, 'GST upload failed.'))
        notifyError(err, 'GST upload failed.')
      }
    })()
  }

  const runGenerate = () => {
    void (async () => {
      if (actionInFlight) return
      setError(null)
      try {
        await longRun.run(async () => {
          const res = await generateAdminGstAnalysisReport(applicationId)
          if (String(res.outcome).toUpperCase() === 'SUCCESS') {
            notifySuccess('GST analysis report generated. PDF/Excel stored under documents.')
            setAdvancedOpen(false)
            longRun.markOutcome(true, res.outcome)
          } else {
            const msg = res.errorMessage || 'Report generation failed.'
            notifyErrorMessage(msg)
            setError(msg)
            longRun.markOutcome(false, res.outcome, msg)
          }
          await reload()
          onUpdated?.()
          return res
        })
      } catch (err) {
        setError(intakeErrorMessage(err, 'GST report generation failed.'))
        notifyError(err, 'GST report generation failed.')
      }
    })()
  }

  const uploadLabel =
    actionBusy
      ? 'Working…'
      : longRun.state.showBackgroundNotice
        ? 'Still processing…'
        : longRun.state.phase === 'timeout' || longRun.state.phase === 'error'
          ? uploadOk
            ? 'Retry re-upload'
            : 'Retry upload to provider'
          : uploadOk
            ? 'Re-upload to provider'
            : 'Upload to provider'

  const generateLabel =
    actionBusy
      ? 'Working…'
      : longRun.state.showBackgroundNotice
        ? 'Still processing…'
        : longRun.state.phase === 'timeout' || longRun.state.phase === 'error'
          ? 'Retry generate report'
          : 'Generate report'

  const primaryActions = (
    <div className="mt-1 space-y-2">
      <BackgroundProcessingNotice
        visible={longRun.state.showBackgroundNotice}
        label="GST provider request is processing in the background"
      />
      {longRun.state.phase === 'timeout' && !longRun.state.inFlight ? (
        <div className="rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-950">
          The GST provider request timed out after 5 minutes. Use retry below, or Refresh to check if it completed
          server-side.
        </div>
      ) : null}
      <div className="flex flex-wrap gap-2">
        {!reportOk ? (
          <>
            <button
              type="button"
              className="bt-btn bt-btn-primary bt-btn-sm"
              disabled={actionBusy || !prepared || (actionInFlight && !longRun.state.showBackgroundNotice)}
              title={!prepared ? 'Borrower must prepare GSTIN, consent, and PDFs first' : undefined}
              onClick={runUpload}
            >
              {uploadLabel}
            </button>
            <button
              type="button"
              className="bt-btn bt-btn-secondary bt-btn-sm"
              disabled={actionBusy || !uploadOk || (actionInFlight && !longRun.state.showBackgroundNotice)}
              title={!uploadOk ? 'Upload must succeed first' : undefined}
              onClick={runGenerate}
            >
              {generateLabel}
            </button>
          </>
        ) : null}
        <button
          type="button"
          className="bt-btn bt-btn-secondary bt-btn-sm"
          disabled={!status?.fullResponse && !status?.uploadResult}
          onClick={() => setShowResponse(true)}
        >
          View provider response
        </button>
        <button
          type="button"
          className="bt-btn bt-btn-secondary bt-btn-sm"
          disabled={actionBusy}
          onClick={() => void reload()}
        >
          Refresh
        </button>
      </div>
    </div>
  )

  return (
    <div className={`bt-section-card ${reportOk ? 'bt-section-card--success' : ''}`}>
      <div className="bt-section-card__header">
        <div className="bt-section-card__header-text">
          <h3 className="bt-section-card__title">GST analysis</h3>
          <p className="bt-section-card__subtitle">
            Borrower prepares GSTIN + PDFs. Upload and generate report from here.
          </p>
        </div>
        <div className="flex flex-wrap gap-1">
          <span className={`bt-section-card__chip ${prepared ? 'bt-section-card__chip--success' : 'bt-section-card__chip--warning'}`}>
            Prep: {prepared ? 'Ready' : 'Incomplete'}
          </span>
          <span className={`bt-section-card__chip ${uploadOk ? 'bt-section-card__chip--success' : ''}`}>
            Upload: {uploadOk ? 'OK' : 'Pending'}
          </span>
          <span className={`bt-section-card__chip ${reportOk ? 'bt-section-card__chip--success' : ''}`}>
            Report: {reportOk ? 'OK' : 'Pending'}
          </span>
        </div>
      </div>

      <div className="bt-section-card__body space-y-3">
        {/* Compact required summary only */}
        <dl className="grid gap-x-4 gap-y-2 text-sm sm:grid-cols-3">
          <div>
            <dt className="text-xs text-slate-500">GSTIN</dt>
            <dd className="font-mono text-slate-900">{status?.gstin || '—'}</dd>
          </div>
          <div>
            <dt className="text-xs text-slate-500">Phase / status</dt>
            <dd className="text-slate-900">
              {phase} · {outcome}
            </dd>
          </div>
          <div>
            <dt className="text-xs text-slate-500">Input PDFs</dt>
            <dd className="text-slate-900">
              {docCount > 0 ? `${docCount} file${docCount === 1 ? '' : 's'}` : 'None'}
            </dd>
          </div>
          {status?.requestId ? (
            <div className="sm:col-span-2">
              <dt className="text-xs text-slate-500">Request id</dt>
              <dd className="break-all font-mono text-xs text-slate-700">{status.requestId}</dd>
            </div>
          ) : null}
          {status?.errorMessage ? (
            <div className="sm:col-span-3">
              <dt className="text-xs text-slate-500">Last error</dt>
              <dd className="text-rose-700">{status.errorMessage}</dd>
            </div>
          ) : null}
        </dl>

        {/* Source documents — collapsed by default */}
        {docCount > 0 ? (
          <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
            <button
              type="button"
              className="flex w-full items-center justify-between gap-2 px-3 py-2.5 text-left text-sm hover:bg-slate-50"
              aria-expanded={docsOpen}
              onClick={() => setDocsOpen((o) => !o)}
            >
              <span className="inline-flex items-center gap-2 font-medium text-slate-800">
                <IconPdf className="h-4 w-4 text-slate-500" />
                Source documents
                <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-normal text-slate-600">
                  {docCount}
                </span>
              </span>
              <IconChevron open={docsOpen} className="h-4 w-4 shrink-0 text-slate-500" />
            </button>
            {docsOpen ? (
              <ul className="border-t border-slate-100 divide-y divide-slate-50">
                {docs.map((d) => (
                  <li key={d.id ?? d.fileName} className="flex items-center gap-2 px-3 py-2 text-sm">
                    <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-slate-100 text-slate-600">
                      <IconPdf className="h-4 w-4" />
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-medium text-slate-900">{d.fileName || d.id}</p>
                      {d.fileSize != null ? (
                        <p className="text-xs text-slate-500">{formatBytes(d.fileSize)}</p>
                      ) : null}
                    </div>
                    <button
                      type="button"
                      className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-slate-700 ring-1 ring-slate-200 hover:bg-slate-50 disabled:opacity-50"
                      disabled={!d.id || previewLoading}
                      title="Preview"
                      onClick={() => void openPreview(d)}
                    >
                      <IconEye className="h-3.5 w-3.5" />
                      {previewLoading ? '…' : 'Preview'}
                    </button>
                  </li>
                ))}
              </ul>
            ) : null}
          </div>
        ) : (
          <p className="text-xs text-amber-800">No GST analysis input PDFs on file yet.</p>
        )}

        {/* Metrics collapsed unless report done and user expands */}
        {hasMetrics ? (
          <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
            <button
              type="button"
              className="flex w-full items-center justify-between gap-2 px-3 py-2.5 text-left text-sm hover:bg-slate-50"
              aria-expanded={metricsOpen}
              onClick={() => setMetricsOpen((o) => !o)}
            >
              <span className="font-medium text-slate-800">Mapped metrics</span>
              <IconChevron open={metricsOpen} className="h-4 w-4 shrink-0 text-slate-500" />
            </button>
            {metricsOpen && status?.mappedMetrics ? (
              <div className="flex flex-wrap gap-2 border-t border-slate-100 px-3 py-2.5 text-xs">
                {Object.entries(status.mappedMetrics)
                  .slice(0, 12)
                  .map(([k, v]) => (
                    <span key={k} className="rounded-md bg-slate-50 px-2 py-0.5 ring-1 ring-slate-200">
                      {k}: {String(v)}
                    </span>
                  ))}
              </div>
            ) : null}
          </div>
        ) : null}

        {error ? <p className="text-sm text-rose-700">{error}</p> : null}

        {!reportOk ? (
          primaryActions
        ) : (
          <>
            <div className="rounded-md border border-emerald-200 bg-emerald-50/60 px-3 py-2 text-sm text-emerald-900">
              Report generated successfully. Advanced re-upload / regenerate is collapsed by default.
            </div>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                className="bt-btn bt-btn-secondary bt-btn-sm"
                disabled={!status?.fullResponse && !status?.uploadResult}
                onClick={() => setShowResponse(true)}
              >
                View provider response
              </button>
              <button type="button" className="bt-btn bt-btn-secondary bt-btn-sm" disabled={actionBusy} onClick={() => void reload()}>
                Refresh
              </button>
              <button
                type="button"
                className="bt-btn bt-btn-secondary bt-btn-sm"
                onClick={() => setAdvancedOpen((o) => !o)}
              >
                {advancedOpen ? 'Hide advanced actions' : 'Show advanced actions'}
              </button>
            </div>
            {advancedOpen ? (
              <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 space-y-2">
                <BackgroundProcessingNotice
                  visible={longRun.state.showBackgroundNotice}
                  label="GST provider request is processing in the background"
                />
                <p className="mb-2 text-xs text-slate-600">
                  Use only if borrower documents or GSTIN changed after send-back, or report needs re-fetch.
                </p>
                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    className="bt-btn bt-btn-secondary bt-btn-sm"
                    disabled={actionBusy || !prepared}
                    onClick={runUpload}
                  >
                    {uploadLabel}
                  </button>
                  <button
                    type="button"
                    className="bt-btn bt-btn-primary bt-btn-sm"
                    disabled={actionBusy || !uploadOk}
                    onClick={runGenerate}
                  >
                    {generateLabel}
                  </button>
                </div>
              </div>
            ) : null}
          </>
        )}
      </div>

      {showResponse ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4"
          role="dialog"
          aria-modal="true"
        >
          <div className="max-h-[85vh] w-full max-w-3xl overflow-hidden rounded-lg bg-white shadow-xl">
            <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
              <h4 className="text-sm font-semibold text-slate-900">GST analysis response</h4>
              <button type="button" className="text-sm text-slate-600 hover:text-slate-900" onClick={() => setShowResponse(false)}>
                Close
              </button>
            </div>
            <pre className="max-h-[70vh] overflow-auto p-4 text-xs text-slate-800">
              {JSON.stringify(status?.fullResponse ?? status?.uploadResult ?? status, null, 2)}
            </pre>
          </div>
        </div>
      ) : null}

      {preview ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="gst-doc-preview-title"
        >
          <div className="w-full max-w-4xl rounded-lg border border-slate-200 bg-white p-5 shadow-lg">
            <div className="flex items-center justify-between gap-2">
              <h3 id="gst-doc-preview-title" className="text-base font-semibold text-slate-900">
                Document preview
              </h3>
              <button
                type="button"
                className="rounded border border-slate-300 bg-white px-2 py-1 text-xs text-slate-700"
                onClick={closePreview}
              >
                Close
              </button>
            </div>
            <p className="mt-2 text-xs text-slate-500">{preview.fileName}</p>
            <div className="mt-3 max-h-[70vh] overflow-auto rounded border border-slate-200 bg-slate-50 p-2">
              {isPdfContentType(preview.contentType) ? (
                <iframe title={preview.fileName} src={preview.url} className="h-[65vh] w-full rounded border-0 bg-white" />
              ) : isImageContentType(preview.contentType) ? (
                <img src={preview.url} alt={preview.fileName} className="mx-auto h-auto max-w-full rounded" />
              ) : (
                <p className="p-3 text-sm text-slate-700">Preview not supported for this file type.</p>
              )}
            </div>
          </div>
        </div>
      ) : null}
    </div>
  )
}
