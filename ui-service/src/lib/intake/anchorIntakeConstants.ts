import type { BorrowerType } from '@/types/createApplication'

/** Anchor invoice-discounting onboarding always uses corporate entity class. */
export const ANCHOR_BORROWER_TYPE = 'COMPANY' as const satisfies BorrowerType
