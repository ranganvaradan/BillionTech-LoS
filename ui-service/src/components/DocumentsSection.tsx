import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  deleteDocument,
  downloadDocumentAsBlob,
  fetchDocumentPreviewBlob,
  listDocuments,
  uploadDocument,
} from '@/api/documents'
import { ErrorState } from '@/components/ErrorState'
import { applicationPartyLabels } from '@/lib/applicationPartyLabels'
import { isInvoiceDiscountingAnchorApp } from '@/lib/invoiceDiscountingFlow'
import { formatInstant } from '@/lib/format'
import type { ApplicationIntakeSegment } from '@/types/application'
import type { DocumentResponse } from '@/types/document'

const PRESET_DOC_TYPES = [
  { value: 'PAN_CARD', label: 'PAN card' },
  { value: 'AADHAAR', label: 'Aadhaar' },
  { value: 'BANK_STATEMENT', label: 'Bank statement' },
  { value: 'PHOTOGRAPH', label: 'Photograph' },
  { value: 'INCOME_PROOF', label: 'Income proof (salary / ITR)' },
  { value: 'BORROWER_KYC', label: 'Borrower / KYC (general)' },
  { value: 'PAYSLIP', label: 'Payslip' },
  { value: 'SALARY_SLIP', label: 'Salary slip' },
  { value: 'GST_RETURNS', label: 'GST returns' },
  { value: 'GST_RETURN', label: 'GST return (GSTR)' },
  { value: 'BUSINESS_PROOF', label: 'Business proof (Udyam / license)' },
  { value: 'ITR', label: 'ITR / tax return' },
  { value: 'BOARD_RESOLUTION', label: 'Board resolution' },
  { value: 'SIGNED_AGREEMENT', label: 'Signed agreement' },
  { value: 'PROPERTY_DOCUMENT', label: 'Property document (title / deed)' },
  { value: 'PROPERTY_VALUATION', label: 'Property valuation' },
  { value: 'SHARE_HOLDING_STATEMENT', label: 'Share / demat holding statement' },
  { value: 'GOLD_PHOTO', label: 'Gold / security photo' },
  { value: 'GOLD_VALUATION', label: 'Gold valuation' },
  { value: 'COLLATERAL_OTHER', label: 'Other collateral document' },
  { value: 'OTHER', label: 'Other' },
] as const

const OTHER = 'OTHER'

const DOC_TYPE_LABEL: Record<string, string> = Object.fromEntries(PRESET_DOC_TYPES.map((d) => [d.value, d.label]))

function documentTypeLabel(code: string, intakeSegment?: ApplicationIntakeSegment | null): string {
  if (code === 'BORROWER_KYC') return applicationPartyLabels(intakeSegment).kycDocumentPreset
  if (DOC_TYPE_LABEL[code]) return DOC_TYPE_LABEL[code]
  if (code.startsWith('OTHER_')) return `Other (${code.replace(/^OTHER_/, '')})`
  return code.replaceAll('_', ' ')
}

