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

/** Single primary status for lender surfaces (Rules / Inventory / Test / Scorecard). */
export function lenderPrimaryStatus(flags: {
  calculationRequired?: boolean | null
  policyTestReady?: boolean | null
  runtimeReady?: boolean | null
  productionReady?: boolean | null
  unresolved?: boolean | null
  unavailable?: boolean | null
}): { state: LenderSetupState; label: string; detail?: string } {
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
      label: 'Needs your input',
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
      label: 'Ready',
      detail: 'Available for evaluation when data is present.',
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
      return 'Certified for production'
    case 'RUNTIME_READY_NONPROD':
      return 'Ready (non-production)'
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
