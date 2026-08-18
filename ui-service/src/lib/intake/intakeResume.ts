import { isInvoiceDiscountingProduct } from '@/catalog/loanProducts'
import { workflowById, isWorkflowDrivenIntake } from '@/lib/workflow/workflowIntakeRules'
import type { ApplicationResponse } from '@/types/application'
import type { WorkflowConfigResponse } from '@/types/workflow'
import { isBorrowerResumableIntakeStatus } from '@/lib/borrowerApplicationDeletable'
import type { IntakeFormState, IntakeMode } from './intakeTypes'
import type { StaffIntakeStepIndices } from './staffIntakeSteps'
import {
  missingIntakeDocumentTypes,
  validateBorrowerStep,
  validateCollateralIntakeStep,
  validateConsentStep,
  validateKycStep,
  validateProductStep,
} from './intakeValidation'

/** Normalize hydrated application data before resume / validation. */
export function applyHydratedIntakeDefaults(
  form: IntakeFormState,
  app: ApplicationResponse,
  variant: 'borrower' | 'staff',
): IntakeFormState {
  const next = { ...form }
  if (isInvoiceDiscountingProduct(next.loanProduct)) {
    if (variant === 'borrower') {
      next.invoiceOnboardingChoice = 'BORROWER'
    } else if (!next.invoiceOnboardingChoice) {
      next.invoiceOnboardingChoice = app.intakeSegment === 'ANCHOR' ? 'ANCHOR' : 'BORROWER'
    }
  }
  return next
}

/**
 * Walk intake steps in order and return the first step that still needs data.
 * Avoids jumping ahead when stored intakeCompletedStep uses staff step numbering
 * that does not match the borrower wizard (e.g. PLP anchor step only on staff).
 */
export function inferFirstIncompleteIntakeStep(
  form: IntakeFormState,
  steps: StaffIntakeStepIndices,
  mode: IntakeMode,
  activeWorkflows: WorkflowConfigResponse[],
  needPlp: boolean,
  needColl: boolean,
): number {
  const workflow = workflowById(activeWorkflows, form.workflowId)

  if (validateProductStep(form, mode, activeWorkflows)) {
    return steps.product
  }

  if (needPlp && steps.plp >= 0 && !form.selectedSubProgramId?.trim()) {
    return steps.plp
  }

  if (validateBorrowerStep(form, mode, workflow)) {
    return steps.borrower
  }

  if (!form.workflowId?.trim()) {
    return steps.category
  }

  if (needColl && validateCollateralIntakeStep(form)) {
    return steps.collateral
  }

  if (workflow && isWorkflowDrivenIntake(workflow)) {
    const missingDocs = missingIntakeDocumentTypes(form, workflow)
    if (missingDocs.length > 0) {
      return steps.documents
    }
  }

  if (validateConsentStep(form)) {
    return steps.consent
  }

  if (validateKycStep(form, workflow)) {
    return steps.kyc
  }

  return steps.review
}

/** @deprecated Prefer inferFirstIncompleteIntakeStep after hydrating the form. */
export function resolveIntakeResumeStep(
  app: ApplicationResponse,
  steps: StaffIntakeStepIndices,
  variant: 'borrower' | 'staff',
): number {
  const last = steps.last
  if (app.intakeCompletedStep != null && app.intakeCompletedStep >= 0) {
    return Math.min(app.intakeCompletedStep, last)
  }
  if (variant === 'staff' && app.loanProduct) {
    return Math.min(steps.borrower, last)
  }
  return steps.product
}

/**
 * Staff may open Continue intake for:
 * - staff-owned DRAFT (in-progress staff intake),
 * - borrower apps still with RM ({@code BORROWER_SUBMITTED} / {@code SENT_BACK_TO_RM}),
 * - anchor apps still with RM ({@code DRAFT}, pre-CO {@code KYC_*} / {@code SENT_BACK_TO_RM}).
 *
 * Credit officers work cases from the application detail view, not intake resume.
 */
export function staffCanContinueIntake(app: ApplicationResponse, role?: string | null): boolean {
  const r = (role ?? '').trim().toUpperCase()
  if (r === 'CREDIT_OFFICER') {
    return false
  }
  if (app.intakeSegment === 'ANCHOR') {
    return (
      app.status === 'DRAFT' ||
      app.status === 'KYC_IN_PROGRESS' ||
      app.status === 'KYC_FAILED' ||
      app.status === 'BORROWER_SUBMITTED' ||
      app.status === 'SENT_BACK_TO_RM'
    )
  }
  return (
    (app.status === 'DRAFT' && app.intakeOwner !== 'BORROWER') ||
    app.status === 'BORROWER_SUBMITTED' ||
    app.status === 'SENT_BACK_TO_RM'
  )
}

export function borrowerCanContinueIntake(app: ApplicationResponse): boolean {
  return isBorrowerResumableIntakeStatus(app.status)
}
