/**
 * Aligns with backend `DocumentResponse`.
 */
export interface DocumentResponse {
  id: string
  applicationId: string
  documentType: string
  kycStepType: string | null
  fileName: string
  contentType: string
  fileSize: number
  createdAt: string | null
}
