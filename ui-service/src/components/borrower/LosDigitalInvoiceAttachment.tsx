import { useCallback, useEffect, useState } from 'react'
import { http } from '@/api/http'
import { ApiError } from '@/api/http'

export type LosDigitalInvoiceFile = {
  blob: Blob
  contentType: string
  filename: string
}

function parseFilenameFromContentDisposition(cd: string | undefined): string | null {
  if (!cd) return null
  const quoted = /filename="([^"]+)"/i.exec(cd)
  if (quoted) return quoted[1]
  const plain = /filename=([^;\n]+)/i.exec(cd)
  return plain ? plain[1].trim().replace(/"/g, '') : null
}

function isPdfContentType(ct: string): boolean {
  return ct.includes('application/pdf')
}

function isImageContentType(ct: string): boolean {
  return ct.startsWith('image/')
}

function triggerBlobDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.rel = 'noopener noreferrer'
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 60_000)
}

export async function fetchLosDigitalInvoiceFile(invoiceId: string): Promise<LosDigitalInvoiceFile> {
  const res = await http.get<Blob>(`/borrower/invoice-discounting/invoices/${invoiceId}/digital-invoice`, {
    responseType: 'blob',
  })
  const blob = res.data
  const cd = res.headers['content-disposition']
  const parsedName = parseFilenameFromContentDisposition(
    typeof cd === 'string' ? cd : Array.isArray(cd) ? cd[0] : undefined,
  )
  const ctHeader = res.headers['content-type']
  const contentType =
    (typeof ctHeader === 'string' ? ctHeader : Array.isArray(ctHeader) ? ctHeader[0] : '') ||
    blob.type ||
    'application/octet-stream'
  return {
    blob,
    contentType,
    filename: parsedName || 'digital-invoice',
  }
}

function IconEye({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.75} aria-hidden>
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.964-7.178z"
      />
      <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
    </svg>
  )
}

function IconDownload({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.75} aria-hidden>
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5M7.5 10.5L12 15m0 0l4.5-4.5M12 15V3"
      />
    </svg>
  )
}

type PreviewState = {
  url: string
  filename: string
  contentType: string
}

type Props = {
  invoiceId: string
  fileName?: string | null
}

export function LosDigitalInvoiceAttachment({ invoiceId, fileName }: Props) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [preview, setPreview] = useState<PreviewState | null>(null)

  const closePreview = useCallback(() => {
    setPreview((p) => {
      if (p?.url) URL.revokeObjectURL(p.url)
      return null
    })
  }, [])

  useEffect(() => () => {
    if (preview?.url) URL.revokeObjectURL(preview.url)
  }, [preview?.url])

  const loadFile = async (): Promise<LosDigitalInvoiceFile> => {
    setLoading(true)
    setError(null)
    try {
      return await fetchLosDigitalInvoiceFile(invoiceId)
    } catch (e: unknown) {
      const msg = e instanceof ApiError ? e.message : e instanceof Error ? e.message : 'Could not load invoice copy'
      setError(msg)
      throw e
    } finally {
      setLoading(false)
    }
  }

  const onPreview = async () => {
    try {
      const file = await loadFile()
      if (!isPdfContentType(file.contentType) && !isImageContentType(file.contentType)) {
        triggerBlobDownload(file.blob, file.filename)
        return
      }
      closePreview()
      const url = URL.createObjectURL(file.blob)
      setPreview({ url, filename: file.filename, contentType: file.contentType })
    } catch {
      /* error set */
    }
  }

  const onDownload = async () => {
    try {
      const file = await loadFile()
      triggerBlobDownload(file.blob, file.filename)
    } catch {
      /* error set */
    }
  }

  if (!fileName) {
    return <span className="text-slate-400 text-xs">—</span>
  }

  return (
    <>
      <div className="relative inline-flex items-center justify-start gap-1 whitespace-nowrap align-middle">
        <button
          type="button"
          onClick={() => void onPreview()}
          disabled={loading}
          title="Preview invoice copy"
          aria-label={`Preview ${fileName}`}
          className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-slate-600 hover:bg-slate-100 hover:text-sky-700 disabled:opacity-50"
        >
          <IconEye className="h-4 w-4" />
        </button>
        <button
          type="button"
          onClick={() => void onDownload()}
          disabled={loading}
          title="Download invoice copy"
          aria-label={`Download ${fileName}`}
          className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-slate-600 hover:bg-slate-100 hover:text-sky-700 disabled:opacity-50"
        >
          <IconDownload className="h-4 w-4" />
        </button>
        {error ? (
          <span
            className="absolute left-1/2 top-full z-10 mt-0.5 -translate-x-1/2 whitespace-nowrap rounded bg-rose-600 px-1.5 py-0.5 text-[10px] text-white shadow"
            title={error}
          >
            {error}
          </span>
        ) : null}
      </div>
      {preview ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="los-digital-invoice-preview-title"
          onClick={closePreview}
        >
          <div
            className="w-full max-w-4xl rounded-xl border border-slate-200 bg-white p-5 shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between gap-3">
              <h3 id="los-digital-invoice-preview-title" className="text-base font-semibold text-slate-900">
                Invoice copy
              </h3>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => void onDownload()}
                  className="inline-flex items-center gap-1 rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
                >
                  <IconDownload className="h-3.5 w-3.5" />
                  Download
                </button>
                <button
                  type="button"
                  onClick={closePreview}
                  className="rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
                >
                  Close
                </button>
              </div>
            </div>
            <p className="mt-1 truncate text-xs text-slate-500">{preview.filename}</p>
            <div className="mt-3 max-h-[70vh] overflow-auto rounded-lg border border-slate-200 bg-slate-50 p-2">
              {isPdfContentType(preview.contentType) ? (
                <iframe
                  title={preview.filename}
                  src={preview.url}
                  className="h-[65vh] w-full rounded border-0 bg-white"
                />
              ) : isImageContentType(preview.contentType) ? (
                <img src={preview.url} alt={preview.filename} className="mx-auto h-auto max-w-full rounded" />
              ) : (
                <p className="p-4 text-sm text-slate-600">Preview is not available for this file type. Use download.</p>
              )}
            </div>
          </div>
        </div>
      ) : null}
    </>
  )
}
