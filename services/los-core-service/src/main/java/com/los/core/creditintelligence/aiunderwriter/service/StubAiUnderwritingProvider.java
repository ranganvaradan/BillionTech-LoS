package com.los.core.creditintelligence.aiunderwriter.service;

import com.los.core.creditintelligence.aiunderwriter.domain.AiOutputType;
import com.los.core.creditintelligence.aiunderwriter.domain.AiUnderwritingContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic grounded stub — no live LLM required. Never uses DEMO_AI_LOS_URL.
 */
@Component
public class StubAiUnderwritingProvider implements AiUnderwritingProvider {

    public static final String PROVIDER = "stub";
    public static final String MODEL = "stub-grounded-v1";
    public static final String MODEL_VERSION = "A1";

    @Override
    public ProviderResponse analyze(
            AiUnderwritingContext context,
            List<String> requestedOutputTypes,
            Map<String, Object> promptVersions) {
        List<String> types = requestedOutputTypes == null || requestedOutputTypes.isEmpty()
                ? List.of(AiOutputType.NARRATIVE.name(), AiOutputType.EXPLANATION.name(), AiOutputType.QUESTION.name())
                : requestedOutputTypes;
        List<RawSuggestion> out = new ArrayList<>();
        for (String type : types) {
            out.add(buildSuggestion(type, context, promptVersions));
        }
        return new ProviderResponse(true, null, PROVIDER, MODEL, MODEL_VERSION, out);
    }

    private RawSuggestion buildSuggestion(
            String type, AiUnderwritingContext ctx, Map<String, Object> promptVersions) {
        String promptVer = resolvePrompt(type, promptVersions);
        Map<String, Object> metrics = ctx.knownMetrics();
        List<Object> evidenceRefs = new ArrayList<>();
        for (Map.Entry<String, Object> e : metrics.entrySet()) {
            evidenceRefs.add(Map.of("kind", "METRIC", "reference", e.getKey()));
        }
        List<Object> limitations = List.of(
                "AI_SUGGESTION — non-authoritative",
                "Human review required",
                "Does not approve, reject, sanction, or disburse"
        );

        return switch (type) {
            case "EXPLANATION" -> explanation(ctx, promptVer, evidenceRefs, limitations);
            case "QUESTION" -> questions(ctx, promptVer, evidenceRefs, limitations);
            case "CAM_DRAFT", "CREDIT_NOTE_DRAFT" -> camDraft(ctx, type, promptVer, evidenceRefs, limitations);
            case "ALTERNATE_STRUCTURE_SUGGESTION" -> alternate(ctx, promptVer, evidenceRefs, limitations);
            case "SCENARIO" -> scenarioNote(ctx, promptVer, evidenceRefs, limitations);
            case "ANOMALY" -> anomaly(ctx, promptVer, evidenceRefs, limitations);
            case "POLICY_CLARIFICATION_SUGGESTION" -> policyClarification(ctx, promptVer, evidenceRefs, limitations);
            default -> narrative(ctx, promptVer, evidenceRefs, limitations);
        };
    }

