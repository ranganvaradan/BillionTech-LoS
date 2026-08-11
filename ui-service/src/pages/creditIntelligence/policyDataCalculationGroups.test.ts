import { describe, expect, it } from 'vitest'
import {
  adjustmentPeerCount,
  duplicateClauseCount,
  formatHowCalculated,
  groupDataCalculations,
  resolveParameterKey,
} from './policyDataCalculationGroups'

const adbReq = {
  id: 'c1',
  status: 'Data requirement',
  dataRequirementOnly: true,
  ruleName: 'Average Daily Balance of last 3 months',
  sourceClause: 'Average Daily Balance of last 3 months.',
  evaluatedFrom: 'Bank Statement',
  parameterId: 'banking.avg_daily_balance_3m',
  canonicalParameterId: 'banking.avg_daily_balance_3m',
  itemKind: 'DERIVED_PARAMETER',
  howCalculated: {
    source: 'Bank Statement',
    metric: 'Average daily balance',
    calculation: 'End-of-day balance carry-forward average over trailing 3 months',
  },
  canonicalParameter: {
    id: 'banking.avg_daily_balance_3m',
    businessName: 'Average daily balance',
    type: 'DERIVED',
    evaluatedFrom: 'Bank Statement',
  },
}

const loanAdj = {
  id: 'c2',
  status: 'Metric adjustment',
  metricAdjustment: true,
  itemKind: 'CALCULATION_ADJUSTMENT',
  affectedParameterId: 'banking.avg_daily_balance_3m',
  metadata: { affectedMetric: 'banking.avg_daily_balance_3m', metricAdjustment: true },
  ruleName: 'Any Loans disbursed in last 3 months to be removed from Average Daily Balance calculation',
  sourceClause: 'Any Loans disbursed in last 3 months to be removed from Average Daily Balance calculation.',
  evaluatedFrom: 'Bank Statement',
}

const gamingAdj = {
  id: 'c3',
  status: 'Metric adjustment',
  metricAdjustment: true,
  itemKind: 'CALCULATION_ADJUSTMENT',
  affectedParameterId: 'banking.avg_daily_balance_3m',
  metadata: { affectedMetric: 'banking.avg_daily_balance_3m' },
  sourceClause: 'Any credits from online gaming to be removed from Average Daily Balance calculation.',
  evaluatedFrom: 'Bank Statement',
}

const bulkAdj = {
  id: 'c4',
  status: 'Metric adjustment',
  metricAdjustment: true,
  itemKind: 'CALCULATION_ADJUSTMENT',
  affectedParameterId: 'banking.avg_daily_balance_3m',
  metadata: { affectedMetric: 'banking.avg_daily_balance_3m' },
  sourceClause:
    'Any bulk deposition by merchant which is more than 10 times of average depositions in last 3 months to be removed from Average Daily Balance calculation.',
  evaluatedFrom: 'Bank Statement',
}

const large = {
  id: 'c5',
  status: 'Data requirement',
  dataRequirementOnly: true,
  itemKind: 'REPORT_ANALYST_INFORMATION',
  sourceClause: 'Party wise Large credits with name and amount of transaction.',
  missingDefinition: {
    question: 'What qualifies as a "Large" credit?',
    action: 'DEFINE',
  },
}

const emi = {
  id: 'c6',
  status: 'Data requirement',
  dataRequirementOnly: true,
  sourceClause: 'EMI bounces in last 3 months.',
}

const txn = {
  id: 'c7',
  status: 'Data requirement',
  dataRequirementOnly: true,
  canonicalParameterId: 'banking.transaction_count.average_monthly_3m',
  sourceClause: 'Average monthly transaction for last 3 months.',
}

const uw = {
  id: 'uw1',
  ruleName: 'DigiLeap — banking capacity',
  technicalExpression: { left: { metric: 'banking.avg_daily_balance_3m' } },
}

