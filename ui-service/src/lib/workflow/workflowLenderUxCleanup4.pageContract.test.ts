/**
 * Focused presentation assertions for WORKFLOW-LENDER-UX-CLEANUP-4.
 * Does not mount the full page (API/runtime heavy); guards label/copy contracts.
 */
import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const pagePath = join(__dirname, '../../pages/WorkflowsPage.tsx')
const pageSrc = readFileSync(pagePath, 'utf8')

describe('WorkflowsPage CLEANUP-4 contracts', () => {
  it('does not show LMS fields in Overview block', () => {
    // LMS controls must live under Advanced test id region, not Overview section copy
    expect(pageSrc).toContain('data-testid="workflow-advanced-lms"')
    expect(pageSrc).toMatch(/detailTab === 'advanced'[\s\S]*workflow-advanced-lms/)
    // Overview should not contain the LMS product code label outside Advanced
    const overviewChunk = pageSrc.split("detailTab === 'overview'")[1]?.split("detailTab === 'application'")[0] ?? ''
    expect(overviewChunk).not.toMatch(/LMS product code/)
    expect(overviewChunk).not.toMatch(/LMS tenure type/)
  })

  it('renames Advanced / Internal to Advanced for normal lender tabs', () => {
    expect(pageSrc).toContain("['advanced', 'Advanced']")
    expect(pageSrc).not.toContain('Advanced / Internal')
  })

  it('Data collection stays explanatory and links to Data & Parameters', () => {
    const dataChunk =
      pageSrc.split("detailTab === 'dataCollection'")[1]?.split("detailTab === 'journey'")[0] ?? ''
    expect(dataChunk).toMatch(/Data collection is driven automatically by your Credit Policy/)
    expect(dataChunk).toContain('to="/data-parameters"')
    expect(dataChunk).toContain('Manage data sources')
    expect(dataChunk).not.toMatch(/subscription configuration|Require Bureau|W4|W6/)
  })

  it('uses lender-facing version helper', () => {
    expect(pageSrc).toContain('lenderFacingWorkflowVersion')
    expect(pageSrc).toContain('formatLenderWorkflowVersionLabel')
  })
})
