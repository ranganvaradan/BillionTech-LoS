import { http } from './http'
import type { StepExecutionRecordView } from '@/types/stepExecution'

export async function listStepExecutions(applicationId: string): Promise<StepExecutionRecordView[]> {
  const { data } = await http.get<StepExecutionRecordView[]>(
    `/applications/${applicationId}/step-executions`,
  )
  return data
}
