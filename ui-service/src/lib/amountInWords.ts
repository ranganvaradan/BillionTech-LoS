/**
 * Indian amount display helpers (thousand / lakh / crore). Display-only — storage stays numeric.
 */

const ONES = [
  '',
  'one',
  'two',
  'three',
  'four',
  'five',
  'six',
  'seven',
  'eight',
  'nine',
  'ten',
  'eleven',
  'twelve',
  'thirteen',
  'fourteen',
  'fifteen',
  'sixteen',
  'seventeen',
  'eighteen',
  'nineteen',
]

const TENS = ['', '', 'twenty', 'thirty', 'forty', 'fifty', 'sixty', 'seventy', 'eighty', 'ninety']

function titleCaseWords(s: string): string {
  return s
    .split(/\s+/)
    .filter(Boolean)
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ')
}

function twoDigitWords(n: number): string {
  if (n < 20) return ONES[n] ?? ''
  const t = Math.floor(n / 10)
  const o = n % 10
  return o ? `${TENS[t]} ${ONES[o]}` : TENS[t] ?? ''
}

/** Convert 0–999 to words (Indian style for hundreds). */
function hundredWords(n: number): string {
  if (n <= 0) return ''
  if (n < 100) return twoDigitWords(n)
  const h = Math.floor(n / 100)
  const rest = n % 100
  const head = `${ONES[h]} hundred`
  return rest ? `${head} ${twoDigitWords(rest)}` : head
}

function parseAmount(n: number | string | null | undefined): number | null {
  if (n == null || n === '') return null
  const v = typeof n === 'number' ? n : Number(String(n).replace(/,/g, '').trim())
  if (!Number.isFinite(v) || v < 0) return null
  return v
}

function formatScaleNumber(n: number): string {
  if (Number.isInteger(n)) return String(n)
  const s = n.toFixed(2).replace(/\.?0+$/, '')
  return s
}

/**
 * Short Indian scale: "50 thousand", "5 lakh", "1.25 crore".
 * Below 1,000 returns the plain integer/decimal string.
 */
export function formatAmountShortScale(n: number | string | null | undefined): string {
  const v = parseAmount(n)
  if (v == null) return '—'
  if (v === 0) return '0'
  if (v < 1000) return formatScaleNumber(v)
  if (v < 100_000) {
    const thousands = v / 1000
    return `${formatScaleNumber(thousands)} thousand`
  }
  if (v < 10_000_000) {
    const lakhs = v / 100_000
    return `${formatScaleNumber(lakhs)} lakh`
  }
  const crores = v / 10_000_000
  return `${formatScaleNumber(crores)} crore`
}

/**
 * Full Indian words: "Fifty thousand rupees".
 */
export function formatAmountInWords(n: number | string | null | undefined): string {
  const v = parseAmount(n)
  if (v == null) return ''
  const whole = Math.floor(v)
  if (whole === 0) return 'Zero rupees'

  const crore = Math.floor(whole / 10_000_000)
  const lakh = Math.floor((whole % 10_000_000) / 100_000)
  const thousand = Math.floor((whole % 100_000) / 1000)
  const rem = whole % 1000

  const parts: string[] = []
  if (crore) parts.push(`${hundredWords(crore)} crore`)
  if (lakh) parts.push(`${hundredWords(lakh)} lakh`)
  if (thousand) parts.push(`${hundredWords(thousand)} thousand`)
  if (rem) parts.push(hundredWords(rem))

  const body = parts.join(' ').replace(/\s+/g, ' ').trim()
  return `${titleCaseWords(body)} rupees`
}
