import axios, { type AxiosError } from 'axios'
import { loadSessionUser } from '@/auth/types'
import { xHeadersForUser } from '@/auth/sessionHeaders'

const baseURL =
  (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, '') || '/los/api/v1'

/** Build-time CI token — only set when the target env requires X-Internal-Token. */
const internalToken = (
  import.meta.env.VITE_CREDIT_INTELLIGENCE_INTERNAL_TOKEN as string | undefined
)?.trim()

export const http = axios.create({
  baseURL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 60_000,
  validateStatus: (s) => s >= 200 && s < 300,
})

http.interceptors.request.use((config) => {
  for (const [k, v] of Object.entries(xHeadersForUser(loadSessionUser()))) {
    config.headers.set(k, v)
  }
  if (internalToken) {
    config.headers.set('X-Internal-Token', internalToken)
  }
  // Default JSON content-type would break multipart; let the browser/axios set boundary for FormData.
  if (config.data instanceof FormData) {
    config.headers.delete('Content-Type')
  }
  return config
})

function isRecord(x: unknown): x is Record<string, unknown> {
  return x !== null && typeof x === 'object' && !Array.isArray(x)
}

/**
 * Shape used by {@code GlobalExceptionHandler} (BusinessRuleException):
 * message, reason, action, context.
 * Also tolerates errorCode, detail, errors[] (validation), etc.
 */
export function parseApiErrorResponse(data: unknown): {
  message: string
  reason?: string
  action?: string
  context?: Record<string, unknown>
} {
  if (data == null) return { message: '' }
  if (typeof data === 'string' && data.trim().length) return { message: data.trim() }
  if (!isRecord(data)) return { message: String(data) }

  const reason =
    typeof data.reason === 'string' && data.reason
      ? data.reason
      : typeof data.errorCode === 'string' && data.errorCode
        ? data.errorCode
        : undefined
  const action = typeof data.action === 'string' && data.action ? data.action : undefined
  const context = isRecord(data.context) ? data.context : undefined

  // Spring Boot default error / our GlobalExceptionHandler
  let message = ''
  if (typeof data.message === 'string' && data.message) {
    message = data.message
  } else if (typeof data.detail === 'string' && data.detail) {
    message = data.detail
  } else if (Array.isArray(data.errors)) {
    const parts: string[] = []
    for (const e of data.errors) {
      if (!isRecord(e)) continue
      const field = typeof e.field === 'string' && e.field ? `${e.field}: ` : ''
      const dm = typeof e.defaultMessage === 'string' ? e.defaultMessage : ''
      const em = typeof e.message === 'string' ? e.message : ''
      const line = field + (dm || em)
      if (line) parts.push(line)
    }
    message = parts.length ? parts.join(' · ') : ''
  }

  // Single validation error object (non-array)
  if (!message && isRecord((data as { error?: unknown }).error)) {
    const errObj = (data as { error: unknown }).error
    if (typeof errObj === 'string') message = errObj
  }

  if (!message && typeof (data as { error?: unknown }).error === 'string') {
    const e = (data as { error: string }).error
    if (e && data.status) message = e
  }

  if (!message) {
    try {
      if (isRecord(data) && Object.keys(data).length > 0) {
        message = JSON.stringify(data)
      }
    } catch {
      message = ''
    }
  }

  if (!message) {
    message = 'Request failed'
  }

  return { message, reason, action, context }
}

function primaryErrorMessage(parsed: ReturnType<typeof parseApiErrorResponse>, err: AxiosError<unknown>): string {
  const raw = parsed.message.trim()
  if (raw && raw !== 'Request failed') return raw
  const ax = typeof err.message === 'string' ? err.message.trim() : ''
  if (ax) return ax
  return 'Request failed'
}

export class ApiError extends Error {
  readonly status: number | undefined
  readonly body: unknown
  /**
   * Business rule or machine reason from backend (e.g. same as `reason` in API JSON). Maps to
   * {@code BusinessRuleException.getReason()}.
   */
  readonly reason: string | undefined
  /**
   * Optional map from the API (e.g. stepSummary, kycOutcome, field list).
   */
  readonly context: Record<string, unknown> | undefined
  /**
   * Human-readable message as returned in the response body, before any UI-specific mapping.
   */
  readonly serverMessage: string

  constructor(
    message: string,
    status?: number,
    body?: unknown,
    meta?: { reason?: string; context?: Record<string, unknown>; serverMessage?: string },
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.body = body
    this.serverMessage = meta?.serverMessage ?? message
    this.reason = meta?.reason
    this.context = meta?.context
  }
}

http.interceptors.response.use(
  (r) => r,
  (err: AxiosError<unknown>) => {
    const status = err.response?.status
    const body = err.response?.data
    const parsed = parseApiErrorResponse(body)
    const msg = primaryErrorMessage(parsed, err)
    const serverLine = (() => {
      const t = parsed.message.trim()
      if (t && t !== 'Request failed') return t
      return msg
    })()
    return Promise.reject(
      new ApiError(msg, status, body, {
        reason: parsed.reason,
        context: parsed.context,
        serverMessage: serverLine,
      }),
    )
  },
)
