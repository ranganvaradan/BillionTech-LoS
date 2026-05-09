import type { IntakeMode } from './intakeTypes'

export function pageTitle(mode: IntakeMode): string {
  if (mode === 'BORROWER_SELF_SERVICE') return 'Apply for a loan'
  if (mode === 'SALES_ASSISTED') return 'New application (sales assisted)'
  return 'New application'
}

export function pageDescription(mode: IntakeMode): string {
  if (mode === 'BORROWER_SELF_SERVICE') {
    return 'A guided form: product, your details, identity checks, consent, documents, and review—before we send the case for verification.'
  }
  if (mode === 'SALES_ASSISTED') {
    return 'Complete this on behalf of the borrower. Sales and consent metadata are stored for audit.'
  }
  return 'Create an application using the same intake flow as the borrower and sales channels. Internal metadata is added where applicable.'
}

export function consentHelper(mode: IntakeMode): string {
  if (mode === 'ADMIN_INTERNAL') {
    return 'Confirm you have a valid basis to obtain these consents (or that the customer has been informed) before you continue.'
  }
  if (mode === 'SALES_ASSISTED') {
    return 'The borrower should understand these consents. Tick each box to confirm agreement was captured in your presence or per process.'
  }
  return 'We need your agreement to run checks and to contact you about this loan application.'
}
