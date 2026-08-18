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

export type PolicyStudioMappingState =
  | 'CURRENT_MAPPING_RESOLVED'
  | 'EXISTING_MAPPING_UNRESOLVED'
  | 'NOT_YET_MAPPED'

export type PolicyStudioPersistedMapping = {
  state: PolicyStudioMappingState
  canonicalParameterId: string | null
  source: string | null
  classification: 'RAW' | 'DERIVED' | 'MANUAL' | null
  headerLabel: string
}

function blankToNull(v: unknown): string | null {
  if (v == null) return null
  const s = String(v).trim()
  if (!s || s === 'null' || s === 'undefined') return null
  return s
}

/** Persisted canonical parameter id. Unresolved flags must not hide this. */
export function persistedCanonicalParameterId(op: Record<string, unknown>): string | null {
  const nested = asRecord(op.canonicalParameterState)
  const nestedTruth = asRecord(op.canonicalTruth)
  return (
    blankToNull(op.parameterId) ||
    blankToNull(op.canonicalParameterId) ||
    blankToNull(op.persistedParameterId) ||
    blankToNull(nested.canonicalParameterId) ||
    blankToNull(nestedTruth.canonicalParameterId)
  )
}

function classificationOf(op: Record<string, unknown>): 'RAW' | 'DERIVED' | 'MANUAL' | null {
  const raw = String(
    op.parameterClassification ?? op.resolutionState ?? op.type ?? op.kind ?? '',
  ).toUpperCase()
  if (raw === 'RAW') return 'RAW'
  if (raw === 'MANUAL') return 'MANUAL'
  if (raw === 'DERIVED' || raw.includes('DERIV')) return 'DERIVED'
  const avail = String(op.availabilityLabel ?? op.availability ?? '').toLowerCase()
  if (avail.includes('manual')) return 'MANUAL'
  if (avail.includes('derived') || avail.includes('automatic')) return 'DERIVED'
  if (avail.includes('raw')) return 'RAW'
  return null
}

/**
 * ONE mapping authority for rule card, Change parameter drawer, Policy Test faces.
 * A persisted id is never "Not yet mapped", even if a caller forced unresolved=true.
 */
