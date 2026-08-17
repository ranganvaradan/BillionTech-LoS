package com.los.core.creditintelligence.policystudio.lifecycle;

import java.util.UUID;

/**
 * POLICY-ACTIVE-LIFECYCLE-AND-CUSTOMER-CATEGORY-LINKAGE-INVARIANT-1
 * <p>
 * Single backend lifecycle identity for a policy version. Policy List, Versions,
 * editor editability, and customer-category eligibility must call this — they must
 * not derive lifecycle independently or invent ACTIVE from the clock.
 * <p>
 * Does not enable production underwriting authority.
 */
public final class PolicyCanonicalLifecycleAuthority {

    public static final String NAME = "PolicyCanonicalLifecycleAuthority";
    /** Customer-category bind owner is the catalogue applicability row (exact policy version). */
    public static final String OWNER_TYPE = "POLICY_APPLICABILITY";

    private PolicyCanonicalLifecycleAuthority() {}

    public static String normalize(String raw) {
        return PolicyBusinessLifecycleStatus.fromStored(raw);
    }

    public static int rank(String status) {
        return switch (normalize(status)) {
            case PolicyBusinessLifecycleStatus.DRAFT -> 0;
            case PolicyBusinessLifecycleStatus.IN_REVIEW -> 1;
            case PolicyBusinessLifecycleStatus.APPROVED -> 2;
            case PolicyBusinessLifecycleStatus.SCHEDULED -> 3;
            case PolicyBusinessLifecycleStatus.ACTIVE -> 4;
            case PolicyBusinessLifecycleStatus.SUPERSEDED, PolicyBusinessLifecycleStatus.RETIRED -> 5;
            default -> 0;
        };
    }

    /**
     * Reconcile session metadata and durable catalogue status for the SAME version identity.
     * <ul>
     *   <li>Session DRAFT cannot mask catalogue APPROVED/SCHEDULED/ACTIVE</li>
     *   <li>Session ACTIVE (partial schedule) is not overwritten by catalogue APPROVED</li>
     *   <li>RETIRED/SUPERSEDED on either side wins</li>
     *   <li>Does not derive ACTIVE from effective-date vs today</li>
     * </ul>
     */
    public static String reconcile(String sessionStatus, String catalogueStatus) {
        boolean sessionPresent = sessionStatus != null && !sessionStatus.isBlank();
        boolean cataloguePresent = catalogueStatus != null && !catalogueStatus.isBlank();
        if (!sessionPresent && !cataloguePresent) {
            return PolicyBusinessLifecycleStatus.DRAFT;
        }
        if (!sessionPresent) {
            return normalize(catalogueStatus);
        }
        if (!cataloguePresent) {
            return normalize(sessionStatus);
        }
        String session = normalize(sessionStatus);
        String catalogue = normalize(catalogueStatus);
        if (PolicyBusinessLifecycleStatus.RETIRED.equals(catalogue)
                || PolicyBusinessLifecycleStatus.SUPERSEDED.equals(catalogue)) {
            return catalogue;
        }
        if (PolicyBusinessLifecycleStatus.RETIRED.equals(session)
                || PolicyBusinessLifecycleStatus.SUPERSEDED.equals(session)) {
            return session;
        }
        return rank(session) >= rank(catalogue) ? session : catalogue;
    }

    public static boolean contentEditable(String canonicalStatus) {
        String n = normalize(canonicalStatus);
        return PolicyBusinessLifecycleStatus.DRAFT.equals(n)
                || PolicyBusinessLifecycleStatus.IN_REVIEW.equals(n);
    }

    public static boolean eligibleForCustomerCategoryLinkage(String canonicalStatus) {
        String n = normalize(canonicalStatus);
        return PolicyBusinessLifecycleStatus.APPROVED.equals(n)
                || PolicyBusinessLifecycleStatus.SCHEDULED.equals(n)
                || PolicyBusinessLifecycleStatus.ACTIVE.equals(n);
    }

    public static String ineligibleReason(String canonicalStatus) {
        if (eligibleForCustomerCategoryLinkage(canonicalStatus)) {
            return null;
        }
        String n = normalize(canonicalStatus);
        if (PolicyBusinessLifecycleStatus.RETIRED.equals(n)
                || PolicyBusinessLifecycleStatus.SUPERSEDED.equals(n)) {
            return "POLICY_NOT_GOVERNED_" + n.replace(' ', '_');
        }
        return "DRAFT_NOT_ELIGIBLE";
    }

    public record Projection(
            String businessStatus,
            boolean contentEditable,
            boolean eligibleForCustomerCategoryLinkage,
            String lifecycleAuthority,
            String ownerType,
            String ownerId,
            String ineligibleReason
    ) {}

    public static Projection project(
            String sessionStatus,
            String catalogueStatus,
            UUID applicabilityId,
            Boolean contentImmutable) {
        String status = reconcile(sessionStatus, catalogueStatus);
        boolean editable = contentEditable(status) && !Boolean.TRUE.equals(contentImmutable);
        boolean eligible = eligibleForCustomerCategoryLinkage(status);
        return new Projection(
                status,
                editable,
                eligible,
                NAME,
                OWNER_TYPE,
                applicabilityId == null ? null : applicabilityId.toString(),
                ineligibleReason(status));
    }
}
