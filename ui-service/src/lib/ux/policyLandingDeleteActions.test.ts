import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  DELETE_UNAVAILABLE_LINEAGE_HINT,
  EXAMPLE_BADGE_HINT,
  EXAMPLE_DELETE_CONFIRM_NOTE,
  EXAMPLE_DELETE_SUCCESS_MSG,
} from '@/pages/creditIntelligence/CiCreditPoliciesLanding'

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

  it('uses distinct EXAMPLE reseed messaging (not lineage denial)', () => {
    const landing = readFileSync(landingPath, 'utf8')
    expect(landing).toContain('EXAMPLE_DELETE_CONFIRM_NOTE')
    expect(landing).toContain('EXAMPLE_BADGE_HINT')
    expect(landing).toContain('policy-example-badge-')
    expect(landing).toContain('policy-landing-success')
    expect(landing).toContain('Delete example session')
    const page = readFileSync(pagePath, 'utf8')
    expect(page).toContain('successMsg={demoMsg}')
    expect(page).toContain('EXAMPLE_DELETE_SUCCESS_MSG')
    expect(EXAMPLE_BADGE_HINT).toMatch(/Reseeded fresh/i)
    expect(EXAMPLE_DELETE_CONFIRM_NOTE).toMatch(/Examples & templates/i)
    expect(EXAMPLE_DELETE_SUCCESS_MSG).toBe(
      'Example session removed. Reopening it from Examples & templates will create a new draft.',
    )
    expect(EXAMPLE_DELETE_SUCCESS_MSG).not.toMatch(/ACTIVE\/APPROVED|Delete unavailable/)
    expect(EXAMPLE_DELETE_CONFIRM_NOTE).not.toMatch(/ACTIVE\/APPROVED/)
    expect(DELETE_UNAVAILABLE_LINEAGE_HINT).not.toEqual(EXAMPLE_DELETE_SUCCESS_MSG)
  })

  it('scopes delete/retire/open busy to rowBusyId on the studio page', () => {
    const page = readFileSync(pagePath, 'utf8')
    expect(page).toContain('setRowBusyId')
    expect(page).toContain('rowBusyId={rowBusyId}')
    expect(page).toContain('formatLifecycleActionError')
    const deleteFn = page.match(
      /const deleteDraft = async \(documentId: string, opts\?: \{ demo\?: boolean \}\) => \{[\s\S]*?\n  \}/,
    )?.[0]
    expect(deleteFn).toBeTruthy()
    expect(deleteFn).toContain('setRowBusyId(documentId)')
    expect(deleteFn).not.toContain('setBusy(true)')
  })
})

