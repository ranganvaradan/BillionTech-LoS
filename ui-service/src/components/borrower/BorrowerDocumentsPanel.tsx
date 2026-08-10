import { useCallback, useEffect, useState } from 'react'
import {
  fetchBorrowerDocumentPreviewBlob,
  listBorrowerDocuments,
  type BorrowerDocumentItem,
} from '@/api/borrowerPortal'
import { ErrorState } from '@/components/ErrorState'
import { formatInstant } from '@/lib/format'
import {
  borrowerDocumentTypeLabel,
  formatDocumentBytes,
  inferPreviewContentType,
  isImageContentType,
  isPdfContentType,
} from '@/lib/borrowerDocumentLabels'

function IconEye({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  )
}

type PreviewState = {
  documentId: string
  fileName: string
  contentType: string
  url: string
}

export function BorrowerDocumentsPanel({ applicationId }: { applicationId: string }) {
  const [docs, setDocs] = useState<BorrowerDocumentItem[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [previewLoading, setPreviewLoading] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [preview, setPreview] = useState<PreviewState | null>(null)

  const load = useCallback(async () => {
    setLoadError(null)
    try {
      const list = await listBorrowerDocuments(applicationId)
      setDocs(list)
    } catch (e) {
      setDocs(null)
      setLoadError(e instanceof Error ? e.message : 'Could not load documents')
    }
  }, [applicationId])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    return () => {
      if (preview?.url) URL.revokeObjectURL(preview.url)
    }
  }, [preview])

  async function onPreview(d: BorrowerDocumentItem) {
    setActionError(null)
    setPreviewLoading(true)
    try {
      const blob = await fetchBorrowerDocumentPreviewBlob(applicationId, d)
      const effectiveType = inferPreviewContentType(blob, d.contentType, d.fileName)
      const viewBlob = blob.type === effectiveType ? blob : new Blob([blob], { type: effectiveType })
      const url = URL.createObjectURL(viewBlob)
      setPreview({
        documentId: d.id,
        fileName: d.fileName || `document-${d.id}`,
        contentType: effectiveType,
        url,
      })
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Preview failed')
    } finally {
      setPreviewLoading(false)
    }
  }

  function closePreview() {
    if (preview?.url) URL.revokeObjectURL(preview.url)
    setPreview(null)
  }

  if (loadError) {
    return <ErrorState message={loadError} />
  }
  if (!docs) {
    return <p className="text-sm text-slate-500">Loading documents…</p>
  }

  const kycDocs = docs.filter((d) => d.category === 'KYC')
  const signedDocs = docs.filter((d) => d.category === 'SIGNED')

  function renderTable(rows: BorrowerDocumentItem[]) {
    if (rows.length === 0) {
      return <p className="text-sm text-slate-600">No documents in this category yet.</p>
    }
    return (
      <div className="bt-card overflow-x-auto">
        <table className="bt-table min-w-full">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs font-medium text-slate-600">
            <tr>
              <th className="px-3 py-2 text-left">Name</th>
              <th className="px-3 py-2 text-left">Type</th>
              <th className="px-3 py-2 text-left">Size</th>
              <th className="px-3 py-2 text-left">Date</th>
              <th className="px-3 py-2 text-left"> </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((d) => (
              <tr key={`${d.source}-${d.id}`}>
                <td className="px-3 py-2 text-slate-900">{d.fileName}</td>
                <td className="px-3 py-2 text-slate-600">
                  {borrowerDocumentTypeLabel(d.documentType, d.source)}
                </td>
                <td className="px-3 py-2 tabular-nums text-slate-600">{formatDocumentBytes(d.fileSize)}</td>
                <td className="px-3 py-2 text-slate-600">{formatInstant(d.createdAt)}</td>
                <td className="px-3 py-2">
                  <button
                    type="button"
                    onClick={() => void onPreview(d)}
                    disabled={previewLoading}
                    className="inline-flex items-center justify-center text-slate-800 disabled:opacity-50"
                    title="Preview"
                    aria-label={`Preview ${d.fileName}`}
                  >
                    <IconEye className="h-4 w-4" />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <p className="text-sm text-slate-600">
        View-only access to your KYC uploads and signed agreements. Internal lender documents are not shown here.
      </p>
      {actionError ? <ErrorState message={actionError} /> : null}
      {docs.length === 0 ? (
        <p className="text-sm text-slate-600">No documents are available for this application yet.</p>
      ) : (
        <>
          <section className="space-y-3">
            <h2 className="text-sm font-semibold text-slate-900">KYC documents</h2>
            {renderTable(kycDocs)}
          </section>
          <section className="space-y-3">
            <h2 className="text-sm font-semibold text-slate-900">Signed documents</h2>
            {renderTable(signedDocs)}
          </section>
        </>
      )}
      {preview ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="borrower-document-preview-title"
        >
          <div className="w-full max-w-4xl rounded-lg border border-slate-200 bg-white p-5 shadow-lg">
            <div className="flex items-center justify-between gap-2">
              <h3 id="borrower-document-preview-title" className="text-base font-semibold text-slate-900">
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
                <p className="p-3 text-sm text-slate-700">Preview is not supported for this file type.</p>
              )}
            </div>
          </div>
        </div>
      ) : null}
    </div>
  )
}
