import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { POLICY_STUDIO_PRIMARY_TAB_IDS } from '@/lib/applicationWorkbench'

const uiSrc = join(__dirname, '../..')

describe('POLICY-PARAMETER-RESOLVER-1', () => {
  it('policy shell remains Scope | Rules | Scorecard | Test | Versions', () => {
    expect([...POLICY_STUDIO_PRIMARY_TAB_IDS]).toEqual([
      'scope',
      'rules',
      'scorecard',
      'simulation',
      'lifecycle',
    ])
  })

  it('Rules tab wires operand Resolve parameter + resolver panel', () => {
    const rules = readFileSync(join(uiSrc, 'pages/creditIntelligence/CiPolicyRulesTab.tsx'), 'utf8')
    expect(rules).toContain('CiParameterResolverPanel')
    expect(rules).toContain('Resolve parameter')
    expect(rules).toContain('rule-operands')
    expect(rules).toContain('Data & calculations')
    expect(rules).not.toMatch(/unresolved:\s*true,\s*suggestedSource/)
  })

  it('resolver panel exposes source browse, search, describe, manual', () => {
    const panel = readFileSync(
      join(uiSrc, 'pages/creditIntelligence/CiParameterResolverPanel.tsx'),
      'utf8',
    )
    expect(panel).toContain('Choose source')
    expect(panel).toContain('Search all')
    expect(panel).toContain('Describe meaning')
    expect(panel).toContain('Manual input')
    expect(panel).toContain('Propose definition')
    expect(panel).toContain('Use this definition')
    expect(panel).toContain('not accepted until you confirm')
    expect(panel).toContain('fact source')
    expect(panel).toContain('getParameterCatalogue')
    expect(panel).toContain('searchCanonicalParameters')
    expect(panel).toContain('proposeParameterDefinition')
    expect(panel).toContain('RESOLVE_PARAMETER_MAP')
    expect(panel).toContain('RESOLVE_PARAMETER_MANUAL')
    expect(panel).toContain('RESOLVE_PARAMETER_USE_PROPOSAL')
    expect(panel).toContain('CURRENT_MAPPING_RESOLVED')
    expect(panel).toContain('persistedMappingFromOperand')
    expect(panel).not.toMatch(/unresolved === true \|\| !operand\.parameterId/)
  })

  it('API client exposes parameter resolver endpoints', () => {
    const api = readFileSync(join(uiSrc, 'api/creditIntelligence.ts'), 'utf8')
    expect(api).toContain('policy-studio/parameters')
    expect(api).toContain('propose-definition')
    expect(api).toContain('RESOLVE_PARAMETER_MAP')
  })
})
