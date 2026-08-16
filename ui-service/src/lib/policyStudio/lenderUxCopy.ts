/**
 * POLICY-STUDIO-LENDER-UX-SIMPLIFICATION-1
 * Layer-1 lender-facing copy. Technical codes stay in Advanced / admin only.
 */

export type LenderSetupState =
  | 'READY_TO_TEST'
  | 'NEEDS_YOUR_INPUT'
  | 'CALCULATION_READY'
  | 'DATA_NOT_AVAILABLE'
  | 'NEEDS_REVIEW'
  | 'READY'

/** Single primary status for lender surfaces (Rules / Inventory / Test / Scorecard).
 * Wave-9: prefer backend canonicalTruth.primaryStatus via lenderTruthDisplay.lenderPrimaryFromTruth.
 * Local flag derivation is a compatibility fallback only — not capability authority.
 */
export function lenderPrimaryStatus(flags: {
  calculationRequired?: boolean | null
  policyTestReady?: boolean | null
  runtimeReady?: boolean | null
  productionReady?: boolean | null
  unresolved?: boolean | null
  unavailable?: boolean | null
  primaryStatusLabel?: string | null
  primaryStatus?: string | null
  calculationExplanation?: string | null
  nextAction?: string | null
}): { state: LenderSetupState | string; label: string; detail?: string } {
  if (flags.primaryStatusLabel) {
    return {
      state: (flags.primaryStatus as LenderSetupState) || 'READY_TO_TEST',
      label: String(flags.primaryStatusLabel),
      detail: flags.calculationExplanation ? String(flags.calculationExplanation) : undefined,
    }
  }
  // productionReady flag must NEVER imply certified live use
  if (flags.unavailable) {
    return {
      state: 'DATA_NOT_AVAILABLE',
      label: 'Data not available',
      detail: 'Understood, but this is not available from current data sources.',
    }
  }
  if (flags.unresolved) {
    return {
      state: 'NEEDS_YOUR_INPUT',
      label: 'Needs your input',
      detail: 'Map this to a known parameter before the rule can run.',
    }
  }
  if (flags.calculationRequired === true) {
    return {
      state: 'NEEDS_YOUR_INPUT',
      label: 'Calculation needs setup',
      detail:
        'I have related data, but I need to understand how you want this calculated before I can apply the rule.',
    }
  }
  if (flags.policyTestReady === true) {
    return {
      state: 'READY_TO_TEST',
      label: 'Ready to test',
      detail: 'This rule is ready to test. Production use still requires certification.',
    }
  }
  if (flags.runtimeReady === true) {
    return {
      state: 'READY',
      label: 'Ready to test',
      detail: 'Available for evaluation when data is present. Live use requires certification.',
    }
  }
  return {
    state: 'NEEDS_REVIEW',
    label: 'Needs review',
    detail: 'Review this item before relying on it in underwriting.',
  }
}

export function lenderSupportLabel(supportStatus: unknown): string {
  const s = String(supportStatus ?? '')
  switch (s) {
    case 'SUPPORTED_RAW':
      return 'Provided directly'
    case 'SUPPORTED_DERIVED':
      return 'Calculated by BillionTech'
    case 'CALCULATION_NOT_IMPLEMENTED':
    case 'SUPPORT_CALCULATION_NOT_IMPLEMENTED':
      return 'Needs your input'
    case 'PROVIDER_DOES_NOT_SUPPORT':
      return 'Data not available from provider'
    case 'SOURCE_NOT_INTEGRATED':
      return 'Data source not connected yet'
    case 'NOT_APPLICABLE':
      return 'Application or internal input'
    default:
      return s ? 'Needs review' : '—'
  }
}

/** Soften technical readiness codes for Layer 1; keep codes for Advanced. */
export function lenderOverallReadinessLabel(code: unknown): string {
  switch (String(code ?? '')) {
    case 'PRODUCTION_READY':
      // Wave-9: never present catalogue PRODUCTION_READY as live approval
      return 'Listed — not certification'
    case 'APPROVED_FOR_LIVE_USE':
      return 'Approved for live use'
    case 'READY_TO_TEST':
      return 'Ready to test'
    case 'CALCULATION_NEEDS_SETUP':
      return 'Calculation needs setup'
    case 'CAN_CALCULATE_WHEN_DATA_AVAILABLE':
      return 'Can calculate when data is available'
    case 'NEEDS_MANUAL_INPUT':
      return 'Needs your input'
    case 'NOT_YET_SUPPORTED':
      return 'Not yet supported'
    case 'APPROVAL_REVOKED':
      return 'Approval revoked'
    case 'RUNTIME_READY_NONPROD':
      return 'Ready to test'
    case 'POLICY_TEST_ONLY':
      return 'Ready to test'
    case 'CATALOGUE_ONLY':
      return 'Listed — setup incomplete'
    case 'READINESS_UNKNOWN':
      return 'Needs review'
    default:
      return code ? String(code) : '—'
  }
}

