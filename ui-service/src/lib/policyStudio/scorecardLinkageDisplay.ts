/**
 * SCORECARD-LINKAGE-PROJECTION-INVARIANT-1
 * Distinguish linked / genuinely none / still loading. Never treat unknown as "No scorecard".
 */

export type ScorecardLinkageKind = 'LOADING' | 'LINKED' | 'NONE'

export type ScorecardLinkageDisplay =
  | { kind: 'LOADING' }
  | {
      kind: 'LINKED'
      scorecardId: string
      name?: string | null
      status?: string | null
      scoringMode?: string | null
    }
  | { kind: 'NONE' }

export function resolveScorecardLinkageDisplay(input: {
  sessionLoaded?: boolean
  linkageKnown?: boolean | null
  scorecardId?: string | null
  scorecardName?: string | null
  scorecardStatus?: string | null
  scorecardScoringMode?: string | null
}): ScorecardLinkageDisplay {
  const id = String(input.scorecardId ?? '').trim()
  if (id) {
    return {
      kind: 'LINKED',
      scorecardId: id,
      name: input.scorecardName ?? null,
      status: input.scorecardStatus ?? null,
      scoringMode: input.scorecardScoringMode ?? null,
    }
  }
  if (input.sessionLoaded === true && input.linkageKnown === true) {
    return { kind: 'NONE' }
  }
  return { kind: 'LOADING' }
}

export function falseNoScorecardDisplayCount(display: ScorecardLinkageDisplay, uiShowsNoScorecard: boolean): number {
  if (!uiShowsNoScorecard) return 0
  return display.kind === 'NONE' ? 0 : 1
}
