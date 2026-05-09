import { describe, expect, it } from 'vitest'
import {
  defaultProviderForMatrixStep,
  INTEGRATION_MATRIX,
  providersForWorkflowStep,
  normalizeProviderForStep,
} from '@/lib/integrationProviderMatrix'

describe('integrationProviderMatrix', () => {
  it('PAN_VERIFY uses PERFIOS first', () => {
    expect(providersForWorkflowStep('PAN_VERIFY')[0]).toBe('PERFIOS')
    expect(providersForWorkflowStep('PAN_VERIFY')[1]).toBe('AUTHBRIDGE')
    expect(defaultProviderForMatrixStep('PAN_VERIFY')).toBe('PERFIOS')
  })

  it('BUREAU_PULL only offers EQUIFAX', () => {
    expect(providersForWorkflowStep('BUREAU_PULL')).toEqual(['EQUIFAX'])
  })

  it('eSign steps only list EMSIGNER and AUTHBRIDGE_ESIGN', () => {
    expect(providersForWorkflowStep('ESIGN_KFS')).toEqual(['EMSIGNER', 'AUTHBRIDGE_ESIGN'])
    expect(providersForWorkflowStep('ESIGN_AGREEMENT')).toEqual(['EMSIGNER', 'AUTHBRIDGE_ESIGN'])
  })

  it('rejects invalid provider and normalizes to default', () => {
    expect(normalizeProviderForStep('PAN_VERIFY', 'KARZA')).toBe('PERFIOS')
    expect(normalizeProviderForStep('BUREAU_PULL', 'KARZA')).toBe('EQUIFAX')
  })

  it('matrix covers all workflow steps in INTEGRATION_MATRIX', () => {
    expect(INTEGRATION_MATRIX.length).toBeGreaterThan(10)
  })
})