describe('policyDataCalculationGroups', () => {
  it('1: clauses group by canonical parameter', () => {
    const groups = groupDataCalculations([adbReq, txn, loanAdj], [uw])
    expect(groups.map((g) => g.parameterId)).toEqual(
      expect.arrayContaining([
        'banking.avg_daily_balance_3m',
        'banking.transaction_count.average_monthly_3m',
      ]),
    )
  })

  it('2–3: ADB adjustments group under ADB and are not peer parameters', () => {
    const groups = groupDataCalculations([adbReq, loanAdj, gamingAdj, bulkAdj], [uw])
    const adb = groups.find((g) => g.parameterId === 'banking.avg_daily_balance_3m')
    expect(adb).toBeTruthy()
    expect(adb!.policyAdjustments).toHaveLength(3)
    expect(groups.filter((g) => g.members.every((m) => m.metricAdjustment))).toHaveLength(0)
    expect(adjustmentPeerCount(groups)).toBe(0)
  })

  it('4: howCalculated stays enterprise base (policyScoped note only on card)', () => {
    const groups = groupDataCalculations([adbReq, loanAdj], [])
    const adb = groups.find((g) => g.parameterId === 'banking.avg_daily_balance_3m')!
    expect(String(adb.howCalculated?.calculation)).toContain('End-of-day')
    expect(adb.policyAdjustments).toHaveLength(1)
  })

  it('5: Large credit exposes exact missing definition', () => {
    const groups = groupDataCalculations([large], [])
    const g = groups.find((g) => g.parameterId === 'banking.large_credit_transactions')!
    expect(g.cmStatus).toBe('NEEDS_YOUR_INPUT')
    expect(String(g.missingDefinition?.question)).toMatch(/Large/i)
    expect(g.usedFor).toMatch(/Analyst/i)
  })

  it('6: READY derived metric does not request unnecessary CM input', () => {
    const groups = groupDataCalculations([adbReq, txn], [])
    const adb = groups.find((g) => g.parameterId === 'banking.avg_daily_balance_3m')!
    expect(adb.cmStatus).toBe('READY')
    expect(adb.missingDefinition).toBeUndefined()
  })

  it('7: source clause provenance retained', () => {
    const groups = groupDataCalculations([adbReq, loanAdj], [])
    const adb = groups.find((g) => g.parameterId === 'banking.avg_daily_balance_3m')!
    expect(adb.sourceClauses.join(' ')).toContain('Average Daily Balance')
    expect(adb.sourceClauses.join(' ')).toContain('Loans disbursed')
  })

  it('8: no [object Object] from howCalculated formatter', () => {
    expect(formatHowCalculated({ calculation: 'x', nested: { a: 1 } })).toEqual({
      calculation: 'x',
      nested: '{"a":1}',
    })
    expect(formatHowCalculated('[object Object]')).toBeNull()
  })

  it('10: Banking duplicate clause count = 0', () => {
    const groups = groupDataCalculations([adbReq, loanAdj, gamingAdj, bulkAdj, txn, large, emi], [uw])
    expect(duplicateClauseCount(groups)).toBe(0)
  })

  it('11: resolver key uses same canonical id', () => {
    expect(resolveParameterKey(adbReq).parameterId).toBe('banking.avg_daily_balance_3m')
  })

  it('rule linkage surfaces DigiLeap capacity', () => {
    const groups = groupDataCalculations([adbReq], [uw])
    const adb = groups.find((g) => g.parameterId === 'banking.avg_daily_balance_3m')!
    expect(adb.usedByRules.map((r) => r.name).join(' ')).toContain('DigiLeap')
  })

  it('EMI bounce → needs configuration', () => {
    const groups = groupDataCalculations([emi], [])
    const g = groups.find((g) => g.parameterId === 'banking.emi_bounce_count_3m')!
    expect(g.cmStatus).toBe('NEEDS_CONFIGURATION')
  })
})
