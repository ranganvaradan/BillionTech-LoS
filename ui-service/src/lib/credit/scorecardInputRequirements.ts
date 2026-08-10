import type { ScorecardParameterDef, ScorecardRow } from '@/api/scorecards'
import { paramDef, type ScorecardParamType } from '@/lib/credit/scorecardConfig'

export type ScorecardInputRequirement = {
  parameter: string
  source: string
  label: string
  manualKey: string
  ready: boolean
  inputType?: 'number' | 'text' | 'dropdown' | 'yesno'
  options?: { value: string; label: string }[]
}

function readScorecardValue(scorecardMap: Record<string, string> | undefined, parameter: string): string {
  if (!scorecardMap) return ''
  const direct = scorecardMap[parameter]
  if (direct != null && String(direct).trim() !== '') return String(direct).trim()
  const upper = scorecardMap[parameter.toUpperCase()]
  if (upper != null && String(upper).trim() !== '') return String(upper).trim()
  return ''
}

function manualKeyForRow(row: ScorecardRow): string {
  const def = paramDef(row.source, row.parameter)
  return def?.manualKey ?? row.parameter
}

function inputMetaFromDefs(
  parameter: string,
  source: string,
  parameterDefs?: Record<string, ScorecardParameterDef>,
): Pick<ScorecardInputRequirement, 'inputType' | 'options' | 'label'> {
  const catalog = paramDef(source, parameter)
  const custom = parameterDefs?.[parameter]
  if (custom?.inputType === 'dropdown') {
    return {
      inputType: 'dropdown',
      label: catalog?.label ?? parameter,
      options: (custom.options ?? []).map((o) => ({ value: o.value, label: o.label })),
    }
  }
  if (custom?.inputType === 'text') {
    return { inputType: 'text', label: catalog?.label ?? parameter }
  }
  if (catalog?.type === 'yesno') {
    return { inputType: 'yesno', label: catalog.label }
  }
  if (catalog?.type === 'enum') {
    return {
      inputType: 'dropdown',
      label: catalog.label,
      options: catalog.enumOptions,
    }
  }
  if (catalog?.type === 'text') {
    return { inputType: 'text', label: catalog.label }
  }
  return { inputType: 'number', label: catalog?.label ?? parameter }
}

const SERVER_DEFAULTED_POLICY_PARAMS = new Set([
  'PAT',
  'officeOwned',
])

const MANUAL_UNDERWRITING_POLICY_PARAMS = new Set([
  'DSCR',
  'INTEREST_COVERAGE',
  'DEBT_TO_EQUITY',
  'TOL_TNW',
  'BUSINESSSTABILITY',
  'residenceOwned',
  'RESIDENCEOWNED',
])

/** Manual rows referenced by the matched scorecard.
 * GST_STATEMENT / ITR / BANK_STATEMENT use document OCR extract + server gap-fill (not manual forms). */
export function scorecardManualInputRequirements(
  rows: ScorecardRow[],
  scorecardMap: Record<string, string> | undefined,
  parameterDefs?: Record<string, ScorecardParameterDef>,
): ScorecardInputRequirement[] {
  const seen = new Set<string>()
  const out: ScorecardInputRequirement[] = []
  for (const row of rows) {
    const source = String(row.source ?? '').toUpperCase()
    const parameter = String(row.parameter ?? '').trim()
    if (!parameter) continue
    const normalizedParameter = parameter.toUpperCase()
    const isManualPolicyParam =
      MANUAL_UNDERWRITING_POLICY_PARAMS.has(normalizedParameter) ||
      MANUAL_UNDERWRITING_POLICY_PARAMS.has(parameter)
    // Document-extract sources are filled by OCR / gap defaults — do not force manual entry.
    if (source !== 'OTHER' && !isManualPolicyParam) continue
    if (SERVER_DEFAULTED_POLICY_PARAMS.has(parameter) || SERVER_DEFAULTED_POLICY_PARAMS.has(parameter.toUpperCase())) {
      continue
    }
    const key = `${source}:${parameter}`
    if (seen.has(key)) continue
    seen.add(key)
    const manualKey = manualKeyForRow(row)
    const value = readScorecardValue(scorecardMap, parameter)
    const meta = inputMetaFromDefs(parameter, row.source, parameterDefs)
    out.push({
      parameter,
      source,
      label: meta.label,
      manualKey,
      ready: value.length > 0,
      inputType: meta.inputType,
      options: meta.options,
    })
  }
  return out
}

/** OTHER / bank-statement rows that still need manual values before underwriting. */
export function missingScorecardInputRequirements(
  rows: ScorecardRow[],
  scorecardMap: Record<string, string> | undefined,
  parameterDefs?: Record<string, ScorecardParameterDef>,
): ScorecardInputRequirement[] {
  return scorecardManualInputRequirements(rows, scorecardMap, parameterDefs).filter((r) => !r.ready)
}

export function allScorecardInputsReady(requirements: ScorecardInputRequirement[]): boolean {
  if (requirements.length === 0) return true
  return requirements.every((r) => r.ready)
}

/** Merge requirement lists (scorecard + rule hardRules OTHER), preferring first occurrence. */
export function unionScorecardInputRequirements(
  ...lists: ScorecardInputRequirement[][]
): ScorecardInputRequirement[] {
  const seen = new Set<string>()
  const out: ScorecardInputRequirement[] = []
  for (const list of lists) {
    for (const req of list) {
      const key = `${req.source}:${req.parameter}`
      if (seen.has(key)) continue
      seen.add(key)
      out.push(req)
    }
  }
  return out
}

export function requirementToParamDef(req: ScorecardInputRequirement): {
  value: string
  label: string
  type: ScorecardParamType
  enumOptions?: { value: string; label: string }[]
} {
  if (req.inputType === 'dropdown') {
    return {
      value: req.parameter,
      label: req.label,
      type: 'enum',
      enumOptions: req.options,
    }
  }
  if (req.inputType === 'text') {
    return { value: req.parameter, label: req.label, type: 'text' }
  }
  if (req.inputType === 'yesno') {
    return { value: req.parameter, label: req.label, type: 'yesno' }
  }
  return { value: req.parameter, label: req.label, type: 'number' }
}
