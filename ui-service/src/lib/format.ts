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

export function isUuid(s: string): boolean {
  return /^[0-9a-fA-F-]{36}$/.test(s)
}
