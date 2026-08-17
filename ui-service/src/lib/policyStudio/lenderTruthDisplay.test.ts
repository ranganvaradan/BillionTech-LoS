import { describe, expect, it } from 'vitest'
import {
  lenderPrimaryFromTruth,
  requiresCalculationSetupAction,
  SETUP_CALCULATION_ACTION,
} from './lenderTruthDisplay'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

describe('lenderTruthDisplay golden', () => {
  it('prefers backend READY label', () => {
    const r = lenderPrimaryFromTruth({
      primaryStatus: 'READY',
      primaryStatusLabel: 'Ready',
      businessReadiness: 'READY',
    })
    expect(r.state).toBe('READY')
    expect(r.label).toBe('Ready')
  })

  it('prefers backend NOT_READY calculation label', () => {
    const r = lenderPrimaryFromTruth({
      primaryStatus: 'NOT_READY',
      primaryStatusLabel: 'Calculation not defined',
      businessReadinessReason: 'CALCULATION_NOT_DEFINED',
    })
    expect(r.label).toBe('Calculation not defined')
  })

  it('fallback calculationRequired maps to Not ready', () => {
    expect(lenderPrimaryFromTruth(null, { calculationRequired: true }).label).toBe(
      'Calculation not defined',
    )
  })

  it('requiresCalculationSetupAction for CALCULATION_NOT_DEFINED regardless of legacy support', () => {
    expect(
      requiresCalculationSetupAction({
        businessReadinessReason: 'CALCULATION_NOT_DEFINED',
        nextAction: SETUP_CALCULATION_ACTION,
        presentation: { allowedActions: [SETUP_CALCULATION_ACTION] },
      }),
    ).toBe(true)
    expect(
      requiresCalculationSetupAction({
        businessReadinessReason: 'CALCULATION_NOT_DEFINED',
      }),
    ).toBe(true)
    expect(
      requiresCalculationSetupAction({
        primaryStatus: 'READY',
        businessReadinessReason: null,
        nextAction: null,
      }),
    ).toBe(false)
  })
})

describe('CALCULATION-SETUP-ACTION-INVARIANT static guard', () => {
  it('consumers cannot independently suppress setup via legacy SUPPORTED_DERIVED alone', () => {
    const workflow = readFileSync(
      resolve(__dirname, '../../components/dataParameters/SuggestCalculationWorkflow.tsx'),
      'utf8',
    )
    expect(workflow).toContain('requiresCalculationSetupAction')
    expect(workflow).toContain('SETUP_CALCULATION_ACTION')
    expect(workflow).toContain("from '@/lib/policyStudio/lenderTruthDisplay'")
    // NeedsSetup must not be exclusively gated on legacy support buckets
    expect(workflow).not.toMatch(
      /needsSetup\s*=\s*[\s\S]{0,80}supportStatus\s*===\s*'SUPPORTED_DERIVED'/,
    )
    const dp = readFileSync(resolve(__dirname, '../../pages/DataParametersPage.tsx'), 'utf8')
    expect(dp).toContain('requiresCalculationSetupAction')
    expect(dp).toContain('businessReadinessReason')
    const inventory = readFileSync(
      resolve(__dirname, '../../components/policy/PolicyParameterInventoryPanel.tsx'),
      'utf8',
    )
    expect(inventory).toContain('requiresCalculationSetupAction')
    expect(inventory).toContain('SETUP_CALCULATION_ACTION')
  })
})
