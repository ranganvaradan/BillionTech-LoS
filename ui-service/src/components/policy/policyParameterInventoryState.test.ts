import { describe, expect, it } from 'vitest'
import {
  classifyInventoryLoad,
  unresolvedTokens,
  type InvRow,
} from '@/components/policy/policyParameterInventoryState'

describe('policyParameterInventoryState', () => {
  it('distinguishes technical error from empty/audit states', () => {
    expect(
      classifyInventoryLoad({
        busy: false,
        error: 'An unexpected error occurred',
        graphPresent: null,
        rows: [],
        unresolvedCount: 0,
      }),
    ).toBe('TECHNICAL_ERROR')
    expect(
      classifyInventoryLoad({
        busy: false,
        error: null,
        graphPresent: false,
        rows: [],
        unresolvedCount: 0,
      }),
    ).toBe('NO_POLICY_GRAPH')
    expect(
      classifyInventoryLoad({
        busy: false,
        error: null,
        graphPresent: true,
        rows: [],
        unresolvedCount: 0,
      }),
    ).toBe('NO_PARAMETERS')
  })

  it('treats unresolved operands as loaded audit state, not error', () => {
    const rows: InvRow[] = [
      { canonicalParameterId: 'banking.adb', resolutionStatus: 'RESOLVED', originalToken: 'banking.adb' },
      {
        canonicalParameterId: null,
        resolutionStatus: 'UNRESOLVED_CANONICAL_PARAMETER',
        originalToken: 'banking.inward_return.count_3m',
      },
    ]
    expect(
      classifyInventoryLoad({
        busy: false,
        error: null,
        graphPresent: true,
        rows,
        unresolvedCount: 2,
      }),
    ).toBe('LOADED_WITH_UNRESOLVED')
    expect(unresolvedTokens(rows)).toEqual(['banking.inward_return.count_3m'])
  })

  it('marks all-resolved inventory', () => {
    expect(
      classifyInventoryLoad({
        busy: false,
        error: null,
        graphPresent: true,
        rows: [{ canonicalParameterId: 'a', resolutionStatus: 'RESOLVED' }],
        unresolvedCount: 0,
      }),
    ).toBe('LOADED_WITH_PARAMETERS')
  })
})
