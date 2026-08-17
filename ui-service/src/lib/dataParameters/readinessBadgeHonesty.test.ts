import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  businessFacingInputLabels,
  formatCalculationResultLabel,
  formatCanCalculateNarrative,
  lenderPrimaryStatus,
  lenderSupportLabel,
  sanitizeLenderTechnicalPhrase,
} from '@/lib/policyStudio/lenderUxCopy'

describe('readiness badge honesty', () => {
  it('inventory panel does not affirm Production Ready when false', () => {
    const src = readFileSync(
      resolve(__dirname, '../../components/policy/PolicyParameterInventoryPanel.tsx'),
      'utf8',
    )
    expect(src).toContain('productionReady === true')
    expect(src).toContain('Production not ready')
    expect(src).toContain('Policy test unavailable')
    expect(src).toContain('Calculation required')
    // Must not render bare affirmative label without true guard nearby
    expect(src).toMatch(/productionReady === true[\s\S]*Production Ready/)
  })

  it('suggest calculation workflow is generic (no clean-history-specific UI)', () => {
    const src = readFileSync(
      resolve(__dirname, '../../components/dataParameters/SuggestCalculationWorkflow.tsx'),
      'utf8',
    )
    expect(src).toContain('Work it out for me')
    expect(src).toContain('Use this calculation')
    expect(src).not.toMatch(/clean_history_months/)
    expect(src).not.toMatch(/Clean history months/)
  })
})

