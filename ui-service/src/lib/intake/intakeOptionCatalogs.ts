import type { WorkflowCodedOption, WorkflowConfigResponse } from '@/types/workflow'

export type IntakeCodedOption = WorkflowCodedOption

export const DEFAULT_OCCUPATION_OPTIONS: IntakeCodedOption[] = [
  { value: 'OTHER', label: 'None / Other' },
  { value: 'SELF_EMPLOYED_BUSINESS', label: 'Self Employed / Business' },
  { value: 'SELF_EMPLOYED_PROFESSIONAL', label: 'Self Employed professional' },
  { value: 'SALARIED_PRIVATE', label: 'Salaried — private sector' },
  { value: 'SALARIED_GOVERNMENT', label: 'Salaried — government' },
]

export const DEFAULT_LOAN_PURPOSE_OPTIONS: IntakeCodedOption[] = [
  { value: 'OTHER', label: 'Others' },
  { value: 'SIBLING_MARRIAGE', label: "Sibling's Marriage" },
  { value: 'PURCHASE_DURABLES', label: 'Purchase of Durables' },
  { value: 'BUSINESS_PURPOSE', label: 'Business Purpose' },
  { value: 'OWN_MARRIAGE', label: 'Own Marriage' },
  { value: 'EDUCATION', label: 'Education' },
  { value: 'VEHICLE_PURCHASE', label: 'Vehicle Purchase' },
  { value: 'HOUSE_REPAIR', label: 'House Repair' },
  { value: 'DEBT_CONSOLIDATION', label: 'Debt Consolidation' },
]

export function resolveOccupationOptions(
  workflow: WorkflowConfigResponse | null | undefined,
): IntakeCodedOption[] {
  const configured = workflow?.intakeConfig?.occupationRules?.options
  if (configured?.length) return configured.map(({ value, label }) => ({ value, label }))
  return DEFAULT_OCCUPATION_OPTIONS
}

export function resolveLoanPurposeOptions(
  workflow: WorkflowConfigResponse | null | undefined,
): IntakeCodedOption[] {
  const configured = workflow?.intakeConfig?.loanPurposeRules?.options
  if (configured?.length) return configured.map(({ value, label }) => ({ value, label }))
  return DEFAULT_LOAN_PURPOSE_OPTIONS
}

export function labelForOccupation(
  code: string,
  workflow?: WorkflowConfigResponse | null,
): string {
  const opt = resolveOccupationOptions(workflow).find((o) => o.value === code)
  return opt?.label ?? code
}

export function labelForLoanPurpose(
  code: string,
  workflow?: WorkflowConfigResponse | null,
): string {
  const opt = resolveLoanPurposeOptions(workflow).find((o) => o.value === code)
  return opt?.label ?? code
}

/** Infer coded loan purpose from legacy free-text purpose field. */
export function inferLoanPurposeCodeFromLegacyText(purpose: string): string {
  const p = purpose.trim().toLowerCase()
  if (!p) return ''
  for (const opt of DEFAULT_LOAN_PURPOSE_OPTIONS) {
    if (opt.label.toLowerCase() === p) return opt.value
  }
  return 'OTHER'
}

/** Infer coded occupation from legacy occupationIndustry text. */
export function inferOccupationCodeFromLegacyText(text: string): string {
  const p = text.trim().toLowerCase()
  if (!p) return ''
  for (const opt of DEFAULT_OCCUPATION_OPTIONS) {
    if (opt.label.toLowerCase() === p) return opt.value
  }
  return 'OTHER'
}

export function computeAgeFromDob(dateOfBirth: string): number | null {
  const dobStr = dateOfBirth.trim()
  if (!dobStr) return null
  const dob = new Date(dobStr)
  if (Number.isNaN(dob.getTime())) return null
  const today = new Date()
  let age = today.getFullYear() - dob.getFullYear()
  const m = today.getMonth() - dob.getMonth()
  if (m < 0 || (m === 0 && today.getDate() < dob.getDate())) age -= 1
  return age
}
