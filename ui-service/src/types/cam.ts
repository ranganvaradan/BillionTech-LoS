/**
 * GET /api/v1/applications/{id}/cam — backend {@code CamResponse} (sections are object maps).
 */
export interface CamResponse {
  applicationId: string
  applicationStatus: string
  section1ApplicantSummary: Record<string, unknown> | null
  section2KycSummary: Record<string, unknown> | null
  section3CreditSummary: Record<string, unknown> | null
  section4UnderwritingSummary: Record<string, unknown> | null
  /** Auto sections: executive summary, income, collateral, risk flags, etc. */
  sectionExtended: Record<string, unknown> | null
  section5Observations: string | null
  section5RiskAssessment: string | null
  section5Mitigants: string | null
  section6RecommendedDecision: string | null
  camReviewed: boolean
  camVersion: number | null
  camStatus: string | null
  recommendedAmount: number | null
  recommendedTenureMonths: number | null
  recommendedRate: number | null
  conditionsPrecedent: string[] | null
  conditionsSubsequent: string[] | null
  creditOfficerRemarks: string | null
  creditManagerRemarks: string | null
  submittedAt: string | null
  approvedAt: string | null
  updatedAt: string | null
  createdAt: string | null
  approvedByUserId: string | null
  /** User-edited section narrative / structured overrides (merged for PDF). */
  editableSections: Record<string, unknown> | null
}

export interface CamUpdateRequest {
  observations?: string
  riskAssessment?: string
  mitigants?: string
  recommendedDecision?: string
  recommendedAmount?: number
  recommendedTenureMonths?: number
  recommendedRate?: number
  conditionsPrecedent?: string[]
  conditionsSubsequent?: string[]
  creditOfficerRemarks?: string
  creditManagerRemarks?: string
  editableSectionsPatch?: Record<string, unknown>
}
