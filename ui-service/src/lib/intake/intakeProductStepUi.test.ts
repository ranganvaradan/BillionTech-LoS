import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const ROOT = resolve(__dirname, '../..')

describe('ApplicationIntakeWizard product step (NEW intake)', () => {
  it('does not render LMS product code or workflow-derived LMS labels on product step', () => {
    const wizard = readFileSync(resolve(ROOT, 'components/intake/ApplicationIntakeWizard.tsx'), 'utf8')
    const productStepBlock = wizard.split('{step === steps.product ? (')[1]?.split(') : null}\n\n      {needPlpAnchorStep')[0] ?? ''
    expect(productStepBlock).not.toMatch(/LmsWorkflowConfigReadonly/)
    expect(productStepBlock).not.toMatch(/LMS product code/)
    expect(productStepBlock).not.toMatch(/LMS product mapping/)
    expect(productStepBlock).not.toMatch(/Provided by workflow configuration/)
    expect(productStepBlock).toMatch(/Loan product/)
    expect(productStepBlock).toMatch(/IntakeTenureField/)
  })
})
