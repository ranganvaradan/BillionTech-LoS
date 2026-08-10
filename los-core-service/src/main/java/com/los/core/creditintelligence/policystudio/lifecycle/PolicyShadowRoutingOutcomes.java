package com.los.core.creditintelligence.policystudio.lifecycle;

/**
 * P2 outcomes for catalogue linkage and shadow eligibility.
 */
public final class PolicyShadowRoutingOutcomes {

    public static final String POLICY_PACKAGE_NOT_EXECUTABLE = "POLICY_PACKAGE_NOT_EXECUTABLE";
    public static final String NOT_ELIGIBLE_FOR_SHADOW_ROUTING = "NOT_ELIGIBLE_FOR_SHADOW_ROUTING";
    public static final String DEMO_ONLY_NOT_ROUTABLE = "DEMO_ONLY_NOT_ROUTABLE";

    public static final String LINK_PROPER = "PROPER_IMMUTABLE_PACKAGE_LINK";
    public static final String LINK_DEMO_UNLINKED = "DEMO_ONLY_UNLINKED";
    public static final String LINK_DEMO_NOT_ROUTABLE = "DEMO_ONLY_NOT_ROUTABLE";
    public static final String LINK_INVALID = "INVALID";
    public static final String LINK_UNLINKED = "UNLINKED";

    public static final String ELIGIBLE = "ELIGIBLE_FOR_SHADOW_ROUTING";
    public static final String NOT_ELIGIBLE = "NOT_ELIGIBLE_FOR_SHADOW_ROUTING";

    public static final String CERT_INSUFFICIENT = "INSUFFICIENT_EVIDENCE";
    public static final String CERT_BLOCKED = "BLOCKED_BY_DEFECTS";
    public static final String CERT_SHADOW_VALIDATED = "SHADOW_VALIDATED";

    private PolicyShadowRoutingOutcomes() {}
}
