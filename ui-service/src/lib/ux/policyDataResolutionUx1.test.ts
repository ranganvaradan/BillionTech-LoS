import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  filterParameterGroups,
  groupDataCalculations,
} from '@/pages/creditIntelligence/policyDataCalculationGroups'

const uiSrc = join(__dirname, '../..')

function read(rel: string): string {
  return readFileSync(join(uiSrc, rel), 'utf8')
}

describe('POLICY-DATA-RESOLUTION-UX-1', () => {
  it('Define/Configure are real buttons not plain text only', () => {
    const rules = read('pages/creditIntelligence/CiPolicyRulesTab.tsx')
    expect(rules).toContain('CiDataCalcResolutionPanel')
    expect(rules).toContain('data-calc-resolve-')
    expect(rules).toContain('Define')
    expect(rules).toContain('Configure calculation')
    // must not only render action as plain paragraph without button for missingDefinition
    expect(rules).toContain('bt-btn-primary')
  })

  it('resolution panel has typed money threshold and EMI bounce configure UX', () => {
    const panel = read('pages/creditIntelligence/CiDataCalcResolutionPanel.tsx')
    expect(panel).toContain('RESOLVE_DATA_THRESHOLD')
    expect(panel).toContain('large-credit-threshold')
    expect(panel).toContain('RESOLVE_DATA_CLASSIFICATION')
    expect(panel).toContain('RESOLVE_DATA_CALCULATION')
    expect(panel).toContain('RESOLVE_DATA_ADJUSTMENT')
    expect(panel).toContain('proposal only')
    expect(panel).toContain('emi-bounce-config')
    expect(panel).toContain('emi-bounce-preview')
    expect(panel).toContain('Save configuration')
    expect(panel).toContain('previewDataCalculation')
  })

  it('large credit ready; EMI becomes Ready only with executable binding', () => {
    const cards = [
      {
        id: 'r1',
        sourceClause: 'Party wise Large credits',
        canonicalParameterId: 'banking.large_credit_transactions',
        itemKind: 'REPORT_ANALYST_INFORMATION',
        missingDefinition: { question: 'What qualifies as Large?', action: 'DEFINE' },
      },
      {
        id: 'r2',
        sourceClause: 'EMI bounces in last 3 months',
        canonicalParameterId: 'banking.emi_bounce_count_3m',
        missingDefinition: { action: 'CONFIGURE' },
      },
    ]
    const resolutions = {
      'banking.large_credit_transactions': {
        cmStatus: 'READY',
        howDefined: 'Transaction amount >= ₹5,00,000 — Defined in this policy version',
        displayStatus: 'READY FOR ANALYST INFORMATION',
        executionImpact: 'NON_BLOCKING',
        businessDefinitionStatus: 'BUSINESS_DEFINITION_PROVIDED',
      },
      'banking.emi_bounce_count_3m': {
        cmStatus: 'READY',
        howDefined: 'EMI Bounce Count · Binding EmiBounceCountCalculator.V1',
        executionCapabilityAvailable: true,
        definition: {
          executableMetric: true,
          binding: 'EmiBounceCountCalculator.V1',
          periodMonths: 3,
        },
      },
    }
    const groups = groupDataCalculations(cards, [], resolutions)
    const large = groups.find((g) => g.parameterId === 'banking.large_credit_transactions')
    const emi = groups.find((g) => g.parameterId === 'banking.emi_bounce_count_3m')
    expect(large?.cmStatus).toBe('READY')
    expect(large?.howDefined).toContain('5,00,000')
    expect(emi?.cmStatus).toBe('READY')
  })

  it('EMI READY stamp without binding stays Needs configuration', () => {
    const groups = groupDataCalculations(
      [
        {
          id: 'r2',
          sourceClause: 'EMI bounce',
          canonicalParameterId: 'banking.emi_bounce_count_3m',
        },
      ],
      [],
      {
        'banking.emi_bounce_count_3m': {
          cmStatus: 'READY',
          executionCapabilityAvailable: false,
          definition: { executableMetric: false },
        },
      },
    )
    expect(groups[0]?.cmStatus).toBe('NEEDS_CONFIGURATION')
  })

  it('filters separate needs input vs needs configuration', () => {
    const groups = groupDataCalculations(
      [
        {
          id: 'a',
          sourceClause: 'Large credits',
          canonicalParameterId: 'banking.large_credit_transactions',
          missingDefinition: { action: 'DEFINE' },
        },
        {
          id: 'b',
          sourceClause: 'EMI bounce',
          canonicalParameterId: 'banking.emi_bounce_count_3m',
          missingDefinition: { action: 'CONFIGURE' },
        },
      ],
      [],
    )
    expect(filterParameterGroups(groups, 'NEEDS_INPUT').every((g) => g.cmStatus === 'NEEDS_YOUR_INPUT')).toBe(true)
    expect(filterParameterGroups(groups, 'NEEDS_CONFIGURATION').every((g) => g.cmStatus === 'NEEDS_CONFIGURATION')).toBe(true)
  })
})
