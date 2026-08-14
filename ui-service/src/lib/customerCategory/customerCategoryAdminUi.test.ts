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
    expect(cc).toContain('eligible-rule-sets')
    expect(cc).toContain('eligible-scorecards')
    expect(ps).toContain('/policy-sets')
    expect(ps).toContain('activation-readiness')
  })
})
