export type DueDiligenceQuestion = {
  key: string
  label: string
  hint?: string
  options: { value: string; label: string }[]
}

export function mapQuestionsFromApi(raw: unknown): DueDiligenceQuestion[] {
  if (!Array.isArray(raw) || raw.length === 0) return ANCHOR_DUE_DILIGENCE_QUESTIONS
  const mapped: DueDiligenceQuestion[] = []
  for (const item of raw) {
    if (!item || typeof item !== 'object') continue
    const row = item as Record<string, unknown>
    const key = String(row.key ?? '').trim()
    const label = String(row.label ?? '').trim()
    if (!key || !label) continue
    const options: { value: string; label: string }[] = []
    if (Array.isArray(row.options)) {
      for (const opt of row.options) {
        if (!opt || typeof opt !== 'object') continue
        const o = opt as Record<string, unknown>
        const value = String(o.value ?? '').trim()
        const optLabel = String(o.label ?? value).trim()
        if (value) options.push({ value, label: optLabel })
      }
    }
    if (options.length === 0) continue
    mapped.push({
      key,
      label,
      hint: row.hint != null ? String(row.hint) : undefined,
      options,
    })
  }
  return mapped.length > 0 ? mapped : ANCHOR_DUE_DILIGENCE_QUESTIONS
}

export function anchorRatingLabel(
  rating: string | undefined,
  bands?: { rating: string; label: string }[],
): string {
  if (!rating) return '—'
  const fromBand = bands?.find((b) => b.rating === rating)?.label
  if (fromBand) return fromBand
  return creditRatingLabel(rating)
}

export const ANCHOR_DUE_DILIGENCE_QUESTIONS: DueDiligenceQuestion[] = [
  {
    key: 'externalCreditRating',
    label: 'External credit rating (if available)',
    hint: 'Objective — agency rating or not rated.',
    options: [
      { value: 'AAA', label: 'AAA' },
      { value: 'AA', label: 'AA' },
      { value: 'A', label: 'A' },
      { value: 'BBB', label: 'BBB' },
      { value: 'BB', label: 'BB' },
      { value: 'B', label: 'B' },
      { value: 'C', label: 'C / below investment grade' },
      { value: 'D', label: 'D / default' },
      { value: 'NOT_RATED', label: 'Not rated externally' },
    ],
  },
  {
    key: 'financialPerformance',
    label: 'Financial performance (reviewed financials)',
    options: [
      { value: 'STRONG', label: 'Strong — stable revenue and margins' },
      { value: 'SATISFACTORY', label: 'Satisfactory' },
      { value: 'WEAK', label: 'Weak — declining or volatile' },
      { value: 'DISTRESSED', label: 'Distressed' },
    ],
  },
  {
    key: 'businessVintage',
    label: 'Business vintage',
    options: [
      { value: 'GTE_10', label: '10+ years' },
      { value: 'Y5_9', label: '5–9 years' },
      { value: 'Y3_4', label: '3–4 years' },
      { value: 'LT_3', label: 'Under 3 years' },
    ],
  },
  {
    key: 'gstCompliance',
    label: 'GST / statutory compliance',
    options: [
      { value: 'FULL', label: 'Fully compliant' },
      { value: 'MINOR_DELAYS', label: 'Minor filing delays' },
      { value: 'SIGNIFICANT_GAPS', label: 'Significant gaps' },
    ],
  },
  {
    key: 'industryRisk',
    label: 'Industry / sector risk',
    options: [
      { value: 'LOW', label: 'Low' },
      { value: 'MEDIUM', label: 'Medium' },
      { value: 'HIGH', label: 'High' },
    ],
  },
  {
    key: 'adverseNewsFlow',
    label: 'Adverse news flow',
    hint: 'Subjective — media, regulatory, or market signals.',
    options: [
      { value: 'NONE', label: 'None identified' },
      { value: 'MINOR', label: 'Minor / isolated' },
      { value: 'MATERIAL', label: 'Material adverse news' },
    ],
  },
  {
    key: 'legalLitigation',
    label: 'Legal / litigation history',
    options: [
      { value: 'NONE', label: 'None' },
      { value: 'RESOLVED', label: 'Resolved matters only' },
      { value: 'ONGOING_MATERIAL', label: 'Ongoing material litigation' },
    ],
  },
  {
    key: 'managementTrackRecord',
    label: 'Management track record',
    options: [
      { value: 'STRONG', label: 'Strong — proven leadership' },
      { value: 'ADEQUATE', label: 'Adequate' },
      { value: 'CONCERNS', label: 'Concerns identified' },
    ],
  },
]

export function creditRatingLabel(rating: string | undefined): string {
  if (!rating) return '—'
  const map: Record<string, string> = {
    A: 'A — Strong',
    B: 'B — Satisfactory',
    C: 'C — Refer / manual review',
    D: 'D — Not acceptable',
  }
  return map[rating] ?? rating
}

export type CreditRatingTone = 'success' | 'info' | 'warning' | 'danger' | 'default'

export function creditRatingTone(rating: string | undefined): CreditRatingTone {
  switch (rating?.toUpperCase()) {
    case 'A':
      return 'success'
    case 'B':
      return 'info'
    case 'C':
      return 'warning'
    case 'D':
      return 'danger'
    default:
      return 'default'
  }
}
