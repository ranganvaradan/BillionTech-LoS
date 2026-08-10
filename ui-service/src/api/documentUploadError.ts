import { ApiError } from './http'

/**
 * Business-facing upload error — never surfaces stack traces / filesystem paths.
 */
export function messageForDocumentUpload(err: unknown): string {
  if (err instanceof ApiError) {
    const reason = (err.reason ?? '').toUpperCase()
    if (reason === 'DOCUMENT_STORAGE_UNAVAILABLE') {
      return 'Document storage unavailable. Please try again or contact support.'
    }
    if (reason === 'DOCUMENT_UNSUPPORTED_TYPE') {
      return 'Unsupported file type. Upload a PDF, JPG, or PNG.'
    }
    if (reason === 'DOCUMENT_EMPTY') {
      return 'Upload could not be completed — no file was received.'
    }
    if (err.status === 413) {
      return 'File exceeds allowed size'
    }
    const m = (err.serverMessage && err.serverMessage.trim()) || (err.message && err.message.trim()) || ''
    if (/accessdenied|permission denied|storage unavailable/i.test(m)) {
      return 'Document storage unavailable. Please try again or contact support.'
    }
    if (/exceeds|too large|max.?upload|payload/i.test(m)) {
      return 'File exceeds allowed size'
    }
    if (/unsupported file type/i.test(m)) {
      return 'Unsupported file type. Upload a PDF, JPG, or PNG.'
    }
    if (/document upload failed:/i.test(m)) {
      return 'Upload could not be completed. Please try again.'
    }
    if (m && m !== 'An unexpected error occurred') return m
  }
  if (err instanceof Error && err.message.trim()) {
    if (/accessdenied|permission denied/i.test(err.message)) {
      return 'Document storage unavailable. Please try again or contact support.'
    }
    return err.message
  }
  return 'Upload could not be completed. Please try again.'
}

/** Soft client-side checks before multipart POST (backend remains authoritative). */
export function validateIdentityDocumentFile(file: File): string | null {
  const name = file.name || ''
  const ext = name.includes('.') ? name.slice(name.lastIndexOf('.') + 1).toLowerCase() : ''
  const allowed = new Set(['pdf', 'jpg', 'jpeg', 'png'])
  if (ext && !allowed.has(ext)) {
    return 'Unsupported file type. Upload a PDF, JPG, or PNG.'
  }
  // Align with staging spring.servlet.multipart.max-file-size (12MB)
  const maxBytes = 12 * 1024 * 1024
  if (file.size > maxBytes) {
    return 'File exceeds allowed size'
  }
  return null
}
