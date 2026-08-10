import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const root = resolve(__dirname, '../..')

function read(rel: string): string {
  return readFileSync(resolve(root, rel), 'utf8')
}

describe('POLICY-UX-2C catalogue parameter editor', () => {
  it('catalogue panel supports Add to Policy and parameter editors', () => {
    const panel = read('pages/creditIntelligence/CiCapabilityCataloguePanel.tsx')
    expect(panel).toContain('Add to Policy')
    expect(panel).toContain('If rule fails')
    expect(panel).toContain('Use manual input instead')
    expect(panel).toContain('addCatalogueCapabilityRule')
    expect(panel).toContain('formatInr')
    expect(panel).toContain('Show advanced / alias capabilities')
    expect(panel).not.toContain('land in POLICY-UX-2C')
  })

  it('API helper posts catalogue capability add', () => {
    const api = read('api/creditIntelligence.ts')
    expect(api).toContain('addCatalogueCapabilityRule')
    expect(api).toContain('add-catalogue-capability')
    expect(api).toContain('searchCreditCapabilities')
  })

  it('Rules tab wires documentId and catalogue edit', () => {
    const rules = read('pages/creditIntelligence/CiPolicyRulesTab.tsx')
    expect(rules).toContain('documentId')
    expect(rules).toContain('onCatalogueChanged')
    expect(rules).toContain('businessCapabilityId')
    expect(rules).toMatch(/Existing capability|existing capability|capability catalogue/i)
    expect(rules.includes('Browse / Add Rule') || rules.includes('+ Add rule')).toBe(true)
  })

  it('Studio page applies catalogue session without requiring activation', () => {
    const page = read('pages/creditIntelligence/CiPolicyStudioPage.tsx')
    expect(page).toContain('applyCatalogueSession')
    expect(page).toContain('onCatalogueChanged={applyCatalogueSession}')
    expect(page).toContain('saveLifecycleDraft')
  })
})