describe('POLICY-DERIVED-CALCULATION-BUSINESS-ASSISTANT-1', () => {
  it('maps calculation-required to Calculation needs setup', () => {
    const s = lenderPrimaryStatus({ calculationRequired: true, policyTestReady: false })
    expect(s.label).toBe('Calculation needs setup')
    expect(lenderSupportLabel('CALCULATION_NOT_IMPLEMENTED')).toBe('Calculation needs setup')
  })

  it('sanitizes technical jargon from lender copy', () => {
    const out = sanitizeLenderTechnicalPhrase(
      'GACAT REF RAW DERIVED NEEDS_INPUT CALCULATION_NOT_IMPLEMENTED typed expression vocabulary configuration semantic compatibility',
    )
    expect(out).not.toMatch(/\bGACAT\b/)
    expect(out).not.toMatch(/\bREF\b/)
    expect(out).not.toMatch(/\bRAW\b/)
    expect(out).not.toMatch(/\bDERIVED\b/)
    expect(out).not.toMatch(/NEEDS_INPUT/)
    expect(out).not.toMatch(/CALCULATION_NOT_IMPLEMENTED/)
    expect(out).not.toMatch(/typed expression/i)
    expect(out).not.toMatch(/vocabulary configuration/i)
    expect(out).not.toMatch(/semantic compatibility/i)
  })

  it('lender workflow hides technical terms and keeps a single Advanced section', () => {
    const src = readFileSync(
      resolve(__dirname, '../../components/dataParameters/SuggestCalculationWorkflow.tsx'),
      'utf8',
    )
    expect(src).toContain('SETUP_CALCULATION_ACTION')
    expect(src).toContain('Work it out for me')
    expect(src).toContain('Use this calculation')
    expect(src).toContain('I can calculate this')
    expect(src).toContain('I need one detail')
    expect(src).toContain("I can&apos;t calculate this yet")
    expect(src).toContain('data-lender-ux="layer-1"')
    expect(src).toContain('data-business-assistant="1"')
    expect(src).toContain('data-universal-flow="1"')
    expect(src).toContain("How I'll calculate it")
    expect(src).toContain('change-calculation')
    expect(src).toContain('lender-change-flow')
    // Exactly one Advanced summary style in the needs-input card path (plus optional ready-state)
    const advancedMatches = src.match(/>Advanced &gt;</g) ?? src.match(/>Advanced >/g) ?? []
    expect(advancedMatches.length).toBeGreaterThanOrEqual(1)
    expect(advancedMatches.length).toBeLessThanOrEqual(2)
    expect(src).not.toContain('Advanced details')
    expect(src).toContain('lender-ill-use-list')
    expect(src).toContain('formatCanCalculateNarrative')
    expect(src).toContain('businessFacingInputLabels')
    const layer1Idle = src.slice(0, src.indexOf('data-testid="lender-advanced-details"'))
    expect(layer1Idle).not.toMatch(/>\s*Dependencies\s*</)
    expect(layer1Idle).not.toContain('exact GACAT')
    expect(layer1Idle).not.toContain('Suggest calculation')
    expect(layer1Idle).not.toContain('Accept &amp; create calculation')
    expect(layer1Idle).not.toContain('vocabulary configuration')
    expect(layer1Idle).not.toContain('typed expression')
  })

  it('proposal polish strips duplicate intro and collapses input labels', () => {
    const narrative = formatCanCalculateNarrative(
      "I can calculate this.\n\nI'll find the most recent month in which any bureau account was overdue (DPD > 0), then count the number of months since that overdue.\n\nI'll use:\n• Bureau payment history\n• DPD for each reported month\n• Reporting month/date\n\nResult: Clean history months",
    )
    expect(narrative).not.toMatch(/I can calculate this/i)
    expect(narrative).not.toMatch(/I'll use/i)
    expect(narrative).not.toMatch(/^Result:/im)
    expect(narrative).toMatch(/completed months/)
    const labels = businessFacingInputLabels([
      { parameterId: 'bureau.tradeline.payment_history', displayName: 'Payment history' },
      { parameterId: 'bureau.tradeline.dpd_month', displayName: 'Days past due (month)' },
      { parameterId: 'bureau.report.date', displayName: 'Bureau report date' },
    ])
    expect(labels).toEqual([
      'Bureau payment history',
      'Days past due (DPD)',
      'Reporting month/date',
    ])
    expect(formatCalculationResultLabel('Clean history months (post-overdue)')).toBe(
      'Clean history months',
    )
  })

  it('hides How calculated under calculationRequired workflow surfaces', () => {
    const rules = readFileSync(
      resolve(__dirname, '../../pages/creditIntelligence/CiPolicyRulesTab.tsx'),
      'utf8',
    )
    // Wave 10A — calculation resolver only when presentation.showCalculationResolver
    expect(rules).toContain('derivePolicyStudioOperandPresentation')
    expect(rules).toContain('showCalculationResolver')
    expect(rules).toContain('parameter-execution-status')
    expect(rules).toContain('rule-lifecycle-status')
    const sim = readFileSync(
      resolve(__dirname, '../../pages/creditIntelligence/CiPolicySimulationTab.tsx'),
      'utf8',
    )
    expect(sim).toContain('p.calculationRequired !== true && (how.calculation || how.source)')
  })

  it('universal lender flow exposes Accept Change and shared change path', () => {
    const src = readFileSync(
      resolve(__dirname, '../../components/dataParameters/SuggestCalculationWorkflow.tsx'),
      'utf8',
    )
    expect(src).toContain('data-universal-flow="1"')
    expect(src).toContain('knownExisting')
    expect(src).toContain('lender-change-flow')
    expect(src).toContain("How I'll calculate it")
    expect(src).toContain('clarification-choice-${c.id}')
    expect(src).toContain('SEMANTIC_CONFLICT')
    expect(src).toContain('change-after-accept')
    expect(src).toContain('openChangeFlow')
    expect(src).toContain("choiceId === 'other'")
  })

  it('inventory default title drops GACAT jargon', () => {
    const src = readFileSync(
      resolve(__dirname, '../../components/policy/PolicyParameterInventoryPanel.tsx'),
      'utf8',
    )
    expect(src).toContain('Parameters used by this policy')
    expect(src).not.toContain('Policy parameter inventory (GACAT)')
    expect(src).toContain('SETUP_CALCULATION_ACTION')
    expect(src).toContain('Advanced details')
  })

  it('rules/resolver do not nest duplicate Advanced outside the assistant card', () => {
    const rules = readFileSync(
      resolve(__dirname, '../../pages/creditIntelligence/CiPolicyRulesTab.tsx'),
      'utf8',
    )
    const calcBlock = rules.slice(
      rules.indexOf('SuggestCalculationWorkflow'),
      rules.indexOf('SuggestCalculationWorkflow') + 800,
    )
    expect(calcBlock).not.toContain('Advanced details')
    const resolver = readFileSync(
      resolve(__dirname, '../../pages/creditIntelligence/CiParameterResolverPanel.tsx'),
      'utf8',
    )
    expect(resolver).not.toContain('executability-modes-advanced')
  })

  it('rules tab prefers lifecycle projection over local badge heuristics', () => {
    const rules = readFileSync(
      resolve(__dirname, '../../pages/creditIntelligence/CiPolicyRulesTab.tsx'),
      'utf8',
    )
    expect(rules).toContain('lenderStateLabel')
    expect(rules).toContain('forbidAcceptedBadgeWhenNeedsInput')
    expect(rules).toContain('forbidNeedsInputWhenAcceptedReady')
    expect(rules).toContain('showAcceptRule')
    expect(rules).toContain('onRefresh')
    expect(rules).toContain('lifecycleNeedsInput')
  })
})

describe('POLICY-STUDIO-LENDER-UX-SIMPLIFICATION-1', () => {
  it('keeps readiness honesty helpers', () => {
    expect(lenderPrimaryStatus({ calculationRequired: true }).label).toBe('Calculation needs setup')
  })
})
