import type { ApplicationResponse } from './application'

/**
 * Spring `Page<>` JSON (Jackson default property names).
 */
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  first: boolean
  last: boolean
  empty: boolean
  numberOfElements: number
}

export type ApplicationPage = PageResponse<ApplicationResponse>

/**
 * `Map<String, Object>` from `/applications/dashboard/summary` (counts, etc.).
 */
export type DashboardSummary = Record<string, string | number | boolean | null | undefined>
