import { describe, expect, it } from 'vitest'
import { ApplicationIntakeWizard } from './ApplicationIntakeWizard'

/** Ensures the shared intake entry stays importable in CI the same way as route smoke tests. */
describe('ApplicationIntakeWizard', () => {
  it('is a function component', () => {
    expect(typeof ApplicationIntakeWizard).toBe('function')
  })
})
