import { describe, expect, it } from 'vitest'
import { formatCamSectionExtended, formatCamSectionFieldValue } from '@/lib/cam/camSectionFormat'

describe('camSectionFormat', () => {
  it('formats parameter breakdown rows as readable lines', () => {
    const text = formatCamSectionFieldValue([
      { Parameter: 'BUREAU_SCORE', 'Value used': '750', Points: '15 / 15', Source: 'ctx:BUREAU' },
      { Parameter: 'MONTHLY_INCOME', 'Value used': '85000', Points: '10 / 10', Source: 'MANUAL' },
    ])
    expect(text).toContain('1. BUREAU_SCORE')
    expect(text).toContain('value: 750')
    expect(text).not.toContain('[object Object]')
  })

  it('builds section narrative without internal keys', () => {
    const text = formatCamSectionExtended({
      'Aggregate score': '92',
      'Parameter breakdown': '1. BUREAU_SCORE · value: 750',
      _parameterBreakdownRows: [{ Parameter: 'X' }],
    })
    expect(text).toContain('Aggregate score: 92')
    expect(text).not.toContain('_parameterBreakdownRows')
    expect(text).not.toContain('[object Object]')
  })
})
