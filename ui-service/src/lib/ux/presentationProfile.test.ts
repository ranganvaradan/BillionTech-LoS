import { describe, expect, it } from 'vitest'
import {
  defaultKycSubSection,
  destructiveActionClassName,
  formatRoleLabel,
  reportsNavVisibleForRole,
  resolvePresentationProfile,
} from './presentationProfile'
import { formatStatusLabel } from '@/lib/dashboardLabels'
import { formatAssessmentOutcome } from '@/lib/credit/assessmentPresentation'
import {
  defaultWorkbenchTab,
  showCamInDecision,
  visibleWorkbenchTabs,
} from '@/lib/applicationWorkbench'
import { showStagingDemoNav } from '@/nav/workspaceNav'
import type { ApplicationResponse } from '@/types/application'

function app(status: string): ApplicationResponse {
  return { id: 'a1', status, loanProduct: 'PERSONAL_LOAN' } as ApplicationResponse
}

describe('presentationProfile', () => {
  it('formats role labels for header', () => {
    expect(formatRoleLabel('RELATIONSHIP_MANAGER')).toBe('Relationship Manager')
    expect(formatRoleLabel('CREDIT_OFFICER')).toBe('Credit Officer')
    expect(formatRoleLabel('VKYC_MANAGER')).toBe('VKYC Manager')
  })

  it('role-specific primary nav visibility (Reports/Admin gate unchanged)', () => {
    expect(reportsNavVisibleForRole('ADMINISTRATOR')).toBe(true)
    expect(reportsNavVisibleForRole('CREDIT_MANAGER')).toBe(true)
    expect(reportsNavVisibleForRole('CREDIT_OFFICER')).toBe(false)
    expect(reportsNavVisibleForRole('RELATIONSHIP_MANAGER')).toBe(false)
    expect(reportsNavVisibleForRole('RISK_MANAGER')).toBe(false)
    expect(reportsNavVisibleForRole('ACCOUNTS')).toBe(false)
  })

  it('RM workbench hides Credit Assessment; Decision still visible', () => {
    const tabs = visibleWorkbenchTabs({ role: 'RELATIONSHIP_MANAGER' })
    expect(tabs.some((t) => t.id === 'credit')).toBe(false)
    expect(tabs.some((t) => t.id === 'decision')).toBe(true)
    expect(showCamInDecision({ role: 'RELATIONSHIP_MANAGER', app: app('CAM_READY') })).toBe(false)
    expect(resolvePresentationProfile('RELATIONSHIP_MANAGER').emphasiseNewApplication).toBe(true)
  })

  it('CO stage defaults: KYC → kyc, UW → credit, CAM → decision', () => {
    const tabs = visibleWorkbenchTabs({ role: 'CREDIT_OFFICER' }).map((t) => t.id)
    expect(defaultWorkbenchTab('KYC_IN_PROGRESS', tabs)).toBe('kyc')
    expect(defaultWorkbenchTab('UNDERWRITING', tabs)).toBe('credit')
    expect(defaultWorkbenchTab('CAM_READY', tabs)).toBe('decision')
    expect(resolvePresentationProfile('CREDIT_OFFICER').persona).toBe('credit_officer')
  })

  it('CM Decision defaults and persona', () => {
    const tabs = visibleWorkbenchTabs({ role: 'CREDIT_MANAGER' }).map((t) => t.id)
    expect(defaultWorkbenchTab('SANCTION_PENDING', tabs)).toBe('decision')
    expect(defaultWorkbenchTab('CAM_REVIEWED', tabs)).toBe('decision')
    expect(resolvePresentationProfile('CREDIT_MANAGER').persona).toBe('credit_manager')
  })

  it('Ops/KYC defaults prefer KYC landing and VKYC sub for VKYC manager', () => {
    expect(resolvePresentationProfile('OPERATIONS').preferOpsKycLanding).toBe(true)
    expect(defaultKycSubSection({ role: 'OPERATIONS', vkycVisible: true })).toBe('identity')
    expect(defaultKycSubSection({ role: 'VKYC_MANAGER', vkycVisible: true })).toBe('vkyc')
    expect(defaultKycSubSection({ role: 'VKYC_MANAGER', vkycVisible: false })).toBe('identity')
  })

  it('Admin nav persona retains admin focus', () => {
    expect(resolvePresentationProfile('ADMINISTRATOR').persona).toBe('admin')
    expect(reportsNavVisibleForRole('ADMINISTRATOR')).toBe(true)
  })

  it('terminal stage defaults land on overview', () => {
    const tabs = visibleWorkbenchTabs({ role: 'CREDIT_OFFICER' }).map((t) => t.id)
    expect(defaultWorkbenchTab('DISBURSED', tabs)).toBe('overview')
    expect(defaultWorkbenchTab('REJECTED', tabs)).toBe('overview')
    expect(defaultWorkbenchTab('WITHDRAWN', tabs)).toBe('overview')
  })

  it('status-label mappings prefer business language', () => {
    expect(formatStatusLabel('UNDERWRITING')).toBe('In review')
    expect(formatStatusLabel('REJECTED')).toBe('Declined')
    expect(formatStatusLabel('DISBURSED')).toBe('Completed')
    expect(formatAssessmentOutcome('MANUAL_REVIEW')).toBe('Manual Credit Review')
    expect(formatAssessmentOutcome('REJECT')).toBe('Declined')
    expect(formatAssessmentOutcome('DATA_INSUFFICIENT')).toBe('Missing Information')
  })

  it('destructive action helper avoids primary class', () => {
    expect(destructiveActionClassName()).not.toContain('bt-btn-primary')
    expect(destructiveActionClassName()).toContain('bt-btn-danger')
  })

  it('staging demo isolation helper is boolean (env-driven)', () => {
    expect(typeof showStagingDemoNav()).toBe('boolean')
  })
})
