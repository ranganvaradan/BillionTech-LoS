import { loanProductLabel } from '@/catalog/loanProducts'

type UnknownRecord = Record<string, unknown>

function asRecord(v: unknown): UnknownRecord | null {
  if (!v || typeof v !== 'object' || Array.isArray(v)) return null
  return v as UnknownRecord
}

function safeString(v: unknown): string {
  if (v === null || v === undefined) return ''
  return typeof v === 'string' ? v : String(v)
}

export type LosProductOption = {
  code: string // canonical LOS product code used by external_product_mapping
  label: string // canonical business-facing LOS product name (same authority as intake)
}

/** Detect workflow display names that must never appear as LOS Product labels. */
export function looksLikeWorkflowNameLabel(label: string): boolean {
  const t = label.trim()
  if (!t) return false
  return /^Default Workflow\s-/i.test(t) || /\bWorkflow\s-/i.test(t)
}

/**
 * Converts a backend product option DTO into canonical LOS Product selector semantics.
 *
 * Label authority: shared UI catalog (`loanProductLabel`) — same as Application Intake.
 * Value authority: `loanProduct` canonical code.
 *
 * Backend shape (from /admin/live-readiness/product-configuration/options):
 *   { loanProduct }
 *
 * Workflow names (`sampleWorkflowName`, workflow.name) are intentionally ignored.
 */
export function canonicalLosProductOption(optionDto: unknown): LosProductOption {
  if (typeof optionDto === 'string') {
    const code = optionDto.trim()
    return { code, label: loanProductLabel(code) }
  }

  const r = asRecord(optionDto)
  if (!r) {
    return { code: '', label: '' }
  }

  const code = safeString(r.loanProduct ?? r.code ?? r.losProductCode ?? '').trim()
  const label = loanProductLabel(code)

  if (code === '[object Object]') return { code: '', label: '' }
  if (label === '[object Object]') return { code, label: loanProductLabel(code) }

  return { code, label }
}

/** One option per canonical LOS product code (products, not workflow/product combinations). */
export function distinctCanonicalLosProductOptions(optionDtos: unknown[]): LosProductOption[] {
  const byCode = new Map<string, LosProductOption>()
  for (const dto of optionDtos) {
    const opt = canonicalLosProductOption(dto)
    if (!opt.code) continue
    if (!byCode.has(opt.code)) byCode.set(opt.code, opt)
  }
  return [...byCode.values()].sort((a, b) => a.label.localeCompare(b.label))
}
