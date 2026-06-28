import { http } from '@/api/http'

export type WorkflowEventTemplateMappingDto = {
  id: string
  workflowEvent: string
  channel: string
  templateCode: string
  defaultMapping: boolean
  active: boolean
  sortOrder: number
}

export async function listWorkflowEventTemplateMappings(params?: {
  workflowEvent?: string
  channel?: string
}): Promise<WorkflowEventTemplateMappingDto[]> {
  const { data } = await http.get<WorkflowEventTemplateMappingDto[]>('/workflow-event-template-mappings', {
    params: params ?? {},
  })
  return data
}
