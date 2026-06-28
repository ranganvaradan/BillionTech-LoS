const STATUS_LABELS: Record<string, string> = {
  total: 'Total applications',
  active: 'Active (non-terminal)',
  byStatus: 'By status',
  kycInProgress: 'KYC in progress',
  underwritingInProgress: 'Underwriting in progress',
  DRAFT: 'Draft',
  KYC_IN_PROGRESS: 'KYC in progress',
  KYC_FAILED: 'KYC failed',
  UNDERWRITING: 'Underwriting',
  UNDERWRITING_COMPLETED: 'Underwriting completed',
  CAM_READY: 'CAM ready',
  CAM_REVIEWED: 'CAM reviewed',
  SANCTIONED: 'Sanctioned',
  DISBURSED: 'Disbursed',
  REJECTED: 'Rejected',
  WITHDRAWN: 'Withdrawn',
}

function formatSnakeCaseLabel(key: string): string {
  return key
    .split('_')
    .filter(Boolean)
    .map((word) => {
      const upper = word.toUpperCase()
      if (upper === 'KYC' || upper === 'CAM') return upper
      return word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()
    })
    .join(' ')
}

/** Human labels for dashboard summary keys (from API snake/camel). */
export function formatStatusLabel(key: string): string {
  const k = key.trim()
  if (STATUS_LABELS[k]) return STATUS_LABELS[k]
  if (k.includes('_')) return formatSnakeCaseLabel(k)
  return k.replace(/([A-Z])/g, ' $1').replace(/^./, (s) => s.toUpperCase()).trim()
}
