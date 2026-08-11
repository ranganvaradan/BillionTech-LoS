import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { POLICY_STUDIO_PRIMARY_TAB_IDS } from '@/lib/applicationWorkbench'

const uiSrc = join(__dirname, '../..')

describe('POLICY-UX-2E Test experience', () => {
  it('Test tab exposes Quick and Existing Application modes', () => {
    const tab = readFileSync(
      join(uiSrc, 'pages/creditIntelligence/CiPolicySimulationTab.tsx'),
      'utf8',
    )
    expect(tab).toContain('Test Policy')
    expect(tab).toContain('Quick Test')
    expect(tab).toContain('Existing Application')
    expect(tab).toContain('Historical / Batch (future)')
    expect(tab).toContain('Run Test')
    expect(tab).toContain('Simulated Decision')
    expect(tab).toContain('Test value only')
    expect(tab).toContain('Resolve parameter')
    expect(tab).toContain('data-testid="policy-test-experience"')
    expect(tab).not.toContain('VALIDATION_FIXTURES')
  })

  it('API wires test endpoints', () => {
    const api = readFileSync(join(uiSrc, 'api/creditIntelligence.ts'), 'utf8')
    expect(api).toContain('/test/quick')
    expect(api).toContain('/test/application')
    expect(api).toContain('getPolicyTestContext')
    expect(api).toContain('runPolicyQuickTest')
  })

  it('shell remains Scope | Rules | Test | Versions and Save Draft ungated', () => {
    expect([...POLICY_STUDIO_PRIMARY_TAB_IDS]).toEqual([
      'scope',
      'rules',
      'simulation',
      'lifecycle',
    ])
    const page = readFileSync(
      join(uiSrc, 'pages/creditIntelligence/CiPolicyStudioPage.tsx'),
      'utf8',
    )
    expect(page).toContain('data-testid="save-draft"')
    expect(page).toContain('onResolveParameter')
  })

  it('parameter resolver remains on Rules', () => {
    const rules = readFileSync(join(uiSrc, 'pages/creditIntelligence/CiPolicyRulesTab.tsx'), 'utf8')
    expect(rules).toContain('CiParameterResolverPanel')
  })

  it('live scorecard route unchanged', () => {
    const life = readFileSync(
      join(uiSrc, 'pages/creditIntelligence/CiPolicyLifecycleTab.tsx'),
      'utf8',
    )
    expect(life).toContain('/underwriting-scorecards')
    const scoreApi = readFileSync(join(uiSrc, 'api/scorecards.ts'), 'utf8')
    expect(scoreApi.length).toBeGreaterThan(50)
  })
})
