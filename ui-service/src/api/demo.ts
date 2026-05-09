import { http } from './http'

export interface ClearDemoApplicationsResponse {
  deletedApplications: number
  status: string
}

export interface DemoStatusResponse {
  demoEnabled: boolean
  profile: string
  applicationCount: number
}

export async function getDemoStatus(): Promise<DemoStatusResponse> {
  const { data } = await http.get<DemoStatusResponse>('/demo/status')
  return data
}

/**
 * Requires demo mode (local profile or los.demo.enabled=true). Otherwise 404 with { message }.
 */
export async function clearDemoApplications(): Promise<ClearDemoApplicationsResponse> {
  const { data } = await http.delete<ClearDemoApplicationsResponse>('/demo/applications')
  return data
}