    private RawSuggestion narrative(
            AiUnderwritingContext ctx, String promptVer, List<Object> refs, List<Object> limitations) {
        StringBuilder sb = new StringBuilder();
        sb.append("AI-generated — non-authoritative underwriting summary.\n");
        sb.append("Borrower overview: case from supplied application context.\n");
        sb.append("Data coverage and source sections derived from CreditEvidenceView only.\n");
        appendMetricLine(sb, ctx, "gst.turnover.trailing_12m", "GST turnover");
        appendMetricLine(sb, ctx, "bank.turnover.trailing_12m", "Bank turnover");
        appendMetricLine(sb, ctx, "bureau.emi.monthly", "Bureau EMI");
        Object outcome = ctx.policyOutcome().get("recommendationOutcome");
        if (outcome == null) {
            outcome = ctx.policyOutcome().get("overallOutcome");
        }
        if (outcome != null) {
            sb.append("Policy/recommendation outcome (canonical): ").append(outcome).append(".\n");
        }
        appendMetricLine(sb, ctx, "recommendation.amount", "Canonical recommended amount");
        sb.append("Key risks: see material reconciliations and open questions.\n");
        sb.append("Key strengths: evidence strength section when present.\n");
        sb.append("Open questions: ").append(ctx.investigationQuestions().size()).append(" deterministic items.\n");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sections", List.of(
                "Borrower overview", "Data coverage", "Bureau", "Banking", "GST", "ITR/tax",
                "Turnover triangulation", "Obligations", "Policy outcome", "Recommended structure",
                "Conditions", "Key risks", "Key strengths", "Open questions"));
        payload.put("outputMarker", "AI_SUGGESTION");

        return new RawSuggestion(
                AiOutputType.NARRATIVE.name(),
                "Underwriting summary",
                sb.toString(),
                payload,
                refs,
                List.of(Map.of("kind", "EVALUATION_CONTEXT",
                        "reference", ctx.evaluationContextId() == null ? "" : ctx.evaluationContextId().toString())),
                limitations,
                promptVer,
                0.55
        );
    }

    private RawSuggestion explanation(
            AiUnderwritingContext ctx, String promptVer, List<Object> refs, List<Object> limitations) {
        StringBuilder sb = new StringBuilder();
        sb.append("AI-generated — non-authoritative policy explanation.\n");
        if (ctx.materialReconciliations().isEmpty() && ctx.knownRuleIds().isEmpty()) {
            sb.append("No deterministic rule/reconciliation evidence supplied; cannot invent an explanation.\n");
        } else {
            for (Map.Entry<String, Object> e : ctx.materialReconciliations().entrySet()) {
                sb.append("Rule/recon ").append(e.getKey()).append(": see deterministic detail; AI does not change outcome.\n");
                refs = new ArrayList<>(refs);
                refs.add(Map.of("kind", "RECONCILIATION", "reference", e.getKey()));
            }
            for (String ruleId : ctx.knownRuleIds().keySet()) {
                if (!ctx.materialReconciliations().containsKey(ruleId)) {
                    sb.append("Rule ").append(ruleId).append(" present in context.\n");
                }
            }
        }
        return new RawSuggestion(
                AiOutputType.EXPLANATION.name(),
                "Policy explanation",
                sb.toString(),
                Map.of("outputMarker", "AI_SUGGESTION"),
                refs,
                List.of(),
                limitations,
                promptVer,
                0.5
        );
    }

    private RawSuggestion questions(
            AiUnderwritingContext ctx, String promptVer, List<Object> refs, List<Object> limitations) {
        List<Map<String, Object>> refined = new ArrayList<>();
        StringBuilder sb = new StringBuilder("AI-generated — non-authoritative investigation questions.\n");
        for (Map<String, Object> q : ctx.investigationQuestions()) {
            String original = String.valueOf(q.getOrDefault("question",
                    q.getOrDefault("text", q.toString())));
            String rewritten = "Please clarify: " + original;
            refined.add(Map.of(
                    "originalDeterministicQuestion", original,
                    "aiRewrittenQuestion", rewritten,
                    "evidenceRefs", q.getOrDefault("evidenceRefs", List.of())
            ));
            sb.append("- ").append(rewritten).append("\n");
        }
        if (refined.isEmpty()) {
            sb.append("No deterministic investigation questions supplied.\n");
        }
        return new RawSuggestion(
                AiOutputType.QUESTION.name(),
                "Investigation questions",
                sb.toString(),
                Map.of("questions", refined, "outputMarker", "AI_SUGGESTION"),
                refs,
                List.of(),
                limitations,
                promptVer,
                0.5
        );
    }

    private RawSuggestion camDraft(
            AiUnderwritingContext ctx, String type, String promptVer,
            List<Object> refs, List<Object> limitations) {
        StringBuilder sb = new StringBuilder();
        sb.append("AI-generated — non-authoritative CAM draft. Underwriter must review/edit before use.\n");
        sb.append("Does not mutate CAM sanction fields.\n");
        appendMetricLine(sb, ctx, "recommendation.amount", "Proposed facility (canonical recommendation)");
        appendMetricLine(sb, ctx, "gst.turnover.trailing_12m", "GST turnover");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("camSections", List.of("Overview", "Evidence", "Policy", "Recommendation", "Conditions"));
        payload.put("outputMarker", "AI_SUGGESTION");
        payload.put("mutatesCamFields", false);
        return new RawSuggestion(
                type,
                "CAM draft",
                sb.toString(),
                payload,
                refs,
                List.of(),
                limitations,
                promptVer,
                0.45
        );
    }

    private RawSuggestion alternate(
            AiUnderwritingContext ctx, String promptVer, List<Object> refs, List<Object> limitations) {
        Object amount = metricValue(ctx, "recommendation.amount");
        Object tenure = metricValue(ctx, "recommendation.tenureMonths");
        StringBuilder sb = new StringBuilder();
        sb.append("AI-generated — non-authoritative alternate structure suggestion.\n");
        sb.append("Canonical recommendation: amount=").append(amount).append(" tenure=").append(tenure).append(".\n");
        sb.append("Suggestion: consider a lower amount or longer tenure if lender policy permits; ");
        sb.append("would reduce monthly obligation. Status: AI_SUGGESTION. No automatic adoption.\n");
        return new RawSuggestion(
                AiOutputType.ALTERNATE_STRUCTURE_SUGGESTION.name(),
                "Alternate structure",
                sb.toString(),
                Map.of(
                        "canonicalAmount", amount == null ? "" : amount,
                        "canonicalTenure", tenure == null ? "" : tenure,
                        "outputMarker", "AI_SUGGESTION",
                        "overwritesRecommendation", false
                ),
                refs,
                List.of(),
                limitations,
                promptVer,
                0.4
        );
    }

    private RawSuggestion scenarioNote(
            AiUnderwritingContext ctx, String promptVer, List<Object> refs, List<Object> limitations) {
        return new RawSuggestion(
                AiOutputType.SCENARIO.name(),
                "Scenario placeholder",
                "AI-generated — non-authoritative. Use /scenario to invoke deterministic Decision Engine; AI only explains.",
                Map.of("requiresDeterministicEngine", true, "outputMarker", "AI_SUGGESTION"),
                refs,
                List.of(),
                limitations,
                promptVer,
                0.4
        );
    }

    private RawSuggestion anomaly(
            AiUnderwritingContext ctx, String promptVer, List<Object> refs, List<Object> limitations) {
        StringBuilder sb = new StringBuilder("AI-generated — non-authoritative anomaly observations.\n");
        for (String code : ctx.materialReconciliations().keySet()) {
            sb.append("Material reconciliation flagged: ").append(code).append(".\n");
        }
        if (ctx.materialReconciliations().isEmpty()) {
            sb.append("No material reconciliations in context.\n");
        }
        return new RawSuggestion(
                AiOutputType.ANOMALY.name(),
                "Anomaly observations",
                sb.toString(),
                Map.of("outputMarker", "AI_SUGGESTION"),
                refs,
                List.of(),
                limitations,
                promptVer,
                0.45
        );
    }

    private RawSuggestion policyClarification(
            AiUnderwritingContext ctx, String promptVer, List<Object> refs, List<Object> limitations) {
        return new RawSuggestion(
                AiOutputType.POLICY_CLARIFICATION_SUGGESTION.name(),
                "Policy clarification suggestion",
                "AI-generated — non-authoritative. Suggests Policy Studio review; cannot edit active/shadow package.",
                Map.of("routesToPolicyStudio", true, "outputMarker", "AI_SUGGESTION", "mutatesPolicy", false),
                refs,
                List.of(),
                limitations,
                promptVer,
                0.4
        );
    }

    private void appendMetricLine(StringBuilder sb, AiUnderwritingContext ctx, String path, String label) {
        Object v = metricValue(ctx, path);
        if (v != null) {
            sb.append(label).append(": ").append(v).append(" [").append(path).append("].\n");
        }
    }

    private Object metricValue(AiUnderwritingContext ctx, String path) {
        Object entry = ctx.knownMetrics().get(path);
        if (entry instanceof Map<?, ?> m) {
            return m.get("value");
        }
        return entry;
    }

    private String resolvePrompt(String type, Map<String, Object> promptVersions) {
        if (promptVersions != null && promptVersions.get(type) instanceof Map<?, ?> m) {
            Object code = m.get("templateCode");
            Object ver = m.get("version");
            return String.valueOf(code == null ? "UNDERWRITING_SUMMARY_V1" : code)
                    + "@" + (ver == null ? "1" : ver);
        }
        return "UNDERWRITING_SUMMARY_V1@1";
    }
}
