export type TimelineCategory = 'STATUS' | 'ACTION' | 'PROCESS' | 'INTEGRATION'

export interface ApplicationTimelineEntry {
  id: string
  occurredAt: string | null
  completedAt?: string | null
  category: TimelineCategory | string
  title: string
  actor?: string | null
  result?: string | null
  summary?: string | null
  details?: Record<string, unknown>
}

export interface ApplicationTimelineResponse {
  applicationId: string
  applicationNumber: string
  currentStatus: string | null
  lifecycleStages: string[]
  visitedStatuses: string[]
  entries: ApplicationTimelineEntry[]
}
