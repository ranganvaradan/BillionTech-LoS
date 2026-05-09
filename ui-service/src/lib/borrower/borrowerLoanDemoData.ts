/**
 * Demo / placeholder post-disbursement data shapes. Live data is served from
 * GET /api/v1/borrower/loans/{loanId}/... (demo schedule on the server).
 * This module keeps types and optional mock rows isolated for UI and tests
 * if the API is extended or for offline test doubles.
 */
export type BorrowerRepaymentScheduleRow = {
  installmentNo: number
  dueDate: string
  emi: number
  principal: number
  interest: number
  outstandingPrincipal: number
}

export type BorrowerStatementLine = {
  valueDate: string
  description: string
  credit: number | null
  debit: number | null
  balance: number | null
}

export type BorrowerTransactionLine = {
  postedAt: string
  description: string
  reference: string
  amount: number
  type: string
}

export const BORROWER_LOAN_DEMO_SOURCE_NOTE =
  'Post-disbursement numbers shown here are demo data from the borrower API until a live LMS link is available.'

/** Example rows for unit tests and Storybook-style scenarios only — not used as production fallback. */
export function mockRepaymentRows(seed: string): BorrowerRepaymentScheduleRow[] {
  const n = (seed || '0').length % 3
  return [
    {
      installmentNo: 1,
      dueDate: '2026-05-15',
      emi: 12000 + n * 100,
      principal: 8000,
      interest: 4000 + n * 100,
      outstandingPrincipal: 420000 - n * 1000,
    },
  ]
}
