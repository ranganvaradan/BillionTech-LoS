package com.los.core.creditintelligence.policystudio.catalogue;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One underlying implementation/data binding for a business capability.
 * Multiple bindings may exist; they are NOT separate business rules.
 */
public record ImplementationBinding(
        String bindingId,
        String sourceKind,
        String engine,
        String reference,
        String notes,
        boolean productionPath
) {
    public static final String PRODUCTION_HARD_RULE = "PRODUCTION_HARD_RULE";
    public static final String PRODUCTION_SCORECARD = "PRODUCTION_SCORECARD";
    public static final String PRODUCTION_RULE_SET = "PRODUCTION_RULE_SET";
    public static final String GOLDEN_STUDIO_TEMPLATE = "GOLDEN_STUDIO_TEMPLATE";
    public static final String CANONICAL_EVALUATOR = "CANONICAL_EVALUATOR";
    public static final String METRIC_SERVICE = "METRIC_SERVICE";
    public static final String REGISTRY = "POLICY_AUTHORING_REGISTRY";
    public static final String LIMIT_SIZING = "LIMIT_SIZING";
    public static final String SHADOW_DECISION = "SHADOW_DECISION";
    public static final String KYC_DSL = "KYC_DSL";

    public Map<String, Object> toBusinessView(boolean advanced) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sourceKind", sourceKind);
        m.put("productionPath", productionPath);
        if (advanced) {
            m.put("bindingId", bindingId);
            m.put("engine", engine);
            m.put("reference", reference);
            m.put("notes", notes);
        } else {
            m.put("label", businessLabel());
        }
        return m;
    }

    private String businessLabel() {
        return switch (sourceKind) {
            case PRODUCTION_HARD_RULE -> "Live underwriting hard rule";
            case PRODUCTION_SCORECARD -> "Live scorecard";
            case PRODUCTION_RULE_SET -> "Live underwriting rule set";
            case GOLDEN_STUDIO_TEMPLATE -> "Policy Studio template";
            case CANONICAL_EVALUATOR -> "Canonical evaluator (shadow)";
            case METRIC_SERVICE -> "Metric calculation";
            case REGISTRY -> "Authoring registry";
            case LIMIT_SIZING -> "Limit sizing";
            case SHADOW_DECISION -> "Shadow decision engine";
            case KYC_DSL -> "KYC policy authoring";
            default -> sourceKind;
        };
    }
}
