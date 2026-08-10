import { describe, expect, it } from 'vitest'
import {
  businessOutcomeLabel,
  decisionPolicyDomainLabel,
  kycRequirementTypeLabel,
} from './businessLexicon'

describe('KYC-2 business lexicon labels', () => {
  it('labels decision policy domains', () => {
    expect(decisionPolicyDomainLabel('KYC')).toBe('KYC')
    expect(decisionPolicyDomainLabel('CREDIT')).toBe('Credit Underwriting')
    expect(decisionPolicyDomainLabel('KYC_ELIGIBILITY')).toBe('KYC & Eligibility')
    expect(decisionPolicyDomainLabel('IDENTITY_KYC')).toBe('KYC & Eligibility')
  })

  it('labels KYC requirement types', () => {
    expect(kycRequirementTypeLabel('VERIFICATION')).toBe('Verification')
    expect(kycRequirementTypeLabel('BOUNDARY_CONDITION')).toBe('Boundary Condition')
    expect(kycRequirementTypeLabel('REGULATORY_GUARDRAIL')).toBe('Platform Guardrail')
  })

  it('maps MISSING_INFORMATION to Missing Information', () => {
    expect(businessOutcomeLabel('MISSING_INFORMATION')).toBe('Missing Information')
    expect(businessOutcomeLabel('REFER')).toBe('Manual Credit Review')
  })
})
