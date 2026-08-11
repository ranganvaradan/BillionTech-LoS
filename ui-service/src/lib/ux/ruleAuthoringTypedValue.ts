/**
 * POLICY-TYPED-RULE-AUTHORING-1 — client-side typed value helpers aligned with backend AuthoringValueTypes.
 */

export type ValueControl =
  | 'BOOLEAN'
  | 'ENUM'
  | 'INTEGER'
  | 'NUMBER'
  | 'PERCENTAGE'
  | 'MONEY'
  | 'DURATION'
  | 'DATE'
  | 'STRING'
  | 'PARAMETER_REFERENCE'

export function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : {}
}

export function asList(v: unknown): unknown[] {
  return Array.isArray(v) ? v : []
}

export function valueControlOf(param: Record<string, unknown> | null | undefined): ValueControl {
  if (!param) return 'NUMBER'
  const fromMeta = String(param.valueControl ?? '')
  if (fromMeta) return fromMeta as ValueControl
  const unit = String(param.unit ?? '').toUpperCase()
  if (unit === 'BOOLEAN' || unit === 'FLAG') return 'BOOLEAN'
  if (unit === 'PERCENT' || unit === 'PERCENTAGE') return 'PERCENTAGE'
  if (unit === 'INR' || unit === 'MONEY') return 'MONEY'
  if (unit === 'MONTHS' || unit === 'YEARS' || unit === 'DAYS') return 'DURATION'
  if (unit === 'CODE' || unit === 'ENUM') {
    return asList(param.allowedValues).length ? 'ENUM' : 'STRING'
  }
  if (unit === 'SCORE' || unit === 'COUNT' || unit === 'INTEGER') return 'INTEGER'
  if (unit === 'DATE') return 'DATE'
  return 'NUMBER'
}

/** Canonical payload value for BUILD preview/confirm — never Number("Yes"). */
export function toCanonicalBuildValue(
  control: ValueControl,
  raw: string,
  durationUnit?: string,
): unknown {
  const s = (raw ?? '').trim()
  if (!s) return null
  switch (control) {
    case 'BOOLEAN': {
      const l = s.toLowerCase()
      if (['yes', 'true', '1', 'y'].includes(l)) return true
      if (['no', 'false', '0', 'n'].includes(l)) return false
      return null
    }
    case 'PERCENTAGE': {
      const n = Number(s.replace(/%/g, '').replace(/,/g, ''))
      if (!Number.isFinite(n) || n < 0 || n > 100) return null
      return n
    }
    case 'MONEY': {
      const cleaned = s.replace(/[₹,\s]|INR|Rs\.?/gi, '')
      const n = Number(cleaned)
      return Number.isFinite(n) ? n : null
    }
    case 'DURATION':
    case 'INTEGER':
    case 'NUMBER': {
      // Duration unit conversion happens on the backend from durationUnit
      void durationUnit
      const n = Number(s.replace(/%/g, '').replace(/,/g, ''))
      return Number.isFinite(n) ? n : null
    }
    case 'ENUM':
    case 'STRING':
    case 'DATE':
      return s
    default:
      return s
  }
}

export function hasVisibleCanonicalValue(
  control: ValueControl,
  raw: string,
  valueMode: 'FIXED' | 'PARAMETER',
  rightParameterId: string,
): boolean {
  if (valueMode === 'PARAMETER') return Boolean(rightParameterId)
  return toCanonicalBuildValue(control, raw) != null
}

export function operatorsForParam(
  param: Record<string, unknown> | null | undefined,
  operatorsByType: Record<string, unknown>,
): string[] {
  const control = valueControlOf(param)
  const fromParam = asList(param?.operators).map(String)
  if (fromParam.length) return fromParam
  const byType = asList(operatorsByType[control] ?? operatorsByType[String(param?.unit ?? 'NUMBER')]).map(
    String,
  )
  if (byType.length) return byType
  if (control === 'BOOLEAN' || control === 'ENUM') return ['is', 'is not']
  return ['>', '>=', '<', '<=', '=', '!=']
}

export function defaultOperator(control: ValueControl): string {
  return control === 'BOOLEAN' || control === 'ENUM' ? 'is' : '>='
}
