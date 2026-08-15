import { describe, expect, it } from 'vitest'
import { DP3_POLICY_GRAPH_API_BASE } from '@/api/dp3PolicyGraph'

describe('dp3PolicyGraph API base', () => {
  it('is relative to http baseURL (no leading /api/v1) to avoid path doubling', () => {
    expect(DP3_POLICY_GRAPH_API_BASE).toBe('internal/credit-intelligence/dp3')
    expect(DP3_POLICY_GRAPH_API_BASE.startsWith('/api/')).toBe(false)
    expect(DP3_POLICY_GRAPH_API_BASE.startsWith('api/')).toBe(false)
  })

  it('joins with staging baseURL to the real controller path', () => {
    const baseURL = '/api/v1'
    const joined = `${baseURL.replace(/\/+$/, '')}/${DP3_POLICY_GRAPH_API_BASE.replace(/^\/+/, '')}`
    expect(joined).toBe('/api/v1/internal/credit-intelligence/dp3')
    expect(joined.includes('/api/v1/api/v1')).toBe(false)
  })
})
