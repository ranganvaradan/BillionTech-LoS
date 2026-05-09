import { http } from './http'
import type { AxiosResponseHeaders, RawAxiosResponseHeaders } from 'axios'
import type { DocumentResponse } from '@/types/document'

/**
 * Multipart upload — uses shared axios client so:
 * - {@code X-User-Id} / {@code X-User-Role} are applied (same as all other API calls)
 * - {@code Content-Type} is not forced to application/json (see {@link http} interceptor for FormData)
 * Backend: POST /api/v1/documents/{applicationId}/upload?documentType=...&kycStepType= optional, part name {@code file}.
 */
export async function listDocuments(applicationId: string): Promise<DocumentResponse[]> {
  const { data } = await http.get<DocumentResponse[]>(`/documents/${applicationId}`)
  return data
}

export async function uploadDocument(
  applicationId: string,
  file: File,
  documentType: string,
  kycStepType?: string,
): Promise<DocumentResponse> {
  const form = new FormData()
  form.append('file', file)
  const q = new URLSearchParams()
  q.set('documentType', documentType)
  if (kycStepType) q.set('kycStepType', kycStepType)
  const { data } = await http.post<DocumentResponse>(`/documents/${applicationId}/upload?${q.toString()}`, form)
  return data
}

function getHeader(
  headers: RawAxiosResponseHeaders | AxiosResponseHeaders,
  name: string,
): string | undefined {
  const v = headers[name]
  if (typeof v === 'string') return v
  if (Array.isArray(v) && typeof v[0] === 'string') return v[0]
  return undefined
}

/** Parse filename from Content-Disposition (RFC 5987 filename* or quoted filename). */
function filenameFromContentDisposition(header: string | undefined): string | null {
  if (!header) return null
  const star = /filename\*=(?:UTF-8''|utf-8'')([^;\s]+)/i.exec(header)
  if (star) {
    try {
      return decodeURIComponent(star[1].replace(/["']$/, '').trim())
    } catch {
      return star[1].trim()
    }
  }
  const quoted = /filename\s*=\s*"((?:\\.|[^"\\])*)"/i.exec(header)
  if (quoted) return quoted[1].replace(/\\(.)/g, '$1')
  const plain = /filename\s*=\s*([^;\s]+)/i.exec(header)
  if (plain) return plain[1].replace(/^["']|["']$/g, '')
  return null
}

export type DocumentBlobResult = { blob: Blob; suggestedFileName?: string }

/** Download bytes with {@code Content-Disposition: attachment}; parses suggested filename when present. */
export async function downloadDocumentAsBlob(documentId: string): Promise<DocumentBlobResult> {
  const res = await http.get<Blob>(`/documents/download/${documentId}`, { responseType: 'blob' })
  const disposition =
    getHeader(res.headers, 'content-disposition') ?? getHeader(res.headers, 'Content-Disposition')
  const suggested = disposition ? filenameFromContentDisposition(disposition) : null
  return { blob: res.data, suggestedFileName: suggested ?? undefined }
}

export async function downloadDocumentBlob(documentId: string): Promise<Blob> {
  const { blob } = await downloadDocumentAsBlob(documentId)
  return blob
}

/** Inline document stream for preview (correct MIME, {@code Content-Disposition: inline}). */
export async function fetchDocumentPreviewBlob(documentId: string): Promise<Blob> {
  const { data } = await http.get<Blob>(`/documents/content/${documentId}`, { responseType: 'blob' })
  return data
}

export async function deleteDocument(documentId: string): Promise<void> {
  await http.delete(`/documents/${documentId}`)
}
