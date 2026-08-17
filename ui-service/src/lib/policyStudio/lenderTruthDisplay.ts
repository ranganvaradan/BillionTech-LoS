/**
 * Shared lender-facing display from backend canonical truth.
 * Do not invent READY/NOT_READY in TypeScript — prefer backend primaryStatusLabel.
 */

export type CanonicalTruthLike = {
  primaryStatus?: string | null
  primaryStatusLabel?: string | null
  businessReadiness?: string | null
  businessReadinessReason?: string | null
  businessReadinessLabel?: string | null
  nextAction?: string | null
  calculationExplanation?: string | null
  certificationLabel?: string | null
  executionLabel?: string | null
  parameterClassLabel?: string | null
  presentation?: {
    allowedActions?: unknown
    businessReadinessReason?: string | null
    primaryStatus?: string | null
  } | null
  liveUseDisplay?: { label?: string; status?: string; available?: boolean } | null
  execution?: { capability?: boolean; status?: string | null; valueAvailable?: boolean } | null
  certification?: { certificationStatus?: string; status?: string } | null
}

/** Canonical primary lender action for missing calculation definitions. */
export const SETUP_CALCULATION_ACTION = 'Set up calculation'

/**
 * Sole consumer-facing gate for Set up calculation.
 * Do NOT use legacy parameterSupport / SUPPORTED_DERIVED as authority.
 */
export function requiresCalculationSetupAction(
  truth: CanonicalTruthLike | null | undefined,
  extras?: {
    calculationRequired?: boolean | null
    allowedActions?: unknown
    businessReadinessReason?: string | null
    nextAction?: string | null
  },
): boolean {
  const reason = String(
    extras?.businessReadinessReason ??
      truth?.businessReadinessReason ??
      truth?.presentation?.businessReadinessReason ??
      '',
  )
  if (reason === 'CALCULATION_NOT_DEFINED' || reason === 'CALCULATION_INVALID') {
    return true
  }
  const next = String(extras?.nextAction ?? truth?.nextAction ?? '')
  if (next === SETUP_CALCULATION_ACTION) {
    return true
  }
  const actionsRaw = extras?.allowedActions ?? truth?.presentation?.allowedActions
  const actions = Array.isArray(actionsRaw) ? actionsRaw.map(String) : []
  if (actions.includes(SETUP_CALCULATION_ACTION)) {
    return true
  }
  // Explicit calculationRequired from CPS calculation.required — not legacy support buckets
  return extras?.calculationRequired === true
}

/** Prefer backend primaryStatusLabel; never map catalogue PRODUCTION_READY to live. */
export function lenderPrimaryFromTruth(
  truth: CanonicalTruthLike | null | undefined,
  fallbackFlags?: {
    calculationRequired?: boolean | null
    policyTestReady?: boolean | null
    unavailable?: boolean | null
    unresolved?: boolean | null
  },
): { state: string; label: string; detail?: string; nextAction?: string | null } {
  if (truth?.primaryStatusLabel) {
    return {
      state: String(truth.primaryStatus ?? truth.businessReadiness ?? 'FROM_BACKEND'),
      label: String(truth.primaryStatusLabel),
      detail: truth.calculationExplanation ? String(truth.calculationExplanation) : undefined,
      nextAction: truth.nextAction != null ? String(truth.nextAction) : null,
    }
  }
  if (truth?.businessReadinessLabel) {
    return {
      state: String(truth.businessReadiness ?? 'FROM_BACKEND'),
      label: String(truth.businessReadinessLabel),
      nextAction: truth.nextAction != null ? String(truth.nextAction) : null,
    }
  }
  // Fallback only when backend truth missing (legacy clients)
  if (fallbackFlags?.unavailable) {
    return { state: 'DATA_NOT_AVAILABLE', label: 'Data not available' }
  }
  if (fallbackFlags?.unresolved || fallbackFlags?.calculationRequired) {
    return {
      state: fallbackFlags.calculationRequired ? 'NOT_READY' : 'NEEDS_YOUR_INPUT',
      label: fallbackFlags.calculationRequired ? 'Calculation not defined' : 'Needs your input',
    }
  }
  if (fallbackFlags?.policyTestReady) {
    return {
      state: 'READY',
      label: 'Ready',
      detail: 'Production use still requires certification.',
    }
  }
  return { state: 'NEEDS_REVIEW', label: 'Needs review' }
}

export function certificationDisplayLabel(status: unknown): string {
  switch (String(status ?? '')) {
    case 'CERTIFIED':
      return 'Approved for live use'
    case 'REVOKED':
      return 'Approval revoked'
    default:
      return 'Not approved for live use'
  }
}

/** Never treat catalogue production_ready as live authority. */
export function isLiveApprovedFromTruth(truth: CanonicalTruthLike | null | undefined): boolean {
  const st =
    truth?.certification?.certificationStatus ??
    truth?.certification?.status ??
    truth?.liveUseDisplay?.status
  return String(st) === 'CERTIFIED' && truth?.execution?.capability === true
}
