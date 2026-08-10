import { formatAmountShortScale } from '@/lib/amountInWords'

export function formatInstant(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString()
}

export function formatMoney(n: number | null | undefined): string {
  if (n == null || Number.isNaN(n)) return '—'
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: 'INR' }).format(n)
}

/** Currency + Indian short scale, e.g. "₹50,000.00 · 50 thousand". */
export function formatMoneyWithScale(n: number | null | undefined): string {
  if (n == null || Number.isNaN(n)) return '—'
  return `${formatMoney(n)} · ${formatAmountShortScale(n)}`
}

const NON_AMOUNT_LABEL_RE =
  /ratio|percent|%|score|enquiry|enquir|count|flag|ntc|outcome|status|source|note|type|city|state|email|mobile|name|employment|exception|version|band/i

/**
 * Formats a summary display value: pure numeric amounts get currency + short scale;
 * ratios/scores/labels stay unchanged. Also upgrades plain INR currency strings.
 */
export function formatSummaryValueDisplay(label: string, value: string | null | undefined): string {
  if (value == null || value === '' || value === '—') return value ?? '—'
  if (NON_AMOUNT_LABEL_RE.test(label)) return value
  const cleaned = String(value)
    .replace(/₹/g, '')
    .replace(/INR/gi, '')
    .replace(/,/g, '')
    .replace(/\s+/g, ' ')
    .trim()
  // Already has short-scale text — leave as-is
  if (/\b(thousand|lakh|crore)\b/i.test(value)) return value
  if (!/^-?\d+(\.\d+)?$/.test(cleaned)) return value
  const n = Number(cleaned)
  if (!Number.isFinite(n) || Math.abs(n) < 1000) return value
  return formatMoneyWithScale(n)
}

export function isUuid(s: string): boolean {
  return /^[0-9a-fA-F-]{36}$/.test(s)
}
