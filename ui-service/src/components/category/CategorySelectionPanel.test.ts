import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const ROOT = resolve(__dirname, '../..')

describe('CategorySelectionPanel contract', () => {
  it('safe question catalogue id is FINANCIAL_DATA_ROUTE', () => {
    expect('FINANCIAL_DATA_ROUTE').toBe('FINANCIAL_DATA_ROUTE')
  })

  it('selection states exclude arbitrary winner', () => {
    const states = [
      'NO_ELIGIBLE_CATEGORY',
      'AUTO_SINGLE_MATCH',
      'DISAMBIGUATION_REQUIRED',
      'EXPLICIT_PROPOSITION_SELECTION_REQUIRED',
      'CATEGORY_SELECTED',
    ]
    expect(states).not.toContain('PRIORITY_WINNER')
  })

  it('shows pinned workflow summary after category selection', () => {
    const panel = readFileSync(resolve(ROOT, 'components/category/CategorySelectionPanel.tsx'), 'utf8')
    const summary = readFileSync(resolve(ROOT, 'components/workflow/PinnedWorkflowSummary.tsx'), 'utf8')
    expect(panel).toMatch(/PinnedWorkflowSummary/)
    expect(panel).toMatch(/pinnedWorkflowDisplayFromCategoryHandoff/)
    expect(summary).toMatch(/Selected through Customer Category/)
  })

  it('accepts persisted pin and preserves it on revisit', () => {
    const panel = readFileSync(resolve(ROOT, 'components/category/CategorySelectionPanel.tsx'), 'utf8')
    expect(panel).toMatch(/pinnedSelection/)
    expect(panel).toMatch(/shouldPreservePinOnEvaluateError/)
    expect(panel).toMatch(/mergeCategoryEvaluateResult/)
    expect(panel).not.toMatch(/setResult\(null\)/)
  })

  it('wizard passes category pin into CategorySelectionPanel', () => {
    const wizard = readFileSync(resolve(ROOT, 'components/intake/ApplicationIntakeWizard.tsx'), 'utf8')
    expect(wizard).toMatch(/categoryPinnedSelection/)
    expect(wizard).toMatch(/pinnedSelection=\{categoryPinnedSelection\}/)
    expect(wizard).toMatch(/categoryPinnedSelectionFromApplication/)
  })
})
