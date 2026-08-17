import { describe, expect, it } from 'vitest'
import {
  displayAmountRange,
  displayBorrowerType,
  displayIntake,
  displayLoanProduct,
  displayMatchDimension,
  hasAction,
  historyEventLabel,
  isEditableStatus,
  overlapPeerName,
  parseOptionalAmount,
  statusLabel,
  toApiMatchValue,
} from '@/lib/customerCategory/display'
import { userFriendlyMessage } from '@/lib/userFriendlyError'
import { ApiError } from '@/api/http'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

describe('customerCategory display helpers', () => {
  it('shows Any for ANY match dimensions', () => {
    expect(displayMatchDimension('ANY')).toBe('Any')
    expect(displayMatchDimension('any')).toBe('Any')
    expect(displayBorrowerType('ANY')).toBe('Any')
    expect(displayLoanProduct('ANY')).toBe('Any')
    expect(displayIntake('ANY')).toBe('Any')
    expect(displayIntake('BORROWER')).toBe('Borrower')
  })

  it('formats unbounded amount bounds', () => {
    expect(displayAmountRange(null, null)).toContain('Any amount')
    expect(displayAmountRange(null, 1000)).toContain('unbounded below')
    expect(displayAmountRange(500, null)).toContain('unbounded above')
    expect(displayAmountRange(100, 200)).toContain('inclusive')
  })

  it('parses optional amounts', () => {
    expect(parseOptionalAmount('')).toBeNull()
    expect(parseOptionalAmount('  ')).toBeNull()
    expect(parseOptionalAmount('1000')).toBe(1000)
    expect(parseOptionalAmount('1,000')).toBe(1000)
  })

  it('maps lifecycle labels and actions', () => {
    expect(statusLabel('IN_REVIEW')).toBe('In review')
    expect(isEditableStatus('DRAFT')).toBe(true)
    expect(isEditableStatus('ACTIVE')).toBe(false)
    expect(hasAction(['EDIT', 'SUBMIT'], 'SUBMIT')).toBe(true)
    expect(hasAction(['EDIT'], 'ACTIVATE')).toBe(false)
    expect(historyEventLabel('SUBMITTED')).toBe('Submitted for review')
  })

  it('resolves overlap peer names', () => {
    expect(
      overlapPeerName(
        { left: 'A@v1', right: 'B@v1', leftName: 'Alpha', rightName: 'Beta' },
        'A@v1',
      ),
    ).toBe('Beta')
  })

  it('normalizes API match values', () => {
    expect(toApiMatchValue('')).toBe('ANY')
    expect(toApiMatchValue('Any')).toBe('ANY')
    expect(toApiMatchValue('INDIVIDUAL')).toBe('INDIVIDUAL')
  })
})

describe('customer category / policy set typed errors', () => {
  it('maps maker-checker and multi-rule reasons', () => {
    expect(
      userFriendlyMessage(
        new ApiError('denied', 403, null, { reason: 'SELF_APPROVAL_FORBIDDEN' }),
        'fallback',
      ),
    ).toMatch(/cannot approve/i)
    expect(
      userFriendlyMessage(
        new ApiError('multi', 422, null, { reason: 'MULTI_RULE_SET_NOT_ENABLED' }),
        'fallback',
      ),
    ).toMatch(/exactly one underwriting rule set/i)
    expect(
      userFriendlyMessage(
        new ApiError('ps', 422, null, { reason: 'POLICY_SET_NOT_READY' }),
        'fallback',
      ),
    ).toMatch(/Policy Set must be ACTIVE/i)
  })

  it('maps workflow linkage reasons', () => {
    expect(
      userFriendlyMessage(
        new ApiError('wf', 422, null, { reason: 'WORKFLOW_LINKAGE_REQUIRED' }),
        'fallback',
      ),
    ).toMatch(/Workflow linkage required/i)
    expect(
      userFriendlyMessage(
        new ApiError('wf', 422, null, { reason: 'WORKFLOW_SCOPE_INCOMPATIBLE' }),
        'fallback',
      ),
    ).toMatch(/does not match the Category/i)
    expect(
      userFriendlyMessage(
        new ApiError('wf', 404, null, { reason: 'WORKFLOW_NOT_FOUND' }),
        'fallback',
      ),
    ).toMatch(/not found/i)
    expect(
      userFriendlyMessage(
        new ApiError('wf', 422, null, { reason: 'WORKFLOW_VERSION_MUTATED' }),
        'fallback',
      ),
    ).toMatch(/content changed/i)
    expect(
      userFriendlyMessage(
        new ApiError('wf', 422, null, { reason: 'WORKFLOW_NOT_ELIGIBLE' }),
        'fallback',
      ),
    ).toMatch(/not active or eligible/i)
  })
})

