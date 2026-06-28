import type { AnchorFormState } from '@/lib/intake/anchorIntakeTypes'

const DRAFT_KEY = 'los_anchor_intake_draft_v1'

export type AnchorDraftState = {
  step: number
  applicationId: string | null
  form: AnchorFormState
}

export function loadAnchorDraft(): AnchorDraftState | null {
  try {
    const raw = localStorage.getItem(DRAFT_KEY)
    if (!raw) return null
    return JSON.parse(raw) as AnchorDraftState
  } catch {
    return null
  }
}

export function saveAnchorDraft(state: AnchorDraftState): void {
  try {
    localStorage.setItem(DRAFT_KEY, JSON.stringify(state))
  } catch {
    /* ignore quota */
  }
}

export function clearAnchorDraft(): void {
  try {
    localStorage.removeItem(DRAFT_KEY)
  } catch {
    /* ignore */
  }
}
