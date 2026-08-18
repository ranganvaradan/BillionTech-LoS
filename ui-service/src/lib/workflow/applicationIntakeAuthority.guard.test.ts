import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const ROOT = resolve(__dirname, '../..')

function read(rel: string): string {
  return readFileSync(resolve(ROOT, rel), 'utf8')
}

describe('application intake authority guards', () => {
  it('ordinary intake UI does not discover latest/product-default workflow', () => {
    const wizard = read('components/intake/ApplicationIntakeWizard.tsx')
    const kyc = read('components/KycDetailsSection.tsx')
    const detail = read('pages/ApplicationDetailPage.tsx')
    const resume = read('lib/intake/intakeResume.ts')
    const rules = read('lib/workflow/workflowIntakeRules.ts')
    const products = read('utils/workflowProducts.ts')

    expect(wizard).not.toMatch(/getActiveWorkflow/)
    expect(wizard).not.toMatch(/resolveWorkflowIdForProduct/)
    expect(kyc).not.toMatch(/getActiveWorkflow/)
    expect(detail).not.toMatch(/getActiveWorkflow/)
    expect(resume).not.toMatch(/activeWorkflowForProduct/)
    expect(rules).toMatch(/Never invents latest/)
    expect(products).toMatch(/Never invents latest/)
    expect(rules).not.toMatch(/sort\(\(a, b\) => b\.version - a\.version\)/)
  })
})
