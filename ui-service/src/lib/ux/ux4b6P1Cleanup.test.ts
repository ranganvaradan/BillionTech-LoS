import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { canAccessAdminConfigNav, canCreateOrNotifyBorrowerIntake } from '@/auth/types'
import {
  collateralOverviewCopy,
  kycWorkbenchSubSections,
  PROSPECT_DEMO_HIDDEN_TAB_IDS,
  PROSPECT_DEMO_VISIBLE_TAB_IDS,
  visibleWorkbenchTabs,
} from '@/lib/applicationWorkbench'
import { showStagingDemoNav } from '@/nav/workspaceNav'

const uiSrc = join(dirname(fileURLToPath(import.meta.url)), '../..')

describe('UX-4B6 P1 cleanup', () => {
  it('P1-1: RM collateral copy never points at Credit Assessment', () => {
    const withIntake = collateralOverviewCopy({
      role: 'RELATIONSHIP_MANAGER',
      hasCollateralIntake: true,
      canContinueIntake: false,
    })
    expect(withIntake.body.toLowerCase()).not.toContain('credit assessment')
    expect(withIntake.snapshotHint).toBeNull()
    expect(withIntake.body).toContain('application summary')

    const withoutIntake = collateralOverviewCopy({
      role: 'RELATIONSHIP_MANAGER',
      hasCollateralIntake: false,
      canContinueIntake: true,
    })
    expect(withoutIntake.body.toLowerCase()).not.toContain('credit assessment')
    expect(withoutIntake.suggestContinueIntake).toBe(true)

    const co = collateralOverviewCopy({
      role: 'CREDIT_OFFICER',
      hasCollateralIntake: true,
      canContinueIntake: false,
    })
    expect(co.body).toContain('Credit Assessment')
    expect(co.snapshotHint).toContain('Credit Assessment')
  })

  it('P1-2: GST is discoverable as Business Verification within KYC', () => {
    const labels = kycWorkbenchSubSections({ vkycVisible: true, bankingVisible: true }).map((s) => s.label)
    expect(labels).toContain('Business Verification')
    expect(labels).not.toContain('Verification Details')
    expect(labels[0]).toBe('Identity Verification')
    expect(labels).toEqual([
      'Identity Verification',
      'Video / Physical KYC',
      'Banking Evidence',
      'Business Verification',
    ])
  })

  it('P1-3: SALES_OFFICER is not authorised to create — do not surface sales create CTA', () => {
    expect(canCreateOrNotifyBorrowerIntake('SALES_OFFICER')).toBe(false)
    expect(canCreateOrNotifyBorrowerIntake('RELATIONSHIP_MANAGER')).toBe(true)
    // Route /sales/applications/new remains for RM/Admin SALES_ASSISTED mode only.
  })

  it('P1-4: P2 Validation and Staging Readiness routes use AdminConfigGate', () => {
    const appSrc = readFileSync(join(uiSrc, 'App.tsx'), 'utf8')
    expect(appSrc).toMatch(
      /path="credit-intelligence\/p2-validation"[\s\S]*?<AdminConfigGate>[\s\S]*?CiP2ValidationDashboardPage/,
    )
    expect(appSrc).toMatch(
      /path="credit-intelligence\/validation"[\s\S]*?<AdminConfigGate>[\s\S]*?CiValidationPage/,
    )
    expect(canAccessAdminConfigNav('RELATIONSHIP_MANAGER')).toBe(false)
    expect(canAccessAdminConfigNav('CREDIT_OFFICER')).toBe(false)
    expect(canAccessAdminConfigNav('OPERATIONS')).toBe(false)
    expect(canAccessAdminConfigNav('KYC_REVIEWER')).toBe(false)
    expect(canAccessAdminConfigNav('ADMINISTRATOR')).toBe(true)
    expect(canAccessAdminConfigNav('CREDIT_MANAGER')).toBe(true)
  })

  it('P1-5: Credit Manager primary tabs; Advanced holds technical surfaces', () => {
    expect(PROSPECT_DEMO_VISIBLE_TAB_IDS).toEqual([
      'scope',
      'rules',
      'simulation',
      'lifecycle',
      'overview',
    ])
    expect([...PROSPECT_DEMO_HIDDEN_TAB_IDS]).toEqual([
      'kyc-eligibility',
      'structure',
      'ambiguities',
      'data-readiness',
      'tests',
      'approvals',
    ])
    const studioSrc = readFileSync(join(uiSrc, 'pages/creditIntelligence/CiPolicyStudioPage.tsx'), 'utf8')
    expect(studioSrc).toContain('Show full policy workspace')
    expect(studioSrc).toContain('Demo view')
    expect(studioSrc).toContain('PROSPECT_DEMO_VISIBLE_TAB_IDS')
    expect(studioSrc).toContain('Save Draft')
    expect(studioSrc).toContain("setTab('rules')")
    expect(studioSrc).toContain('Advanced')
  })

  it('workbench role visibility unchanged (six-tab model; RM hides Credit Assessment)', () => {
    const rm = visibleWorkbenchTabs({ role: 'RELATIONSHIP_MANAGER' }).map((t) => t.id)
    const co = visibleWorkbenchTabs({ role: 'CREDIT_OFFICER' }).map((t) => t.id)
    expect(rm).toEqual(['overview', 'kyc', 'decision', 'documents', 'history'])
    expect(co).toEqual(['overview', 'kyc', 'credit', 'decision', 'documents', 'history'])
  })

  it('primary navigation definitions unchanged', () => {
    const layout = readFileSync(join(uiSrc, 'layouts/MainLayout.tsx'), 'utf8')
    expect(layout).toContain("label: 'Dashboard'")
    expect(layout).toContain("label: 'Applications'")
    expect(layout).toContain("label: 'Programs'")
    expect(layout).toContain("label: 'Settlements'")
    expect(layout).toContain("label: 'Policies'")
    expect(layout).toContain("label: 'Reports'")
    expect(layout).toContain("label: 'Administration'")
    expect(typeof showStagingDemoNav()).toBe('boolean')
  })

  it('Simulation helper clarifies risk / offer / decision coverage (copy only)', () => {
    const sim = readFileSync(join(uiSrc, 'pages/creditIntelligence/CiPolicySimulationTab.tsx'), 'utf8')
    expect(sim).toContain('risk/score')
    expect(sim).toContain('eligible amount')
    expect(sim).toContain('final recommendation')
  })
})
