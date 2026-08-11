import { describe, expect, it } from 'vitest'
import {
  duplicateIdsAcrossGroups,
  resolveDataAndCalculations,
  resolveOtherPolicyContent,
  resolveUnderwritingRules,
} from './policyRuleDisplayGroups'

const adb = {
  id: 'adb-1',
  systemRuleId: 'CLASSIFICATION_DATA_REQUIREMENT',
  status: 'Data requirement',
  classificationOnly: true,
  dataRequirementOnly: true,
  ruleName: 'Average Daily Balance of last 3 months',
  businessGroup: 'Data requirements',
  canonicalParameterId: 'banking.average_daily_balance',
}
const txn = {
  id: 'txn-1',
  status: 'Data requirement',
  classificationOnly: true,
  dataRequirementOnly: true,
  ruleName: 'Average monthly transaction for last 3 months',
}
const gamingAdj = {
  id: 'adj-1',
  systemRuleId: 'CLASSIFICATION_METRIC_ADJUSTMENT',
  status: 'Metric adjustment',
  classificationOnly: true,
  metricAdjustment: true,
  ruleName: 'Exclude online gaming credits from ADB',
}
const uwRule = {
  id: 'uw-1',
  systemRuleId: 'BANK_ADB_EDI',
  status: 'Ready',
  classificationOnly: false,
  ruleName: 'ADB >= Proposed EDI',
}
const narrative = {
  id: 'nar-1',
  systemRuleId: 'CLASSIFICATION_NARRATIVE',
  status: 'Excluded',
  classificationOnly: true,
  businessGroup: 'Other policy content',
  ruleName: 'Portfolio monitoring note',
}

describe('policyRuleDisplayGroups', () => {
  it('1–3: data requirement / derived / metric adjustment appear only once in Data & calculations', () => {
    const session = {
      underwritingRules: [uwRule],
      dataAndCalculations: [adb, txn, gamingAdj],
      otherPolicyContent: [],
      ruleCards: [uwRule, adb, txn, gamingAdj],
    }
    const uw = resolveUnderwritingRules(session, session.ruleCards)
    const data = resolveDataAndCalculations(session, session.ruleCards)
    const other = resolveOtherPolicyContent(session, session.ruleCards)
    expect(data).toHaveLength(3)
    expect(other).toEqual([])
    expect(duplicateIdsAcrossGroups(uw, data, other)).toEqual([])
  })

  it('4: underwriting rule appears only in UW group', () => {
    const session = {
      underwritingRules: [uwRule],
      dataAndCalculations: [adb],
      otherPolicyContent: [],
      ruleCards: [uwRule, adb],
    }
    expect(resolveUnderwritingRules(session, session.ruleCards).map((c) => (c as { id: string }).id)).toEqual([
      'uw-1',
    ])
    expect(resolveDataAndCalculations(session, session.ruleCards).map((c) => (c as { id: string }).id)).not.toContain(
      'uw-1',
    )
    expect(resolveOtherPolicyContent(session, session.ruleCards)).toEqual([])
  })

  it('5: genuine narrative appears only in Other policy content', () => {
    const session = {
      underwritingRules: [uwRule],
      dataAndCalculations: [adb],
      otherPolicyContent: [narrative],
      ruleCards: [uwRule, adb, narrative],
    }
    const other = resolveOtherPolicyContent(session, session.ruleCards)
    expect(other).toEqual([narrative])
    expect(resolveDataAndCalculations(session, session.ruleCards).map((c) => (c as { id: string }).id)).not.toContain(
      'nar-1',
    )
  })

  it('6: empty otherPolicyContent is authoritative — no Replace-with-rule rebuild of data stubs', () => {
    const session = {
      underwritingRules: [uwRule],
      dataAndCalculations: [adb, gamingAdj],
      otherPolicyContent: [],
      ruleCards: [uwRule, adb, gamingAdj],
    }
    // Bug was: length===0 fell back to classificationOnly → adb/adj in Other
    expect(resolveOtherPolicyContent(session, session.ruleCards)).toEqual([])
  })

  it('7: canonical parameter identity is preserved on data items', () => {
    const session = {
      underwritingRules: [],
      dataAndCalculations: [adb],
      otherPolicyContent: [],
      ruleCards: [adb],
    }
    const item = resolveDataAndCalculations(session, session.ruleCards)[0] as typeof adb
    expect(item.canonicalParameterId).toBe('banking.average_daily_balance')
  })

  it('8: Banking BRE style duplicate count = 0', () => {
    const session = {
      underwritingRules: [uwRule],
      dataAndCalculations: [adb, txn, gamingAdj],
      otherPolicyContent: [],
      ruleCards: [uwRule, adb, txn, gamingAdj],
    }
    expect(
      duplicateIdsAcrossGroups(
        resolveUnderwritingRules(session, session.ruleCards),
        resolveDataAndCalculations(session, session.ruleCards),
        resolveOtherPolicyContent(session, session.ruleCards),
      ),
    ).toEqual([])
  })

  it('legacy fallback excludes data/metric stubs from Other', () => {
    const session = { ruleCards: [uwRule, adb, gamingAdj, narrative] } as Record<string, unknown>
    const cards = session.ruleCards as unknown[]
    const ids = resolveOtherPolicyContent(session, cards).map((c) => (c as { id: string }).id)
    expect(ids).not.toContain('adb-1')
    expect(ids).not.toContain('adj-1')
    expect(ids).toContain('nar-1')
  })
})
