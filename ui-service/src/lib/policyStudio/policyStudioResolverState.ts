/**
 * Wave 10A — single state-driven Policy Studio parameter/resolver presentation.
 * Surfaces may render actions differently; they must not invent alternate truth.
 */

import {
  lenderPrimaryFromTruth,
  type CanonicalTruthLike,
} from '@/lib/policyStudio/lenderTruthDisplay'

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

export type PolicyStudioResolverCase =
  | 'EXECUTABLE'
  | 'REVIEW_PROPOSAL'
  | 'SETUP_CALCULATION'
  | 'MANUAL_INPUT'
  | 'UNRESOLVED_MAP'
  | 'CHANGE_PARAMETER'
  | 'RULE_REVIEW_ONLY'

export type PolicyStudioOperandPresentation = {
  case: PolicyStudioResolverCase
  parameterLabel: string
  parameterState: string
  showCalculationResolver: boolean
  showManualResolver: boolean
  showMapResolver: boolean
  resolverActionLabel: string | null
  explanation: string | null
  capability: boolean
}

export function truthFromOperand(op: Record<string, unknown>): CanonicalTruthLike {
  const nested = asRecord(op.canonicalParameterState)
  if (Object.keys(nested).length) return nested as CanonicalTruthLike
  const legacy = asRecord(op.canonicalTruth)
  if (Object.keys(legacy).length) return legacy as CanonicalTruthLike
  return {
    primaryStatus: op.primaryStatus != null ? String(op.primaryStatus) : null,
    primaryStatusLabel: op.primaryStatusLabel != null ? String(op.primaryStatusLabel) : null,
    nextAction: op.nextAction != null ? String(op.nextAction) : null,
    calculationExplanation:
      op.calculationExplanation != null ? String(op.calculationExplanation) : null,
    execution: asRecord(op.execution) as CanonicalTruthLike['execution'],
    certification: asRecord(op.certification) as CanonicalTruthLike['certification'],
  }
}

/**
 * Derive resolver presentation from CanonicalParameterState + PolicyRuleState context.
 * Frontend must not invent primary status — only map enum → actions/copy.
 */
export function derivePolicyStudioOperandPresentation(
  op: Record<string, unknown>,
  opts?: {
    proposalReadyForReview?: boolean | null
    ruleNeedsReview?: boolean | null
  },
): PolicyStudioOperandPresentation {
  const truth = truthFromOperand(op)
  const execution = asRecord(truth.execution)
  const semantic = asRecord((truth as Record<string, unknown>).semantic)
  const paramClass = String(semantic.parameterClass ?? '')
  const capability =
    execution.capability === true ||
    Boolean(asRecord(asRecord(op.canonicalParameterState).execution).capability) ||
    Boolean(asRecord(asRecord(op.canonicalTruth).execution).capability) ||
    (op.policyTestReady === true && op.calculationRequired !== true)

  const primary = lenderPrimaryFromTruth(truth, {
    calculationRequired: capability ? false : op.calculationRequired === true,
    policyTestReady: capability || op.policyTestReady === true,
    unavailable: op.unavailable === true,
    unresolved: op.unresolved === true,
  })

  const primaryStatus = String(truth.primaryStatus ?? primary.state ?? '')
  const manual =
    primaryStatus === 'NEEDS_MANUAL_INPUT' ||
    op.manualInput === true ||
    String(op.availability ?? '').toUpperCase() === 'MANUAL'

  // SOURCE_INGREDIENT — never calculation setup
  if (paramClass === 'INGREDIENT' || String(op.parameterClassLabel ?? '').includes('Source ingredient')) {
    return {
      case: 'EXECUTABLE',
      parameterLabel: primary.label || 'Source ingredient',
      parameterState: primaryStatus || 'DATA_SOURCE_REQUIRED',
      showCalculationResolver: false,
      showManualResolver: false,
      showMapResolver: false,
      resolverActionLabel: null,
      explanation: 'Source ingredient — calculation setup not applicable',
      capability,
    }
  }

  if (op.unresolved === true) {
    return {
      case: 'UNRESOLVED_MAP',
      parameterLabel: primary.label === 'Needs review' ? 'Needs your input' : primary.label,
      parameterState: 'UNRESOLVED',
      showCalculationResolver: false,
      showManualResolver: false,
      showMapResolver: true,
      resolverActionLabel: 'Resolve parameter',
      explanation: null,
      capability: false,
    }
  }

  if (manual && !capability) {
    return {
      case: 'MANUAL_INPUT',
      parameterLabel: 'Needs manual input',
      parameterState: 'NEEDS_MANUAL_INPUT',
      showCalculationResolver: false,
      showManualResolver: true,
      showMapResolver: false,
      resolverActionLabel: 'Provide manual input',
      explanation: truth.calculationExplanation ? String(truth.calculationExplanation) : null,
      capability: false,
    }
  }

  if (capability) {
    const label =
      primaryStatus === 'CAN_CALCULATE_WHEN_DATA_AVAILABLE' ||
      primary.label === 'Can calculate when data is available'
        ? 'Can calculate when data is available'
        : primary.label === 'Approved for live use'
          ? 'Approved for live use'
          : 'Ready to test'
    return {
      case: opts?.ruleNeedsReview ? 'RULE_REVIEW_ONLY' : 'EXECUTABLE',
      parameterLabel: label,
      parameterState: primaryStatus || 'READY_TO_TEST',
      showCalculationResolver: false,
      showManualResolver: false,
      showMapResolver: false,
      resolverActionLabel: null,
      explanation: truth.calculationExplanation ? String(truth.calculationExplanation) : null,
      capability: true,
    }
  }

  // Not executable — calculation setup path
  if (opts?.proposalReadyForReview) {
    return {
      case: 'REVIEW_PROPOSAL',
      parameterLabel: 'Calculation needs setup',
      parameterState: 'CALCULATION_NEEDS_SETUP',
      showCalculationResolver: true,
      showManualResolver: false,
      showMapResolver: false,
      resolverActionLabel: 'Review proposed calculation',
      explanation: 'A proposed calculation is ready for review — not executable until accepted.',
      capability: false,
    }
  }

  return {
    case: 'SETUP_CALCULATION',
    parameterLabel: 'Calculation needs setup',
    parameterState: 'CALCULATION_NEEDS_SETUP',
    showCalculationResolver: true,
    showManualResolver: false,
    showMapResolver: false,
    resolverActionLabel: 'Set up calculation',
    explanation:
      truth.calculationExplanation != null
        ? String(truth.calculationExplanation)
        : 'Calculation has not yet been configured',
    capability: false,
  }
}

export function ruleLifecycleNeedsReview(life: Record<string, unknown>): boolean {
  const state = String(life.lenderState ?? '')
  const label = String(life.ruleLifecycleLabel ?? life.lenderStateLabel ?? life.statusChip ?? '')
  return (
    state === 'READY_FOR_CONFIRMATION' ||
    state === 'NEEDS_INPUT' ||
    label === 'Needs review' ||
    life.showAcceptRule === true
  )
}
