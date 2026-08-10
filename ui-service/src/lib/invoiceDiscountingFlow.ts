import { isInvoiceDiscountingProduct } from '@/catalog/loanProducts'
import type { ApplicationIntakeSegment, ApplicationResponse } from '@/types/application'

export function isInvoiceDiscountingAnchorApp(app: Pick<ApplicationResponse, 'loanProduct' | 'intakeSegment'>): boolean {
  return isInvoiceDiscountingProduct(app.loanProduct) && app.intakeSegment === 'ANCHOR'
}

export function isInvoiceDiscountingBorrowerApp(
  app: Pick<ApplicationResponse, 'loanProduct' | 'intakeSegment'>,
): boolean {
  return isInvoiceDiscountingProduct(app.loanProduct) && app.intakeSegment !== 'ANCHOR'
}

export function anchorSkipsPostSanctionSteps(app: Pick<ApplicationResponse, 'loanProduct' | 'intakeSegment'>): boolean {
  return isInvoiceDiscountingAnchorApp(app)
}

/** Anchor hides CAM and disbursement tabs; eSign is required for program terms. */
export function anchorHiddenDetailTabs(): Set<string> {
  return new Set(['cam', 'disbursement'])
}

/** Invoice discounting borrower onboarding ends after eSign — no term-loan disbursement. */
export function idBorrowerSkipsDisbursement(app: Pick<ApplicationResponse, 'loanProduct' | 'intakeSegment'>): boolean {
  return isInvoiceDiscountingBorrowerApp(app)
}

export function isInvoiceDiscountingBorrowerProduct(product: string | null | undefined): boolean {
  return isInvoiceDiscountingProduct(product ?? '')
}

export function idFlowSkipsLmsAtSanction(app: Pick<ApplicationResponse, 'loanProduct' | 'intakeSegment'>): boolean {
  return isInvoiceDiscountingAnchorApp(app) || isInvoiceDiscountingBorrowerApp(app)
}

/** Anchor only — borrower ID flow produces a terms KFS row for eSign. */
export function idFlowSkipsKfsAtSanction(app: Pick<ApplicationResponse, 'loanProduct' | 'intakeSegment'>): boolean {
  return isInvoiceDiscountingAnchorApp(app)
}

/** @deprecated use {@link idFlowSkipsKfsAtSanction} */
export function idFlowSkipsLmsKfs(app: Pick<ApplicationResponse, 'loanProduct' | 'intakeSegment'>): boolean {
  return idFlowSkipsKfsAtSanction(app)
}

export type AnchorDueDiligenceBlock = {
  answers?: Record<string, string>
  comments?: Record<string, string>
  creditRating?: string
  score?: number
  completedAt?: string
  updatedAt?: string
}

export function readAnchorDueDiligence(app: ApplicationResponse): AnchorDueDiligenceBlock {
  const fi = app.financialInfo as Record<string, unknown> | null | undefined
  const raw = fi?.anchorDueDiligence
  if (!raw || typeof raw !== 'object') return { answers: {} }
  return raw as AnchorDueDiligenceBlock
}

export function underwritingTabLabel(segment?: ApplicationIntakeSegment | null, loanProduct?: string): string {
  return isInvoiceDiscountingProduct(loanProduct) && segment === 'ANCHOR' ? 'Anchor rating' : 'Underwriting'
}
