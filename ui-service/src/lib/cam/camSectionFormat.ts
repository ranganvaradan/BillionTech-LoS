/** Format CAM sectionExtended values for narrative text areas and PDF-friendly plain text. */

function isPlainScalar(value: unknown): value is string | number | boolean {
  const t = typeof value
  return t === 'string' || t === 'number' || t === 'boolean'
}

function formatParameterRow(row: Record<string, unknown>, index: number): string {
  const parameter = String(row.Parameter ?? row.parameter ?? '—')
  const valueUsed = String(row['Value used'] ?? row.valueUsed ?? '—')
  const points = String(row.Points ?? `${row.pointsEarned ?? '—'} / ${row.maxScore ?? '—'}`)
  const source = String(row.Source ?? row.valueSource ?? '')
  const parts = [`${index + 1}. ${parameter}`, `value: ${valueUsed}`, `points: ${points}`]
  if (source && source !== '—') {
    parts.push(`source: ${source}`)
  }
  return parts.join(' · ')
}

export function formatCamSectionFieldValue(value: unknown): string {
  if (value == null) return '—'
  if (isPlainScalar(value)) return String(value)
  if (Array.isArray(value)) {
    if (value.length === 0) return '—'
    const objects = value.filter((item) => item && typeof item === 'object' && !Array.isArray(item))
    if (objects.length === value.length) {
      return objects
        .map((item, i) => formatParameterRow(item as Record<string, unknown>, i))
        .join('\n')
    }
    return value.map((item) => formatCamSectionFieldValue(item)).join('; ')
  }
  if (typeof value === 'object') {
    return Object.entries(value as Record<string, unknown>)
      .map(([k, v]) => `${k}: ${formatCamSectionFieldValue(v)}`)
      .join('\n')
  }
  return String(value)
}

/** Build default narrative text from a sectionExtended block (skips internal PDF-only keys). */
export function formatCamSectionExtended(section: Record<string, unknown> | null | undefined): string {
  if (!section) return ''
  return Object.entries(section)
    .filter(([k]) => !k.startsWith('_'))
    .map(([k, v]) => `${k}: ${formatCamSectionFieldValue(v)}`)
    .join('\n')
}
