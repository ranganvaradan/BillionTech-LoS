import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

const uiSrc = join(__dirname, '../..')

describe('POLICY-UX-2A capability catalogue foundation', () => {
  it('exposes catalogue API client', () => {
    const api = readFileSync(join(uiSrc, 'api/creditIntelligence.ts'), 'utf8')
    expect(api).toContain('getCreditCapabilityCatalogue')
    expect(api).toContain('policy-studio/capabilities')
  })

  it('Rules tab offers Browse / Add Rule catalogue panel', () => {
    const rules = readFileSync(join(uiSrc, 'pages/creditIntelligence/CiPolicyRulesTab.tsx'), 'utf8')
    expect(rules).toContain('Browse / Add Rule')
    expect(rules).toContain('CiCapabilityCataloguePanel')
    const panel = readFileSync(
      join(uiSrc, 'pages/creditIntelligence/CiCapabilityCataloguePanel.tsx'),
      'utf8',
    )
    expect(panel).toContain('businessCapabilityId')
    expect(panel).toContain('parameterDefinitions')
    expect(panel).toContain('Production authority remains disabled')
  })
})
