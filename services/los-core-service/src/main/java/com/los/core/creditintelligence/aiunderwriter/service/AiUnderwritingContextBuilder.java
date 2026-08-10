package com.los.core.creditintelligence.aiunderwriter.service;

import com.los.core.creditintelligence.aiunderwriter.domain.AiUnderwritingContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds sanitized AiUnderwritingContext from canonical views — no raw provider payloads.
 */
@Service
public class AiUnderwritingContextBuilder {

    private final PiiMinimizer piiMinimizer;

    public AiUnderwritingContextBuilder() {
        this(new PiiMinimizer());
    }

    public AiUnderwritingContextBuilder(PiiMinimizer piiMinimizer) {
        this.piiMinimizer = piiMinimizer != null ? piiMinimizer : new PiiMinimizer();
    }

    public AiUnderwritingContext build(
            UUID tenantId,
            UUID applicationId,
            UUID evaluationContextId,
            UUID policyEvaluationId,
            UUID recommendationId,
            Map<String, Object> creditEvidenceView,
            Map<String, Object> policyDecisionExplanation,
            Map<String, Object> creditDecisionView,
            Map<String, Object> shadowRecommendation,
            List<Map<String, Object>> investigationQuestions) {

        Map<String, Object> evidence = creditEvidenceView == null ? Map.of() : creditEvidenceView;
        Map<String, Object> policyExpl = policyDecisionExplanation == null ? Map.of() : policyDecisionExplanation;
        Map<String, Object> decisionView = creditDecisionView == null ? Map.of() : creditDecisionView;
        Map<String, Object> recommendation = shadowRecommendation == null ? Map.of() : shadowRecommendation;

        Map<String, Object> material = extractMaterial(evidence);
        Map<String, Object> policyOutcome = extractPolicyOutcome(policyExpl, decisionView, recommendation);
        Map<String, Object> knownMetrics = extractKnownMetrics(evidence, decisionView, recommendation);
        Map<String, Object> knownRuleIds = extractKnownRuleIds(policyExpl, material, recommendation);
        List<String> paths = new ArrayList<>(knownMetrics.keySet());
        paths.addAll(knownRuleIds.keySet());

        Map<String, Object> appSummary = new LinkedHashMap<>();
        appSummary.put("applicationId", applicationId == null ? null : applicationId.toString());
        appSummary.put("evaluationContextId", evaluationContextId == null ? null : evaluationContextId.toString());
        appSummary.put("policyEvaluationId", policyEvaluationId == null ? null : policyEvaluationId.toString());
        appSummary.put("recommendationId", recommendationId == null ? null : recommendationId.toString());

        Map<String, Object> borrower = new LinkedHashMap<>();
        if (evidence.get("Identity") instanceof Map<?, ?> id) {
            @SuppressWarnings("unchecked")
            Map<String, Object> idMap = (Map<String, Object>) id;
            borrower.put("caseCode", idMap.get("caseCode"));
            borrower.put("dataOrigin", idMap.get("dataOrigin"));
            borrower.put("note", "Masked identity — no raw KYC/PII");
        }

        List<Map<String, Object>> questions = investigationQuestions;
        if (questions == null || questions.isEmpty()) {
            Object open = evidence.get("OpenInvestigationQuestions");
            if (open instanceof List<?> list) {
                questions = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mm = (Map<String, Object>) m;
                        questions.add(mm);
                    }
                }
            } else {
                questions = List.of();
            }
        }

        AiUnderwritingContext raw = new AiUnderwritingContext(
                tenantId,
                applicationId,
                evaluationContextId,
                policyEvaluationId,
                recommendationId,
                "A1_V1",
                appSummary,
                borrower,
                stripRawProviders(evidence),
                stripRawProviders(policyExpl),
                stripRawProviders(decisionView),
                material,
                policyOutcome,
                stripRawProviders(recommendation),
                questions,
                knownMetrics,
                knownRuleIds,
                paths
        );

        Map<String, Object> sanitized = piiMinimizer.minimize(raw.toSanitizedMap());
        return new AiUnderwritingContext(
                tenantId,
                applicationId,
                evaluationContextId,
                policyEvaluationId,
                recommendationId,
                "A1_V1",
                asMap(sanitized.get("applicationSummary")),
                asMap(sanitized.get("borrowerProfile")),
                asMap(sanitized.get("creditEvidenceView")),
                asMap(sanitized.get("policyDecisionExplanation")),
                asMap(sanitized.get("creditDecisionView")),
                asMap(sanitized.get("materialReconciliations")),
                asMap(sanitized.get("policyOutcome")),
                asMap(sanitized.get("shadowRecommendation")),
                listOfMaps(sanitized.get("investigationQuestions")),
                asMap(sanitized.get("knownMetrics")),
                asMap(sanitized.get("knownRuleIds")),
                stringList(sanitized.get("allowedCanonicalPaths"))
        );
    }

    private Map<String, Object> extractMaterial(Map<String, Object> evidence) {
        Object m = evidence.get("MaterialReconciliations");
        if (m instanceof List<?> list) {
            Map<String, Object> out = new LinkedHashMap<>();
            int i = 0;
            for (Object o : list) {
                if (o instanceof Map<?, ?> row) {
                    Object codeObj = row.get("code");
                    String code = codeObj == null ? "recon_" + i : String.valueOf(codeObj);
                    out.put(code, row);
                }
                i++;
            }
            return out;
        }
        if (m instanceof Map<?, ?>) {
            return asMap(m);
        }
        return Map.of();
    }

    private Map<String, Object> extractPolicyOutcome(
            Map<String, Object> policyExpl,
            Map<String, Object> decisionView,
            Map<String, Object> recommendation) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (policyExpl.containsKey("overallOutcome")) {
            out.put("overallOutcome", policyExpl.get("overallOutcome"));
        }
        if (decisionView.get("Eligibility") instanceof Map<?, ?> el) {
            out.put("eligibility", el);
        }
        if (recommendation.get("outcome") != null) {
            out.put("recommendationOutcome", recommendation.get("outcome"));
        }
        if (recommendation.get("recommendationOutcome") != null) {
            out.put("recommendationOutcome", recommendation.get("recommendationOutcome"));
        }
        return out;
    }

    private Map<String, Object> extractKnownMetrics(
            Map<String, Object> evidence,
            Map<String, Object> decisionView,
            Map<String, Object> recommendation) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        putMetric(metrics, "gst.turnover.trailing_12m", nested(evidence, "GST", "turnover"));
        putMetric(metrics, "bank.turnover.trailing_12m", nested(evidence, "Banking", "turnover"));
        putMetric(metrics, "itr.turnover.trailing_12m", nested(evidence, "ITR", "turnover"));
        putMetric(metrics, "bureau.emi.monthly", nested(evidence, "Bureau", "emi"));
        putMetric(metrics, "bank.emi.monthly", nested(evidence, "Banking", "emi"));
        putMetric(metrics, "bank.abb.average", nested(evidence, "Banking", "abb"));
        if (recommendation.get("amount") != null) {
            putMetric(metrics, "recommendation.amount", recommendation.get("amount"));
        }
        if (recommendation.get("recommendedAmount") != null) {
            putMetric(metrics, "recommendation.amount", recommendation.get("recommendedAmount"));
        }
        if (recommendation.get("tenureMonths") != null) {
            putMetric(metrics, "recommendation.tenureMonths", recommendation.get("tenureMonths"));
        }
        if (recommendation.get("recommendedTenureMonths") != null) {
            putMetric(metrics, "recommendation.tenureMonths", recommendation.get("recommendedTenureMonths"));
        }
        if (decisionView.get("Limit") instanceof Map<?, ?> lim && lim.get("amount") != null) {
            putMetric(metrics, "decision.limit.amount", lim.get("amount"));
        }
        Object triangulation = evidence.get("TurnoverTriangulation");
        if (triangulation instanceof Map<?, ?> t) {
            for (Map.Entry<?, ?> e : t.entrySet()) {
                if (e.getValue() instanceof Number || e.getValue() instanceof BigDecimal || e.getValue() instanceof String) {
                    putMetric(metrics, "turnover." + e.getKey(), e.getValue());
                }
            }
        }
        return metrics;
    }

    private Map<String, Object> extractKnownRuleIds(
            Map<String, Object> policyExpl,
            Map<String, Object> material,
            Map<String, Object> recommendation) {
        Map<String, Object> rules = new LinkedHashMap<>();
        for (String code : material.keySet()) {
            rules.put(code, material.get(code));
        }
        Object ruleResults = policyExpl.get("ruleResults");
        if (ruleResults instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m && m.get("ruleId") != null) {
                    rules.put(String.valueOf(m.get("ruleId")), m);
                }
            }
        }
        Object reasonCodes = recommendation.get("reasonCodes");
        if (reasonCodes instanceof List<?> list) {
            for (Object o : list) {
                rules.put(String.valueOf(o), Map.of("code", String.valueOf(o)));
            }
        }
        return rules;
    }

    private void putMetric(Map<String, Object> metrics, String path, Object value) {
        if (value == null) {
            return;
        }
        metrics.put(path, Map.of(
                "metric", path,
                "value", value,
                "evidenceRef", path
        ));
    }

    private Object nested(Map<String, Object> root, String section, String field) {
        Object sec = root.get(section);
        if (sec instanceof Map<?, ?> m) {
            return m.get(field);
        }
        return null;
    }

    private Map<String, Object> stripRawProviders(Map<String, Object> in) {
        Map<String, Object> out = new LinkedHashMap<>(in);
        out.remove("rawProviderPayload");
        out.remove("rawPayload");
        out.remove("providerResponse");
        out.remove("DEMO_AI_LOS_URL");
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object o) {
        if (!(o instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    private List<String> stringList(Object o) {
        if (!(o instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            out.add(String.valueOf(item));
        }
        return out;
    }
}
