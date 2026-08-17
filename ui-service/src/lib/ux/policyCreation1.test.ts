import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { POLICY_STUDIO_PRIMARY_TAB_IDS } from '@/lib/applicationWorkbench'

const uiSrc = join(__dirname, '../..')

describe('POLICY-CREATION-1', () => {
  it('landing is Credit Policies with create paths', () => {
    const landing = readFileSync(
      join(uiSrc, 'pages/creditIntelligence/CiCreditPoliciesLanding.tsx'),
      'utf8',
    )
    expect(landing).toContain('Credit Policies')
    expect(landing).toContain('+ Create Policy')
    expect(landing).toContain('Upload Existing Policy')
    expect(landing).toContain('Start from scratch')
    expect(landing).toContain('Copy existing policy')
    expect(landing).toContain('existing-policies-table')
    expect(landing).toContain('Examples &amp; templates')
    expect(landing).not.toContain('What should I do next?')
    expect(landing).not.toContain('BillionTech will')
    expect(landing).toContain('policies.length === 0')
    expect(landing).toContain('data-testid="policies-loading"')
    expect(landing).not.toContain('Approval:')
  })

  it('studio page wires create/copy/open into same session shell', () => {
    const page = readFileSync(join(uiSrc, 'pages/creditIntelligence/CiPolicyStudioPage.tsx'), 'utf8')
    expect(page).toContain('CiCreditPoliciesLanding')
    expect(page).toContain('createPolicyFromScratch')
    expect(page).toContain('copyPolicyStudioDocument')
    expect(page).toContain('enterSession')
    expect(page).toContain('data-testid="policy-primary-tabs"')
  })

  it('shell remains Scope | Rules | Scorecard | Test | Versions', () => {
    expect([...POLICY_STUDIO_PRIMARY_TAB_IDS]).toEqual([
      'scope',
      'rules',
      'scorecard',
      'simulation',
      'lifecycle',
    ])
  })

  it('approved policy exposes scorecard handoff inside Policy Studio', () => {
    const life = readFileSync(
      join(uiSrc, 'pages/creditIntelligence/CiPolicyLifecycleTab.tsx'),
      'utf8',
    )
    expect(life).toContain('Policy approved')
    expect(life).toContain('Configure Scorecard in Policy')
    expect(life).toContain('create-scorecard-handoff')
    expect(life).toContain('onOpenScorecardTab')
    const page = readFileSync(join(uiSrc, 'pages/creditIntelligence/CiPolicyStudioPage.tsx'), 'utf8')
    expect(page).toContain('CiPolicyScorecardTab')
  })

  it('parameter resolver panel remains available', () => {
    const rules = readFileSync(join(uiSrc, 'pages/creditIntelligence/CiPolicyRulesTab.tsx'), 'utf8')
    expect(rules).toContain('CiParameterResolverPanel')
    expect(rules).toContain('Resolve parameter')
  })

  it('API exposes create and copy endpoints', () => {
    const api = readFileSync(join(uiSrc, 'api/creditIntelligence.ts'), 'utf8')
    expect(api).toContain('policy-studio/create')
    expect(api).toContain('/copy')
    expect(api).toContain('createPolicyFromScratch')
    expect(api).toContain('copyPolicyStudioDocument')
  })

  it('live underwriting and scorecard routes remain distinct', () => {
    const scorecards = readFileSync(join(uiSrc, 'pages/ScorecardsPage.tsx'), 'utf8')
    expect(scorecards.length).toBeGreaterThan(100)
    const api = readFileSync(join(uiSrc, 'api/scorecards.ts'), 'utf8')
    expect(api).toMatch(/scorecard/i)
    const life = readFileSync(
      join(uiSrc, 'pages/creditIntelligence/CiPolicyLifecycleTab.tsx'),
      'utf8',
    )
    expect(life).not.toContain('autoActivateScorecard')
    expect(life).toContain('Configure Scorecard in Policy')
  })
})
