import { describe, expect, it } from 'vitest'
import { formatCondition, opsForParamType, parseCondition } from './scorecardCondition'

describe('scorecardCondition', () => {
  it('parses numeric operators', () => {
    expect(parseCondition('GTE:650')).toEqual({ op: 'GTE', value: '650' })
    expect(parseCondition('BETWEEN:3:60')).toEqual({ op: 'BETWEEN', min: '3', max: '60' })
  })

  it('formats back to encoded condition', () => {
    expect(formatCondition({ op: 'LTE', value: '40' })).toBe('LTE:40')
    expect(formatCondition({ op: 'BETWEEN', min: '10', max: '20' })).toBe('BETWEEN:10:20')
  })

  it('limits yes/no to EQ and NE', () => {
    expect(opsForParamType('yesno')).toEqual(['EQ', 'NE'])
    expect(opsForParamType('number')).toContain('BETWEEN')
  })
})
