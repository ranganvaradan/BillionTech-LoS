import axios from 'axios'
import {
  ApiError,
  SERVICE_UNAVAILABLE_MESSAGE,
  looksLikeHtmlErrorBody,
} from '@/api/http'

function nonEmptyString(v: unknown): string | null {
  if (typeof v !== 'string') return null
  const t = v.trim()
  return t.length > 0 ? t : null
}

function looksTechnical(message: string): boolean {
  const m = message.trim()
  if (!m) return true
  if (looksLikeHtmlErrorBody(m)) return true
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
  SELF_APPROVAL_FORBIDDEN: 'You cannot approve a configuration you submitted. Another reviewer must approve.',
  MULTI_RULE_SET_NOT_ENABLED: 'Phase 1 Policy Sets allow exactly one underwriting rule set. Remove additional rule sets.',
  CONFIGURATION_CONFLICT: 'This configuration conflicts with an existing record. Review codes and versions.',
  POLICY_SET_NOT_READY: 'The linked Policy Set must be ACTIVE before this category can be activated.',
  CATEGORY_NOT_APPROVED: 'Only an APPROVED category can be activated.',
  POLICY_SET_NOT_APPROVED: 'Only an APPROVED Policy Set can be activated.',
  RETIREMENT_REASON_REQUIRED: 'A retirement reason is required.',
  CATEGORY_NOT_EDITABLE: 'Only DRAFT categories can be edited.',
  POLICY_SET_NOT_EDITABLE: 'Only DRAFT Policy Sets can be edited.',
  TERMINOLOGY_CONFLICT_CUSTOMER_ROLE:
    'Customer Role and Intake Segment disagree. Send one value, or matching aliases.',
  TERMINOLOGY_CONFLICT_ENTITY_TYPE:
    'Entity Type and Borrower Type disagree. Send one value, or matching aliases.',
}

const HTTP_FALLBACK: Record<number, string> = {
  400: 'Please check your entries and try again.',
  403: 'You do not have permission to perform this action.',
  404: 'The requested item was not found.',
  409: 'This record conflicts with existing data.',
  422: 'We could not complete this action. Please review the details.',
  500: 'Something went wrong. Please try again shortly.',
  502: SERVICE_UNAVAILABLE_MESSAGE,
  503: SERVICE_UNAVAILABLE_MESSAGE,
  504: SERVICE_UNAVAILABLE_MESSAGE,
}

/** Map API/business-rule failures to user-friendly copy (never raw JSON/HTML). */
export function userFriendlyMessage(err: unknown, fallback: string): string {
  if (err instanceof ApiError) {
    if (err.reason === 'SERVICE_UNAVAILABLE') return SERVICE_UNAVAILABLE_MESSAGE
    if (err.status === 502 || err.status === 503 || err.status === 504) {
      return SERVICE_UNAVAILABLE_MESSAGE
    }
    const reason = err.reason?.trim()
    if (reason && REASON_MESSAGES[reason]) return REASON_MESSAGES[reason]
    const server = nonEmptyString(err.serverMessage)
    if (server && !looksTechnical(server)) return server
    if (err.status != null && HTTP_FALLBACK[err.status]) return HTTP_FALLBACK[err.status]
  }
  if (axios.isAxiosError(err)) {
    if (looksLikeHtmlErrorBody(err.response?.data)) return SERVICE_UNAVAILABLE_MESSAGE
    const status = err.response?.status
    if (status === 502 || status === 503 || status === 504) return SERVICE_UNAVAILABLE_MESSAGE
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
