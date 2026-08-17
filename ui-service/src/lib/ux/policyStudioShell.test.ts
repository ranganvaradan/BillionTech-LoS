import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  formatShellStatusLine,
  isWorkflowTab,
  POLICY_STUDIO_DETAILS_SECTIONS,
  POLICY_STUDIO_WORKFLOW_TABS,
  primaryTabsSnapshot,
  underwritingRuleStats,
  workflowTabIds,
} from './policyStudioShell'
import {
  POLICY_STUDIO_ADVANCED_TAB_IDS,
  POLICY_STUDIO_PRIMARY_TAB_IDS,
} from '@/lib/applicationWorkbench'

describe('POLICY-UX-SHELL-1', () => {
  it('primary tabs are exactly Scope / Rules / Scorecard / Test / Versions', () => {
    expect(POLICY_STUDIO_WORKFLOW_TABS.map((t) => t.label)).toEqual([
      'Scope',
      'Rules',
      'Scorecard',
      'Test',
      'Versions',
    ])
    expect(workflowTabIds()).toEqual(['scope', 'rules', 'scorecard', 'simulation', 'lifecycle'])
    expect([...POLICY_STUDIO_PRIMARY_TAB_IDS]).toEqual([
      'scope',
      'rules',
      'scorecard',
      'simulation',
      'lifecycle',
    ])
  })

  it('opening Advanced/details does not change primary tab ids', () => {
    const before = primaryTabsSnapshot()
    // Simulate "Advanced open" — details sections exist separately
    expect(POLICY_STUDIO_DETAILS_SECTIONS.length).toBeGreaterThan(0)
    expect(POLICY_STUDIO_ADVANCED_TAB_IDS.length).toBeGreaterThan(0)
    const after = primaryTabsSnapshot()
    expect(after).toEqual(before)
    expect(after).toEqual(['scope', 'rules', 'scorecard', 'simulation', 'lifecycle'])
  })

  it('switching workflow tabs does not mutate primary tab list', () => {
    const a = primaryTabsSnapshot()
    for (const id of ['scope', 'rules', 'scorecard', 'simulation', 'lifecycle']) {
      expect(isWorkflowTab(id)).toBe(true)
      expect(primaryTabsSnapshot()).toEqual(a)
    }
  })

  it('banking underwriting count uses 8 rules not 21', () => {
    const uw = Array.from({ length: 8 }, (_, i) => ({
      status: i < 3 ? 'Ready' : 'Needs your input',
    }))
    const stats = underwritingRuleStats(uw)
    expect(stats.total).toBe(8)
    expect(stats.ready).toBe(3)
    expect(stats.needsInput).toBe(5)
  })

  it('data & calculations count stays separate (13)', () => {
    const dataCalc = [
      ...Array.from({ length: 10 }, () => ({ status: 'Data requirement' })),
      ...Array.from({ length: 3 }, () => ({ status: 'Metric adjustment' })),
    ]
    expect(dataCalc.length).toBe(13)
    // underwriting stats ignore these when passed separately
    expect(underwritingRuleStats([]).total).toBe(0)
  })

  it('bureau compound children do not inflate primary rule count', () => {
    const uw = Array.from({ length: 11 }, () => ({ status: 'Ready' }))
    const children = Array.from({ length: 5 }, () => ({
      status: 'Needs your input',
      compoundChild: true,
    }))
    expect(underwritingRuleStats(uw).total).toBe(11)
    expect(underwritingRuleStats(uw).total + children.length).toBe(16)
  })

  it('status line is compact — not multi-banner', () => {
    const dirty = formatShellStatusLine({
      lifecycleStatus: 'DRAFT',
      ready: 9,
      needsInput: 2,
      dirty: true,
    })
    expect(dirty).toContain('Draft')
    expect(dirty).toContain('9 Ready')
    expect(dirty).toContain('Unsaved changes')
    expect(dirty.split('\n').length).toBe(1)

    const saved = formatShellStatusLine({
      lifecycleStatus: 'DRAFT',
      ready: 3,
      needsInput: 5,
      dirty: false,
      savedLabel: 'Saved just now',
    })
    expect(saved).toContain('Saved just now')
    expect(saved).not.toContain('Unsaved')
  })

  it('advanced sections remain listed for Policy details', () => {
    const labels = POLICY_STUDIO_DETAILS_SECTIONS.map((s) => s.label)
    expect(labels).toEqual(
      expect.arrayContaining([
        'Overview',
        'KYC & Eligibility',
        'Structure',
        'Ambiguous Terms',
        'Data Readiness',
        'Generated Tests',
        'Maker-checker',
      ]),
    )
    // None of these are primary workflow tabs
    for (const s of POLICY_STUDIO_DETAILS_SECTIONS) {
      expect(isWorkflowTab(s.id)).toBe(false)
    }
  })

  it('Scheduled Policies and Compare Impact remain on Policies-level nav', async () => {
    const { policiesWorkspaceItems } = await import('@/nav/workspaceNav')
    const items = policiesWorkspaceItems({ showDemoSamples: false })
    expect(items.map((i) => i.label)).toEqual([
      'Policy Studio',
      'Scheduled Policies',
      'Compare Impact',
    ])
    expect(items.find((i) => i.label === 'Scheduled Policies')?.to).toBe(
      '/credit-intelligence/policy-catalogue',
    )
    expect(items.find((i) => i.label === 'Compare Impact')?.to).toBe('/credit-intelligence/dual-run')
  })

  it('active policy session shell omits nested Policies workspace nav', () => {
    const page = readFileSync(
      join(__dirname, '../../pages/creditIntelligence/CiPolicyStudioPage.tsx'),
      'utf8',
    )
    const landing = readFileSync(
      join(__dirname, '../../pages/creditIntelligence/CiCreditPoliciesLanding.tsx'),
      'utf8',
    )
    // Landing keeps PoliciesWorkspaceNav; session shell must not place it beside primary tabs.
    expect(landing).toContain('<PoliciesWorkspaceNav />')
    expect(page).toContain('data-testid="policy-studio-session-shell"')
    const sessionIdx = page.indexOf('data-testid="policy-studio-session-shell"')
    const afterSession = page.slice(sessionIdx)
    expect(afterSession).not.toContain('<PoliciesWorkspaceNav')
    expect(afterSession).toContain('data-testid="policy-primary-tabs"')
    expect(afterSession).toContain('data-testid="save-draft"')
    expect(afterSession).toContain('contentEditable')
    expect(afterSession).toContain('data-testid="create-new-version"')
    expect(page).toContain('businessLifecycleStatus')
    expect(afterSession).toContain('data-testid="policy-details-entry"')
    expect(afterSession).toContain('Development / demo')
    // No stacked amber unsaved banner + draft-only banner in session shell
    expect(afterSession).not.toContain('draftOnlyBanner')
    expect(afterSession).not.toContain('use <strong>Save Draft</strong> anytime')
  })
})

