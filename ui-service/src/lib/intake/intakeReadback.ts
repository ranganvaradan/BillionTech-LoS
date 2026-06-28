import { borrowerTypeLabel } from '@/catalog/borrowerTypes'
import { loanProductLabel } from '@/catalog/loanProducts'
import { applicationPartyLabels, resolveIntakeSegment } from '@/lib/applicationPartyLabels'
import { BORROWER_INTAKE_KEY } from './collateralIntakePayload'
import type { ApplicationResponse } from '@/types/application'

/** Internal / system keys we never surface as row labels to staff (values may still inform other UI). */
const HIDDEN_KEYS = new Set([
  'password',
  'passwordHash',
  'token',
  'journeyChannel', // superseded by friendly intake mode where present
])

function str(v: unknown): string {
  if (v == null) return ''
  if (typeof v === 'boolean') return v ? 'Yes' : 'No'
  if (typeof v === 'number') return String(v)
  return String(v).trim()
}

function formatIntakeMode(raw: string): string {
  const u = raw.toUpperCase()
  if (u === 'BORROWER_SELF_SERVICE') return 'Borrower self-service'
  if (u === 'SALES_ASSISTED') return 'Sales-assisted'
  if (u === 'ADMIN_INTERNAL') return 'Internal (admin / operations)'
  return raw
}

function formatConsentLine(v: unknown): string {
  const s = str(v)
  if (s === 'true' || s === '1') return 'Yes'
  if (s === 'false' || s === '0') return 'No'
  return s || '—'
}

function formatYesNo(v: unknown): string {
  if (v === true) return 'Yes'
  if (v === false) return 'No'
  return formatConsentLine(v)
}

export type IntakeReadbackRow = { label: string; value: string }

export type IntakeReadbackSection = { title: string; rows: IntakeReadbackRow[] }

function pushRow(
  rows: IntakeReadbackRow[],
  label: string,
  value: unknown,
  allowEmpty = false,
) {
  const v = str(value)
  if (!allowEmpty && !v) return
  rows.push({ label, value: v || '—' })
}

/**
 * Structured, business-friendly readback of borrower + staff intake data for internal Application Detail.
 * Omits empty fields; never includes raw map keys in labels.
 */
