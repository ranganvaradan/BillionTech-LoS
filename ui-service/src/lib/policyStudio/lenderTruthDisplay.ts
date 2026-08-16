/**
 * Shared lender-facing display from backend canonical truth (Wave 9).
 * Do not invent capability / certification in TypeScript.
 */

export type CanonicalTruthLike = {
  primaryStatus?: string | null
  primaryStatusLabel?: string | null
  nextAction?: string | null
  calculationExplanation?: string | null
  certificationLabel?: string | null
  executionLabel?: string | null
  parameterClassLabel?: string | null
  liveUseDisplay?: { label?: string; status?: string; available?: boolean } | null
  execution?: { capability?: boolean; status?: string | null } | null
  certification?: { certificationStatus?: string; status?: string } | null
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
      state: String(truth.primaryStatus ?? 'FROM_BACKEND'),
      label: String(truth.primaryStatusLabel),
      detail: truth.calculationExplanation ? String(truth.calculationExplanation) : undefined,
      nextAction: truth.nextAction != null ? String(truth.nextAction) : null,
    }
  }
  // Fallback only when backend truth missing (legacy clients)
  if (fallbackFlags?.unavailable) {
    return { state: 'DATA_NOT_AVAILABLE', label: 'Data not available' }
  }
  if (fallbackFlags?.unresolved || fallbackFlags?.calculationRequired) {
    return {
      state: fallbackFlags.calculationRequired ? 'CALCULATION_NEEDS_SETUP' : 'NEEDS_YOUR_INPUT',
      label: fallbackFlags.calculationRequired ? 'Calculation needs setup' : 'Needs your input',
    }
  }
  if (fallbackFlags?.policyTestReady) {
    return {
      state: 'READY_TO_TEST',
      label: 'Ready to test',
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
