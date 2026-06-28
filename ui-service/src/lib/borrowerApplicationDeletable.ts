import type { ApplicationStatus } from '@/types/application'

export function isBorrowerDeletableApplicationStatus(s: ApplicationStatus): boolean {
  return s === 'DRAFT' || s === 'CONSENT_PENDING'
}
