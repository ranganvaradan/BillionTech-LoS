import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import {
  matchesDp1Filters,
  overallReadinessLabel,
  providerStatusLabel,
  readinessBadgeClass,
  sourceTypeLabel,
} from '@/lib/dataParameters/dp1Display'

describe('DP-1 Data & Parameters display', () => {
  it('labels overall readiness honestly', () => {
    expect(overallReadinessLabel('PRODUCTION_READY')).toBe('Production Ready')
    expect(overallReadinessLabel('RUNTIME_READY_NONPROD')).toBe('Runtime Ready — Non-Production')
    expect(overallReadinessLabel('POLICY_TEST_ONLY')).toBe('Policy Test Only')
    expect(overallReadinessLabel('CATALOGUE_ONLY')).toBe('Catalogue Only')
    expect(overallReadinessLabel('READINESS_UNKNOWN')).toBe('Readiness Unknown')
  })

  it('labels source types', () => {
    expect(sourceTypeLabel('APPLICATION_INPUT')).toBe('Application input')
    expect(sourceTypeLabel('PROVIDER')).toBe('Provider')
    expect(sourceTypeLabel('WORKFLOW')).toBe('Workflow')
  })

  it('distinguishes provider registration states', () => {
    expect(providerStatusLabel({ status: 'NOT_APPLICABLE' })).toContain('not applicable')
    expect(providerStatusLabel({ status: 'INFERRED', label: 'Equifax bureau XML path' })).toContain('inferred')
    expect(providerStatusLabel({ status: 'UNKNOWN' })).toContain('unknown')
    expect(providerStatusLabel({ status: 'REGISTERED', label: 'Equifax' })).toContain('registered')
  })

  it('filters by source family, type, readiness, productionReady', () => {
    const row = {
      id: 'bureau.score',
      businessName: 'Bureau score',
      sourceFamily: 'Bureau Retail',
      sourceType: 'PROVIDER',
      overallReadiness: 'PRODUCTION_READY',
      productionReady: true,
    }
    expect(
      matchesDp1Filters(row, {
        sourceFamily: 'Bureau Retail',
        sourceType: 'PROVIDER',
        overallReadiness: 'PRODUCTION_READY',
        productionReady: 'true',
        q: 'score',
      }),
    ).toBe(true)
    expect(
      matchesDp1Filters(row, {
        sourceFamily: '',
        sourceType: '',
        overallReadiness: 'POLICY_TEST_ONLY',
        productionReady: '',
        q: '',
      }),
    ).toBe(false)
    expect(
      matchesDp1Filters(row, {
        sourceFamily: '',
        sourceType: '',
        overallReadiness: '',
        productionReady: 'false',
        q: '',
      }),
    ).toBe(false)
  })

  it('badge classes differ for readiness buckets', () => {
    expect(readinessBadgeClass('PRODUCTION_READY')).toContain('emerald')
    expect(readinessBadgeClass('RUNTIME_READY_NONPROD')).toContain('amber')
    expect(readinessBadgeClass('POLICY_TEST_ONLY')).toContain('sky')
    expect(readinessBadgeClass('CATALOGUE_ONLY')).toContain('slate')
    expect(readinessBadgeClass('READINESS_UNKNOWN')).toContain('rose')
  })

  it('DataParametersPage wires badges, filters, and detail sections', () => {
    const page = readFileSync(
      resolve(__dirname, '../../pages/DataParametersPage.tsx'),
      'utf8',
    )
    expect(page).toContain('data-testid="dp1-badges"')
    expect(page).toContain('data-testid="dp1-filters"')
    expect(page).toContain('data-testid="dp1-detail-panel"')
    expect(page).toContain('A. Definition')
    expect(page).toContain('D. Readiness')
    expect(page).toContain('Advanced / Technical Details')
    expect(page).toContain('getDataParametersDetail')
  })

  it('API client exposes parameter detail', () => {
    const api = readFileSync(resolve(__dirname, '../../api/liveReadiness.ts'), 'utf8')
    expect(api).toContain('getDataParametersDetail')
    expect(api).toContain('data-parameters/')
  })
})
