import type { BorrowerType } from '@/types/createApplication'
import type { IntakeFormState } from './intakeTypes'
import { isBusinessBorrowerType } from './intakeTypes'
import { COLLATERAL_DOC, detectSecuredCollateralKind } from './securedProducts'

export interface IntakeDocumentSlot {
  documentType: string
  label: string
  reason: string
  required?: boolean
}

/** Borrower-individual docs excluded from anchor corporate onboarding. */
const ANCHOR_EXCLUDED_DOCUMENT_TYPES = new Set(['AADHAAR', 'PHOTOGRAPH'])

/**
 * Document checklist for anchor (invoice discounting) intake — business/entity docs only.
 */
export function documentSlotsForAnchorIntake(bt: BorrowerType): IntakeDocumentSlot[] {
  return documentSlotsForBorrowerType(bt).filter(
    (slot) => !ANCHOR_EXCLUDED_DOCUMENT_TYPES.has(slot.documentType),
  )
}

export function documentSlotsForBorrowerType(bt: BorrowerType): IntakeDocumentSlot[] {
  const base: IntakeDocumentSlot[] = [
    {
      documentType: 'PAN_CARD',
      label: 'PAN card',
      reason: 'Used to verify identity and match the name you provided with the income tax record.',
      required: true,
    },
    {
      documentType: 'AADHAAR',
      label: 'Aadhaar',
      reason: 'Confirms your address and helps complete e-KYC where applicable.',
      required: true,
    },
    {
      documentType: 'BANK_STATEMENT',
      label: 'Bank statement',
      reason: 'Shows cash flows so we can assess ability to repay and validate income activity.',
      required: true,
    },
    {
      documentType: 'PHOTOGRAPH',
      label: 'Photograph',
      reason: 'Required for KYC records and to complete the document checklist for processing.',
      required: true,
    },
  ]

  if (bt === 'INDIVIDUAL') {
    return [
      ...base,
      {
        documentType: 'INCOME_PROOF',
        label: 'Income proof (salary slip, ITR, or Form 16)',
        reason: 'Validates the income you declared for the requested loan amount.',
        required: true,
      },
      {
        documentType: 'OTHER',
        label: 'Other supporting documents',
        reason: 'Any extra papers that explain your case (optional but recommended if asked).',
        required: false,
      },
    ]
  }

  if (isBusinessBorrowerType(bt)) {
    return [
    ...base,
    {
      documentType: 'GST_RETURN',
      label: 'GST return / GSTR',
      reason: 'Helps confirm turnover and business continuity for the entity.',
      required: true,
    },
    {
      documentType: 'BUSINESS_PROOF',
      label: 'Business proof (Udyam, license, or partnership deed)',
      reason: 'Proves the business exists, its structure, and (for companies) that filings are in order.',
      required: true,
    },
    {
      documentType: 'OTHER',
      label: 'Other supporting documents',
      reason: 'Board resolutions, CIN printouts, or other context the credit team may need.',
      required: false,
    },
    ]
  }

  return base
}

