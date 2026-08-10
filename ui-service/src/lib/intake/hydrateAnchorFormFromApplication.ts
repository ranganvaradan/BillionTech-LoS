import type { ApplicationResponse } from '@/types/application'
import type { LoanProductCode } from '@/types/createApplication'
import { ANCHOR_BORROWER_TYPE } from './anchorIntakeConstants'
import { createEmptyAnchorFormState, type AnchorFormState } from './anchorIntakeTypes'

/**
 * Fills the anchor intake form from a GET /applications response for Continue intake.
 */
export function hydrateAnchorFormFromApplication(
  app: ApplicationResponse,
  base: AnchorFormState = createEmptyAnchorFormState(),
): AnchorFormState {
  const pi = (app.personalInfo ?? {}) as Record<string, unknown>
  const bi = (app.businessInfo ?? {}) as Record<string, unknown>
  const fi = (app.financialInfo ?? {}) as Record<string, unknown>
  const str = (o: Record<string, unknown>, k: string) =>
    o[k] != null && o[k] !== undefined ? String(o[k]) : ''
  const toBool = (o: Record<string, unknown>, k: string) =>
    o[k] === true || String(o[k] ?? '').toLowerCase() === 'true'

  const customFieldsRaw =
    pi.customFields && typeof pi.customFields === 'object' && !Array.isArray(pi.customFields)
      ? (pi.customFields as Record<string, unknown>)
      : {}
  const customFieldValues: Record<string, string | boolean> = {
    ...base.customFieldValues,
    ...Object.fromEntries(
      Object.entries(customFieldsRaw).map(([k, v]) => [k, typeof v === 'boolean' ? v : String(v ?? '')]),
    ),
  }

  return {
    ...base,
    borrowerType: ANCHOR_BORROWER_TYPE,
    loanProduct: (app.loanProduct as LoanProductCode) || base.loanProduct,
    workflowId: app.workflowId ?? base.workflowId,
    requestedAmount: app.requestedAmount != null ? String(app.requestedAmount) : base.requestedAmount,
    tenureMonths: app.tenureMonths != null ? String(app.tenureMonths) : base.tenureMonths,
    purpose: str(pi, 'purpose') || base.purpose,
    corporateName: str(bi, 'corporateName') || str(bi, 'businessName') || base.corporateName,
    email: str(bi, 'email') || str(pi, 'email') || base.email,
    mobile: str(bi, 'mobile') || str(pi, 'mobile') || str(pi, 'phone') || base.mobile,
    dateOfIncorporation: str(bi, 'dateOfIncorporation') || base.dateOfIncorporation,
    addressLine: str(bi, 'addressLine') || str(bi, 'businessAddress') || base.addressLine,
    city: str(bi, 'city') || base.city,
    state: str(bi, 'state') || base.state,
    country: str(bi, 'country') || base.country || 'India',
    pincode: str(bi, 'pincode') || base.pincode,
    entityPan: str(bi, 'entityPan') || str(pi, 'panNumber') || base.entityPan,
    gstin: str(bi, 'gstin') || base.gstin,
    cin: str(bi, 'cin') || base.cin,
    bankAccountNumber:
      str(bi, 'bankAccountNumber') || str(pi, 'bankAccountNumber') || str(fi, 'accountNumber') || base.bankAccountNumber,
    ifscCode: str(bi, 'ifscCode') || str(pi, 'ifsc') || str(fi, 'ifsc') || base.ifscCode,
    accountHolderName: str(bi, 'accountHolderName') || str(pi, 'fullName') || base.accountHolderName,
    customFieldValues,
    consentKyc: toBool(fi, 'consentKyc') || toBool(pi, 'consentKyc') || base.consentKyc,
    consentBureau: toBool(fi, 'consentBureau') || toBool(pi, 'consentBureau') || base.consentBureau,
    consentAccountAggregator: toBool(fi, 'consentAccountAggregator') || base.consentAccountAggregator,
    consentComms: toBool(fi, 'consentComms') || toBool(pi, 'consentComms') || base.consentComms,
    documentUploaded: { ...base.documentUploaded },
  }
}
