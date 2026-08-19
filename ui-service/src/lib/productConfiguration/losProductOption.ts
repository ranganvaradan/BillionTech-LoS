type UnknownRecord = Record<string, unknown>

function asRecord(v: unknown): UnknownRecord | null {
  if (!v || typeof v !== 'object' || Array.isArray(v)) return null
  return v as UnknownRecord
}

function safeString(v: unknown): string {
  if (v === null || v === undefined) return ''
  return typeof v === 'string' ? v : String(v)
}

function humanizeCode(code: string): string {
  // Generic, non-hard-coded transformation from canonical codes to display labels.
  return code
    .replace(/_/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase()
    .replace(/\b\w/g, (m) => m.toUpperCase())
}

export type LosProductOption = {
  code: string // canonical LOS product code used by external_product_mapping
  label: string // canonical human-readable label for dropdown display
}

/**
 * Converts a backend "product option" DTO into:
 * - `code` to pass to external_product_mapping as `los_product_code`
 * - `label` for `<option>` rendering (must not become "[object Object]")
 *
 * Backend shape (from /admin/live-readiness/product-configuration/options):
 *   { borrowerType, loanProduct, intakeSegment, sampleWorkflowId, sampleWorkflowName, ... }
 */
export function canonicalLosProductOption(optionDto: unknown): LosProductOption {
  if (typeof optionDto === 'string') {
    const code = optionDto
    return { code, label: humanizeCode(code) }
  }

  const r = asRecord(optionDto)
  if (!r) {
    return { code: '', label: '' }
  }

  const codeRaw = r.loanProduct ?? r.code ?? r.losProductCode ?? ''
  const labelExplicitRaw = r.sampleWorkflowName ?? r.loanProductDisplayName ?? r.label

  const code = safeString(codeRaw)
  const label = safeString(labelExplicitRaw) || humanizeCode(code)

  // Guardrail: never let React stringification leak "[object Object]" into UI.
  if (code === '[object Object]') return { code: '', label: safeString(r.loanProduct) || '' }
  if (label === '[object Object]') return { code, label: humanizeCode(code) }

  return { code, label }
}

