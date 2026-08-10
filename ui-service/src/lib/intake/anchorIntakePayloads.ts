import type { CreateApplicationRequest, IntakeSegment } from '@/types/createApplication'
import type { UpdateApplicationRequest } from '@/types/updateApplication'
import type { SessionUser } from '@/auth/types'
import type { IntakeMode } from './intakeTypes'
import type { AnchorFormState } from './anchorIntakeTypes'

function parseTenure(s: string): number | null {
  const t = s.trim()
  if (!t) return null
  const n = Number.parseInt(t, 10)
  if (Number.isNaN(n) || n <= 0) return null
  return n
}

export function buildAnchorCreateRequest(
  s: AnchorFormState,
  staff: SessionUser | null,
  staffIntakeMode: IntakeMode = 'ADMIN_INTERNAL',
): CreateApplicationRequest {
  const amount = Number.parseFloat(s.requestedAmount)
  if (Number.isNaN(amount) || amount <= 0) {
    throw new Error('Invalid amount')
  }
  const tenure = parseTenure(s.tenureMonths)
  const journeyChannel = staffIntakeMode === 'SALES_ASSISTED' ? 'SALES_ASSISTED' : 'INTERNAL'
  const intakeModeStr = staffIntakeMode === 'SALES_ASSISTED' ? 'SALES_ASSISTED' : 'ADMIN_INTERNAL'
  const customFieldValues = Object.fromEntries(
    Object.entries(s.customFieldValues ?? {}).filter(([, v]) =>
      typeof v === 'boolean' ? true : String(v ?? '').trim().length > 0,
    ),
  )
  const personalInfo: Record<string, unknown> = {
    journeyChannel,
    intakeMode: intakeModeStr,
    phone: s.mobile.trim(),
    mobile: s.mobile.trim(),
    ...(s.purpose.trim() ? { purpose: s.purpose.trim() } : {}),
    ...(Object.keys(customFieldValues).length ? { customFields: customFieldValues } : {}),
  }
  if (staff) {
    personalInfo.createdByName = staff.name
    personalInfo.createdByUserId = staff.userId
  }
  const businessInfo: Record<string, unknown> = {
    corporateName: s.corporateName.trim(),
    email: s.email.trim(),
    mobile: s.mobile.trim(),
    dateOfIncorporation: s.dateOfIncorporation.trim(),
    addressLine: s.addressLine.trim(),
    city: s.city.trim(),
    state: s.state.trim(),
    country: s.country.trim(),
    pincode: s.pincode.replace(/\D/g, '').slice(0, 6),
  }
  return {
    borrowerType: s.borrowerType,
    loanProduct: s.loanProduct,
    ...(s.workflowId ? { workflowId: s.workflowId } : {}),
    intakeSegment: 'ANCHOR' satisfies IntakeSegment,
    requestedAmount: amount,
    tenureMonths: tenure ?? undefined,
    personalInfo,
    businessInfo,
  }
}

export function buildAnchorIdentityUpdate(s: AnchorFormState): UpdateApplicationRequest {
  const bi: Record<string, unknown> = {
    entityPan: s.entityPan.trim().toUpperCase(),
    gstin: s.gstin.trim().toUpperCase(),
    cin: s.cin.trim().toUpperCase(),
    bankAccountNumber: s.bankAccountNumber.trim(),
    ifscCode: s.ifscCode.trim().toUpperCase(),
    accountHolderName: s.accountHolderName.trim(),
  }
  return { businessInfo: bi }
}

export function buildAnchorConsentUpdate(s: AnchorFormState, staff: SessionUser | null): UpdateApplicationRequest {
  const financial: Record<string, string> = {
    consentKyc: s.consentKyc ? 'true' : 'false',
    consentBureau: s.consentBureau ? 'true' : 'false',
    consentAccountAggregator: s.consentAccountAggregator ? 'true' : 'false',
    consentComms: s.consentComms ? 'true' : 'false',
  }
  if (staff) {
    financial.consentRecordedByName = staff.name
    financial.consentRecordedByUserId = staff.userId
  }
  return { financialInfo: financial as unknown as Record<string, unknown> }
}

/** Full field merge used when RM saves an existing anchor application via Continue intake. */
export function buildAnchorFullUpdate(
  s: AnchorFormState,
  staff: SessionUser | null,
): UpdateApplicationRequest {
  const amount = Number.parseFloat(s.requestedAmount)
  const tenure = parseTenure(s.tenureMonths)
  const identity = buildAnchorIdentityUpdate(s)
  const consent = buildAnchorConsentUpdate(s, staff)
  const customFieldValues = Object.fromEntries(
    Object.entries(s.customFieldValues ?? {}).filter(([, v]) =>
      typeof v === 'boolean' ? true : String(v ?? '').trim().length > 0,
    ),
  )
  return {
    ...(s.workflowId ? { workflowId: s.workflowId } : {}),
    requestedAmount: Number.isFinite(amount) && amount > 0 ? amount : undefined,
    tenureMonths: tenure ?? undefined,
    personalInfo: {
      phone: s.mobile.trim(),
      mobile: s.mobile.trim(),
      ...(s.purpose.trim() ? { purpose: s.purpose.trim() } : {}),
      ...(Object.keys(customFieldValues).length ? { customFields: customFieldValues } : {}),
    },
    businessInfo: {
      corporateName: s.corporateName.trim(),
      email: s.email.trim(),
      mobile: s.mobile.trim(),
      dateOfIncorporation: s.dateOfIncorporation.trim(),
      addressLine: s.addressLine.trim(),
      city: s.city.trim(),
      state: s.state.trim(),
      country: s.country.trim(),
      pincode: s.pincode.replace(/\D/g, '').slice(0, 6),
      ...(identity.businessInfo ?? {}),
    },
    financialInfo: consent.financialInfo,
  }
}
