package com.los.core.creditintelligence.policystudio.parameters;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * POLICY-STUDIO-GATE2 — one authoritative answer to:
 * "Can this business concept / PolicyDsl operand obtain a trustworthy value?"
 * <p>
 * Reuses GACAT ({@link CanonicalParameterRegistry}) + executable calculator knowledge.
 * Does not invent unrelated parameters. Does not mutate GACAT.
 */
public final class BusinessConceptResolver {

    public static final String READY_EXISTING = "READY_EXISTING";
    public static final String READY_DERIVED = "READY_DERIVED";
    public static final String NEEDS_SOURCE_SELECTION = "NEEDS_SOURCE_SELECTION";
    public static final String NEEDS_PARAMETER_SELECTION = "NEEDS_PARAMETER_SELECTION";
    public static final String NEEDS_DERIVATION = "NEEDS_DERIVATION";
    public static final String NEEDS_MANUAL_INPUT = "NEEDS_MANUAL_INPUT";
    public static final String DATA_SOURCE_UNAVAILABLE = "DATA_SOURCE_UNAVAILABLE";
    public static final String NEEDS_CLARIFICATION = "NEEDS_CLARIFICATION";
    public static final String UNSUPPORTED = "UNSUPPORTED";

    /** Studio calculator path for non-CC write-offs (PolicyBureauMetricService). */
    public static final String WRITEOFF_NON_CC = "bureau.accounts.writeoff_non_cc";
    public static final String WRITEOFF_CC = "bureau.accounts.cc_writeoff";
    public static final String WRITEOFF_COUNT_GACAT = "bureau.written_off_account_count";

    private BusinessConceptResolver() {}

    public static Map<String, Object> resolve(String conceptText) {
        return resolve(conceptText, null);
    }

    /**
     * @param sourceConstraint optional source family (e.g. "Bureau") — constrains search
     */
    public static Map<String, Object> resolve(String conceptText, String sourceConstraint) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("allowCanonicalAuthority", false);
        out.put("operand", conceptText);
        out.put("businessConcept", conceptText);
        out.put("sourceConstraint", sourceConstraint);
        out.put("silentlyInvented", false);
        if (conceptText == null || conceptText.isBlank()) {
            out.put("resolutionState", NEEDS_CLARIFICATION);
            out.put("executable", false);
            out.put("message", "Business concept is empty");
            out.put("candidates", List.of());
            return out;
        }

        String concept = conceptText.trim();
        // Hard fail-closed: never treat write-off / credit-card wording as Proposed EDI
        if (BusinessConceptMatching.isWriteOffPhrase(concept)
                && BusinessConceptMatching.isProposedEdiPhrase(concept)
                && !concept.toLowerCase(Locale.ROOT).contains("proposed edi")) {
            // "credit" false-positive path — treat as write-off only
        } else if (BusinessConceptMatching.isProposedEdiPhrase(concept)
                && !BusinessConceptMatching.isWriteOffPhrase(concept)) {
            return resolveExactId("application.proposed_edi", concept, sourceConstraint, out);
        }

        if (BusinessConceptMatching.isWriteOffPhrase(concept)) {
            return resolveWriteOff(concept, sourceConstraint, out);
        }

        List<Map<String, Object>> candidates = searchCandidates(concept, sourceConstraint);
        out.put("candidates", candidates);
        if (candidates.isEmpty()) {
            if (sourceConstraint != null && !sourceConstraint.isBlank()) {
                out.put("resolutionState", DATA_SOURCE_UNAVAILABLE);
                out.put("executable", false);
                out.put("message", "No matching parameters in source " + sourceConstraint);
                out.put("suggestedSource", sourceConstraint);
                return out;
            }
            out.put("resolutionState", NEEDS_CLARIFICATION);
            out.put("executable", false);
            out.put("message", "Could not map this business concept to a catalogue parameter");
            out.put("blockers", List.of("Unknown concept — choose a source or clarify the term"));
            return out;
        }

        // Multiple strong candidates → ask user
        long strong = candidates.stream().filter(c -> intScore(c) >= 90).count();
        if (strong > 1 || (candidates.size() > 1 && intScore(candidates.get(0)) - intScore(candidates.get(1)) < 8)) {
            out.put("resolutionState", NEEDS_PARAMETER_SELECTION);
            out.put("executable", false);
            out.put("message", "Multiple plausible parameters — select one");
            out.put("suggestedSource", candidates.get(0).get("evaluatedFrom"));
            return out;
        }

