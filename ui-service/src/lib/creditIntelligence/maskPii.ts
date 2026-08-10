/** Mask PAN-like identifiers — never show full PAN in CI review UI. */
export function maskPan(value: unknown): string {
  if (value == null) return '—'
  const s = String(value).replace(/\s+/g, '').toUpperCase()
  if (/^[A-Z]{5}[0-9]{4}[A-Z]$/.test(s)) {
    return `${s.slice(0, 3)}XXXXXX${s.slice(-1)}`
  }
  if (s.length >= 8 && /\d/.test(s)) {
    return `${s.slice(0, 2)}••••${s.slice(-2)}`
  }
  return String(value)
}

export function maskPiiDeep(input: unknown): unknown {
  if (input == null) return input
  if (typeof input === 'string') {
    if (/pan/i.test(input) && input.length < 20) return maskPan(input)
    return input
  }
  if (Array.isArray(input)) return input.map(maskPiiDeep)
  if (typeof input === 'object') {
    const out: Record<string, unknown> = {}
    for (const [k, v] of Object.entries(input as Record<string, unknown>)) {
      if (/pan|aadhaar|aadhar|gstin|mobile|phone|email/i.test(k) && typeof v === 'string') {
        out[k] = maskPan(v)
      } else {
        out[k] = maskPiiDeep(v)
      }
    }
    return out
  }
  return input
}
