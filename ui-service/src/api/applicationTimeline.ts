import { http } from './http'
import type { ApplicationTimelineResponse } from '@/types/applicationTimeline'

export async function getApplicationTimeline(
  applicationId: string,
): Promise<ApplicationTimelineResponse> {
  const { data } = await http.get<ApplicationTimelineResponse>(
    `/applications/${applicationId}/timeline`,
  )
  return data
}
