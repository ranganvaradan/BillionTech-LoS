package com.los.core.creditintelligence.decisionpolicy;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Helpers to stamp Decision Policy metadata onto existing rule candidates
 * without schema migration ({@code metadata} / {@code scope} JSONB).
 */
public final class DecisionPolicyRuleMetadata {

    public static final String KEY_DOMAIN = "decisionDomain";
    public static final String KEY_REQUIREMENT_TYPE = "kycRequirementType";
    public static final String KEY_GUARDRAIL = "guardrailClass";
    public static final String KEY_STAGE = "stageCode";
    public static final String KEY_POLICY_SELECTS_PROVIDER = "policySelectsProvider";
    public static final String KEY_POLICY_ENQUEUES_WORKFLOW = "policyEnqueuesWorkflowStep";

    private DecisionPolicyRuleMetadata() {}

    public static Map<String, Object> stamp(
            Map<String, Object> metadata,
            DecisionPolicyDomain domain,
            KycRequirementType requirementType,
            PolicyGuardrailClass guardrailClass
    ) {
        Map<String, Object> m = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
        DecisionPolicyDomain d = domain == null ? DecisionPolicyDomain.CREDIT : domain;
        m.put(KEY_DOMAIN, d.name());
        m.put(KEY_STAGE, DecisionPolicyStages.stageForDomain(d));
        if (requirementType != null && d.isKycOrEligibility()) {
            m.put(KEY_REQUIREMENT_TYPE, requirementType.name());
        }
        m.put(KEY_GUARDRAIL, guardrailClass == null ? PolicyGuardrailClass.UNRESOLVED.name() : guardrailClass.name());
        // Safety assertions encoded in metadata for tooling
        m.put(KEY_POLICY_SELECTS_PROVIDER, false);
        m.put(KEY_POLICY_ENQUEUES_WORKFLOW, false);
        return m;
    }

    public static DecisionPolicyDomain domainOf(Map<String, Object> metadata, Map<String, Object> scope) {
        Object fromMeta = metadata == null ? null : metadata.get(KEY_DOMAIN);
        if (fromMeta != null) {
            return DecisionPolicyDomain.fromMetadata(fromMeta);
        }
        Object fromScope = scope == null ? null : scope.get(KEY_DOMAIN);
        return DecisionPolicyDomain.fromMetadata(fromScope);
    }

    public static PolicyGuardrailClass guardrailOf(Map<String, Object> metadata) {
        if (metadata == null) {
            return PolicyGuardrailClass.UNRESOLVED;
        }
        return PolicyGuardrailClass.fromMetadata(metadata.get(KEY_GUARDRAIL));
    }

    /**
     * PLATFORM_GUARDRAIL must not become editable via Studio presentation alone.
     */
    public static boolean isStudioEditable(Map<String, Object> metadata) {
        PolicyGuardrailClass g = guardrailOf(metadata);
        return g.isEditableByNbfcPolicyAuthor();
    }
}
