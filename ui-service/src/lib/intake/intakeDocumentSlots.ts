import type { BorrowerType } from '@/types/createApplication'
import type { IntakeFormState } from './intakeTypes'
import { isBusinessBorrowerType } from './intakeTypes'
import { COLLATERAL_DOC, detectSecuredCollateralKind } from './securedProducts'

export interface IntakeDocumentSlot {
  documentType: string
  label: string
  reason: string
}

export function documentSlotsForBorrowerType(bt: BorrowerType): IntakeDocumentSlot[] {
  const base: IntakeDocumentSlot[] = [
    {
      documentType: 'PAN_CARD',
      label: 'PAN card',
      reason: 'Used to verify identity and match the name you provided with the income tax record.',
    },
    {
      documentType: 'AADHAAR',
      label: 'Aadhaar',
      reason: 'Confirms your address and helps complete e-KYC where applicable.',
    },
    {
      documentType: 'BANK_STATEMENT',
      label: 'Bank statement',
      reason: 'Shows cash flows so we can assess ability to repay and validate income activity.',
    },
    {
      documentType: 'PHOTOGRAPH',
      label: 'Photograph',
      reason: 'Required for KYC records and to complete the document checklist for processing.',
    },
  ]

  if (bt === 'INDIVIDUAL') {
    return [
      ...base,
      {
        documentType: 'INCOME_PROOF',
        label: 'Income proof (salary slip, ITR, or Form 16)',
        reason: 'Validates the income you declared for the requested loan amount.',
      },
      {
        documentType: 'OTHER',
        label: 'Other supporting documents',
        reason: 'Any extra papers that explain your case (optional but recommended if asked).',
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
    },
    {
      documentType: 'BUSINESS_PROOF',
      label: 'Business proof (Udyam, license, or partnership deed)',
      reason: 'Proves the business exists, its structure, and (for companies) that filings are in order.',
    },
    {
      documentType: 'OTHER',
      label: 'Other supporting documents',
      reason: 'Board resolutions, CIN printouts, or other context the credit team may need.',
    },
    ]
  }

  return base
}

/** Standard checklist plus product-specific collateral uploads when the selected loan is secured. */
export function allDocumentSlotsForIntake(s: IntakeFormState): IntakeDocumentSlot[] {
  const base = documentSlotsForBorrowerType(s.borrowerType)
  const k = detectSecuredCollateralKind(s.loanProduct)
  if (k === 'PROPERTY') {
    return [
      ...base,
      {
        documentType: COLLATERAL_DOC.PROPERTY_DOCUMENT,
        label: 'Property document (title / deed)',
        reason: 'Confirms the asset offered as security for a LAP or similar loan.',
      },
      {
        documentType: COLLATERAL_DOC.PROPERTY_VALUATION,
        label: 'Property valuation (if available)',
        reason: 'Helps the credit team assess the loan-to-value; optional in demo if not available.',
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
      },
      {
        documentType: COLLATERAL_DOC.GOLD_VALUATION,
        label: 'Valuation of gold (if available)',
        reason: 'Optional weight / purity confirmation from a valuer in demo mode.',
      },
    ]
  }
  return base
}
