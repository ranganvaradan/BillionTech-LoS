import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { http } from './http'
import { deleteBorrowerDraftApplication, getBorrowerApplicationDetail, getBorrowerDashboard } from './borrowerPortal'

vi.mock('./http', () => ({
  http: { get: vi.fn(), delete: vi.fn() },
}))

describe('borrower portal client', () => {
  const mockGet = vi.mocked(http.get)
  const mockDelete = vi.mocked(http.delete)
  beforeEach(() => {
    mockGet.mockReset()
    mockDelete.mockReset()
  })
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('dashboard does not require scorecard in response', async () => {
    mockGet.mockResolvedValue({
      data: {
        fullName: 'A',
        email: 'a@b.com',
        mobile: '999',
        activeLoanCount: 0,
        draftOrOpenApplicationCount: 0,
        hasIncompleteDraftHint: false,
        recentApplications: [],
        secondLoanWarning: null,
        primaryDisbursedApplicationId: null,
      },
    })
    const d = await getBorrowerDashboard()
    expect(d).not.toHaveProperty('bureauScore')
    expect(d.fullName).toBe('A')
  })

  it('detail has timeline and no internal code fields', async () => {
    mockGet.mockResolvedValue({
      data: {
        applicationId: '6ba7b810-9dad-11d1-80b4-00c04fd430c8',
        applicationNumber: 'LOS-1',
        customerName: 'T',
        product: 'PL',
        status: 'KYC_IN_PROGRESS',
        friendlyStatusHeadline: 'Verification in progress',
        currentStageMessage: 'm',
        estimatedProcessingHint: 'h',
        requiredActions: [],
        kycStatus: 'a',
        documentStatus: 'b',
        sanctionStatus: 'c',
        kfsStatus: 'd',
        eSignStatus: 'e',
        disbursementStatus: 'f',
        rejectionMessage: null,
        reapplyVisible: false,
        disbursedAmount: null,
        disbursedAt: null,
        disbursementAccountMask: '****',
        loanAccountNumber: 'LN1',
        timeline: [
          { id: '1', label: 'Application submitted', state: 'completed', description: 'd', completedAt: null },
        ],
        collateralSummary: [],
      },
    })
    const x = await getBorrowerApplicationDetail('6ba7b810-9dad-11d1-80b4-00c04fd430c8')
    expect(x.timeline[0]!.state).toBe('completed')
    expect(x).not.toHaveProperty('bureauScore')
  })

  it('delete draft calls correct path', async () => {
    mockDelete.mockResolvedValue({ data: null })
    await deleteBorrowerDraftApplication('6ba7b810-9dad-11d1-80b4-00c04fd430c8')
    expect(mockDelete).toHaveBeenCalledWith('/borrower/applications/6ba7b810-9dad-11d1-80b4-00c04fd430c8/draft')
  })
})
