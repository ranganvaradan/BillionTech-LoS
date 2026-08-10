import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const root = resolve(__dirname, '../..')

function read(rel: string): string {
  return readFileSync(resolve(root, rel), 'utf8')
}

describe('POLICY-UX-2D ingestion binding UX', () => {
  it('Rules tab shows ingestion summary and safe Accept all ready', () => {
    const rules = read('pages/creditIntelligence/CiPolicyRulesTab.tsx')
    expect(rules).toContain('ingestionBinding')
    expect(rules).toContain('existing automated')
    expect(rules).toContain('acceptAllEligible')
    expect(rules).toContain('Documents')
    expect(rules).toContain('Portfolio Controls')
    expect(rules).toContain('Servicing')
    expect(rules).toContain('high-confidence existing-capability')
  })

  it('Studio page wires ingestionBinding into Rules tab', () => {
    const page = read('pages/creditIntelligence/CiPolicyStudioPage.tsx')
    expect(page).toContain('ingestionBinding={asRecord(asRecord(session).ingestionBinding)}')
    expect(page).toContain("setTab('rules')")
  })
})
