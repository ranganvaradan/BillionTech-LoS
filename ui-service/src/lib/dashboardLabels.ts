/** Human labels for dashboard summary keys (from API snake/camel). */
export function formatStatusLabel(key: string): string {
  const k = key.trim()
  const map: Record<string, string> = {
    total: 'Total applications',
    active: 'Active (non-terminal)',
    byStatus: 'By status',
    kycInProgress: 'KYC in progress',
    underwritingInProgress: 'Underwriting in progress',
  }
  if (map[k]) return map[k]
  return k.replace(/([A-Z])/g, ' $1').replace(/^./, (s) => s.toUpperCase()).trim()
}
