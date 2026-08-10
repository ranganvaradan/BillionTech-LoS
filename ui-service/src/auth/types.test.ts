import { describe, expect, it } from 'vitest'
import {
  canAccessAdminConfigNav,
  canAccessCamActions,
  canAccessSanction,
  canCreateOrNotifyBorrowerIntake,
  canHandOffToCo,
  canRunKycFlow,
  canRunUnderwriting,
  canSendBackToBorrower,
  canSendBackToRm,
  isBorrowerRole,
  isCamCheckerRole,
  isCamEditorRole,
  isCamMakerRole,
  isL2SanctionRole,
  isRelationshipManager,
} from './types'

describe('canAccessAdminConfigNav', () => {
  it('allows Administrator and Credit Manager', () => {
    expect(canAccessAdminConfigNav('ADMINISTRATOR')).toBe(true)
    expect(canAccessAdminConfigNav('CREDIT_MANAGER')).toBe(true)
  })
  it('hides for other staff roles', () => {
    expect(canAccessAdminConfigNav('CREDIT_OFFICER')).toBe(false)
    expect(canAccessAdminConfigNav('SALES_OFFICER')).toBe(false)
    expect(canAccessAdminConfigNav('BORROWER')).toBe(false)
  })
})

describe('canCreateOrNotifyBorrowerIntake', () => {
  it('allows RM and admins', () => {
    expect(canCreateOrNotifyBorrowerIntake('RELATIONSHIP_MANAGER')).toBe(true)
    expect(canCreateOrNotifyBorrowerIntake('ADMIN')).toBe(true)
    expect(canCreateOrNotifyBorrowerIntake('ADMINISTRATOR')).toBe(true)
  })
  it('blocks credit officer', () => {
    expect(canCreateOrNotifyBorrowerIntake('CREDIT_OFFICER')).toBe(false)
  })
})

describe('RM / CO review helpers', () => {
  it('hand off and send back to borrower are RM/admin', () => {
    expect(canHandOffToCo('RELATIONSHIP_MANAGER')).toBe(true)
    expect(canSendBackToBorrower('ADMIN')).toBe(true)
    expect(canHandOffToCo('CREDIT_OFFICER')).toBe(false)
    expect(canSendBackToBorrower('CREDIT_OFFICER')).toBe(false)
  })
  it('send back to RM is CO/admin', () => {
    expect(canSendBackToRm('CREDIT_OFFICER')).toBe(true)
    expect(canSendBackToRm('RELATIONSHIP_MANAGER')).toBe(false)
  })
})

describe('isBorrowerRole', () => {
  it('matches borrower', () => {
    expect(isBorrowerRole('BORROWER')).toBe(true)
    expect(isBorrowerRole('CREDIT_OFFICER')).toBe(false)
  })
})

describe('CAM / RM role gates', () => {
  it('CM is editor and checker but not maker', () => {
    expect(isCamMakerRole('CREDIT_MANAGER')).toBe(false)
    expect(isCamEditorRole('CREDIT_MANAGER')).toBe(true)
    expect(isCamCheckerRole('CREDIT_MANAGER')).toBe(true)
  })
  it('CO is maker and editor but not L2 sanction', () => {
    expect(isCamMakerRole('CREDIT_OFFICER')).toBe(true)
    expect(isCamEditorRole('CREDIT_OFFICER')).toBe(true)
    expect(isL2SanctionRole('CREDIT_OFFICER')).toBe(false)
    expect(canAccessSanction('CREDIT_OFFICER')).toBe(false)
  })
  it('RM cannot run KYC/UW/CAM/sanction', () => {
    expect(isRelationshipManager('RELATIONSHIP_MANAGER')).toBe(true)
    expect(canRunKycFlow('RELATIONSHIP_MANAGER')).toBe(false)
    expect(canRunUnderwriting('RELATIONSHIP_MANAGER')).toBe(false)
    expect(canAccessCamActions('RELATIONSHIP_MANAGER')).toBe(false)
    expect(canAccessSanction('RELATIONSHIP_MANAGER')).toBe(false)
  })
  it('CM can sanction; CO cannot', () => {
    expect(canAccessSanction('CREDIT_MANAGER')).toBe(true)
    expect(canAccessSanction('ADMIN')).toBe(true)
  })
})
