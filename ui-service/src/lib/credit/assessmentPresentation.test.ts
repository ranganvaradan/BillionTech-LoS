import { describe, expect, it } from 'vitest'
import {
  buildAssessmentConcerns,
  concernSeverityLabel,
  formatAssessmentOutcome,
  formatCamStatusLabel,
  hasAssessmentResult,
  whyRowsFromParameterResults,
  whyRowsFromReasons,
} from './assessmentPresentation'

describe('assessmentPresentation', () => {
  it('1 empty vs present assessment result (empty state signal)', () => {
    expect(hasAssessmentResult({ uwMeta: null, latestEval: null, creditDecision: null })).toBe(false)
  })

  it('2 assessment result rendering labels', () => {
    expect(hasAssessmentResult({ uwMeta: { recommendation: 'APPROVE' }, latestEval: null, creditDecision: null })).toBe(
      true,
    )
    expect(formatAssessmentOutcome('APPROVE')).toBe('Approved')
  })

  it('3 business reasons section from existing fields only', () => {
    const rows = whyRowsFromParameterResults([
      {
        parameter: 'Bureau Score',
        valueUsed: '642',
        condition: 'Minimum 650',
        decision: 'MANUAL_REVIEW',
        hardRule: true,
      },
    ])
    expect(rows[0]).toMatchObject({
      reason: 'Bureau Score',
      observed: '642',
      expected: 'Minimum 650',
      outcome: 'Manual Credit Review',
    })
    expect(whyRowsFromReasons(['FOIR elevated'])).toEqual([{ reason: 'FOIR elevated' }])
  })

  it('4 concerns / exceptions classification', () => {
    const concerns = buildAssessmentConcerns({
      pendingManualReview: true,
      riskFlags: [{ severity: 'HIGH', label: 'FOIR elevated' }],
      missingItems: ['Bank statement'],
      failedRuleLabels: ['Hard rule: DPD'],
      hasOverride: true,
    })
    expect(concerns.some((c) => c.severity === 'manual_review')).toBe(true)
    expect(concerns.some((c) => c.severity === 'failed')).toBe(true)
    expect(concerns.some((c) => c.severity === 'missing')).toBe(true)
    expect(concernSeverityLabel('needs_attention')).toBe('Needs Attention')
    expect(concernSeverityLabel('manual_review')).toBe('Manual Review')
    expect(concernSeverityLabel('failed')).toBe('Failed')
    expect(concernSeverityLabel('missing')).toBe('Missing Information')
  })

  it('formats CAM statuses without raw enums in business view', () => {
    expect(formatCamStatusLabel('SUBMITTED')).toBe('Submitted for Review')
    expect(formatCamStatusLabel('SENT_BACK')).toBe('Sent Back')
    expect(formatCamStatusLabel(null)).toBe('Not started')
  })
})