export function buildIntakeReadback(app: ApplicationResponse): IntakeReadbackSection[] {
  const L = applicationPartyLabels(app.intakeSegment)
  const isAnchor = resolveIntakeSegment(app.intakeSegment) === 'ANCHOR'
  const pi = (app.personalInfo ?? null) as Record<string, unknown> | null
  const bi = (app.businessInfo ?? null) as Record<string, unknown> | null
  const fi = (app.financialInfo ?? null) as Record<string, unknown> | null
  const out: IntakeReadbackSection[] = []

  const product: IntakeReadbackRow[] = []
  pushRow(product, 'Loan product', loanProductLabel(app.loanProduct))
  pushRow(product, L.entityClassRow, borrowerTypeLabel(app.borrowerType))
  if (app.requestedAmount != null) {
    product.push({ label: 'Requested amount (INR)', value: new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 }).format(app.requestedAmount) })
  }
  if (app.tenureMonths != null) {
    pushRow(product, 'Tenure (months)', app.tenureMonths)
  }
  if (pi) {
    pushRow(product, 'Purpose of loan', pi.purpose)
  }
  if (product.length) out.push({ title: 'Product & request', rows: product })

  const channel: IntakeReadbackRow[] = []
  if (pi) {
    const im = str(pi.intakeMode)
    if (im) pushRow(channel, 'Intake channel', formatIntakeMode(im))
    const jc = str(pi.journeyChannel)
    if (jc) pushRow(channel, 'Journey channel', formatIntakeMode(jc))
    pushRow(channel, L.linkedPartyUserId, pi.borrowerUserId)
    pushRow(channel, L.partyEmailRecord, pi.borrowerEmail)
    pushRow(channel, L.partyMobileRecord, pi.borrowerMobile)
    pushRow(channel, 'Created by (user id)', pi.createdByUserId)
    pushRow(channel, 'Created by (role)', pi.createdByRole)
    pushRow(channel, 'Assisted by (user id)', pi.assistedByUserId)
    pushRow(channel, 'Assisted by (role)', pi.assistedByRole)
    pushRow(channel, 'Sales / branch id', pi.salesOfficerId)
    pushRow(channel, 'Assisting officer', pi.salesOfficerName)
    const assisted = str(pi.assistedBy)
    if (assisted) pushRow(channel, 'Assistance (summary)', assisted)
  }
  if (channel.length) out.push({ title: 'Intake, ownership & assistance', rows: channel })

  const identity: IntakeReadbackRow[] = []
  if (isAnchor && bi) {
    pushRow(identity, 'Corporate name', bi.corporateName)
    pushRow(identity, 'Email', bi.email)
    pushRow(identity, 'Mobile', bi.mobile)
    pushRow(identity, 'Date of incorporation', bi.dateOfIncorporation)
    pushRow(identity, 'Account holder name', bi.accountHolderName)
  } else if (pi) {
    pushRow(identity, 'Full name', pi.fullName ?? pi.name)
    pushRow(identity, 'Email', pi.email)
    pushRow(identity, 'Mobile', pi.mobile ?? pi.phone)
    pushRow(identity, 'Date of birth', pi.dateOfBirth)
    pushRow(identity, 'Gender', pi.gender ? String(pi.gender).replaceAll('_', ' ') : '')
    pushRow(identity, 'Marital status', pi.maritalStatus ? String(pi.maritalStatus).replaceAll('_', ' ') : '')
  }
  if (identity.length) out.push({ title: L.identitySectionTitle, rows: identity })

  const address: IntakeReadbackRow[] = []
  if (pi) {
    pushRow(address, 'Address line 1', pi.addressLine)
    pushRow(address, 'Address line 2', pi.addressLine2)
    pushRow(address, 'City', pi.city)
    pushRow(address, 'State', pi.state)
    pushRow(address, 'PIN code', pi.pincode)
    if (str(pi.addressProofType)) {
      pushRow(address, 'Address proof type', String(pi.addressProofType).replaceAll('_', ' '))
    }
  }
  if (address.length) out.push({ title: 'Address', rows: address })

  const existing: IntakeReadbackRow[] = []
  if (pi) {
    if (str(pi.hasExistingLoans)) {
      const h = str(pi.hasExistingLoans)
      pushRow(existing, 'Other loans running', h === 'yes' ? 'Yes' : h === 'no' ? 'No' : h)
    }
    pushRow(existing, 'Existing loan details', pi.existingLoansDetails, true)
  }
  if (existing.length) out.push({ title: 'Existing borrowings', rows: existing })

  const kyc: IntakeReadbackRow[] = []
  if (isAnchor && bi) {
    pushRow(kyc, 'Entity PAN', bi.entityPan)
    pushRow(kyc, 'GSTIN', bi.gstin)
    pushRow(kyc, 'CIN / company id', bi.cin)
  } else {
    if (pi) {
      pushRow(kyc, 'PAN', pi.panNumber)
      if (str(pi.aadhaarNumber)) {
        pushRow(kyc, 'Aadhaar (full on file)', 'Submitted')
      } else if (str(pi.aadhaarLast4)) {
        pushRow(kyc, 'Aadhaar (last 4 digits)', pi.aadhaarLast4)
      }
      if (pi.mobileLinkedAadhaar != null) {
        kyc.push({ label: 'Mobile suitable for e-KYC / Aadhaar', value: formatYesNo(pi.mobileLinkedAadhaar) })
      }
    }
    if (bi) {
      pushRow(kyc, 'GSTIN', bi.gstin)
      pushRow(kyc, 'Udyam', bi.udyam)
      pushRow(kyc, 'CIN / company id', bi.cin)
    }
  }
  if (kyc.length) out.push({ title: 'KYC & statutory ids', rows: kyc })

  const bank: IntakeReadbackRow[] = []
  if (isAnchor && bi) {
    const acct = str(bi.bankAccountNumber)
    if (acct) {
      bank.push({ label: 'Bank account number', value: acct })
    }
    pushRow(bank, 'IFSC', bi.ifscCode)
    pushRow(bank, 'Account holder name', bi.accountHolderName)
    pushRow(bank, 'Bank name', bi.bankName)
  } else if (pi) {
    const acct = str(pi.bankAccountNumber)
    if (acct) {
      bank.push({ label: 'Bank account number', value: acct })
    }
    pushRow(bank, 'IFSC', pi.ifsc)
    pushRow(bank, 'Bank name', pi.bankName)
  }
  if (bank.length) out.push({ title: 'Bank account (for disbursement / verification)', rows: bank })

  const work: IntakeReadbackRow[] = []
  if (pi) {
    if (str(pi.employmentType)) {
      pushRow(work, 'Employment type', String(pi.employmentType).replaceAll('_', ' '))
    }
    pushRow(work, 'Employer / business name', pi.employerName)
    if (str(pi.monthlyNetIncome)) {
      work.push({ label: 'Monthly net income (INR, declared)', value: str(pi.monthlyNetIncome) })
    }
    pushRow(work, 'Occupation / industry', pi.occupationIndustry)
    pushRow(work, 'Work experience (years)', pi.workExperienceYears)
  }
  if (work.length) out.push({ title: 'Employment & income (declared at intake)', rows: work })

  const business: IntakeReadbackRow[] = []
  if (bi) {
    pushRow(
      business,
      isAnchor ? 'Corporate name' : 'Business / entity name',
      isAnchor ? bi.corporateName : bi.businessName,
    )
    if (!isAnchor) {
      pushRow(business, 'Contact person', bi.contactPersonName)
    }
    pushRow(business, isAnchor ? 'Registered address' : 'Registered address (business)', bi.addressLine)
    pushRow(business, 'City', bi.city)
    pushRow(business, 'State', bi.state)
    if (isAnchor) {
      pushRow(business, 'PIN code', bi.pincode)
    }
  }
  if (business.length) out.push({ title: 'Business profile', rows: business })

  const consents: IntakeReadbackRow[] = []
  if (fi) {
    consents.push({ label: 'KYC verification', value: formatConsentLine(fi.consentKyc) })
    consents.push({ label: 'Credit bureau pull', value: formatConsentLine(fi.consentBureau) })
    consents.push({ label: 'Bank statement / account aggregator', value: formatConsentLine(fi.consentAccountAggregator) })
    consents.push({ label: 'WhatsApp / SMS / email updates', value: formatConsentLine(fi.consentComms) })
    pushRow(consents, 'Consent recorded by (name)', fi.consentRecordedByName)
    pushRow(consents, 'Consent recorded by (user id)', fi.consentRecordedByUserId)
    pushRow(consents, 'Consent captured by (name)', fi.consentCapturedByName)
    pushRow(consents, 'Consent captured by (user id)', fi.consentCapturedByUserId)
  }
  if (consents.length) out.push({ title: 'Consents (application)', rows: consents })

  const cinfo = (app.collateralInfo ?? null) as Record<string, unknown> | null
  const bint = cinfo
    ? (cinfo[BORROWER_INTAKE_KEY] as Record<string, unknown> | undefined) ?? (cinfo.borrowerIntake as Record<string, unknown> | undefined)
    : null
  if (bint) {
    const cRows: IntakeReadbackRow[] = []
    const ctype = str(bint.collateralType)
    if (ctype) {
      cRows.push({
        label: 'Collateral class',
        value: ctype === 'PROPERTY' ? 'Property' : ctype === 'SHARES' ? 'Securities' : ctype === 'GOLD' ? 'Gold' : ctype,
      })
    }
    if (str(bint.product)) pushRow(cRows, 'Product (workflow)', bint.product)
    if (bint.estimatedValue != null) {
      const n = Number(bint.estimatedValue)
      cRows.push({
        label: 'Estimated value (INR)',
        value: Number.isFinite(n) ? new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 }).format(n) : str(bint.estimatedValue),
      })
    }
    if (str(bint.providedBy)) {
      const pb = str(bint.providedBy)
      const l =
        pb === 'BORROWER' ? 'Borrower' : pb === 'SALES_ASSISTED' ? 'Sales-assisted' : pb === 'ADMIN' ? 'Internal (admin)' : pb
      pushRow(cRows, 'Provided by', l)
    }
    if (str(bint.providedAt)) pushRow(cRows, 'Captured at', bint.providedAt)
    const dj = str(bint.detailsJson)
    if (dj) {
      try {
        const parsed = JSON.parse(dj) as Record<string, unknown>
        for (const [k, v] of Object.entries(parsed)) {
          if (v == null || str(v) === '') continue
          cRows.push({ label: humanizeCollateralDetailKey(k), value: str(v) })
        }
      } catch {
        // ignore
      }
    }
    const ids = bint.supportingDocumentIds
    if (Array.isArray(ids) && ids.length) {
      cRows.push({ label: 'Supporting document uploads (file ids on record)', value: String(ids.length) + ' file(s)' })
    }
    if (cRows.length) out.push({ title: L.collateralIntakeSectionTitle, rows: cRows })
  }

  // Optional: any remaining top-level string fields in personalInfo (excluding hidden), for forward compatibility.
  if (pi) {
    const used = new Set(
      [
        'intakeMode',
        'journeyChannel',
        'borrowerUserId',
        'borrowerEmail',
        'borrowerMobile',
        'createdByUserId',
        'createdByRole',
        'assistedByUserId',
        'assistedByRole',
        'salesOfficerName',
        'salesOfficerId',
        'assistedBy',
        'assistedChannel',
        'fullName',
        'name',
        'email',
        'phone',
        'mobile',
        'purpose',
        'dateOfBirth',
        'gender',
        'maritalStatus',
        'addressLine',
        'addressLine2',
        'city',
        'state',
        'pincode',
        'addressProofType',
        'hasExistingLoans',
        'existingLoansDetails',
        'panNumber',
        'aadhaarNumber',
        'aadhaarLast4',
        'mobileLinkedAadhaar',
        'bankAccountNumber',
        'ifsc',
        'bankName',
        'employmentType',
        'employerName',
        'monthlyNetIncome',
        'occupationIndustry',
        'workExperienceYears',
        'createdByName',
        'lastSavedByName',
        'lastSavedByUserId',
      ].map((k) => k.toLowerCase()),
    )
    const extra: IntakeReadbackRow[] = []
    for (const [k, v] of Object.entries(pi)) {
      if (HIDDEN_KEYS.has(k.toLowerCase())) continue
      if (used.has(k.toLowerCase())) continue
      if (v == null || str(v) === '') continue
      if (typeof v === 'object') continue
      extra.push({
        label: k.replaceAll(/([A-Z])/g, ' $1').replace(/^./, (c) => c.toUpperCase()).trim(),
        value: str(v),
      })
    }
    if (extra.length) {
      out.push({ title: 'Other intake fields', rows: extra.slice(0, 20) })
    }
  }

  return out
}

function humanizeCollateralDetailKey(k: string): string {
  const m: Record<string, string> = {
    propertyType: 'Property type',
    propertyAddress: 'Property address',
    ownershipType: 'Ownership type',
    existingMortgageOrEncumbrance: 'Existing mortgage or encumbrance',
    securityType: 'Security type',
    isin: 'ISIN',
    companyOrMutualFundName: 'Company / fund name',
    quantity: 'Quantity',
    dematAccountNumber: 'Demat account number',
    pledgeConsent: 'Pledge consent',
    goldType: 'Gold type',
    approxGrossWeight: 'Approx. gross weight',
    approxNetWeight: 'Approx. net weight',
    purityOrKarat: 'Purity / karat',
    ornamentDescription: 'Item description',
  }
  if (m[k]) return m[k]!
  return k.replaceAll(/([A-Z])/g, ' $1').replace(/^./, (c) => c.toUpperCase()).trim()
}
