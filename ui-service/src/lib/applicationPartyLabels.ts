import type { ApplicationIntakeSegment } from '@/types/application'

export type ResolvedIntakeSegment = 'BORROWER' | 'ANCHOR'

/** Null/undefined intake segment is treated as borrower (legacy applications). */
export function resolveIntakeSegment(segment?: ApplicationIntakeSegment | null): ResolvedIntakeSegment {
  return segment === 'ANCHOR' ? 'ANCHOR' : 'BORROWER'
}

export function getPartyNoun(segment?: ApplicationIntakeSegment | null): 'Borrower' | 'Anchor' {
  return resolveIntakeSegment(segment) === 'ANCHOR' ? 'Anchor' : 'Borrower'
}

export type ApplicationPartyLabels = {
  party: 'Borrower' | 'Anchor'
  partyLower: 'borrower' | 'anchor'
  isAnchor: boolean
  profileTab: string
  submittedDetailsHeading: string
  submittedDetailsTitle: string
  entityTypeDetail: string
  entityClassRow: string
  identitySectionTitle: string
  collateralIntakeSectionTitle: string
  primaryParty: string
  snapshotHeading: string
  declaredIntakeHeading: string
  partyProductScorecard: string
  copyStatusLink: string
  statusPageAudience: string
  linkedPartyUserId: string
  partyEmailRecord: string
  partyMobileRecord: string
  kycDocumentPreset: string
  camProfileSection: string
  esignSignerFallback: string
  snapshotTypeKey: string
  vkycPkycPlaceholder: string
}

export function applicationPartyLabels(segment?: ApplicationIntakeSegment | null): ApplicationPartyLabels {
  const isAnchor = resolveIntakeSegment(segment) === 'ANCHOR'
  const party = isAnchor ? 'Anchor' : 'Borrower'
  const partyLower = isAnchor ? 'anchor' : 'borrower'
  return {
    party,
    partyLower,
    isAnchor,
    profileTab: `${party} profile`,
    submittedDetailsHeading: `${party}-submitted details`,
    submittedDetailsTitle: `${party} submitted details`,
    entityTypeDetail: isAnchor ? 'Entity type' : 'Borrower type',
    entityClassRow: isAnchor ? 'Entity class' : 'Borrower class',
    identitySectionTitle: `${party} identity & contact`,
    collateralIntakeSectionTitle: `Collateral (${partyLower} intake)`,
    primaryParty: `${party} (primary)`,
    snapshotHeading: `${party} snapshot`,
    declaredIntakeHeading: `${party}-declared (intake)`,
    partyProductScorecard: `${party} / product (scorecard row)`,
    copyStatusLink: `Copy ${partyLower} status link`,
    statusPageAudience: `${partyLower}-friendly`,
    linkedPartyUserId: `Linked ${partyLower} (user id)`,
    partyEmailRecord: `${party} email (record)`,
    partyMobileRecord: `${party} mobile (record)`,
    kycDocumentPreset: `${party} / KYC (general)`,
    camProfileSection: `${party} profile`,
    esignSignerFallback: party,
    snapshotTypeKey: isAnchor ? 'Anchor type' : 'Borrower type',
    vkycPkycPlaceholder: `${party} unable to complete VKYC due to network issue. Physical verification completed by branch officer.`,
  }
}

/** Remap snapshot map keys that use "Borrower" for anchor applications. */
export function remapPartySnapshotKeys(
  snapshot: Record<string, string>,
  segment?: ApplicationIntakeSegment | null,
): Record<string, string> {
  if (resolveIntakeSegment(segment) !== 'ANCHOR') return snapshot
  const L = applicationPartyLabels(segment)
  const out: Record<string, string> = {}
  for (const [key, value] of Object.entries(snapshot)) {
    if (key === 'Borrower type') {
      out[L.snapshotTypeKey] = value
    } else {
      out[key] = value
    }
  }
  return out
}
