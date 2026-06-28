/**
 * Aligns with `StepExecutionRecordView` from the backend.
 */
export interface StepExecutionRecordView {
  id: string
  applicationId: string
  stepType: string
  status: string
  inputJson: string | null
  outputJson: string | null
  errorCode: string | null
  errorMessage: string | null
  startedAt: string | null
  completedAt: string | null
}
