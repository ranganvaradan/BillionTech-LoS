import { describe, expect, it } from 'vitest'
import {
  workflowProviderBusinessLabel,
  workflowStepBusinessLabel,
  workflowStepExplanation,
  workflowStepSelectOptions,
} from './workflowStepLabels'
import {
  formatLenderWorkflowVersionLabel,
  lenderFacingWorkflowVersion,
} from './workflowLenderVersion'

describe('workflowStepLabels', () => {
  it('renders PAN_VERIFY as PAN Verification', () => {
    expect(workflowStepBusinessLabel('PAN_VERIFY')).toBe('PAN Verification')
    expect(workflowStepExplanation('PAN_VERIFY')).toMatch(/PAN/i)
  })

  it('renders AADHAAR_OTP as Aadhaar Verification', () => {
    expect(workflowStepBusinessLabel('AADHAAR_OTP')).toBe('Aadhaar Verification')
    expect(workflowStepExplanation('AADHAAR_OTP')).toMatch(/Aadhaar OTP/i)
  })

  it('keeps select option values as persisted enums', () => {
    const opts = workflowStepSelectOptions()
    const pan = opts.find((o) => o.value === 'PAN_VERIFY')
    const aadhaar = opts.find((o) => o.value === 'AADHAAR_OTP')
    expect(pan?.label).toBe('PAN Verification')
    expect(aadhaar?.label).toBe('Aadhaar Verification')
    expect(opts.every((o) => o.value === o.value.toUpperCase() || o.value.includes('_'))).toBe(true)
  })

  it('formats provider codes without changing persisted IDs', () => {
    expect(workflowProviderBusinessLabel('PERFIOS')).toBe('Perfios')
    expect(workflowProviderBusinessLabel('AUTHBRIDGE')).toBe('Authbridge')
  })
})

describe('workflowLenderVersion', () => {
  it('new lender workflow displays Version 1 semantics', () => {
    expect(lenderFacingWorkflowVersion({ version: 1, active: false })).toBe(1)
    expect(formatLenderWorkflowVersionLabel({ version: 1, active: false })).toBe('Version 1 · Draft')
  })

  it('existing active workflow identity version remains unchanged in display', () => {
    expect(lenderFacingWorkflowVersion({ version: 5, active: true })).toBe(5)
    expect(formatLenderWorkflowVersionLabel({ version: 5, active: true })).toBe('Version 5 · Active')
    expect(
      formatLenderWorkflowVersionLabel({ version: 1, active: false, publicationStatus: 'SUPERSEDED' }),
    ).toBe('Version 1 · Superseded')
  })
})
