package com.los.core.creditintelligence.aiunderwriter.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sanitized structured context sent to AI providers.
 * Maps only — no raw provider payloads.
 */
public record AiUnderwritingContext(
        UUID tenantId,
        UUID applicationId,
        UUID evaluationContextId,
        UUID policyEvaluationId,
        UUID recommendationId,
        String contextVersion,
        Map<String, Object> applicationSummary,
        Map<String, Object> borrowerProfile,
        Map<String, Object> creditEvidenceView,
        Map<String, Object> policyDecisionExplanation,
        Map<String, Object> creditDecisionView,
        Map<String, Object> materialReconciliations,
        Map<String, Object> policyOutcome,
        Map<String, Object> shadowRecommendation,
        List<Map<String, Object>> investigationQuestions,
        Map<String, Object> knownMetrics,
        Map<String, Object> knownRuleIds,
        List<String> allowedCanonicalPaths
) {
    public AiUnderwritingContext {
        applicationSummary = copySansNull(applicationSummary);
        borrowerProfile = copySansNull(borrowerProfile);
        creditEvidenceView = copySansNull(creditEvidenceView);
        policyDecisionExplanation = copySansNull(policyDecisionExplanation);
        creditDecisionView = copySansNull(creditDecisionView);
        materialReconciliations = copySansNull(materialReconciliations);
        policyOutcome = copySansNull(policyOutcome);
        shadowRecommendation = copySansNull(shadowRecommendation);
        investigationQuestions = investigationQuestions == null
                ? List.of()
                : List.copyOf(sanitizeQuestionList(investigationQuestions));
        knownMetrics = copySansNull(knownMetrics);
        knownRuleIds = copySansNull(knownRuleIds);
        allowedCanonicalPaths = allowedCanonicalPaths == null ? List.of() : List.copyOf(allowedCanonicalPaths);
        contextVersion = contextVersion == null ? "A1_V1" : contextVersion;
    }

    /** Map.copyOf rejects null values — staging fixtures often omit optional fields. */
    private static Map<String, Object> copySansNull(Map<String, Object> in) {
        if (in == null || in.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        in.forEach((k, v) -> {
            if (k != null && v != null) {
                out.put(k, v);
            }
        });
        return Map.copyOf(out);
    }

    private static List<Map<String, Object>> sanitizeQuestionList(List<Map<String, Object>> in) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> m : in) {
            if (m == null) {
                continue;
            }
            out.add(copySansNull(m));
        }
        return out;
    }

    public Map<String, Object> toSanitizedMap() {
        return Map.ofEntries(
                Map.entry("tenantId", tenantId == null ? "" : tenantId.toString()),
                Map.entry("applicationId", applicationId == null ? "" : applicationId.toString()),
                Map.entry("evaluationContextId", evaluationContextId == null ? "" : evaluationContextId.toString()),
                Map.entry("policyEvaluationId", policyEvaluationId == null ? "" : policyEvaluationId.toString()),
                Map.entry("recommendationId", recommendationId == null ? "" : recommendationId.toString()),
                Map.entry("contextVersion", contextVersion),
                Map.entry("applicationSummary", applicationSummary),
                Map.entry("borrowerProfile", borrowerProfile),
                Map.entry("creditEvidenceView", creditEvidenceView),
                Map.entry("policyDecisionExplanation", policyDecisionExplanation),
                Map.entry("creditDecisionView", creditDecisionView),
                Map.entry("materialReconciliations", materialReconciliations),
                Map.entry("policyOutcome", policyOutcome),
                Map.entry("shadowRecommendation", shadowRecommendation),
                Map.entry("investigationQuestions", investigationQuestions),
                Map.entry("knownMetrics", knownMetrics),
                Map.entry("knownRuleIds", knownRuleIds),
                Map.entry("allowedCanonicalPaths", allowedCanonicalPaths)
        );
    }
}
