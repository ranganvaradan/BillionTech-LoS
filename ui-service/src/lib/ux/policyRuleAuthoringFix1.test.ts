import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

const uiSrc = join(__dirname, '../..')

function read(rel: string): string {
  return readFileSync(join(uiSrc, rel), 'utf8')
}

describe('POLICY-RULE-AUTHORING-FIX-1', () => {
  it('Rules tab uses shared authoring panel for Add and Edit', () => {
    const rules = read('pages/creditIntelligence/CiPolicyRulesTab.tsx')
    const panel = read('pages/creditIntelligence/CiRuleAuthoringPanel.tsx')
    expect(rules).toContain('CiRuleAuthoringPanel')
    expect(rules).toContain('Other policy content')
    expect(rules).toContain('data-testid="add-rule"')
    expect(panel).toContain('Build rule')
    expect(panel).toContain('Describe rule')
    expect(panel).toContain('Confirm')
    expect(panel).toContain('previewPolicyRule')
    expect(panel).toContain('replaceRuleId')
  })

  it('API exposes preview and authoring sources', () => {
    const api = read('api/creditIntelligence.ts')
    expect(api).toContain('getRuleAuthoringSources')
    expect(api).toContain('previewPolicyRule')
    expect(api).toContain('rule-authoring/sources')
  })

  it('Studio wires otherPolicyContent and session callback', () => {
    const page = read('pages/creditIntelligence/CiPolicyStudioPage.tsx')
    expect(page).toContain('otherPolicyContent')
    expect(page).toContain('onSession={applyAuthoringSession}')
    expect(page).toContain('setBusy={setBusy}')
    expect(page).toContain('resolveOtherPolicyContent')
    expect(page).toContain('resolveDataAndCalculations')
  })

  it('Rules tab does not offer Replace for already-classified data/calc items', () => {
    const rules = read('pages/creditIntelligence/CiPolicyRulesTab.tsx')
    expect(rules).toContain('dataRequirementOnly')
    expect(rules).toContain('metricAdjustment')
    expect(rules).toContain('Already-classified data/calc items must never offer Replace')
  })

  it('typed authoring uses canonical boolean Yes/No not Number(value)', () => {
    const panel = read('pages/creditIntelligence/CiRuleAuthoringPanel.tsx')
    const typed = read('lib/ux/ruleAuthoringTypedValue.ts')
    expect(panel).not.toMatch(/Number\(value\)/)
    expect(panel).toContain('toCanonicalBuildValue')
    expect(panel).toContain('Yes')
    expect(panel).toContain('If rule fails')
    expect(typed).toContain("return true")
    expect(typed).toContain('BOOLEAN')
  })
})
