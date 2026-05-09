const KEY = 'los_borrower_loan_draft_v1'
export const DRAFT_KEY = KEY

export function saveDraftState(state: unknown, applicationId: string | null, step?: number) {
  try {
    window.localStorage.setItem(KEY, JSON.stringify({ state, applicationId, step: step ?? 0, savedAt: Date.now() }))
  } catch {
    // ignore
  }
}

export function loadDraftState(): { state: unknown; applicationId: string | null; step: number } | null {
  try {
    const raw = window.localStorage.getItem(KEY)
    if (!raw) return null
    const p = JSON.parse(raw) as { state: unknown; applicationId: string | null; step?: number }
    return { state: p.state, applicationId: p.applicationId, step: typeof p.step === 'number' ? p.step : 0 }
  } catch {
    return null
  }
}

export function clearDraft() {
  try {
    window.localStorage.removeItem(KEY)
  } catch {
    // ignore
  }
}

export function hasLocalDraft(): boolean {
  return loadDraftState() != null
}

/**
 * Call once at app init: clear saved wizard when staff triggers “Reset demo data” (emits
 * `los:demo-data-cleared`).
 */
export function installBorrowerDraftDemoClearListener(): () => void {
  if (typeof window === 'undefined') {
    return () => {}
  }
  const onClear = () => {
    clearDraft()
  }
  window.addEventListener('los:demo-data-cleared', onClear)
  return () => window.removeEventListener('los:demo-data-cleared', onClear)
}
