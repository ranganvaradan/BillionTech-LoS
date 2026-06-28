export type BadgeTone = 'orange' | 'green' | 'red' | 'amber' | 'blue' | 'gray'

const STATUS_COLOR: Record<string, BadgeTone> = {
  PAID: 'green',
  ACTIVE: 'green',
  COMPLETED: 'green',
  APPROVED: 'green',
  DISBURSED: 'green',
  CLOSED: 'green',
  SANCTION_ISSUED: 'green',
  ESIGN_COMPLETED: 'green',
  PENDING: 'orange',
  PENDING_APPROVAL: 'orange',
  DUE: 'orange',
  DRAFT: 'orange',
  CONSENT_PENDING: 'orange',
  KYC_IN_PROGRESS: 'orange',
  UNDERWRITING: 'orange',
  ESIGN_PENDING: 'orange',
  DISBURSEMENT_PENDING: 'orange',
  ON_HOLD: 'amber',
  OVERDUE: 'red',
  CANCELLED: 'red',
  REJECTED: 'red',
  FAILED: 'red',
  KYC_FAILED: 'red',
  WITHDRAWN: 'gray',
  ELIGIBLE: 'blue',
  VIEWED: 'blue',
  REVISION_REQUESTED: 'amber',
  WAIVED: 'amber',
}

export function badgeToneForStatus(status: string | null | undefined): BadgeTone {
  if (!status) return 'gray'
  const key = status.trim().toUpperCase().replace(/[\s-]+/g, '_')
  return STATUS_COLOR[key] ?? 'gray'
}

export function btBadgeClass(tone: BadgeTone, className = ''): string {
  return ['bt-badge', `bt-badge-${tone}`, className].filter(Boolean).join(' ')
}
