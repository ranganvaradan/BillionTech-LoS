import { resolveIntakeSegment } from '@/lib/applicationPartyLabels'
import type { ApplicationIntakeSegment } from '@/types/application'

function pickStr(rec: Record<string, unknown> | null | undefined, key: string): string {
  if (!rec) return ''
  const v = rec[key]
  return v == null ? '' : String(v).trim()
}

export function isAnchorApplication(segment?: ApplicationIntakeSegment | null): boolean {
  return resolveIntakeSegment(segment) === 'ANCHOR'
}

export type AnchorKycFormValues = {
  panNumber: string
  name: string
  mobile: string
  gstin: string
  businessName: string
  accountNumber: string
  ifsc: string
  bankName: string
  cin: string
}

/** Map anchor intake `businessInfo` keys into the shared KYC form state. */
export function hydrateAnchorKycFields(
  pi: Record<string, unknown> | null | undefined,
  bi: Record<string, unknown> | null | undefined,
  fi: Record<string, unknown> | null | undefined,
): AnchorKycFormValues {
  return {
    panNumber: pickStr(bi, 'entityPan') || pickStr(pi, 'panNumber'),
    name:
      pickStr(bi, 'accountHolderName') ||
      pickStr(bi, 'corporateName') ||
      pickStr(pi, 'fullName') ||
      pickStr(pi, 'name'),
    mobile: pickStr(bi, 'mobile') || pickStr(pi, 'mobile') || pickStr(pi, 'phone'),
    gstin: pickStr(bi, 'gstin'),
    businessName: pickStr(bi, 'corporateName') || pickStr(bi, 'businessName'),
    accountNumber:
      pickStr(bi, 'bankAccountNumber') || pickStr(pi, 'bankAccountNumber') || pickStr(fi, 'accountNumber'),
    ifsc: pickStr(bi, 'ifscCode') || pickStr(pi, 'ifsc') || pickStr(fi, 'ifsc'),
    bankName: pickStr(bi, 'bankName') || pickStr(pi, 'bankName'),
    cin: pickStr(bi, 'cin'),
  }
}

export function buildAnchorKycSavePayload(
  existingPi: Record<string, unknown> | null | undefined,
  existingBi: Record<string, unknown> | null | undefined,
  existingFi: Record<string, unknown> | null | undefined,
  values: AnchorKycFormValues,
): {
  personalInfo: Record<string, unknown>
  businessInfo: Record<string, unknown>
  financialInfo: Record<string, unknown>
} {
  const mobile = values.mobile.trim()
  const accountDigits = values.accountNumber.replace(/\D/g, '').trim()
  const ifsc = values.ifsc.trim().toUpperCase()

  const pan = values.panNumber.trim().toUpperCase()
  const personalInfo: Record<string, unknown> = {
    ...(existingPi ?? {}),
    mobile: mobile || undefined,
    phone: mobile || undefined,
    panNumber: pan || undefined,
  }
  Object.keys(personalInfo).forEach((k) => {
    if (personalInfo[k] === undefined) delete personalInfo[k]
  })

  const businessInfo: Record<string, unknown> = {
    ...(existingBi ?? {}),
    entityPan: values.panNumber.trim().toUpperCase() || undefined,
    corporateName: values.businessName.trim() || undefined,
    accountHolderName: values.name.trim() || undefined,
    gstin: values.gstin.trim().toUpperCase() || undefined,
    cin: values.cin.trim().toUpperCase() || undefined,
    bankAccountNumber: accountDigits || undefined,
    ifscCode: ifsc || undefined,
    bankName: values.bankName.trim() || undefined,
  }
  Object.keys(businessInfo).forEach((k) => {
    if (businessInfo[k] === undefined) delete businessInfo[k]
  })

  const financialInfo: Record<string, unknown> = {
    ...(existingFi ?? {}),
    accountNumber: accountDigits || undefined,
    ifsc: ifsc || undefined,
  }
  Object.keys(financialInfo).forEach((k) => {
    if (financialInfo[k] === undefined) delete financialInfo[k]
  })

  return { personalInfo, businessInfo, financialInfo }
}
