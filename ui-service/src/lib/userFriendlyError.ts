import axios from 'axios'
import { ApiError } from '@/api/http'

function nonEmptyString(v: unknown): string | null {
  if (typeof v !== 'string') return null
  const t = v.trim()
  return t.length > 0 ? t : null
}

function looksTechnical(message: string): boolean {
  const m = message.trim()
  if (!m) return true
  if (m.startsWith('{') || m.startsWith('[')) return true
  if (/^Request failed$/i.test(m)) return true
  if (/^\d{3}\s/.test(m) && m.length < 80) return true
  return false
}

const REASON_MESSAGES: Record<string, string> = {
  DUPLICATE_EMAIL: 'This email is already used by another application.',
  DUPLICATE_MOBILE: 'This mobile number is already used by another application.',
  DUPLICATE_PANNUMBER: 'This PAN is already used by another application.',
  DUPLICATE_GSTIN: 'This GSTIN is already used by another application.',
  EMAIL_REQUIRED: 'Email is required.',
  EMAIL_INVALID: 'Please enter a valid email address.',
  STATUS_TERMINAL: 'This application can no longer be edited.',
}

const HTTP_FALLBACK: Record<number, string> = {
  400: 'Please check your entries and try again.',
  403: 'You do not have permission to perform this action.',
  404: 'The requested item was not found.',
  409: 'This record conflicts with existing data.',
  422: 'We could not complete this action. Please review the details.',
  500: 'Something went wrong. Please try again shortly.',
}

/** Map API/business-rule failures to user-friendly copy (never raw JSON). */
export function userFriendlyMessage(err: unknown, fallback: string): string {
  if (err instanceof ApiError) {
    const reason = err.reason?.trim()
    if (reason && REASON_MESSAGES[reason]) return REASON_MESSAGES[reason]
    const server = nonEmptyString(err.serverMessage)
    if (server && !looksTechnical(server)) return server
    if (err.status != null && HTTP_FALLBACK[err.status]) return HTTP_FALLBACK[err.status]
  }
  if (axios.isAxiosError(err)) {
    const raw = err.response?.data
    if (raw != null && typeof raw === 'object' && !Array.isArray(raw)) {
      const data = raw as Record<string, unknown>
      const reason = nonEmptyString(data.reason)
      if (reason && REASON_MESSAGES[reason]) return REASON_MESSAGES[reason]
      const fromMessage = nonEmptyString(data.message)
      if (fromMessage && !looksTechnical(fromMessage)) return fromMessage
      const fromDetail = nonEmptyString(data.detail)
      if (fromDetail && !looksTechnical(fromDetail)) return fromDetail
    }
    const status = err.response?.status
    if (status != null && HTTP_FALLBACK[status]) return HTTP_FALLBACK[status]
  }
  if (err instanceof Error) {
    const m = nonEmptyString(err.message)
    if (m && !looksTechnical(m)) return m
  }
  return fallback
}

/** Field key for inline duplicate validation (email, mobile, panNumber, gstin). */
export function duplicateFieldFromError(err: unknown): string | null {
  if (!(err instanceof ApiError)) return null
  const ctxField = err.context?.field
  if (typeof ctxField === 'string' && ctxField.trim()) return ctxField.trim()
  const reason = (err.reason ?? '').toUpperCase()
  if (reason === 'DUPLICATE_EMAIL') return 'email'
  if (reason === 'DUPLICATE_MOBILE') return 'mobile'
  if (reason === 'DUPLICATE_PANNUMBER') return 'panNumber'
  if (reason === 'DUPLICATE_GSTIN') return 'gstin'
  return null
}

export function duplicateFieldErrors(err: unknown): Record<string, string> | null {
  const field = duplicateFieldFromError(err)
  if (!field) return null
  return { [field]: userFriendlyMessage(err, 'This value is already in use on another application.') }
}