export function sanitizeLenderTechnicalPhrase(text: string): string {
  return text
    .replace(/\bGACAT\b/gi, 'parameter catalogue')
    .replace(/\bcanonical (parameter )?id\b/gi, 'parameter')
    .replace(/\bREF\b/g, 'reference')
    .replace(/\bRAW\b/g, 'directly provided')
    .replace(/\bDERIVED\b/g, 'calculated')
    .replace(/\bNEEDS_INPUT\b/g, 'needs your input')
    .replace(/\bCALCULATION_NOT_IMPLEMENTED\b/g, 'calculation required')
    .replace(/\bPRODUCTION_READY\b/g, 'production certified')
    .replace(/\bRUNTIME_READY\b/g, 'runtime ready')
    .replace(/\bDATA_INSUFFICIENT\b/g, 'not enough data')
    .replace(/\btyped expression\b/gi, 'calculation')
    .replace(/\bsemantic(ally)? compatible\b/gi, 'suitable')
    .replace(/\bsemantic compatibility\b/gi, 'fit')
    .replace(/\bvocabulary configuration\b/gi, 'business definition')
    .replace(/\bexecutable derivation\b/gi, 'calculation')
    .replace(/\bcatalogue metadata\b/gi, 'available data')
    .replace(/\bdependency graphs?\b/gi, 'inputs')
    .replace(/\bJSON expressions?\b/gi, 'calculation')
    .replace(/\bCustomer-defined\b/gi, 'Business-defined')
}

export const LENDER_SETUP_EXAMPLE =
  'Count consecutive months after the last overdue in which there were no further overdue payments.'

/**
 * DERIVED-CALCULATION-PROPOSAL-UX-POLISH-1
 * Presentation helpers only — strip duplicate heading / I'll-use / Result from stored explanation.
 */
export function formatCanCalculateNarrative(explanation: string): string {
  let t = sanitizeLenderTechnicalPhrase(explanation ?? '')
  t = t.replace(/^I can calculate this\.?\s*/i, '')
  t = t.replace(/\n*I'll use:[\s\S]*$/i, '')
  t = t.replace(/\n*Result:\s*[^\n]*\s*$/i, '')
  t = t.replace(
    /count the number of months since that overdue/gi,
    'count the number of completed months since that overdue',
  )
  t = t.replace(
    /count the number of months from that overdue/gi,
    'count the number of completed months from that overdue',
  )
  t = t.replace(/then count the number of months since/gi, 'then count the number of completed months since')
  return t.trim()
}

/** One business-facing input list; collapse raw/duplicate catalogue labels. */
export function businessFacingInputLabels(
  deps: Array<{ parameterId?: string; displayName?: string; role?: string }>,
): string[] {
  const labels: string[] = []
  const seen = new Set<string>()
  const add = (label: string) => {
    const key = label.trim().toLowerCase()
    if (!key || seen.has(key)) return
    seen.add(key)
    labels.push(label.trim())
  }

  for (const d of deps ?? []) {
    const id = String(d.parameterId ?? '').toLowerCase()
    if (id.includes('payment_history')) {
      add('Bureau payment history')
      continue
    }
    if (id.includes('dpd') || id.includes('days_past_due') || id.includes('dayspastdue')) {
      add('Days past due (DPD)')
      continue
    }
    if (id.includes('report.date') || id.includes('report_date')) {
      add('Reporting month/date')
      continue
    }
    const name = String(d.displayName ?? '').trim()
    if (!name) continue
    if (/payment history/i.test(name)) {
      add('Bureau payment history')
      continue
    }
    if (/dpd|days past due/i.test(name)) {
      add('Days past due (DPD)')
      continue
    }
    if (/report(ing)?\s*(month|date)?/i.test(name)) {
      add('Reporting month/date')
      continue
    }
    add(name)
  }

  // When dated payment history is present, surface the conceptual trio lenders expect.
  if (labels.some((l) => /payment history/i.test(l))) {
    if (!labels.some((l) => /dpd|days past due/i.test(l))) add('Days past due (DPD)')
    if (!labels.some((l) => /reporting|report/i.test(l))) add('Reporting month/date')
  }

  return labels
}

export function formatCalculationResultLabel(businessName?: string): string {
  const raw = (businessName ?? '').trim()
  if (!raw) return 'Clean history months'
  return raw.replace(/\s*\([^)]*\)\s*$/, '').trim() || raw
}
