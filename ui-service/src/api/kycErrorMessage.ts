import { ApiError } from './http'

const REASON_MESSAGES: Record<string, string> = {
  KYC_RETRY_NOT_ALLOWED: 'Retry is only allowed when KYC has failed',
  /** Strict workflow: `WorkflowExecutionCoordinator` — step not in product workflow */
  WORKFLOW_STEP_NOT_IN_CONFIG: 'No workflow configured for this product, or this step is not part of the active workflow.',
}

/** `ResourceNotFoundException` for missing workflow in WorkflowEngineServiceImpl */
const NO_ACTIVE_WORKFLOW = /^No active workflow for\b/i
const NOT_ENABLED_IN_WORKFLOW = /not enabled in the active workflow for this product/i

function isRecord(x: unknown): x is Record<string, unknown> {
  return x !== null && typeof x === 'object' && !Array.isArray(x)
}

/**
 * User-facing line for the KYC section. Maps known {@link ApiError#reason} codes and workflow messages;
 * rewrites the generic 500 copy when the server returns it.
 */
export function messageForKycAction(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.reason) {
      const byReason = REASON_MESSAGES[err.reason]
      if (byReason) return byReason
    }
    const m = (err.serverMessage && err.serverMessage.trim()) || (err.message && err.message.trim()) || ''
    if (m === 'An unexpected error occurred') {
      return 'The server could not complete this request. Please try again in a moment.'
    }
    if (NO_ACTIVE_WORKFLOW.test(m) || NOT_ENABLED_IN_WORKFLOW.test(m)) {
      return 'No workflow configured for this product. An admin may need to add an active workflow for this borrower type and product.'
    }
    if (m) return m
  }
  if (err instanceof Error) return err.message
  if (typeof err === 'string') return err
  return 'We could not complete the request. Please try again.'
}

/** After HTTP 200 KYC run with step failures, surface first failure to the user. */
export function messageFromKycRunOutput(
  out: Record<string, unknown> | null | undefined,
): string | null {
  if (out == null || typeof out !== 'object') return null
  const allPassed = out.allPassed
  if (allPassed === true) return null

  const results = out.results
  if (!Array.isArray(results) || results.length === 0) {
    if (out.failureCount != null && Number(out.failureCount) > 0) {
      return 'One or more KYC steps failed. Expand “Last flow response” for details.'
    }
    return null
  }

  for (const r of results) {
    if (!isRecord(r)) continue
    if (String(r.outcome) !== 'FAILURE') continue
    const step = typeof r.stepType === 'string' ? r.stepType : 'step'
    const prov = typeof r.provider === 'string' && r.provider ? ` (${r.provider})` : ''
    const em = typeof r.errorMessage === 'string' && r.errorMessage ? `: ${r.errorMessage}` : ''
    return `KYC step ${step}${prov} failed${em}`
  }

  if (out.failureCount != null && Number(out.failureCount) > 0) {
    return 'One or more KYC steps failed. See “Last flow response” for provider details.'
  }
  return null
}