describe('customer category admin UI wiring', () => {
  const root = resolve(__dirname, '../..')

  it('registers routes behind AdminConfigGate', () => {
    const app = readFileSync(resolve(root, 'App.tsx'), 'utf8')
    expect(app).toContain('path="customer-categories"')
    expect(app).toContain('path="policy-sets"')
    expect(app).toContain('CustomerCategoriesPage')
    expect(app).toContain('PolicySetsPage')
    expect(app).toContain('AdminConfigGate')
  })

  it('exposes nav entries outside Policy Studio burial', () => {
    const nav = readFileSync(resolve(root, 'nav/workspaceNav.ts'), 'utf8')
    expect(nav).toContain("label: 'Customer Categories'")
    expect(nav).toContain("label: 'Policy Sets'")
    expect(nav).toContain("to: '/customer-categories'")
    expect(nav).toContain("to: '/policy-sets'")
    expect(nav).toContain('administrationNavGroups')
    expect(nav).toContain('hideLegacyDecisionConfigFromLenderNav')
    expect(nav).toContain("'/underwriting-rules'")
    // Client lender surface filters these; catalogue still lists them for Internal
    expect(nav).toContain('CLIENT_HIDDEN_ADMIN_PATHS')
  })

  it('Client surface hides Live UW Rules and Policy Sets from lender nav', () => {
    const rt = readFileSync(resolve(root, 'lib/runtimeEnv.ts'), 'utf8')
    expect(rt).toContain('isClientLenderSurface')
    expect(rt).toContain('hideLegacyDecisionConfigFromLenderNav')
    const admin = readFileSync(resolve(root, 'pages/AdministrationPage.tsx'), 'utf8')
    expect(admin).toContain('CLIENT_HIDDEN_ADMIN_PATHS')
    expect(admin).toContain("'/underwriting-rules'")
    expect(admin).toContain("'/policy-sets'")
    expect(admin).toContain('hideLegacyDecisionConfigFromLenderNav')
    expect(admin).toContain('Policy is the lender-facing underwriting authority')
  })

  it('pages export components and cover required UX', () => {
    const cat = readFileSync(resolve(root, 'pages/CustomerCategoriesPage.tsx'), 'utf8')
    const ps = readFileSync(resolve(root, 'pages/PolicySetsPage.tsx'), 'utf8')
    expect(cat).toContain('export function CustomerCategoriesPage')
    expect(cat).toContain('No Customer Categories configured yet')
    expect(cat).toContain('customerCategoryActivationReadiness')
    expect(cat).toContain('overlapWarnings')
    expect(cat).toContain('allowedActions')
    expect(cat).toContain('Copy')
    expect(cat).toContain('read-only')
    expect(cat).not.toContain('seed/day1')
    expect(cat).toContain('Entity Type')
    expect(cat).toContain('Customer Role')
    expect(cat).not.toContain('label="Borrower type"')
    expect(cat).not.toContain('label="Intake segment"')
    expect(cat).toContain('entityType')
    expect(cat).toContain('customerRole')
    expect(cat).toContain('Policy Version')
    expect(cat).toContain('Workflow Version')
    expect(cat).toContain('POLICY LINKAGE REQUIRED')
    expect(cat).toContain('WORKFLOW LINKAGE REQUIRED')
    expect(cat).toContain('listEligiblePolicies')
    expect(cat).toContain('eligibleForCategoryLinkage')
    expect(cat).toContain("compatibilityStatus !== 'INCOMPATIBLE'")
    expect(cat).toContain('onSubmit')
    expect(cat).toContain('policyApplicabilityId')
    expect(cat).toContain('Select a Policy Version before submitting.')
    expect(cat).toContain('pendingPolicySelection')
    expect(cat).not.toContain("selected?.policyLinkageStatus === 'POLICY_LINKAGE_REQUIRED' || !policyApplicabilityId")
    expect(cat).toContain('NOT ELIGIBLE')
    expect(cat).toContain('listEligibleWorkflows')
    expect(cat).toContain('COMPATIBLE')
    expect(cat).toContain('Show incompatible')
    expect(cat).toContain('Category → Policy Version + Workflow Version')
    expect(cat).toContain('Also eligible propositions')
    expect(cat).not.toContain('label="Policy Set"')
    expect(cat).not.toContain('Select a Policy Set')
    expect(ps).toContain('export function PolicySetsPage')
    expect(ps).toContain('No Policy Sets configured yet')
    expect(ps).toContain('listEligibleRuleSets')
    expect(ps).toContain('listEligibleScorecards')
    expect(ps).not.toMatch(/additionalRuleSetIds/)
    expect(ps).toContain('Category → Policy Set')
  })

  it('api clients exist for governance endpoints', () => {
    const cc = readFileSync(resolve(root, 'api/customerCategories.ts'), 'utf8')
    const ps = readFileSync(resolve(root, 'api/policySets.ts'), 'utf8')
    expect(cc).toContain('/customer-categories')
    expect(cc).toContain('eligible-policies')
    expect(cc).toContain('eligibleForCategoryLinkage')
    expect(cc).toContain('lifecycleAuthority')
    expect(cc).toContain('linkageOwnerType')
    expect(cc).toContain('eligible-workflows')
    expect(cc).toContain('listEligibleWorkflows')
    expect(cc).toContain('compatibilityStatus')
    expect(cc).toContain('scopeSummary')
    expect(cc).toContain('journeyStepSummary')
    expect(cc).toContain('policy-scope-compatibility-report')
    expect(cc).toContain('policyApplicabilityId?: string | null')
    expect(cc).toContain('policyDocumentId')
    expect(cc).toContain('policyVersionLabel')
    expect(cc).toContain('policyLinkageStatus')
    expect(cc).toContain('workflowId')
    expect(cc).toContain('workflowVersion')
    expect(cc).toContain('workflowContentHash')
    expect(cc).toContain('workflowName')
    expect(cc).toContain('workflowLinkageStatus')
    expect(cc).toContain('EligibleWorkflow')
    expect(cc).toContain('eligible-rule-sets')
    expect(cc).toContain('eligible-scorecards')
    expect(cc).toContain('entityType')
    expect(cc).toContain('customerRole')
    expect(ps).toContain('/policy-sets')
    expect(ps).toContain('activation-readiness')
  })
})
