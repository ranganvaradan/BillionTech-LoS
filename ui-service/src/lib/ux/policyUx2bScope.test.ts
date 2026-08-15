import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { POLICY_STUDIO_PRIMARY_TAB_IDS, PROSPECT_DEMO_VISIBLE_TAB_IDS } from '@/lib/applicationWorkbench'

const uiSrc = join(__dirname, '../..')

describe('POLICY-UX-2B Scope experience', () => {
  it('primary tabs are Scope → Rules → Scorecard → Test → Versions', () => {
    expect([...POLICY_STUDIO_PRIMARY_TAB_IDS]).toEqual([
      'scope',
      'rules',
      'scorecard',
      'simulation',
      'lifecycle',
    ])
    expect([...PROSPECT_DEMO_VISIBLE_TAB_IDS]).toEqual([
      'scope',
      'rules',
      'scorecard',
      'simulation',
      'lifecycle',
    ])
  })

  it('studio page wires Scope tab and CiPolicyScopeTab', () => {
    const page = readFileSync(join(uiSrc, 'pages/creditIntelligence/CiPolicyStudioPage.tsx'), 'utf8')
    expect(page).toContain('CiPolicyScopeTab')
    expect(page).toContain("setTab('scope')")
    expect(page).toContain('selectWorkflowTab')
    expect(page).toContain('scopeDirty')
    expect(page).toContain('rulesDirty')
    expect(page).toContain("contentTab === 'scope'")
  })

  it('scope tab is business-facing (All, include-only, no EXCLUDE)', () => {
    const scope = readFileSync(join(uiSrc, 'pages/creditIntelligence/CiPolicyScopeTab.tsx'), 'utf8')
    expect(scope).toContain('All products')
    expect(scope).toContain('Include-only')
    expect(scope).toContain('Application relationship')
    expect(scope).not.toContain('EXCLUDE')
    expect(scope).toContain('saveLifecycleDraft')
    expect(scope).toContain('Potential policy overlap')
    expect(scope).toContain('allowCanonicalAuthority=false')
  })
})