/** SCF Invoice Discounting borrower checklist — business base plus policy document set. */
export function documentSlotsForInvoiceDiscountingBorrower(bt: BorrowerType): IntakeDocumentSlot[] {
  const businessBase = documentSlotsForBorrowerType(isBusinessBorrowerType(bt) ? bt : 'PROPRIETOR')
  const withoutAadhaar =
    bt === 'COMPANY' ? businessBase.filter((s) => s.documentType !== 'AADHAAR') : businessBase

  const scfExtras: IntakeDocumentSlot[] = [
    {
      documentType: 'CONSTITUTION_DOCS',
      label: 'Constitution documents',
      reason: 'Partnership deed, MoA/AoA, or equivalent constitutional papers for the entity.',
      required: true,
    },
    {
      documentType: 'GST_RETURN',
      label: 'GST return / GSTR',
      reason: 'Confirms annual turnover and GST continuity for SCF eligibility.',
      required: true,
    },
    {
      documentType: 'ITR',
      label: 'ITR / tax return',
      reason: 'Validates declared income and PAT for underwriting scorecard checks.',
      required: true,
    },
    {
      documentType: 'BANK_STATEMENT',
      label: 'Bank statement',
      reason: 'Banking turnover, ABB, and cheque-bounce conduct for SCF policy.',
      required: true,
    },
    {
      documentType: 'CC_STATEMENT',
      label: 'CC / OD statement',
      reason: 'Shows working-capital utilisation against sanctioned limits.',
      required: false,
    },
    {
      documentType: 'PAYABLES_RECEIVABLES_AGEING',
      label: 'Payables / receivables ageing',
      reason: 'Trade book ageing used to assess invoice quality and concentration.',
      required: false,
    },
    {
      documentType: 'PROPERTY_OWNERSHIP_PROOF',
      label: 'Property ownership proof',
      reason: 'Evidence of owned office / residence where ownership is declared.',
      required: false,
    },
    {
      documentType: 'EXISTING_FACILITY_SANCTION',
      label: 'Existing facility sanction letter',
      reason: 'Documents current FB/NFB limits with other lenders.',
      required: false,
    },
    {
      documentType: 'EXISTING_FACILITY_STATEMENT',
      label: 'Existing facility statement',
      reason: 'Outstanding and conduct on existing banking facilities.',
      required: false,
    },
    {
      documentType: 'PDC',
      label: 'PDC',
      reason: 'Post-dated cheques as security where the program requires them.',
      required: false,
    },
    {
      documentType: 'NACH_MANDATE',
      label: 'NACH mandate',
      reason: 'Auto-debit mandate for repayments / settlements.',
      required: false,
    },
    {
      documentType: 'BUREAU_REPORT',
      label: 'Bureau report',
      reason: 'Consumer bureau evidence for score and enquiry checks.',
      required: false,
    },
    {
      documentType: 'COMMERCIAL_BUREAU_REPORT',
      label: 'Commercial bureau report',
      reason: 'Commercial bureau evidence for the business entity.',
      required: false,
    },
    {
      documentType: 'BOARD_RESOLUTION',
      label: 'Board resolution',
      reason: 'Authorises borrowing and program participation for companies.',
      required: false,
    },
    {
      documentType: 'PURCHASE_ORDER',
      label: 'Purchase order',
      reason: 'Underlying trade evidence for the receivable being discounted.',
      required: false,
    },
    {
      documentType: 'DELIVERY_GRN',
      label: 'Delivery / GRN',
      reason: 'Proof of delivery supporting the invoice / PO chain.',
      required: false,
    },
    {
      documentType: 'TRADE_PAYMENT_RECORD',
      label: 'Trade payment record',
      reason: 'Historical settlement evidence with the anchor / buyer.',
      required: false,
    },
    {
      documentType: 'BUYER_NOC',
      label: 'Buyer NOC',
      reason: 'Buyer / anchor no-objection where program terms require it.',
      required: false,
    },
  ]

  const seen = new Set<string>()
  const merged: IntakeDocumentSlot[] = []
  for (const slot of [...withoutAadhaar, ...scfExtras]) {
    if (seen.has(slot.documentType)) continue
    seen.add(slot.documentType)
    merged.push(slot)
  }
  return merged
}

/** Standard checklist plus product-specific collateral uploads when the selected loan is secured. */
export function allDocumentSlotsForIntake(s: IntakeFormState): IntakeDocumentSlot[] {
  const base =
    s.loanProduct === 'BUSINESS_WC_INVOICE_DISCOUNTING' && s.invoiceOnboardingChoice !== 'ANCHOR'
      ? documentSlotsForInvoiceDiscountingBorrower(s.borrowerType)
      : documentSlotsForBorrowerType(s.borrowerType)
  const k = detectSecuredCollateralKind(s.loanProduct)
  if (k === 'PROPERTY') {
    return [
      ...base,
      {
        documentType: COLLATERAL_DOC.PROPERTY_DOCUMENT,
        label: 'Property document (title / deed)',
        reason: 'Confirms the asset offered as security for a LAP or similar loan.',
        required: true,
      },
      {
        documentType: COLLATERAL_DOC.PROPERTY_VALUATION,
        label: 'Property valuation (if available)',
        reason: 'Helps the credit team assess the loan-to-value; optional in demo if not available.',
        required: false,
      },
    ]
  }
  if (k === 'SHARES') {
    return [
      ...base,
      {
        documentType: COLLATERAL_DOC.SHARE_HOLDING_STATEMENT,
        label: 'Share / demat holding statement',
        reason: 'Shows positions pledged or offered as security for Loan Against Shares.',
        required: true,
      },
    ]
  }
  if (k === 'GOLD') {
    return [
      ...base,
      {
        documentType: COLLATERAL_DOC.GOLD_PHOTO,
        label: 'Photo of gold / ornaments',
        reason: 'Visual record of the items pledged for a gold loan.',
        required: true,
      },
      {
        documentType: COLLATERAL_DOC.GOLD_VALUATION,
        label: 'Valuation of gold (if available)',
        reason: 'Optional weight / purity confirmation from a valuer in demo mode.',
        required: false,
      },
    ]
  }
  return base
}

export function missingAnchorDocumentTypes(
  borrowerType: BorrowerType,
  documentUploaded: Record<string, boolean>,
): string[] {
  return documentSlotsForAnchorIntake(borrowerType)
    .filter((slot) => slot.required !== false && slot.documentType !== 'OTHER')
    .filter((slot) => !documentUploaded[slot.documentType])
    .map((slot) => slot.documentType)
}