        Map<String, Object> top = candidates.get(0);
        String type = String.valueOf(top.getOrDefault("type", ""));
        boolean executable = Boolean.TRUE.equals(top.get("policyStudioReady"))
                || Boolean.TRUE.equals(top.get("executable"));
        out.put("canonicalParameter", top.get("parameterId"));
        out.put("businessName", top.get("businessName"));
        out.put("source", top.get("evaluatedFrom"));
        out.put("suggestedSource", top.get("evaluatedFrom"));
        out.put("resolutionType", type);
        out.put("derivation", top.get("howCalculated"));
        Map<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("parameterId", top.get("parameterId"));
        provenance.put("evaluatedFrom", top.get("evaluatedFrom"));
        provenance.put("type", type);
        provenance.put("howCalculated", top.getOrDefault("howCalculated", ""));
        out.put("provenance", provenance);
        if (CanonicalParameterDefinition.MANUAL.equals(type)) {
            out.put("resolutionState", NEEDS_MANUAL_INPUT);
            out.put("executable", false);
            out.put("dataAvailability", ParameterResolutionSupport.AVAIL_MANUAL);
            out.put("message", "Requires manual input");
            stampExec(out, String.valueOf(top.get("parameterId")));
            return out;
        }
        if (!executable) {
            out.put("resolutionState", NEEDS_DERIVATION);
            out.put("executable", false);
            out.put("message", "Concept mapped but derivation/calculator is not executable yet");
            stampExec(out, String.valueOf(top.get("parameterId")));
            return out;
        }
        if (CanonicalParameterDefinition.DERIVED.equals(type)) {
            out.put("resolutionState", READY_DERIVED);
        } else {
            out.put("resolutionState", READY_EXISTING);
        }
        out.put("executable", true);
        out.put("dataAvailability", top.get("availability"));
        out.put("message", "Ready — existing catalogue binding");
        stampExec(out, String.valueOf(top.get("parameterId")));
        return out;
    }

    private static Map<String, Object> resolveWriteOff(
            String concept, String sourceConstraint, Map<String, Object> out) {
        out.put("businessConcept", "loan write-off / written-off accounts");
        out.put("suggestedSource", "Bureau");
        if (sourceConstraint != null && !sourceConstraint.isBlank()
                && !sourceConstraint.toLowerCase(Locale.ROOT).contains("bureau")) {
            out.put("resolutionState", DATA_SOURCE_UNAVAILABLE);
            out.put("executable", false);
            out.put("message", "Write-off history is a Bureau concept — selected source cannot supply it");
            out.put("candidates", List.of());
            return out;
        }

        boolean exceptCc = BusinessConceptMatching.isCreditCardExceptionPhrase(concept);
        List<Map<String, Object>> candidates = new ArrayList<>();

        // Prefer executable studio calculator for except-credit-cards
        Map<String, Object> nonCc = overlayDerived(
                WRITEOFF_NON_CC,
                "Non-credit-card write-off count",
                "Bureau",
                "Count of written-off tradelines excluding credit cards (PolicyBureauMetricService.writeoffCounts)",
                List.of("bureau.tradeline.write_off_amount", "bureau.tradeline.account_status"),
                true);
        Map<String, Object> allWo = fromRegistry(WRITEOFF_COUNT_GACAT);
        Map<String, Object> amount = fromRegistry("bureau.tradeline.write_off_amount");
        Map<String, Object> ccOnly = overlayDerived(
                WRITEOFF_CC,
                "Credit-card write-off count",
                "Bureau",
                "Count of written-off credit-card tradelines",
                List.of("bureau.tradeline.write_off_amount"),
                true);

        if (exceptCc) {
            candidates.add(withScore(nonCc, 100));
            if (allWo != null) candidates.add(withScore(allWo, 70));
            if (ccOnly != null) candidates.add(withScore(ccOnly, 60));
            out.put("candidates", candidates);
            out.put("canonicalParameter", WRITEOFF_NON_CC);
            out.put("businessName", "Non-credit-card write-off count");
            out.put("source", "Bureau");
            out.put("resolutionType", CanonicalParameterDefinition.DERIVED);
            out.put("resolutionState", READY_DERIVED);
            out.put("executable", true);
            out.put("derivation", nonCc.get("howCalculated"));
            out.put("exceptionModel", "ALLOW credit-card write-offs; reject when non-CC write-off count > 0");
            out.put("suggestedExpression", Map.of(
                    "op", "LTE",
                    "left", Map.of("metric", WRITEOFF_NON_CC),
                    "right", Map.of("const", 0)));
            out.put("provenance", Map.of(
                    "parameterId", WRITEOFF_NON_CC,
                    "evaluatedFrom", "Bureau",
                    "calculator", "PolicyBureauMetricService.writeoffCounts",
                    "rawIngredients", List.of(
                            "bureau.tradeline.write_off_amount",
                            "bureau.tradeline.account_status",
                            "tradeline.creditCard flag")));
            out.put("message", "Mapped to Bureau non-credit-card write-off count (not Proposed EDI)");
            out.put("mappedToProposedEdi", false);
            ParameterExecutabilitySupport.stampOnto(out,
                    ParameterExecutabilitySupport.evaluate(WRITEOFF_NON_CC));
            // Gate-3 honesty: READY_DERIVED here means Policy-Test-ready, not production.
            out.put("policyTestReady", true);
            out.put("runtimeReady", false);
            out.put("productionReady", false);
            return out;
        }

        if (allWo != null) candidates.add(withScore(allWo, 95));
        candidates.add(withScore(nonCc, 85));
        if (amount != null) candidates.add(withScore(amount, 75));
        out.put("candidates", candidates);
        out.put("mappedToProposedEdi", false);
        if (candidates.size() > 1) {
            out.put("resolutionState", NEEDS_PARAMETER_SELECTION);
            out.put("executable", false);
            out.put("message", "Multiple Bureau write-off parameters — select the intended measure");
            return out;
        }
        return resolveExactId(WRITEOFF_COUNT_GACAT, concept, "Bureau", out);
    }

    private static Map<String, Object> resolveExactId(
            String id, String concept, String sourceConstraint, Map<String, Object> out) {
        Map<String, Object> row = fromRegistry(id);
        if (row == null) {
            out.put("resolutionState", UNSUPPORTED);
            out.put("executable", false);
            out.put("candidates", List.of());
            out.put("message", "Catalogue entry missing: " + id);
            return out;
        }
        out.put("candidates", List.of(withScore(row, 100)));
        out.put("canonicalParameter", id);
        out.put("businessName", row.get("businessName"));
        out.put("source", row.get("evaluatedFrom"));
        out.put("resolutionType", row.get("type"));
        boolean exec = Boolean.TRUE.equals(row.get("executable"))
                || Boolean.TRUE.equals(row.get("policyStudioReady"));
        out.put("executable", exec);
        if (CanonicalParameterDefinition.MANUAL.equals(row.get("type"))) {
            // Known mapped manual parameter — READY_EXISTING means "mapped"; capture still manual.
            out.put("resolutionState", READY_EXISTING);
            out.put("executable", true);
            out.put("dataAvailability", ParameterResolutionSupport.AVAIL_MANUAL);
        } else if (!exec) {
            out.put("resolutionState", NEEDS_DERIVATION);
        } else if (CanonicalParameterDefinition.DERIVED.equals(row.get("type"))) {
            out.put("resolutionState", READY_DERIVED);
        } else {
            out.put("resolutionState", READY_EXISTING);
        }
        out.put("provenance", Map.of(
                "parameterId", id,
                "evaluatedFrom", row.get("evaluatedFrom"),
                "type", row.get("type")));
        out.put("message", "Mapped to " + row.get("businessName"));
        stampExec(out, id);
        return out;
    }

    private static List<Map<String, Object>> searchCandidates(String concept, String sourceConstraint) {
        String q = BusinessConceptMatching.normalize(concept);
        List<Map<String, Object>> scored = new ArrayList<>();
        for (CanonicalParameterDefinition def : CanonicalParameterRegistry.shared().all()) {
            if (sourceConstraint != null && !sourceConstraint.isBlank()) {
                String from = def.evaluatedFrom() == null ? "" : def.evaluatedFrom();
                if (!from.toLowerCase(Locale.ROOT).contains(sourceConstraint.toLowerCase(Locale.ROOT))
                        && !sourceConstraint.toLowerCase(Locale.ROOT).contains(from.toLowerCase(Locale.ROOT))) {
                    continue;
                }
            }
            int score = scoreDef(def, q, concept);
            if (score <= 0) continue;
            // Never surface Proposed EDI for write-off / credit-card concepts
            if (BusinessConceptMatching.isWriteOffPhrase(concept)
                    && "application.proposed_edi".equals(def.id())) {
                continue;
            }
            if (!BusinessConceptMatching.isProposedEdiPhrase(concept)
                    && "application.proposed_edi".equals(def.id())
                    && score < 95) {
                continue;
            }
            Map<String, Object> row = toRow(def);
            row.put("score", score + BusinessConceptMatching.sourceAffinity(concept, def.evaluatedFrom()));
            scored.add(row);
        }
        scored.sort(Comparator.comparingInt(BusinessConceptResolver::intScore).reversed());
        if (scored.size() > 12) return new ArrayList<>(scored.subList(0, 12));
        return scored;
    }

    private static int scoreDef(CanonicalParameterDefinition def, String qNorm, String original) {
        int score = 0;
        if (def.id() != null && BusinessConceptMatching.normalize(def.id()).contains(qNorm)) {
            score = Math.max(score, 70);
        }
        if (def.businessName() != null) {
            String n = BusinessConceptMatching.normalize(def.businessName());
            if (n.equals(qNorm)) score = Math.max(score, 100);
            else if (n.contains(qNorm) || qNorm.contains(n)) score = Math.max(score, 88);
        }
        if (def.aliases() != null) {
            for (String a : def.aliases()) {
                if (BusinessConceptMatching.aliasMatches(original, a)
                        || BusinessConceptMatching.aliasMatches(qNorm, a)) {
                    String al = BusinessConceptMatching.normalize(a);
                    score = Math.max(score, al.length() <= 3 ? 96 : 94);
                }
            }
        }
        return score;
    }

    private static Map<String, Object> fromRegistry(String id) {
        return CanonicalParameterRegistry.shared().findById(id).map(BusinessConceptResolver::toRow).orElse(null);
    }

    private static Map<String, Object> toRow(CanonicalParameterDefinition def) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("parameterId", def.id());
        m.put("id", def.id());
        m.put("businessName", def.businessName());
        m.put("evaluatedFrom", def.evaluatedFrom());
        m.put("type", def.type());
        m.put("availability", def.availability());
        m.put("howCalculated", def.calculationSummary());
        m.put("unit", def.unit());
        // Gate-3: Policy Studio ready requires an implemented calculator — NOT derivationDefined alone.
        Map<String, Object> exec = ParameterExecutabilitySupport.evaluate(def);
        boolean studioReady = Boolean.TRUE.equals(exec.get("policyTestReady"));
        m.put("policyStudioReady", studioReady);
        m.put("executable", studioReady);
        m.put("executionState", exec.get("executionState"));
        m.put("policyTestReady", exec.get("policyTestReady"));
        m.put("runtimeReady", exec.get("runtimeReady"));
        m.put("productionReady", exec.get("productionReady"));
        m.put("aliases", def.aliases());
        return m;
    }

    private static Map<String, Object> overlayDerived(
            String id, String name, String source, String how,
            List<String> ingredients, boolean executable) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("parameterId", id);
        m.put("id", id);
        m.put("businessName", name);
        m.put("evaluatedFrom", source);
        m.put("type", CanonicalParameterDefinition.DERIVED);
        m.put("availability", ParameterResolutionSupport.AVAIL_DERIVABLE);
        m.put("howCalculated", how);
        m.put("rawIngredients", ingredients);
        m.put("policyStudioReady", executable);
        m.put("executable", executable);
        m.put("authoringOverlay", true);
        m.put("calculator", "PolicyBureauMetricService");
        Map<String, Object> exec = ParameterExecutabilitySupport.studioOverlay(
                id, "PolicyBureauMetricService.writeoffCounts", ingredients);
        ParameterExecutabilitySupport.stampOnto(m, exec);
        return m;
    }

    private static void stampExec(Map<String, Object> out, String parameterId) {
        if (parameterId == null || parameterId.isBlank() || "null".equals(parameterId)) return;
        ParameterExecutabilitySupport.stampOnto(out, ParameterExecutabilitySupport.evaluate(parameterId));
    }

    private static Map<String, Object> withScore(Map<String, Object> row, int score) {
        Map<String, Object> copy = new LinkedHashMap<>(row);
        copy.put("score", score);
        return copy;
    }

    private static int intScore(Map<String, Object> c) {
        Object s = c.get("score");
        if (s instanceof Number n) return n.intValue();
        return 0;
    }
}