function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / (1024 * 1024)).toFixed(1)} MB`
}

function inferPreviewContentType(blob: Blob, doc: DocumentResponse): string {
  if (blob.type && blob.type !== 'application/octet-stream') return blob.type
  const stored = doc.contentType
  if (stored && stored !== 'application/octet-stream') return stored
  const name = (doc.fileName || '').toLowerCase()
  if (name.endsWith('.pdf')) return 'application/pdf'
  if (name.endsWith('.png')) return 'image/png'
  if (name.endsWith('.jpg') || name.endsWith('.jpeg')) return 'image/jpeg'
  if (name.endsWith('.webp')) return 'image/webp'
  if (name.endsWith('.gif')) return 'image/gif'
  return blob.type || stored || 'application/octet-stream'
}

function isPdfContentType(ct: string): boolean {
  return ct.toLowerCase().includes('pdf')
}

function isImageContentType(ct: string): boolean {
  return ct.toLowerCase().startsWith('image/')
}

function IconEye({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  )
}

function IconDownload({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3" />
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

function buildDocumentType(preset: string, otherLabel: string): string {
  if (preset !== OTHER) return preset
  const t = otherLabel.trim()
  return t
    ? `OTHER_${t
        .replace(/[^a-zA-Z0-9_-]+/g, '_')
        .replace(/_+/g, '_')
        .slice(0, 64)}`
    : 'OTHER'
}

export function DocumentsSection({
  applicationId,
  intakeSegment,
  appStatus: _appStatus,
  loanProduct,
}: {
  applicationId: string
  intakeSegment?: ApplicationIntakeSegment | null
  appStatus?: string
  loanProduct?: string
}) {
  const isAnchor = isInvoiceDiscountingAnchorApp({ intakeSegment: intakeSegment ?? 'BORROWER', loanProduct: loanProduct ?? '' })
  const presetDocTypes = useMemo(() => {
    const kycLabel = applicationPartyLabels(intakeSegment).kycDocumentPreset
    const base = PRESET_DOC_TYPES.map((p) => (p.value === 'BORROWER_KYC' ? { ...p, label: kycLabel } : p))
    if (isAnchor) {
      const anchorFirst = ['BOARD_RESOLUTION', 'SIGNED_AGREEMENT', 'GST_RETURN', 'BUSINESS_PROOF', 'PAN_CARD', 'BANK_STATEMENT']
      return [...base].sort((a, b) => {
        const ai = anchorFirst.indexOf(a.value)
        const bi = anchorFirst.indexOf(b.value)
        if (ai === -1 && bi === -1) return 0
        if (ai === -1) return 1
        if (bi === -1) return -1
        return ai - bi
      })
    }
    return base
  }, [intakeSegment, isAnchor])
  const [docs, setDocs] = useState<DocumentResponse[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [preset, setPreset] = useState<string>(presetDocTypes[0].value)
  const [otherLabel, setOtherLabel] = useState('')
  const [uploading, setUploading] = useState(false)
  const [previewLoading, setPreviewLoading] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [uploadOk, setUploadOk] = useState<string | null>(null)
  const [preview, setPreview] = useState<{
    documentId: string
    fileName: string
    contentType: string
    url: string
  } | null>(null)

  const effectiveType = useMemo(() => buildDocumentType(preset, otherLabel), [preset, otherLabel])

  const load = useCallback(async () => {
    setLoadError(null)
    try {
      const list = await listDocuments(applicationId)
      setDocs(list)
    } catch (e) {
      setDocs(null)
      setLoadError(e instanceof Error ? e.message : 'Could not load documents')
    }
  }, [applicationId])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- load() is async; linter cannot prove setState is deferred
    void load()
  }, [load])

  useEffect(() => {
    return () => {
      if (preview?.url) URL.revokeObjectURL(preview.url)
    }
  }, [preview])

  async function onFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    if (preset === OTHER && !otherLabel.trim()) {
      setActionError('Enter a short label for the document, or pick a preset type.')
      return
    }
    setActionError(null)
    setUploadOk(null)
    setUploading(true)
    try {
      const d = await uploadDocument(applicationId, file, effectiveType)
      await load()
      setUploadOk(
        d.fileName
          ? `Uploaded “${d.fileName}” as ${d.documentType}.`
          : `Document uploaded as ${d.documentType}.`,
      )
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Upload failed')
    } finally {
      setUploading(false)
    }
  }

  async function onDownload(d: DocumentResponse) {
    setActionError(null)
    try {
      const { blob, suggestedFileName } = await downloadDocumentAsBlob(d.id)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = suggestedFileName || d.fileName || `document-${d.id}`
      a.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Download failed')
    }
  }

  async function onDelete(id: string) {
    if (!window.confirm('Delete this document?')) return
    setActionError(null)
    try {
      await deleteDocument(id)
      await load()
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Delete failed')
    }
  }

  async function onPreview(d: DocumentResponse) {
    setActionError(null)
    setPreviewLoading(true)
    try {
      const blob = await fetchDocumentPreviewBlob(d.id)
      const effectiveType = inferPreviewContentType(blob, d)
      const viewBlob =
        blob.type === effectiveType ? blob : new Blob([blob], { type: effectiveType })
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

  return (
    <div className="space-y-4">
      {uploadOk ? (
        <div
          className="rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-900"
          role="status"
        >
          {uploadOk}
        </div>
      ) : null}
      {actionError ? <ErrorState message={actionError} /> : null}
      <div className="flex flex-wrap items-end gap-3 bt-section-card bt-section-card--hero p-4">
        <label className="block text-sm text-slate-700">
          <span className="mb-1 block text-xs font-medium text-slate-500">Document type</span>
          <select
            className="w-56 rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm"
            value={preset}
            onChange={(e) => {
              setPreset(e.target.value)
              setActionError(null)
            }}
          >
            {presetDocTypes.map((d) => (
              <option key={d.value} value={d.value}>
                {d.label}
              </option>
            ))}
          </select>
        </label>
        {preset === OTHER ? (
          <label className="block min-w-[12rem] text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Other — short label</span>
            <input
              className="w-full rounded-md border border-slate-300 px-3 py-1.5 text-sm"
              value={otherLabel}
              onChange={(e) => setOtherLabel(e.target.value)}
              placeholder="e.g. Partnership deed"
            />
          </label>
        ) : null}
        <label className="inline-flex cursor-pointer items-center rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white disabled:opacity-50">
          {uploading ? 'Uploading…' : 'Choose file'}
          <input type="file" className="sr-only" onChange={(e) => void onFileChange(e)} disabled={uploading} />
        </label>
        <p className="text-xs text-slate-500">
          Stored in the document service for this application. Type {preset === OTHER ? 'uses your label' : 'is a filing tag'}
          {preset === OTHER && otherLabel.trim() ? ` (stored as ${effectiveType})` : null}
        </p>
      </div>

      {docs.length === 0 ? (
        <p className="text-sm text-slate-600">No documents uploaded yet.</p>
      ) : (
        <div className="bt-card overflow-x-auto">
          <table className="bt-table min-w-full">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs font-medium text-slate-600">
              <tr>
                <th className="px-3 py-2">Name</th>
                <th className="px-3 py-2">Type</th>
                <th className="px-3 py-2">Size</th>
                <th className="px-3 py-2">Uploaded</th>
                <th className="px-3 py-2"> </th>
              </tr>
            </thead>
            <tbody className="">
              {docs.map((d) => (
                <tr key={d.id}>
                  <td className="px-3 py-2 text-slate-900">{d.fileName}</td>
                  <td className="px-3 py-2 text-slate-600">{documentTypeLabel(d.documentType, intakeSegment)}</td>
                  <td className="px-3 py-2 tabular-nums text-slate-600">{formatBytes(d.fileSize)}</td>
                  <td className="px-3 py-2 text-slate-600">{formatInstant(d.createdAt)}</td>
                  <td className="px-3 py-2">
                    <div className="flex flex-wrap gap-2">
                      <button
                        type="button"
                        onClick={() => void onPreview(d)}
                        disabled={previewLoading}
                        className="inline-flex items-center justify-center text-slate-800 disabled:opacity-50"
                        title="Preview"
                        aria-label="Preview"
                      >
                        <IconEye className="h-4 w-4" />
                      </button>
                      <button
                        type="button"
                        onClick={() => void onDownload(d)}
                        className="inline-flex items-center justify-center text-slate-800"
                        title="Download"
                        aria-label="Download"
                      >
                        <IconDownload className="h-4 w-4" />
                      </button>
                      <button
                        type="button"
                        onClick={() => void onDelete(d.id)}
                        className="inline-flex items-center justify-center text-red-700"
                        title="Delete"
                        aria-label="Delete"
                      >
                        <IconTrash className="h-4 w-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {preview ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="document-preview-title"
        >
          <div className="w-full max-w-4xl rounded-lg border border-slate-200 bg-white p-5 shadow-lg">
            <div className="flex items-center justify-between gap-2">
              <h3 id="document-preview-title" className="text-base font-semibold text-slate-900">
                Document Preview
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
                <div className="p-3 text-sm text-slate-700">
                  Preview not supported
                  <div className="mt-2">
                    <button
                      type="button"
                      onClick={() => {
                        const id = preview.documentId
                        const row = (docs ?? []).find((x) => x.id === id)
                        if (row) void onDownload(row)
                      }}
                      className="font-medium text-slate-800 underline"
                    >
                      Download
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      ) : null}
    </div>
  )
}
