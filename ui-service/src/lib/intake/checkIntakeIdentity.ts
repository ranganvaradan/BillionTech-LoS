import { http } from '@/api/http'
import type { IntakeFormState, IntakeMode } from '@/lib/intake/intakeTypes'
import { isBusinessBorrowerType } from '@/lib/intake/intakeTypes'
import { duplicateFieldErrors, userFriendlyMessage } from '@/lib/userFriendlyError'

export type ValidateIdentityPayload = {
  applicationId?: string
  email?: string
  mobile?: string
  panNumber?: string
  gstin?: string
  asCoApplicant?: boolean
}

export async function validateApplicationIdentity(payload: ValidateIdentityPayload): Promise<void> {
  await http.post('/applications/validate-identity', payload)
}

function normalizeMobile(s: string): string {
  return s.replace(/\D/g, '')
}

export function borrowerIdentityPayload(form: IntakeFormState, mode: IntakeMode): ValidateIdentityPayload {
  if (form.borrowerType === 'INDIVIDUAL') {
    const mobile =
      normalizeMobile(form.mobile) ||
      (mode === 'SALES_ASSISTED' ? normalizeMobile(form.borrowerMobile) : '')
    return {
      email: form.email.trim() || undefined,
      mobile: mobile || undefined,
    }
  }
  return {
    email: form.contactEmail.trim() || undefined,
    mobile: normalizeMobile(form.contactMobile) || undefined,
  }
}

export function kycIdentityPayload(form: IntakeFormState): ValidateIdentityPayload {
  const payload: ValidateIdentityPayload = {
    panNumber: form.panNumber.trim() || undefined,
  }
  if (isBusinessBorrowerType(form.borrowerType)) {
    payload.gstin = form.gstin.trim() || undefined
  }
  return payload
}

/**
 * Returns field-level errors for duplicate identity, or rethrows other API failures.
 */
export async function checkBorrowerIdentity(
  form: IntakeFormState,
  mode: IntakeMode,
  applicationId: string | null,
): Promise<Record<string, string> | null> {
  try {
    await validateApplicationIdentity({
      applicationId: applicationId ?? undefined,
      ...borrowerIdentityPayload(form, mode),
    })
    return null
  } catch (err) {
    const dup = duplicateFieldErrors(err)
    if (dup) return dup
    throw err
  }
}

export async function checkKycIdentity(
  form: IntakeFormState,
  applicationId: string | null,
): Promise<Record<string, string> | null> {
  try {
    await validateApplicationIdentity({
      applicationId: applicationId ?? undefined,
      ...kycIdentityPayload(form),
    })
    return null
  } catch (err) {
    const dup = duplicateFieldErrors(err)
    if (dup) return dup
    throw err
  }
}

export function intakeErrorMessage(err: unknown, fallback: string): string {
  return userFriendlyMessage(err, fallback)
}

export function intakeStepForDuplicateField(
  field: string,
  steps: { borrower: number; kyc: number },
): number | null {
  if (field === 'email' || field === 'mobile' || field === 'borrowerMobile' || field === 'contactMobile') {
    return steps.borrower
  }
  if (field === 'panNumber' || field === 'gstin') return steps.kyc
  return null
}
