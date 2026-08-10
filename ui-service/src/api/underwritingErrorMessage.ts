import { ApiError } from './http'

function isRecord(x: unknown): x is Record<string, unknown> {
  return x !== null && typeof x === 'object' && !Array.isArray(x)
}

const JAVA_EXCEPTION =
  /\b(class\s+[\w.$]+\s+cannot be cast|java\.lang\.|ClassCastException|NullPointerException|SqlException|HibernateException|org\.hibernate\.|org\.springframework\.)/i

const MISSING_INFO =
  /\b(missing information|required information is missing|input required|scorecard inputs?|incomplete data|data insufficient)\b/i

const MANUAL_REVIEW =
  /\b(manual (credit )?review|refer|needs? review|credit information needs)\b/i

/**
 * Business-facing copy for Credit Assessment / underwriting actions.
 * Never surfaces raw JVM exception text in the main UI.
 */
export function messageForUnderwritingAction(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.reason === 'MISSING_INFORMATION' || err.reason === 'INPUT_REQUIRED') {
      return 'Credit assessment could not be completed because required information is missing.'
    }
    if (err.reason === 'MANUAL_REVIEW' || err.reason === 'REFER') {
      return 'Some credit information needs to be reviewed before the assessment can be completed.'
    }
    const m = (err.serverMessage && err.serverMessage.trim()) || (err.message && err.message.trim()) || ''
    if (!m || m === 'An unexpected error occurred' || JAVA_EXCEPTION.test(m)) {
      return "We couldn't complete the credit assessment. Please try again or contact support."
    }
    if (MISSING_INFO.test(m)) {
      return 'Credit assessment could not be completed because required information is missing.'
    }
    if (MANUAL_REVIEW.test(m)) {
      return 'Some credit information needs to be reviewed before the assessment can be completed.'
    }
    if ((err.status ?? 0) >= 500) {
      return "We couldn't complete the credit assessment. Please try again or contact support."
    }
    return m
  }
  if (err instanceof Error) {
    if (JAVA_EXCEPTION.test(err.message) || !err.message.trim()) {
      return "We couldn't complete the credit assessment. Please try again or contact support."
    }
    return err.message
  }
  if (typeof err === 'string') {
    if (JAVA_EXCEPTION.test(err) || !err.trim()) {
      return "We couldn't complete the credit assessment. Please try again or contact support."
    }
    return err
  }
  if (isRecord(err) && typeof err.message === 'string') {
    return messageForUnderwritingAction(new Error(err.message))
  }
  return "We couldn't complete the credit assessment. Please try again or contact support."
}
