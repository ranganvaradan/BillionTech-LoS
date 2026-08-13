import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { DELETE_UNAVAILABLE_LINEAGE_HINT } from '@/pages/creditIntelligence/CiCreditPoliciesLanding'

const landingPath = join(
  __dirname,
  '../../pages/creditIntelligence/CiCreditPoliciesLanding.tsx',
)
const pagePath = join(__dirname, '../../pages/creditIntelligence/CiPolicyStudioPage.tsx')

describe('Policy landing delete actions (availableActions + row busy)', () => {
  it('gates Delete on availableActions only (no DRAFT status override)', () => {
    const landing = readFileSync(landingPath, 'utf8')
    expect(landing).toContain("const showDelete = actions.includes('DELETE')")
    expect(landing).not.toContain("actions.includes('DELETE') || status === 'DRAFT'")
    expect(landing).toContain('DELETE_UNAVAILABLE_LINEAGE_HINT')
    expect(landing).toContain('policy-delete-unavailable-')
    expect(landing).toContain('rowBusyId')
  })

  it('exposes a clear lineage delete-unavailable hint', () => {
    expect(DELETE_UNAVAILABLE_LINEAGE_HINT).toMatch(/ACTIVE\/APPROVED/i)
    expect(DELETE_UNAVAILABLE_LINEAGE_HINT).toMatch(/Delete unavailable/i)
  })

  it('scopes delete/retire/open busy to rowBusyId on the studio page', () => {
    const page = readFileSync(pagePath, 'utf8')
    expect(page).toContain('setRowBusyId')
    expect(page).toContain('rowBusyId={rowBusyId}')
    expect(page).toContain('formatLifecycleActionError')
    const deleteFn = page.match(
      /const deleteDraft = async \(documentId: string\) => \{[\s\S]*?\n  \}/,
    )?.[0]
    expect(deleteFn).toBeTruthy()
    expect(deleteFn).toContain('setRowBusyId(documentId)')
    expect(deleteFn).not.toContain('setBusy(true)')
  })
})

