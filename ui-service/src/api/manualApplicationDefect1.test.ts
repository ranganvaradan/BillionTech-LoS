import { describe, expect, it } from 'vitest'
import { ApiError } from './http'
import { messageForDocumentUpload, validateIdentityDocumentFile } from './documentUploadError'
import { businessKycOutcomeLabel, businessKycStepLabel, messageFromKycRunOutput } from './kycErrorMessage'

describe('document upload errors', () => {
  it('maps storage unavailable reason', () => {
    const err = new ApiError('x', 422, {}, {
      reason: 'DOCUMENT_STORAGE_UNAVAILABLE',
      serverMessage: 'Document storage unavailable. Please try again or contact support.',
    })
    expect(messageForDocumentUpload(err)).toContain('Document storage unavailable')
  })

  it('maps unsupported type and oversize client validation', () => {
    expect(validateIdentityDocumentFile(new File(['x'], 'a.exe'))).toContain('Unsupported')
    const big = new File([new Uint8Array(13 * 1024 * 1024)], 'a.pdf', { type: 'application/pdf' })
    expect(validateIdentityDocumentFile(big)).toContain('exceeds')
    expect(validateIdentityDocumentFile(new File(['x'], 'pan.pdf', { type: 'application/pdf' }))).toBeNull()
  })

  it('PAN_CARD document type is the canonical upload value', () => {
    expect('PAN_CARD').toBe('PAN_CARD')
  })
})

describe('KYC mobile messaging', () => {
  it('does not present missing mobileNumber mapping as borrower wrong-number', () => {
    const msg = messageFromKycRunOutput({
      allPassed: false,
      results: [
        {
          stepType: 'MOBILE_OTP',
          outcome: 'FAILURE',
          provider: 'AUTHBRIDGE',
          errorMessage: 'Mobile number is required for verification',
        },
      ],
    })
    expect(msg).toContain('Mobile verification could not be completed')
    expect(msg?.toLowerCase()).not.toContain('invalid mobile number')
  })

  it('uses business labels for steps and outcomes', () => {
    expect(businessKycStepLabel('MOBILE_OTP')).toBe('Mobile Verification')
    expect(businessKycStepLabel('PAN_VERIFY')).toBe('PAN Verification')
    expect(businessKycOutcomeLabel('SUCCESS')).toBe('Verified')
    expect(businessKycOutcomeLabel('FAILURE')).toBe('Could not be completed')
  })
})
