import { describe, expect, it } from 'vitest'
import {
  falseNoScorecardDisplayCount,
  resolveScorecardLinkageDisplay,
} from './scorecardLinkageDisplay'

describe('scorecardLinkageDisplay', () => {
  it('S5 unknown/loading does not become No scorecard', () => {
    const d = resolveScorecardLinkageDisplay({ sessionLoaded: false, linkageKnown: false })
    expect(d.kind).toBe('LOADING')
    expect(falseNoScorecardDisplayCount(d, true)).toBe(1)
    expect(falseNoScorecardDisplayCount(d, false)).toBe(0)
  })

  it('S1/S6 linked id wins even if known flag missing', () => {
    const d = resolveScorecardLinkageDisplay({
      sessionLoaded: true,
      linkageKnown: false,
      scorecardId: 'cc38f5a0-fab7-4001-8167-5a8f47d2487a',
      scorecardName: 'Vikasam Bureau — Scorecard',
      scorecardStatus: 'DRAFT',
      scorecardScoringMode: 'POLICY_WEIGHTED_V2',
    })
    expect(d.kind).toBe('LINKED')
    if (d.kind === 'LINKED') {
      expect(d.scorecardId).toBe('cc38f5a0-fab7-4001-8167-5a8f47d2487a')
      expect(d.name).toBe('Vikasam Bureau — Scorecard')
    }
    expect(falseNoScorecardDisplayCount(d, true)).toBe(1)
  })

  it('S4 genuine unlink shows NONE only when known', () => {
    const d = resolveScorecardLinkageDisplay({
      sessionLoaded: true,
      linkageKnown: true,
      scorecardId: null,
    })
    expect(d.kind).toBe('NONE')
    expect(falseNoScorecardDisplayCount(d, true)).toBe(0)
  })

  it('S7 does not invent the duplicate reverse-link id', () => {
    const d = resolveScorecardLinkageDisplay({
      sessionLoaded: true,
      linkageKnown: true,
      scorecardId: 'cc38f5a0-fab7-4001-8167-5a8f47d2487a',
    })
    expect(d.kind).toBe('LINKED')
    if (d.kind === 'LINKED') {
      expect(d.scorecardId).not.toBe('a504db06-f6ce-47f4-a858-2b9b6acbad40')
    }
  })
})
