import { describe, expect, it } from 'vitest'
import { lenderPrimaryFromTruth } from './lenderTruthDisplay'

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
})