export function persistedMappingFromOperand(op: Record<string, unknown>): PolicyStudioPersistedMapping {
  const id = persistedCanonicalParameterId(op)
  const source = blankToNull(op.evaluatedFrom) || blankToNull(op.suggestedSource)
  const classification = classificationOf(op)
  if (op.existingMappingUnresolved === true && id) {
    return {
      state: 'EXISTING_MAPPING_UNRESOLVED',
      canonicalParameterId: id,
      source,
      classification,
      headerLabel: `EXISTING_MAPPING_UNRESOLVED · ${id}`,
    }
  }
  if (id) {
    return {
      state: 'CURRENT_MAPPING_RESOLVED',
      canonicalParameterId: id,
      source,
      classification,
      headerLabel: `CURRENT_MAPPING_RESOLVED · ${id}`,
    }
  }
  return {
    state: 'NOT_YET_MAPPED',
    canonicalParameterId: null,
    source,
    classification,
    headerLabel: 'Not yet mapped',
  }
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
  /** Inner setup-flow hint (e.g. Review proposed calculation) — not the primary action */
  setupFlowHint: string | null
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

  const mapping = persistedMappingFromOperand(op)
  const primary = lenderPrimaryFromTruth(truth, {
    calculationRequired: capability ? false : op.calculationRequired === true,
    policyTestReady: capability || op.policyTestReady === true,
    unavailable: op.unavailable === true,
    unresolved: mapping.state === 'NOT_YET_MAPPED',
  })
  const parameterLabel =
    primary.label === 'Needs review' ? 'Needs your input' : primary.label

  const primaryStatus = String(truth.primaryStatus ?? primary.state ?? '')
  const readinessReason = String(
    (truth as Record<string, unknown>).businessReadinessReason ??
      asRecord(op.canonicalParameterState).businessReadinessReason ??
      '',
  )
  const manual =
    primaryStatus === 'NEEDS_MANUAL_INPUT' ||
    readinessReason === 'MANUAL_INPUT' ||
    primary.label === 'Needs manual input' ||
    op.manualInput === true ||
    String(op.availability ?? '').toUpperCase() === 'MANUAL'

  // SOURCE_INGREDIENT — never calculation setup
  if (paramClass === 'INGREDIENT' || String(op.parameterClassLabel ?? '').includes('Source ingredient')) {
    return {
      case: 'EXECUTABLE',
      parameterLabel: parameterLabel || 'Source ingredient',
      parameterState: primaryStatus || 'NOT_READY',
      showCalculationResolver: false,
      showManualResolver: false,
      showMapResolver: false,
      resolverActionLabel: null,
      setupFlowHint: null,
      explanation: 'Source ingredient — calculation setup not applicable',
      capability,
    }
  }

  if (mapping.state === 'EXISTING_MAPPING_UNRESOLVED') {
    return {
      case: 'CHANGE_PARAMETER',
      parameterLabel: 'EXISTING_MAPPING_UNRESOLVED',
      parameterState: 'EXISTING_MAPPING_UNRESOLVED',
      showCalculationResolver: false,
      showManualResolver: false,
      showMapResolver: false,
      resolverActionLabel: 'Change parameter',
      setupFlowHint: null,
      explanation: 'A persisted mapping exists but cannot be resolved in GACAT.',
      capability: false,
    }
  }

  if (mapping.state === 'NOT_YET_MAPPED') {
    return {
      case: 'UNRESOLVED_MAP',
      parameterLabel: parameterLabel || 'Needs your input',
      parameterState: 'UNRESOLVED',
      showCalculationResolver: false,
      showManualResolver: false,
      showMapResolver: true,
      resolverActionLabel: 'Resolve parameter',
      setupFlowHint: null,
      explanation: null,
      capability: false,
    }
  }

  if (manual) {
    return {
      case: 'MANUAL_INPUT',
      parameterLabel: parameterLabel || 'Needs manual input',
      parameterState: 'READY',
      showCalculationResolver: false,
      showManualResolver: true,
      showMapResolver: false,
      resolverActionLabel: 'Provide manual input',
      setupFlowHint: null,
      explanation: truth.calculationExplanation ? String(truth.calculationExplanation) : null,
      capability: true,
    }
  }

  if (capability || primaryStatus === 'READY') {
    return {
      case: opts?.ruleNeedsReview ? 'RULE_REVIEW_ONLY' : 'EXECUTABLE',
      parameterLabel: parameterLabel || 'Ready',
      parameterState: primaryStatus || 'READY',
      showCalculationResolver: false,
      showManualResolver: false,
      showMapResolver: false,
      resolverActionLabel: null,
      setupFlowHint: null,
      explanation: truth.calculationExplanation ? String(truth.calculationExplanation) : null,
      capability: true,
    }
  }

  // CALCULATION-SETUP-ACTION-INVARIANT: outer primary action is always Set up calculation
  // when calculation is not defined. Proposal review / clarification live inside that flow.
  const calcSetup =
    readinessReason === 'CALCULATION_NOT_DEFINED' ||
    readinessReason === 'CALCULATION_INVALID' ||
    primaryStatus === 'CALCULATION_NEEDS_SETUP' ||
    (op.calculationRequired === true &&
      readinessReason !== 'DEPENDENCY_NOT_READY' &&
      readinessReason !== 'MANUAL_INPUT' &&
      readinessReason !== 'SOURCE_NOT_INTEGRATED' &&
      readinessReason !== 'RAW_FIELD_NOT_AVAILABLE')

  if (calcSetup || opts?.proposalReadyForReview) {
    return {
      case: 'SETUP_CALCULATION',
      parameterLabel: parameterLabel || 'Calculation not defined',
      parameterState: 'NOT_READY',
      showCalculationResolver: true,
      showManualResolver: false,
      showMapResolver: false,
      resolverActionLabel: 'Set up calculation',
      setupFlowHint: opts?.proposalReadyForReview ? 'Review proposed calculation' : null,
      explanation: opts?.proposalReadyForReview
        ? 'A proposed calculation is ready for review inside Set up calculation — not executable until accepted.'
        : truth.calculationExplanation != null
          ? String(truth.calculationExplanation)
          : 'Parameter is not ready because its calculation is not defined.',
      capability: false,
    }
  }

  return {
    case: 'CHANGE_PARAMETER',
    parameterLabel: parameterLabel || 'Not ready',
    parameterState: primaryStatus || 'NOT_READY',
    showCalculationResolver: false,
    showManualResolver: false,
    showMapResolver: false,
    resolverActionLabel: truth.nextAction != null ? String(truth.nextAction) : null,
    setupFlowHint: null,
    explanation:
      truth.calculationExplanation != null
        ? String(truth.calculationExplanation)
        : 'Parameter is not ready',
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
