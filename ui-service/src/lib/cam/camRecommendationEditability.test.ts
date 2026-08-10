import { describe, expect, it } from 'vitest'
import { isCamCheckerRole, isCamEditorRole, isCamMakerRole } from '@/auth/types'

/**
 * Documents CAM recommendation editability rules used by CamSection.
 * Locked when camStatus === APPROVED; editable for CAM editor roles otherwise.
 */
function camFieldsEditable(role: string, camStatus: string): boolean {
  const isLocked = camStatus === 'APPROVED'
  return isCamEditorRole(role) && !isLocked
}

function canReturnApprovedCamForRevision(role: string, camStatus: string): boolean {
  return isCamCheckerRole(role) && camStatus === 'APPROVED'
}

describe('CAM recommendation editability', () => {
  it('credit officer can edit rate while DRAFT / SENT_BACK', () => {
    expect(isCamMakerRole('CREDIT_OFFICER')).toBe(true)
    expect(camFieldsEditable('CREDIT_OFFICER', 'DRAFT')).toBe(true)
    expect(camFieldsEditable('CREDIT_OFFICER', 'SENT_BACK')).toBe(true)
  })

  it('approved CAM locks recommendation fields including rate', () => {
    expect(camFieldsEditable('CREDIT_OFFICER', 'APPROVED')).toBe(false)
    expect(camFieldsEditable('ADMINISTRATOR', 'APPROVED')).toBe(false)
  })

  it('credit manager can return approved CAM for terms revision', () => {
    expect(canReturnApprovedCamForRevision('CREDIT_MANAGER', 'APPROVED')).toBe(true)
    expect(canReturnApprovedCamForRevision('CREDIT_OFFICER', 'APPROVED')).toBe(false)
  })

  it('sanction basis validation rejects blank / negative rate', () => {
    const validate = (rate: string) => {
      const n = Number.parseFloat(rate)
      return !rate.trim() || !Number.isFinite(n) || n < 0
        ? 'Proposed rate (% p.a.)'
        : null
    }
    expect(validate('')).toBe('Proposed rate (% p.a.)')
    expect(validate('abc')).toBe('Proposed rate (% p.a.)')
    expect(validate('-1')).toBe('Proposed rate (% p.a.)')
    expect(validate('14.5')).toBeNull()
  })
})
