package com.los.core.creditintelligence.policystudio.scorecard;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;

import java.util.Map;
import java.util.UUID;

/**
 * SCORECARD-LINKAGE-PROJECTION-INVARIANT-1
 * <p>
 * Canonical linkage authority is {@code ci_policy_document.scorecard_id} on the
 * policy-version row. Session snapshots, prospect views, and UI state must never
 * override a durable document FK — including a genuine {@code null} (unlinked).
 * Reverse links on {@code underwriting_scorecards.policy_document_id} are not
 * an alternate authority.
 */
public final class PolicyVersionScorecardLinkage {

    public static final String AUTHORITY = "ci_policy_document.scorecard_id";
    public static final String OWNER_TYPE = "POLICY_VERSION";

    private PolicyVersionScorecardLinkage() {}

    public record ScorecardIdentity(UUID id, String name, String status, String scoringMode) {}

    /**
     * Overlay durable document FK fields onto the in-memory session document.
     * The durable row always wins, including {@code null}.
     */
    public static void overlayFromDurableDocument(CiPolicyDocument sessionDoc, CiPolicyDocument durableDoc) {
        if (sessionDoc == null || durableDoc == null) {
            return;
        }
        sessionDoc.setScorecardId(durableDoc.getScorecardId());
        if (durableDoc.getRuleGraphImmutable() != null) {
            sessionDoc.setRuleGraphImmutable(durableDoc.getRuleGraphImmutable());
        }
    }

    /**
     * Resolve the linked scorecard exclusively from the document FK.
     * Reverse-linked rows are ignored so a duplicate cannot win.
     */
    public static UUID canonicalScorecardId(UUID documentScorecardFk, UUID... reverseLinkedIgnored) {
        if (reverseLinkedIgnored != null && reverseLinkedIgnored.length > 0) {
            // Explicitly unused: reverse links must not override the document FK.
        }
        return documentScorecardFk;
    }

    /**
     * Identity lookup must be by canonical id only. A reverse-link match is discarded
     * unless it is the same UUID as the document FK.
     */
    public static ScorecardIdentity identityByCanonicalId(
            UUID canonicalId,
            ScorecardIdentity foundById,
            ScorecardIdentity foundByPolicyDocumentId) {
        if (canonicalId == null) {
            return null;
        }
        if (foundById != null && canonicalId.equals(foundById.id())) {
            return foundById;
        }
        if (foundByPolicyDocumentId != null && canonicalId.equals(foundByPolicyDocumentId.id())) {
            return foundByPolicyDocumentId;
        }
        return null;
    }

    public static int crossVersionMixCount(
            UUID versionAId,
            UUID versionAScorecard,
            UUID versionBId,
            UUID versionBScorecard,
            UUID projectedForVersionB) {
        if (versionAId == null || versionBId == null || versionAId.equals(versionBId)) {
            return 0;
        }
        if (projectedForVersionB == null) {
            return versionBScorecard == null ? 0 : 0;
        }
        boolean borrowedFromA = projectedForVersionB.equals(versionAScorecard)
                && (versionBScorecard == null || !versionBScorecard.equals(versionAScorecard));
        return borrowedFromA ? 1 : 0;
    }

    public static int linkageWithoutOwnerIdentityCount(UUID ownerId, Integer ownerVersion, UUID scorecardId) {
        if (scorecardId == null) {
            return 0;
        }
        return ownerId == null || ownerVersion == null ? 1 : 0;
    }

    /**
     * Definitive "No scorecard" is only legal when linkage is known and the
     * authoritative FK is null.
     */
    public static int falseNoScorecardDisplayCount(
            boolean linkageKnown,
            UUID authoritativeScorecardId,
            boolean uiShowsNoScorecard) {
        if (!uiShowsNoScorecard) {
            return 0;
        }
        if (!linkageKnown) {
            return 1;
        }
        return authoritativeScorecardId != null ? 1 : 0;
    }

    /**
     * Observational identity only — does not execute the scorecard or change live decision authority.
     */
    public static void stampObservationalIdentity(Map<String, Object> out, UUID scorecardId, Integer version) {
        if (out == null) {
            return;
        }
        out.put("scorecardId", scorecardId == null ? null : scorecardId.toString());
        out.put("scorecardVersion", version);
        out.put("scorecardLinkageAuthority", AUTHORITY);
        out.put("scorecardLinked", scorecardId != null);
    }

    public static void applyProjection(Map<String, Object> header, CiPolicyDocument doc) {
        applyProjection(header, doc, null);
    }

    public static void applyProjection(
            Map<String, Object> header,
            CiPolicyDocument doc,
            ScorecardIdentity identity) {
        if (header == null) {
            return;
        }
        UUID docId = doc == null ? null : doc.getId();
        Integer version = doc == null ? null : doc.getDocumentVersion();
        UUID scorecardId = doc == null ? null : doc.getScorecardId();
        header.put("scorecardLinkageAuthority", AUTHORITY);
        header.put("scorecardLinkOwnerType", OWNER_TYPE);
        header.put("scorecardLinkOwnerId", docId == null ? null : docId.toString());
        header.put("scorecardLinkOwnerVersion", version);
        header.put("scorecardId", scorecardId == null ? null : scorecardId.toString());
        header.put("scorecardLinked", scorecardId != null);
        header.put("scorecardLinkageKnown", true);
        boolean identityMatches = identity != null
                && scorecardId != null
                && scorecardId.equals(identity.id());
        header.put("scorecardName", identityMatches ? identity.name() : null);
        header.put("scorecardStatus", identityMatches ? identity.status() : null);
        header.put("scorecardScoringMode", identityMatches ? identity.scoringMode() : null);
    }
}
