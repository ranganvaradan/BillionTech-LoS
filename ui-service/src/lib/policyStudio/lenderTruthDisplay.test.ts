import { describe, expect, it } from 'vitest'
import {
  certificationDisplayLabel,
  isLiveApprovedFromTruth,
  lenderPrimaryFromTruth,
} from '../policyStudio/lenderTruthDisplay'
import { lenderPrimaryStatus, lenderOverallReadinessLabel } from '../policyStudio/lenderUxCopy'

describe('Wave9 lender truth display', () => {
  it('prefers backend primaryStatusLabel over local flags', () => {
    const r = lenderPrimaryFromTruth(
      {
        primaryStatus: 'CALCULATION_NEEDS_SETUP',
        primaryStatusLabel: 'Calculation needs setup',
        nextAction: 'Set up calculation',
      },
      { policyTestReady: true, productionReady: true as unknown as boolean },
    )
    expect(r.label).toBe('Calculation needs setup')
    expect(r.nextAction).toBe('Set up calculation')
  })

  it('never treats catalogue production_ready as live approval', () => {
    expect(isLiveApprovedFromTruth({
      execution: { capability: true },
      certification: { certificationStatus: 'UNCERTIFIED' },
      liveUseDisplay: { status: 'UNCERTIFIED', available: false },
    })).toBe(false)
    expect(isLiveApprovedFromTruth({
      execution: { capability: true },
      certification: { certificationStatus: 'CERTIFIED' },
    })).toBe(true)
    expect(certificationDisplayLabel('CERTIFIED')).toBe('Approved for live use')
    expect(certificationDisplayLabel('UNCERTIFIED')).toBe('Not approved for live use')
  })

  it('lenderPrimaryStatus uses backend label when present', () => {
    const s = lenderPrimaryStatus({
      primaryStatusLabel: 'Approved for live use',
      primaryStatus: 'APPROVED_FOR_LIVE_USE',
      productionReady: false,
    })
    expect(s.label).toBe('Approved for live use')
  })

  it('calculationRequired fallback says calculation needs setup', () => {
    expect(lenderPrimaryStatus({ calculationRequired: true }).label).toBe('Calculation needs setup')
  })

  it('overall readiness never maps PRODUCTION_READY to certified live', () => {
    expect(lenderOverallReadinessLabel('PRODUCTION_READY')).not.toMatch(/approved for live/i)
    expect(lenderOverallReadinessLabel('PRODUCTION_READY')).toBe('Listed — not certification')
  })
})
