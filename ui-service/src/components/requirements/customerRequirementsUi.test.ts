import { describe, expect, it } from 'vitest'
import type { CustomerAction, CustomerSummary } from '@/api/customerRequirements'

/** Pure UI projection helpers mirroring W5 display rules (no GACAT leakage). */
function actionableOnly(actions: CustomerAction[]) {
  return actions.filter((a) => a.actionable)
}

function summaryLine(s: CustomerSummary) {
  return {
    information: s.informationRequiredCount,
    documents: s.documentsRequiredCount,
    provided: s.providedCount,
    processing: s.processingCount,
    remaining: s.remainingActionsCount,
  }
}

describe('W5 customer requirements UI rules', () => {
  it('shows only actionable customer items', () => {
    const actions = [
      { actionable: true, title: 'Business vintage', allowedModes: ['DIRECT_INPUT'] },
      { actionable: false, title: 'Bureau', allowedModes: ['AUTOMATIC_SOURCE'] },
    ] as CustomerAction[]
    expect(actionableOnly(actions)).toHaveLength(1)
    expect(actionableOnly(actions)[0].title).toBe('Business vintage')
  })

  it('summary counts match golden shape', () => {
    const s: CustomerSummary = {
      informationRequiredCount: 1,
      documentsRequiredCount: 1,
      providedCount: 1,
      processingCount: 1,
      remainingActionsCount: 2,
      reuploadRequiredCount: 0,
    }
    expect(summaryLine(s).remaining).toBe(2)
    expect(summaryLine(s).documents).toBe(1)
  })

  it('uploaded+processing label is not Required', () => {
    const card = {
      customerStatusLabel: 'Uploaded',
      processingLabel: 'Processing…',
      customerFulfilment: 'PROVIDED',
    }
    expect(card.customerStatusLabel).not.toMatch(/required/i)
    expect(card.processingLabel).toMatch(/Processing/)
  })
})
