import { describe, expect, it } from 'vitest'
import { projectLenderJourney, workflowSetupStatus } from './workflowLenderJourney'
import { defaultWorkflowDrivenIntakeConfig } from '@/lib/workflow/workflowIntakeRules'
import type { VisualWorkflowStep } from '@/lib/workflowVisual'

function step(partial: Partial<VisualWorkflowStep> & { step: string }): VisualWorkflowStep {
  return {
    id: partial.id ?? partial.step,
    step: partial.step,
    name: partial.name ?? partial.step,
    provider: partial.provider ?? '',
    mandatory: partial.mandatory ?? true,
    order: partial.order ?? 1,
    notifications: [],
    allowPhysicalKycFallback: false,
    collectAtIntake: false,
    fieldRequiredAtIntake: false,
    documentRequired: false,
    documentsRequired: [],
  }
}

describe('workflowLenderJourney', () => {
  it('projects supported stages without inventing engines', () => {
    const stages = projectLenderJourney({
      intakeConfig: defaultWorkflowDrivenIntakeConfig(),
      steps: [step({ step: 'PAN_VERIFY' }), step({ step: 'ESIGN_KFS', order: 2 })],
      processNotifications: [],
    })
    expect(stages.map((s) => s.id)).toContain('APPLICATION')
    expect(stages.map((s) => s.id)).toContain('IDENTITY_KYC')
    expect(stages.map((s) => s.id)).toContain('CREDIT_ASSESSMENT')
    expect(stages.find((s) => s.id === 'OFFER_KFS')?.support).toBe('AVAILABLE_VIA_STEPS')
    expect(stages.every((s) => !JSON.stringify(s).includes('W4'))).toBe(true)
  })

  it('setup status flags missing KYC', () => {
    const status = workflowSetupStatus({
      name: 'Personal Loan – Individual Borrower',
      intakeConfig: defaultWorkflowDrivenIntakeConfig(),
      steps: [],
      processNotifications: [],
    })
    expect(status.ready).toBe(false)
    expect(status.attention.some((a) => /KYC|identity/i.test(a))).toBe(true)
  })
})
