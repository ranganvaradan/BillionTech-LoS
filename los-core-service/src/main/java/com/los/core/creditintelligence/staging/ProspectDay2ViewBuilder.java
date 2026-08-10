package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyAmbiguity;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyClause;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyInterpretation;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.ReviewState;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Day-2 Credit Head read models: ambiguity cards, rule cards, readiness banner.
 * No underwriting/engine changes — presentation + status derivation only.
 */
final class ProspectDay2ViewBuilder {

    private ProspectDay2ViewBuilder() {}

    static void enrich(Map<String, Object> out, PolicyStudioSession session) {
        List<Map<String, Object>> ambiguityCards = ambiguityCards(session);
        List<Map<String, Object>> ruleCards = ruleCards(session);
        Map<String, Object> banner = readinessBanner(session, ambiguityCards, ruleCards);

        out.put("ambiguityCards", ambiguityCards);
        out.put("ruleCards", ruleCards);
        out.put("readinessBanner", banner);
        out.put("ambiguityCategories", categoryCounts(ambiguityCards));
        out.put("ambiguityFilters", List.of("All", "Blocking", "Non-blocking", "Resolved"));

        // Enrich counts used by summary banner
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = out.get("counts") instanceof Map<?, ?>
                ? new LinkedHashMap<>((Map<String, Object>) out.get("counts"))
                : new LinkedHashMap<>();
        long readyRules = ruleCards.stream()
                .filter(r -> Set.of("Ready", "Accepted", "Edited", "Approved").contains(String.valueOf(r.get("status"))))
                .count();
        long needsReview = ruleCards.stream()
                .filter(r -> Set.of("Needs your input", "Needs Review", "Blocked").contains(String.valueOf(r.get("status"))))
                .count();
        long blockedRules = ruleCards.stream().filter(r -> "Blocked".equals(r.get("status"))).count();
        long approvedRules = ruleCards.stream()
                .filter(r -> Set.of("Accepted", "Edited", "Approved").contains(String.valueOf(r.get("status"))))
                .count();
        long ignoredRules = ruleCards.stream().filter(r -> "Ignored".equals(r.get("status"))).count();
        long deletedRules = ruleCards.stream().filter(r -> "Deleted".equals(r.get("status"))).count();
        long manualInputRules = ruleCards.stream().filter(r -> "Manual Input".equals(r.get("status"))).count();
        long dataReq = ruleCards.stream().filter(r -> "Data requirement".equals(r.get("status"))).count();
        long metricAdj = ruleCards.stream().filter(r -> "Metric adjustment".equals(r.get("status"))).count();
        long nonUw = ruleCards.stream().filter(r -> "Non-underwriting".equals(r.get("status"))).count();
        long openAmb = ambiguityCards.stream()
                .filter(a -> Boolean.TRUE.equals(a.get("open"))).count();
        long missingMetrics = ambiguityCards.stream()
                .filter(a -> "Missing Data".equals(a.get("category")) && Boolean.TRUE.equals(a.get("open")))
                .count();
        counts.put("rulesTotal", ruleCards.size());
        counts.put("rulesReady", readyRules);
        counts.put("rulesNeedReview", needsReview);
        counts.put("rulesBlocked", blockedRules);
        counts.put("rulesApproved", approvedRules);
        counts.put("rulesIgnored", ignoredRules);
        counts.put("rulesIgnoredByYou", ignoredRules);
        counts.put("rulesDeleted", deletedRules);
        counts.put("rulesManualInput", manualInputRules);
        counts.put("rulesDataRequirements", dataReq);
        counts.put("rulesMetricAdjustments", metricAdj);
        counts.put("rulesNonUnderwriting", nonUw);
        counts.put("underwritingRules",
                ruleCards.size() - dataReq - metricAdj - nonUw - deletedRules);
        counts.put("openAmbiguities", openAmb);
        counts.put("missingMetrics", missingMetrics);
        out.put("counts", counts);

        // POLICY-UX-2D ingestion binding summary for post-upload Rules landing
        Object ingestion = null;
        if (session.getPreview() != null) {
            ingestion = session.getPreview().get("ingestionBinding");
        }
        if (ingestion == null && session.getDocument() != null && session.getDocument().getMetadata() != null) {
            ingestion = session.getDocument().getMetadata().get("ingestionBinding");
        }
        if (ingestion instanceof Map<?, ?>) {
            out.put("ingestionBinding", ingestion);
        }
    }

    private static Map<String, Object> readinessBanner(
            PolicyStudioSession session,
            List<Map<String, Object>> ambiguityCards,
            List<Map<String, Object>> ruleCards) {
        Map<String, Object> readiness = session.getReadiness() == null ? Map.of() : session.getReadiness();
        Object score = readiness.get("score");
        long openAmb = ambiguityCards.stream().filter(a -> Boolean.TRUE.equals(a.get("open"))).count();
        long ready = ruleCards.stream()
                .filter(r -> Set.of("Ready", "Accepted", "Edited", "Approved", "Manual Input")
                        .contains(String.valueOf(r.get("status"))))
                .count();
        long needs = ruleCards.stream()
                .filter(r -> Set.of("Needs your input", "Needs Review", "Blocked").contains(String.valueOf(r.get("status"))))
                .count();
        long ignored = ruleCards.stream().filter(r -> "Ignored".equals(r.get("status"))).count();
        long dataReq = ruleCards.stream().filter(r -> "Data requirement".equals(r.get("status"))).count();
        long metricAdj = ruleCards.stream().filter(r -> "Metric adjustment".equals(r.get("status"))).count();
        long missing = ambiguityCards.stream()
                .filter(a -> "Missing Data".equals(a.get("category")) && Boolean.TRUE.equals(a.get("open")))
                .count();

        Map<String, Object> banner = new LinkedHashMap<>();
        banner.put("totalClauses", session.getClauses().size());
        banner.put("rulesIdentified", ruleCards.size());
        banner.put("rulesReady", ready);
        banner.put("rulesNeedReview", needs);
        banner.put("rulesIgnored", ignored);
        banner.put("rulesIgnoredByYou", ignored);
        banner.put("rulesDataRequirements", dataReq);
        banner.put("rulesMetricAdjustments", metricAdj);
        banner.put("missingMetrics", missing);
        banner.put("ambiguities", openAmb);
        banner.put("testsGenerated", session.getTestCases().size());
        banner.put("policyReadinessPercent", score == null ? 0 : score);
        banner.put("readinessGrade", readiness.getOrDefault("grade", "DRAFT"));
        banner.put("note", readiness.getOrDefault("note", "Authoring readiness only — not a credit score"));
        return banner;
    }

    private static List<Map<String, Object>> categoryCounts(List<Map<String, Object>> cards) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String cat : List.of(
                "Terminology", "Metric Definition", "Boundary Condition",
                "Missing Data", "Product Scope", "Rule Outcome")) {
            counts.put(cat, 0L);
        }
        for (Map<String, Object> c : cards) {
            if (!Boolean.TRUE.equals(c.get("open"))) {
                continue;
            }
            String cat = String.valueOf(c.getOrDefault("category", "Terminology"));
            counts.merge(cat, 1L, Long::sum);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            if (e.getValue() > 0) {
                out.add(Map.of("category", e.getKey(), "count", e.getValue()));
            }
        }
        return out;
    }

    private static List<Map<String, Object>> ambiguityCards(PolicyStudioSession session) {
        Map<UUID, CiPolicyClause> clauses = session.getClauses().stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(CiPolicyClause::getId, c -> c, (a, b) -> a, LinkedHashMap::new));
        Map<UUID, CiPolicyInterpretation> interps = session.getInterpretations().stream()
                .filter(i -> i.getClauseId() != null)
                .collect(Collectors.toMap(CiPolicyInterpretation::getClauseId, i -> i, (a, b) -> a, LinkedHashMap::new));

        List<Map<String, Object>> cards = new ArrayList<>();
        for (CiPolicyAmbiguity a : session.getAmbiguities()) {
            CiPolicyClause clause = a.getClauseId() == null ? null : clauses.get(a.getClauseId());
            CiPolicyInterpretation interp = a.getClauseId() == null ? null : interps.get(a.getClauseId());
            cards.add(toAmbiguityCard(a, clause, interp));
        }
        cards.sort(Comparator
                .comparing((Map<String, Object> m) -> !"OPEN".equals(m.get("resolutionStatus")))
                .thenComparing(m -> !Boolean.TRUE.equals(m.get("blocking")))
                .thenComparing(m -> String.valueOf(m.get("unclearTerm"))));
        return cards;
    }

    private static Map<String, Object> toAmbiguityCard(
            CiPolicyAmbiguity a, CiPolicyClause clause, CiPolicyInterpretation interp) {
        String phrase = a.getPhrase() == null ? "" : a.getPhrase();
        String type = a.getAmbiguityType() == null ? "" : a.getAmbiguityType();
        boolean open = "OPEN".equals(a.getResolutionStatus())
                || "CLARIFICATION_REQUESTED".equals(a.getResolutionStatus());
        boolean blocking = open && ("MATERIAL".equalsIgnoreCase(a.getSeverity())
                || "BOUNDARY_AMBIGUITY".equals(type)
                || "MISSING_METRIC".equals(type)
                || "UNKNOWN_BUSINESS_TERM".equals(type));

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", a.getId() == null ? null : a.getId().toString());
        card.put("unclearTerm", phrase);
        card.put("sourceClause", clause == null ? null : clause.getSourceText());
        card.put("section", clause == null ? null : clause.getSection());
        card.put("pageOrSection", clause == null ? null
                : (clause.getSection() == null ? clause.getClauseNumber() : clause.getSection()));
        card.put("whyConfirmationNeeded", whyNeeded(phrase, type, a.getDescription()));
        card.put("systemInterpretation", systemInterpretation(phrase, type, a.getDescription(), interp));
        card.put("category", categoryFor(type, phrase));
        card.put("typeLabel", friendlyType(type));
        card.put("severity", a.getSeverity());
        card.put("blocking", blocking);
        card.put("open", open);
        card.put("resolutionStatus", a.getResolutionStatus());
        card.put("resolvedOption", a.getResolvedOption());
        card.put("confidence", a.getConfidence() == null ? 0.7 : a.getConfidence().doubleValue());
        card.put("recommendedOption", a.getRecommendedOption());
        card.put("recommendedLabel", labelForOption(phrase, a.getRecommendedOption()));
        card.put("choices", choicesFor(phrase, a.getCandidateOptions(), a.getRecommendedOption()));
        card.put("canonicalMappingBusiness", mappingBusiness(phrase, a.getRecommendedOption()));
        card.put("impactIfUnresolved", impactIfUnresolved(phrase, type));
        card.put("demoHighlight", demoHighlight(phrase));
        card.put("policyContext", policyContext(phrase, clause));
        card.put("rememberDefinitionSupported", true);
        card.put("rememberScopes", List.of(
                Map.of("value", "DOCUMENT", "label", "This Policy Only"),
                Map.of("value", "PRODUCT", "label", "This Product"),
                Map.of("value", "TENANT", "label", "This Lender/Tenant")));
        card.put("vocabularyNote",
                "Remembered definitions appear as previous approved suggestions — they are not automatically authoritative.");
        return card;
    }

    private static String demoHighlight(String phrase) {
        String p = phrase == null ? "" : phrase.toLowerCase(Locale.ROOT);
        if (p.contains("edi") && !p.contains("credit")) {
            return "EDI";
        }
        if (p.contains("exactly 100") || (p.contains("100") && p.contains("transaction"))) {
            return "EXACTLY_100";
        }
        if (p.contains("clean")) {
            return "CLEAN";
        }
        if (p.equals("ntc") || p.startsWith("ntc")) {
            return "NTC";
        }
        return null;
    }

    private static Map<String, Object> policyContext(String phrase, CiPolicyClause clause) {
        String p = phrase == null ? "" : phrase.toLowerCase(Locale.ROOT);
        Map<String, Object> ctx = new LinkedHashMap<>();
        if (p.contains("exactly 100") || (p.contains("100") && p.contains("transaction"))) {
            ctx.put("headline", "Boundary at exactly 100 transactions is unspecified");
            ctx.put("bullets", List.of(
                    "Policy says: >100 → percentage rule (5%)",
                    "Policy says: <100 → count rule (5 returns)",
                    "The policy does not say what happens at exactly 100."));
        } else if (p.contains("clean")) {
            ctx.put("headline", "CLEAN history is not defined in measurable terms");
            ctx.put("bullets", List.of(
                    "Policy says: new loan should have 6 months CLEAN history",
                    "BillionTech will not choose a definition automatically."));
        }
        if (clause != null && clause.getSourceText() != null) {
            ctx.put("quotedText", clause.getSourceText());
        }
        return ctx;
    }

    private static List<Map<String, Object>> choicesFor(String phrase, List<Object> options, String recommended) {
        List<String> keys = curatedKeys(phrase, options);
        List<Map<String, Object>> out = new ArrayList<>();
        for (String key : keys) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("value", key);
            c.put("label", labelForOption(phrase, key));
            c.put("recommended", recommended != null && recommended.equals(key));
            c.put("businessMapping", mappingBusiness(phrase, key));
            out.add(c);
        }
        return out;
    }

    private static List<String> curatedKeys(String phrase, List<Object> options) {
        String p = phrase == null ? "" : phrase.toLowerCase(Locale.ROOT);
        if (p.equals("edi") || (p.contains("edi") && !p.contains("credit"))) {
            return List.of(
                    "application.proposed_edi",
                    "eligible_disposable_income",
                    "proposed_emi",
                    "CREATE_POLICY_PARAMETER",
                    "EXISTING_LENDER_PARAMETER",
                    "OTHER");
        }
        if (p.contains("exactly 100") || (p.contains("100") && p.contains("transaction"))) {
            return List.of(
                    "treat_100_as_ratio_branch",
                    "treat_100_as_count_branch",
                    "create_separate_rule_for_100",
                    "ASK_CUSTOMER");
        }
        if (p.contains("clean")) {
            return List.of(
                    "DPD_EQ_0",
                    "DPD_LTE_30",
                    "NO_ADVERSE_BUREAU_STATUS",
                    "CUSTOMER_COMPOSITE_RULE",
                    "OTHER",
                    "ASK_CUSTOMER");
        }
        List<String> keys = new ArrayList<>();
        if (options != null) {
            for (Object o : options) {
                keys.add(String.valueOf(o));
            }
        }
        if (keys.stream().noneMatch(k -> k.equalsIgnoreCase("ASK_CUSTOMER") || k.equalsIgnoreCase("OTHER"))) {
            keys.add("ASK_CUSTOMER");
        }
        return keys;
    }

    private static String labelForOption(String phrase, String key) {
        if (key == null) {
            return "Other";
        }
        String p = phrase == null ? "" : phrase.toLowerCase(Locale.ROOT);
        String k = key.trim();
        if (p.contains("edi") || "application.proposed_edi".equals(k) || "proposed_emi".equals(k)
                || "eligible_disposable_income".equals(k)) {
            return switch (k) {
                case "application.proposed_edi" -> "Proposed EDI (application field)";
                case "eligible_disposable_income" -> "Eligible Disposable Income";
                case "proposed_emi" -> "Proposed EMI";
                case "CREATE_POLICY_PARAMETER", "CREATE_NEW_APPLICATION_FIELD" -> "Customer-defined policy parameter";
                case "EXISTING_LENDER_PARAMETER" -> "Existing lender parameter";
                case "OTHER" -> "Other / define manually";
                case "ASK_CUSTOMER" -> "Ask customer / keep unresolved";
                default -> humanizeKey(k);
            };
        }
        if (p.contains("100")) {
            return switch (k) {
                case "treat_100_as_ratio_branch" -> "Treat 100 with percentage rule";
                case "treat_100_as_count_branch" -> "Treat 100 with count rule";
                case "create_separate_rule_for_100", "DATA_INSUFFICIENT_AT_100" -> "Create separate rule for 100";
                case "ASK_CUSTOMER" -> "Leave unresolved / ask customer";
                default -> humanizeKey(k);
            };
        }
        if (p.contains("clean")) {
            return switch (k) {
                case "DPD_EQ_0" -> "No DPD > 0";
                case "DPD_LTE_30", "NO_DELINQUENCY_STATUS" -> "No DPD > 30";
                case "NO_ADVERSE_BUREAU_STATUS", "PROVIDER_CLEAN_CODE" -> "No adverse bureau status";
                case "CUSTOMER_COMPOSITE_RULE" -> "Customer-defined composite rule";
                case "ASK_CUSTOMER", "OTHER" -> "Other";
                default -> humanizeKey(k);
            };
        }
        return switch (k) {
            case "ASK_CUSTOMER" -> "Ask customer / keep unresolved";
            case "CREATE_NEW_METRIC", "NEW_METRIC_CANDIDATE" -> "Create new metric";
            case "CREATE_POLICY_PARAMETER" -> "Create policy parameter";
            case "OTHER" -> "Other / define manually";
            case "CC_EXCLUDED" -> "Exclude credit cards from this rule";
            case "CC_SUBJECT_TO_SUBRULES" -> "Apply credit-card sub-rules";
            case "TAXONOMY_AVAILABLE" -> "Classification available in bank data";
            case "UNSUPPORTED" -> "Not supported with current data";
            case "bureau.status_ntc" -> "Bureau NTC status flag";
            case "provider_raw_ntc" -> "Provider-specific NTC code";
            default -> humanizeKey(k);
        };
    }

    private static String humanizeKey(String k) {
        String s = k.replace("application.", "")
                .replace("banking.", "")
                .replace("bureau.", "")
                .replace('_', ' ')
                .replace('.', ' ');
        if (s.length() > 80) {
            return s.substring(0, 80) + "…";
        }
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private static String friendlyType(String type) {
        if (type == null || type.isBlank()) {
            return "Needs clarification";
        }
        return switch (type) {
            case "MULTIPLE_CANONICAL_MATCHES" -> "Multiple matching metrics";
            case "UNKNOWN_BUSINESS_TERM" -> "Unclear business term";
            case "MISSING_METRIC" -> "Missing metric";
            case "UNCLEAR_PERIOD" -> "Unclear time period";
            case "UNCLEAR_DENOMINATOR" -> "Unclear calculation base";
            case "UNCLEAR_SCOPE" -> "Unclear product or account scope";
            case "UNCLEAR_OPERATOR" -> "Unclear comparison";
            case "BOUNDARY_AMBIGUITY" -> "Boundary condition";
            case "UNCLEAR_OUTCOME" -> "Unclear rule outcome";
            case "CONFLICTING_RULE" -> "Conflicting rules";
            case "MISSING_EXCEPTION_BEHAVIOUR" -> "Exception behaviour unclear";
            case "UNSUPPORTED_DATA" -> "Data may be unavailable";
            default -> type.replace('_', ' ').toLowerCase(Locale.ROOT);
        };
    }

    private static String categoryFor(String type, String phrase) {
        String p = phrase == null ? "" : phrase.toLowerCase(Locale.ROOT);
        if ("BOUNDARY_AMBIGUITY".equals(type) || p.contains("exactly 100") || p.contains("100")) {
            return "Boundary Condition";
        }
        if ("MISSING_METRIC".equals(type) || "UNSUPPORTED_DATA".equals(type)) {
            return "Missing Data";
        }
        if ("UNCLEAR_SCOPE".equals(type) || p.contains("loan vs") || p.contains("credit-card") || p.contains("product")) {
            return "Product Scope";
        }
        if ("UNCLEAR_OUTCOME".equals(type) || "CONFLICTING_RULE".equals(type)) {
            return "Rule Outcome";
        }
        if ("UNCLEAR_DENOMINATOR".equals(type) || "UNCLEAR_PERIOD".equals(type)
                || "MULTIPLE_CANONICAL_MATCHES".equals(type)
                || p.contains("average") || p.contains("definition") || p.contains("settlement")) {
            return "Metric Definition";
        }
        return "Terminology";
    }

    private static String whyNeeded(String phrase, String type, String description) {
        String p = phrase == null ? "" : phrase.toLowerCase(Locale.ROOT);
        if (p.equals("edi") || (p.contains("edi") && !p.contains("credit"))) {
            return "EDI appears in capacity checks but is not a fixed industry definition in this policy. "
                    + "Confirming the meaning unlocks banking capacity rules for Starter, DigiLeap, Smart Switch and Reboost.";
        }
        if (p.contains("exactly 100") || (p.contains("100") && p.contains("transaction"))) {
            return "The inward cheque return rule splits at 100 transactions, but equality at 100 is not stated. "
                    + "Without a decision, boundary tests and the executable rule cannot be finalized.";
        }
        if (p.contains("clean")) {
            return "CLEAN history drives overdue exceptions. Different lenders treat CLEAN as zero DPD, "
                    + "mild delinquency tolerance, or a composite bureau status — we need your definition.";
        }
        if (p.equals("ntc") || p.startsWith("ntc")) {
            return "NTC (new-to-credit) flags differ across bureau providers. Confirm which representation this lender uses.";
        }
        if (p.contains("average monthly transaction")) {
            return "Several transaction-count metrics could match. Choosing the wrong one changes DigiLeap eligibility.";
        }
        if (p.contains("deposition") || p.contains("deposit")) {
            return "Bulk deposit exclusions depend on what “average depositions” means — count, value, or credit amount.";
        }
        if (p.contains("settlement") || p.contains("qr")) {
            return "QR / settlement metrics may not be available from current bank-statement data feeds.";
        }
        if (p.contains("gaming")) {
            return "Online gaming classification depends on merchant taxonomy availability in bank data.";
        }
        if (description != null && !description.isBlank()) {
            return friendlyDescription(description);
        }
        return "This wording can be interpreted in more than one underwriting way. Confirm before approving rules.";
    }

    private static String friendlyDescription(String description) {
        return description
                .replace("canonical", "system")
                .replace("Canonical", "System")
                .replace("requires customer confirmation", "needs your confirmation")
                .replace("do not silently decide", "BillionTech will not decide automatically");
    }

    private static String systemInterpretation(
            String phrase, String type, String description, CiPolicyInterpretation interp) {
        if (interp != null && interp.getNaturalLanguageMeaning() != null
                && !interp.getNaturalLanguageMeaning().isBlank()) {
            return interp.getNaturalLanguageMeaning();
        }
        String p = phrase == null ? "" : phrase.toLowerCase(Locale.ROOT);
        if (p.equals("edi") || p.contains("edi")) {
            return "Currently treated as a candidate application field: Proposed EDI used in ADB capacity checks.";
        }
        if (p.contains("exactly 100")) {
            return "Inward return rule branches on transaction count, but the equality case at 100 is left open.";
        }
        if (p.contains("clean")) {
            return "Interpreted as a clean repayment history requirement (definition not finalized).";
        }
        return friendlyDescription(description == null ? "Awaiting human confirmation." : description);
    }

    private static String mappingBusiness(String phrase, String option) {
        if (option == null) {
            return "No mapping selected";
        }
        String o = option.toLowerCase(Locale.ROOT);
        if (o.contains("proposed_edi") || o.contains("eligible_disposable")) {
            return "Application field — Proposed EDI / repayment capacity input";
        }
        if (o.contains("proposed_emi")) {
            return "Application field — Proposed EMI (equated monthly installment)";
        }
        if (o.contains("transaction_count")) {
            return "Bank statement metric — average monthly transactions (last 3 months)";
        }
        if (o.contains("settlement")) {
            return "Bank / QR settlement metric family";
        }
        if (o.contains("dpd_eq_0")) {
            return "Bureau — days past due equals zero";
        }
        if (o.contains("dpd_lte_30")) {
            return "Bureau — no DPD above 30";
        }
        if (o.contains("status_ntc") || o.contains("ntc")) {
            return "Bureau — new-to-credit status";
        }
        if (o.contains("ask_customer") || o.contains("other") || o.contains("composite")) {
            return "Pending customer / credit policy definition";
        }
        if (o.contains("create_policy_parameter") || o.contains("policy_parameter")) {
            return "Lender policy parameter (not a fixed system metric)";
        }
        if (o.contains("new_metric") || o.contains("create_new_metric")) {
            return "New metric candidate to be designed";
        }
        return humanizeKey(option);
    }

    private static String impactIfUnresolved(String phrase, String type) {
        String p = phrase == null ? "" : phrase.toLowerCase(Locale.ROOT);
        if (p.contains("edi")) {
            return "Banking capacity rules stay blocked or need review until EDI is defined.";
        }
        if (p.contains("100")) {
            return "Boundary tests for inward returns remain incomplete; applicants with exactly 100 transactions are ambiguous.";
        }
        if (p.contains("clean")) {
            return "Overdue exception rules that depend on CLEAN history cannot be approved.";
        }
        if ("MISSING_METRIC".equals(type)) {
            return "Related rules stay blocked until the metric is available or an alternative is chosen.";
        }
        return "Executable draft policy readiness remains reduced until this is resolved or parked for customer confirmation.";
    }

    private static List<Map<String, Object>> ruleCards(PolicyStudioSession session) {
        Map<UUID, CiPolicyClause> clauses = session.getClauses().stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(CiPolicyClause::getId, c -> c, (a, b) -> a, LinkedHashMap::new));
        Map<UUID, CiPolicyInterpretation> interps = session.getInterpretations().stream()
                .filter(i -> i.getClauseId() != null)
                .collect(Collectors.toMap(CiPolicyInterpretation::getClauseId, i -> i, (a, b) -> a, LinkedHashMap::new));
        Set<String> openPhrases = session.getAmbiguities().stream()
                .filter(a -> "OPEN".equals(a.getResolutionStatus())
                        || "CLARIFICATION_REQUESTED".equals(a.getResolutionStatus()))
                .map(a -> a.getPhrase() == null ? "" : a.getPhrase().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        List<Map<String, Object>> cards = new ArrayList<>();
        for (CiPolicyRuleCandidate r : session.getRuleCandidates()) {
            cards.add(toRuleCard(r, clauses.get(r.getClauseId()), interps.get(r.getClauseId()), openPhrases));
        }
        cards.sort(Comparator
                .comparing((Map<String, Object> m) -> statusOrder(String.valueOf(m.get("status"))))
                .thenComparing(m -> String.valueOf(m.get("ruleName"))));
        return cards;
    }

    private static int statusOrder(String status) {
        return switch (status) {
            case "Needs your input", "Blocked", "Needs Review" -> 0;
            case "Ready" -> 1;
            case "Accepted", "Edited", "Approved" -> 2;
            case "Manual Input", "Manual Review" -> 3;
            case "Data requirement", "Metric adjustment", "Non-underwriting" -> 4;
            case "Ignored" -> 5;
            case "Deleted" -> 6;
            default -> 7;
        };
    }

    private static Map<String, Object> toRuleCard(
            CiPolicyRuleCandidate r,
            CiPolicyClause clause,
            CiPolicyInterpretation interp,
            Set<String> openPhrases) {
        String product = productScope(r, clause, interp);
        String meaning = interp == null || interp.getNaturalLanguageMeaning() == null
                ? friendlyRuleName(r.getSystemRuleId())
                : interp.getNaturalLanguageMeaning();
        List<String> dataUsed = dataUsed(r, interp);
        Map<String, Object> metaEarly = r.getMetadata() == null ? Map.of() : r.getMetadata();
        String blockedReason = metaEarly.get("blockedReason") != null
                ? String.valueOf(metaEarly.get("blockedReason"))
                : blockedReason(r, dataUsed, openPhrases);
        String status = ruleStatus(r, blockedReason);

        Map<String, Object> meta = r.getMetadata() == null ? Map.of() : r.getMetadata();
        String decisionDomain = String.valueOf(meta.getOrDefault(
                com.los.core.creditintelligence.decisionpolicy.DecisionPolicyRuleMetadata.KEY_DOMAIN,
                com.los.core.creditintelligence.decisionpolicy.DecisionPolicyRuleMetadata.domainOf(meta, r.getScope()).name()));
        String requirementType = meta.get("kycRequirementType") == null
                ? null : String.valueOf(meta.get("kycRequirementType"));
        String guardrail = meta.get("guardrailClass") == null
                ? null : String.valueOf(meta.get("guardrailClass"));
        boolean kycDomain = "KYC".equalsIgnoreCase(decisionDomain) || "ELIGIBILITY".equalsIgnoreCase(decisionDomain);
        String businessTitle = meta.get("businessTitle") == null
                ? friendlyRuleName(r.getSystemRuleId()) : String.valueOf(meta.get("businessTitle"));
        boolean catalogueBacked = Boolean.TRUE.equals(meta.get("catalogueBacked"))
                || Boolean.TRUE.equals(meta.get("existingCapability"))
                || meta.get("businessCapabilityId") != null;

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", r.getId() == null ? null : r.getId().toString());
        card.put("systemRuleId", r.getSystemRuleId());
        card.put("ruleName", meta.get("businessTitle") != null || kycDomain || catalogueBacked
                ? businessTitle
                : friendlyRuleName(r.getSystemRuleId()));
        card.put("sourceClause", clause == null ? null : clause.getSourceText());
        card.put("section", clause == null ? null : clause.getSection());
        card.put("productScope", product);
        String businessRule = meta.get("businessSummary") != null
                ? String.valueOf(meta.get("businessSummary"))
                : businessRuleText(r, meaning, product);
        card.put("businessRule", businessRule);
        card.put("period", friendlyPeriod(r.getPeriodSemantics(), interp));
        card.put("onMissing", friendlyMissing(
                meta.get("outcomeOnMissing") != null ? String.valueOf(meta.get("outcomeOnMissing")) : r.getOnMissing()));
        if (kycDomain) {
            // KYC authoring: condition success/failure from stamped outcomes
            card.put("resultOnPass", friendlyOutcome(String.valueOf(meta.getOrDefault("outcomeOnSuccess", r.getOnTrue()))));
            card.put("resultOnFailure", friendlyOutcome(String.valueOf(meta.getOrDefault("outcomeOnFailure", r.getOnFalse()))));
            card.put("outcomeOnMissing", friendlyOutcome(String.valueOf(meta.getOrDefault("outcomeOnMissing", "MISSING_INFORMATION"))));
        } else {
            Map<String, Object> pf = com.los.core.creditintelligence.policystudio.lineage.PolicyRulePresentationSemantics
                    .passFailPresentation(r);
            card.put("resultOnPass", pf.get("resultOnPass"));
            card.put("resultOnFailure", pf.get("resultOnFailure"));
            card.put("failureTreatmentDisplay", pf.get("failureTreatment"));
            card.put("dslOrientation", pf.get("dslOrientation"));
            if (pf.get("configurationAnomaly") != null) {
                card.put("configurationAnomaly", pf.get("configurationAnomaly"));
            }
        }
        card.put("status", status);
        card.put("blockedReason", blockedReason);
        card.put("dataUsed", dataUsed.stream().map(ProspectDay2ViewBuilder::friendlyMetric).toList());
        card.put("dataFamily", dataFamily(dataUsed));
        String groupOverride = meta.get("businessGroupOverride") != null
                ? String.valueOf(meta.get("businessGroupOverride"))
                : (meta.get("businessCapabilityId") == null
                    ? null
                    : catalogueBusinessGroup(String.valueOf(meta.get("businessCapabilityId"))));
        card.put("businessGroup", groupOverride != null
                ? groupOverride
                : businessGroup(decisionDomain, dataUsed, r.getSystemRuleId()));
        card.put("disposition", meta.get("disposition"));
        card.put("excludedFromActivation", Boolean.TRUE.equals(meta.get("excludedFromActivation"))
                || Boolean.TRUE.equals(meta.get("deleted")));
        card.put("manualInputLabel", meta.get("manualInputLabel"));
        card.put("manualInputType", meta.get("manualInputType"));
        card.put("visualLogic", visualLogic(r, meaning));
        card.put("reviewStatus", r.getReviewStatus());
        card.put("ruleType", r.getRuleType());
        card.put("confidence", r.getConfidence() == null ? null : r.getConfidence().doubleValue());
        card.put("technicalExpression", r.getExpression());
        card.put("decisionDomain", decisionDomain);
        card.put("kycRequirementType", requirementType);
        card.put("guardrailClass", guardrail);
        card.put("studioEditable", !("PLATFORM_GUARDRAIL".equalsIgnoreCase(guardrail)));
        card.put("platformGuardrail", "PLATFORM_GUARDRAIL".equalsIgnoreCase(guardrail));
        card.put("matchCapabilityMissing", Boolean.TRUE.equals(meta.get("matchCapabilityMissing")));
        card.put("unsupportedCapability", Boolean.TRUE.equals(meta.get("unsupportedCapability")));
        card.put("policySelectsProvider", false);
        card.put("policyEnqueuesWorkflowStep", false);
        card.put("verificationRequired", requirementType != null
                && (requirementType.contains("VERIFICATION") || requirementType.contains("COMPLETION")
                || requirementType.contains("MATCH")));
        card.put("manualReviewRequired", "MANUAL_VERIFICATION".equalsIgnoreCase(requirementType)
                || "REFER".equalsIgnoreCase(String.valueOf(meta.get("outcomeOnFailure"))));
        // POLICY-UX-2C catalogue fields for Credit Manager cards
        card.put("businessCapabilityId", meta.get("businessCapabilityId"));
        card.put("catalogueBacked", catalogueBacked);
        card.put("existingCapability", catalogueBacked);
        card.put("capabilityBadge", meta.get("capabilityBadge") != null
                ? meta.get("capabilityBadge")
                : (catalogueBacked
                    ? (Boolean.TRUE.equals(meta.get("plainEnglishAdded")) || meta.get("source") == null
                        ? "Existing capability · extracted from policy"
                        : "MANUAL_CATALOGUE_ADD".equals(String.valueOf(meta.get("source")))
                            ? "Existing capability · added manually"
                            : "Existing capability")
                    : null));
        card.put("parameters", meta.get("parameters"));
        card.put("failureTreatment", meta.get("failureTreatment"));
        card.put("dataRequirement", meta.get("dataRequirement"));
        card.put("dataSource", meta.get("dataSource"));
        card.put("catalogueSource", meta.get("source"));
        if ("MANUAL_CATALOGUE_ADD".equals(String.valueOf(meta.getOrDefault("source", "")))) {
            card.put("sourceLabel", "Added by Credit Manager");
        } else if ("DOCUMENT_CAPABILITY_MATCH".equals(String.valueOf(meta.getOrDefault("source", "")))) {
            card.put("sourceLabel", "Extracted from policy");
        }
        card.put("classification", meta.get("classification"));
        card.put("matchConfidence", meta.get("matchConfidence"));
        card.put("NEEDS_INPUT", meta.get("NEEDS_INPUT"));
        card.put("PARAMETER_DIFFERS", meta.get("PARAMETER_DIFFERS"));
        card.put("potentialDuplicate", meta.get("potentialDuplicate"));
        card.put("capabilityConflict", meta.get("capabilityConflict"));
        card.put("productionReferenceParameters", meta.get("productionReferenceParameters"));
        card.put("uploadedPolicyParameters", meta.get("uploadedPolicyParameters"));
        card.put("implementationNote", meta.get("implementationNote"));
        // Metric lineage (GACAT) — business-facing; technical under Advanced
        com.los.core.creditintelligence.policystudio.lineage.PolicyMetricLineageService lineageSvc =
                new com.los.core.creditintelligence.policystudio.lineage.PolicyMetricLineageService();
        var lineage = lineageSvc.resolveFromInputs(dataUsed, r.getSystemRuleId());
        if (lineage == null && meta.get("dataAvailability") != null) {
            lineage = lineageSvc.resolve(String.valueOf(meta.getOrDefault("businessCapabilityId", "")));
        }
        if (lineage != null) {
            card.put("metricLineage", lineage.toBusinessView());
            card.put("howCalculated", lineageSvc.howCalculated(lineage));
            card.put("dataAvailability", lineage.availability());
            card.put("dataAvailabilityLabel",
                    com.los.core.creditintelligence.policystudio.lineage.PolicyMetricLineageService
                            .friendlyAvailability(lineage.availability()));
            card.put("dataSource", lineage.source());
            card.put("metricLineageTechnical", lineage.toTechnicalView());
        } else if (meta.get("dataAvailability") != null) {
            card.put("dataAvailability", meta.get("dataAvailability"));
            card.put("dataAvailabilityLabel",
                    com.los.core.creditintelligence.policystudio.lineage.PolicyMetricLineageService
                            .friendlyAvailability(String.valueOf(meta.get("dataAvailability"))));
        }
        card.put("acceptAllEligible", isAcceptAllEligible(card, meta));
        if (Boolean.TRUE.equals(meta.get("classificationOnly"))
                && !Boolean.TRUE.equals(meta.get("metricAdjustment"))
                && !Boolean.TRUE.equals(meta.get("dataRequirementOnly"))) {
            card.put("executable", false);
        } else {
            card.put("executable", !Boolean.TRUE.equals(meta.get("dataRequirementOnly"))
                    && !Boolean.TRUE.equals(meta.get("metricAdjustment")));
        }
        return card;
    }

    private static boolean isAcceptAllEligible(Map<String, Object> card, Map<String, Object> meta) {
        if (com.los.core.creditintelligence.policystudio.lineage.PolicyRulePresentationSemantics
                .acceptAllReadyEligible(card)) {
            return true;
        }
        if (meta == null) return false;
        if (Boolean.TRUE.equals(meta.get("NEEDS_INPUT"))) return false;
        if (Boolean.TRUE.equals(meta.get("capabilityConflict"))) return false;
        if (Boolean.TRUE.equals(meta.get("potentialDuplicate"))) return false;
        if (Boolean.TRUE.equals(meta.get("dataRequirementOnly"))) return false;
        if (Boolean.TRUE.equals(meta.get("metricAdjustment"))) return false;
        if (Boolean.TRUE.equals(meta.get("excludedFromActivation"))) return false;
        String cls = String.valueOf(meta.get("classification"));
        return "EXACT_EXISTING_CAPABILITY".equals(cls)
                || "EXISTING_CAPABILITY_PARAMETER_CHANGE".equals(cls)
                || "EXISTING_CAPABILITY_MANUAL_DATA".equals(cls)
                || "NEW_AUTOMATABLE_RULE".equals(cls)
                || "GOLDEN_FALLBACK".equals(String.valueOf(meta.get("source")));
    }

    private static String catalogueBusinessGroup(String capabilityId) {
        if (capabilityId == null) return null;
        if (capabilityId.startsWith("BUREAU.")) return "Bureau";
        if (capabilityId.startsWith("BANK.")) return "Banking";
        if (capabilityId.startsWith("FIN.")) return "Financial / Income";
        if (capabilityId.startsWith("GST.")) return "GST / Business";
        if (capabilityId.startsWith("KYC.") || capabilityId.startsWith("ELIG.")) return "KYC & Eligibility";
        if (capabilityId.startsWith("COLL.")) return "Collateral";
        if (capabilityId.startsWith("RISK.")) return "Risk / Exceptions";
        if (capabilityId.startsWith("LIMIT.") || capabilityId.startsWith("PRICE.")) return "Limit & Pricing";
        if (capabilityId.startsWith("DEC.")) return "Decision / Review";
        return null;
    }

    private static String ruleStatus(CiPolicyRuleCandidate r, String blockedReason) {
        // GACAT-POLICY-LINEAGE-FIX-1 — Ignored = CM disposition only
        String semantic = com.los.core.creditintelligence.policystudio.lineage.PolicyRulePresentationSemantics
                .ruleStatus(r, blockedReason);
        if (semantic != null && !"Needs your input".equals(semantic)) {
            return semantic;
        }
        Map<String, Object> meta = r.getMetadata();
        if (meta != null) {
            if (Boolean.TRUE.equals(meta.get("deleted"))
                    || "DELETED".equalsIgnoreCase(String.valueOf(meta.getOrDefault("disposition", "")))) {
                return "Deleted";
            }
            if ("IGNORED".equalsIgnoreCase(String.valueOf(meta.getOrDefault("disposition", "")))) {
                return "Ignored";
            }
            // excludedFromActivation must NOT map to Ignored
            if ("MANUAL_INPUT".equalsIgnoreCase(String.valueOf(meta.getOrDefault("disposition", "")))
                    || "MANUAL".equalsIgnoreCase(String.valueOf(meta.getOrDefault("verificationMode", "")))
                    || "MANUAL_VERIFICATION".equalsIgnoreCase(
                    String.valueOf(meta.getOrDefault("dataGapDisposition", "")))) {
                return "Manual Input";
            }
            if (Boolean.TRUE.equals(meta.get("NEEDS_INPUT"))
                    || Boolean.TRUE.equals(meta.get("capabilityConflict"))
                    || Boolean.TRUE.equals(meta.get("potentialDuplicate"))
                    || "LOW".equalsIgnoreCase(String.valueOf(meta.getOrDefault("matchConfidence", "")))) {
                return "Needs your input";
            }
            if ("EDITED".equalsIgnoreCase(String.valueOf(meta.getOrDefault("disposition", "")))) {
                return "Edited";
            }
            if ("ACCEPTED".equalsIgnoreCase(String.valueOf(meta.getOrDefault("disposition", "")))) {
                return "Accepted";
            }
            if (Boolean.TRUE.equals(meta.get("catalogueBacked"))
                    && "HIGH".equalsIgnoreCase(String.valueOf(meta.getOrDefault("matchConfidence", "")))
                    && !Boolean.TRUE.equals(meta.get("classificationOnly"))) {
                return "Ready";
            }
            if (Boolean.TRUE.equals(meta.get("classificationOnly"))) {
                String cls = String.valueOf(meta.getOrDefault("classification", ""));
                if ("MANUAL_REVIEW".equals(cls)) return "Needs your input";
                if ("DOCUMENT_REQUIREMENT".equals(cls) || "PRODUCT_CONFIG".equals(cls)
                        || "PORTFOLIO_CONTROL".equals(cls) || "SERVICING_RULE".equals(cls)
                        || "NARRATIVE".equals(cls)) {
                    return "Ready"; // reviewable but non-blocking / non-executable
                }
            }
        }
        String rs = r.getReviewStatus() == null ? "" : r.getReviewStatus();
        if (ReviewState.CREDIT_MANAGER_APPROVED.name().equals(rs)
                || ReviewState.CHECKER_APPROVED.name().equals(rs)
                || ReviewState.READY_FOR_POLICY_BUILD.name().equals(rs)) {
            return "Accepted";
        }
        if (ReviewState.REJECTED.name().equals(rs)) {
            return "Needs your input";
        }
        if (blockedReason != null && !blockedReason.isBlank()) {
            return "Needs your input";
        }
        if (r.getValidationErrors() != null && !r.getValidationErrors().isEmpty()) {
            return "Needs your input";
        }
        if (ReviewState.AI_DRAFTED.name().equals(rs)
                || ReviewState.RULE_REVIEW.name().equals(rs)
                || ReviewState.MAPPING_REVIEW.name().equals(rs)) {
            return "Needs your input";
        }
        return "Ready";
    }

    private static String blockedReason(CiPolicyRuleCandidate r, List<String> dataUsed, Set<String> openPhrases) {
        String sys = r.getSystemRuleId() == null ? "" : r.getSystemRuleId().toUpperCase(Locale.ROOT);
        String joined = String.join(" ", dataUsed).toLowerCase(Locale.ROOT);
        if (openPhrases.stream().anyMatch(p -> p.contains("edi"))
                && (joined.contains("proposed_edi") || sys.contains("EDI"))) {
            return "EDI has not been defined.";
        }
        if (openPhrases.stream().anyMatch(p -> p.contains("clean"))
                && (joined.contains("clean") || sys.contains("CLEAN") || sys.contains("OVERDUE"))) {
            return "CLEAN has not been defined.";
        }
        // Settlement/QR is DERIVABLE from classified bank transactions — do not block as unavailable.
        if (openPhrases.stream().anyMatch(p -> p.contains("exactly 100") || p.contains("100 transaction"))
                && sys.contains("INWARD") && sys.contains("100")) {
            return "Exactly-100 transaction boundary has not been decided.";
        }
        if (openPhrases.stream().anyMatch(p -> p.contains("ntc")) && joined.contains("ntc")) {
            return "NTC meaning has not been confirmed.";
        }
        if (r.getValidationErrors() != null && !r.getValidationErrors().isEmpty()) {
            return "Rule expression still needs validation review.";
        }
        Map<String, Object> meta = r.getMetadata() == null ? Map.of() : r.getMetadata();
        if (Boolean.TRUE.equals(meta.get("unsupportedCapability"))) {
            return "Verification data source not available — DATA_SOURCE / MAPPING required.";
        }
        if (Boolean.TRUE.equals(meta.get("matchCapabilityMissing"))) {
            return "MATCH CAPABILITY REQUIRED — name/address match is not proven executable.";
        }
        if ("PLATFORM_GUARDRAIL".equalsIgnoreCase(String.valueOf(meta.get("guardrailClass")))) {
            return null; // visible but not blocked for missing mapping alone
        }
        return null;
    }

    private static String productScope(CiPolicyRuleCandidate r, CiPolicyClause clause, CiPolicyInterpretation interp) {
        if (clause != null && clause.getProductScope() != null && !clause.getProductScope().isBlank()) {
            return clause.getProductScope();
        }
        if (interp != null && interp.getCandidateProductScope() != null
                && !interp.getCandidateProductScope().isEmpty()) {
            return interp.getCandidateProductScope().stream()
                    .map(String::valueOf).collect(Collectors.joining(", "));
        }
        Object scope = r.getScope() == null ? null : r.getScope().get("products");
        if (scope instanceof List<?> list && !list.isEmpty()) {
            return list.stream().map(String::valueOf).collect(Collectors.joining(", "));
        }
        String sys = r.getSystemRuleId() == null ? "" : r.getSystemRuleId().toUpperCase(Locale.ROOT);
        if (sys.contains("STARTER")) return "Starter Loans";
        if (sys.contains("DIGILEAP")) return "DigiLeap Loans";
        if (sys.contains("SMART_SWITCH")) return "Smart Switch";
        if (sys.contains("REBOOST")) return "Reboost";
        if (sys.contains("BUREAU") || sys.contains("OVERDUE") || sys.contains("DPD") || sys.contains("WRITE")) {
            return "Bureau";
        }
        return "All applicable products";
    }

    private static String friendlyRuleName(String systemRuleId) {
        if (systemRuleId == null || systemRuleId.isBlank()) {
            return "Generated rule";
        }
        String s = systemRuleId
                .replace("BANK_", "")
                .replace("BUREAU_", "")
                .replace('_', ' ');
        return switch (systemRuleId) {
            case "BANK_STARTER_ADB_GTE_EDI" -> "STARTER — Banking Capacity";
            case "BANK_DIGILEAP_TXN_GTE_20" -> "DIGILEAP — Transaction Volume";
            case "BANK_DIGILEAP_ADB_DIV5_GTE_EDI" -> "DIGILEAP — Banking Capacity";
            case "BANK_SMART_SWITCH_SETTLEMENT_COUNT_GTE_20" -> "SMART SWITCH — Settlement Count";
            case "BANK_SMART_SWITCH_SETTLEMENT_DIV10_GTE_EDI" -> "SMART SWITCH — Settlement Capacity";
            case "BANK_REBOOST_TXN_GTE_30_IF_AMT_GT_60000" -> "REBOOST — Transaction Volume";
            case "BANK_REBOOST_ADB_DIV5_GTE_EDI_IF_AMT_GT_60000" -> "REBOOST — Banking Capacity";
            case "BANK_INWARD_RETURN_BRANCHED_100" -> "Inward Cheque Returns — 100 Boundary";
            default -> s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
        };
    }

    private static String businessRuleText(CiPolicyRuleCandidate r, String meaning, String product) {
        String sys = r.getSystemRuleId() == null ? "" : r.getSystemRuleId();
        if ("BANK_DIGILEAP_ADB_DIV5_GTE_EDI".equals(sys) || "BANK_STARTER_ADB_GTE_EDI".equals(sys)
                || sys.contains("ADB_DIV5_GTE_EDI")) {
            return "Adjusted Average Daily Balance ÷ 5 must be greater than or equal to Proposed EDI";
        }
        if ("BANK_DIGILEAP_TXN_GTE_20".equals(sys)) {
            return "Average monthly transactions must be at least 20";
        }
        if (sys.contains("INWARD_RETURN")) {
            return "Inward cheque returns: use percentage rule when transactions > 100, count rule when < 100";
        }
        if (meaning != null && meaning.toLowerCase(Locale.ROOT).contains("overdue exception")) {
            return "Loan overdue may be allowed only when all overdue exception conditions are met";
        }
        if (meaning != null && !meaning.isBlank()) {
            return meaning;
        }
        return product + " — generated underwriting rule";
    }

    private static String friendlyPeriod(String periodSemantics, CiPolicyInterpretation interp) {
        String p = periodSemantics;
        if ((p == null || p.isBlank()) && interp != null) {
            p = interp.getCandidatePeriod();
        }
        if (p == null || p.isBlank()) {
            return "As stated in policy";
        }
        return switch (p.toUpperCase(Locale.ROOT)) {
            case "TRAILING_3M", "LAST_3M", "3M" -> "Last 3 months";
            case "TRAILING_6M", "LAST_6M", "6M" -> "Last 6 months";
            case "TRAILING_12M", "LAST_12M", "12M" -> "Last 12 months";
            default -> p.replace('_', ' ');
        };
    }

    private static String friendlyMissing(String onMissing) {
        if (onMissing == null) {
            return "Refer / Data Insufficient";
        }
        return switch (onMissing.toUpperCase(Locale.ROOT)) {
            case "MISSING_INFORMATION" -> "Missing Information";
            case "DATA_INSUFFICIENT", "REFER_DATA_INSUFFICIENT" -> "Refer / Data Insufficient";
            case "REFER" -> "Refer";
            case "FAIL" -> "Fail";
            case "PASS" -> "Pass";
            default -> onMissing.replace('_', ' ');
        };
    }

    private static String friendlyOutcome(String outcome) {
        if (outcome == null) {
            return "—";
        }
        return switch (outcome.toUpperCase(Locale.ROOT)) {
            case "FAIL" -> "Fail";
            case "PASS" -> "Pass";
            case "REFER" -> "Refer";
            case "MISSING_INFORMATION" -> "Missing Information";
            case "DATA_INSUFFICIENT" -> "Missing Information";
            default -> outcome.replace('_', ' ');
        };
    }

    private static List<String> dataUsed(CiPolicyRuleCandidate r, CiPolicyInterpretation interp) {
        List<String> out = new ArrayList<>();
        if (interp != null && interp.getCandidateInputs() != null) {
            for (Object o : interp.getCandidateInputs()) {
                out.add(String.valueOf(o));
            }
        }
        if (out.isEmpty() && r.getExpression() != null) {
            collectPaths(r.getExpression(), out);
        }
        return out.stream().distinct().toList();
    }

    @SuppressWarnings("unchecked")
    private static void collectPaths(Object node, List<String> out) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> m = (Map<String, Object>) map;
            if (m.get("metric") != null) {
                out.add(String.valueOf(m.get("metric")));
            }
            if (m.get("fact") != null) {
                out.add(String.valueOf(m.get("fact")));
            }
            if (m.get("applicationField") != null) {
                out.add(String.valueOf(m.get("applicationField")));
            }
            if (m.get("path") != null) {
                out.add(String.valueOf(m.get("path")));
            }
            for (Object v : m.values()) {
                collectPaths(v, out);
            }
        } else if (node instanceof List<?> list) {
            for (Object v : list) {
                collectPaths(v, out);
            }
        }
    }

    private static String dataFamily(List<String> paths) {
        boolean kyc = paths.stream().anyMatch(p -> p.startsWith("kyc."));
        boolean bank = paths.stream().anyMatch(p -> p.startsWith("banking.") || p.contains("settlement"));
        boolean bureau = paths.stream().anyMatch(p -> p.startsWith("bureau."));
        boolean app = paths.stream().anyMatch(p -> p.startsWith("application.") || p.equals("borrower_type")
                || p.equals("requested_amount"));
        if (kyc) {
            return "KYC / Identity verification";
        }
        if (bank && !bureau) {
            return "Bank Statement / AA";
        }
        if (bureau && !bank) {
            return "Bureau / Credit Report";
        }
        if (app && !bank && !bureau) {
            return "Application data";
        }
        if (bank && bureau) {
            return "Bank Statement + Bureau";
        }
        return "Policy data";
    }

    /** Business family for Credit Manager grouping — only used when rules exist. */
    private static String businessGroup(String decisionDomain, List<String> paths, String systemRuleId) {
        String domain = decisionDomain == null ? "" : decisionDomain.toUpperCase(Locale.ROOT);
        String sys = systemRuleId == null ? "" : systemRuleId.toUpperCase(Locale.ROOT);
        String joined = String.join(" ", paths).toLowerCase(Locale.ROOT);
        if ("KYC".equals(domain) || "ELIGIBILITY".equals(domain) || joined.contains("kyc.")) {
            return "KYC & Eligibility";
        }
        if (joined.contains("bureau.") || sys.contains("BUREAU") || sys.contains("CIBIL") || sys.contains("SCORE")) {
            return "Bureau";
        }
        if (joined.contains("banking.") || joined.contains("settlement") || sys.contains("ADB") || sys.contains("BANK")) {
            return "Banking";
        }
        if (joined.contains("gst.") || sys.contains("GST")) {
            return "GST / Business";
        }
        if (joined.contains("income") || joined.contains("foir") || joined.contains("financial")
                || sys.contains("FOIR") || sys.contains("INCOME")) {
            return "Financial / Income";
        }
        if (joined.contains("collateral") || sys.contains("COLLATERAL") || sys.contains("SECURITY")) {
            return "Collateral";
        }
        if (sys.contains("LIMIT") || sys.contains("PRICING") || sys.contains("ROI") || sys.contains("RATE")) {
            return "Limit & Pricing";
        }
        if (sys.contains("EXCEPTION") || sys.contains("RISK") || "REFER".equalsIgnoreCase(sys)) {
            return "Risk / Exceptions";
        }
        if ("DECISION".equals(domain) || "REVIEW".equals(domain) || sys.contains("DECISION") || sys.contains("REVIEW")) {
            return "Decision / Review";
        }
        return "Credit Rules";
    }

    private static String friendlyMetric(String path) {
        if (path == null) {
            return "—";
        }
        return switch (path) {
            case "banking.avg_daily_balance_3m" -> "Adjusted ADB";
            case "application.proposed_edi" -> "Proposed EDI";
            case "banking.transaction_count.average_monthly_3m" -> "Average monthly transactions";
            case "banking.transaction_count.total_3m" -> "Total transactions (3m)";
            case "banking.settlement.avg_daily_3m" -> "Average daily settlement";
            case "banking.settlement.count_monthly_avg_3m" -> "Average monthly settlements";
            case "application.loan_amount" -> "Proposed loan amount";
            case "bureau.cc_overdue_amount" -> "Credit-card overdue amount";
            case "bureau.overdue.age_months" -> "Overdue age (months)";
            case "bureau.overdue.amount" -> "Overdue amount";
            case "bureau.credit_after_overdue.exists" -> "New credit after overdue";
            case "bureau.credit_after_overdue.clean_history_months" -> "Clean history months";
            case "bureau.status_ntc" -> "NTC status";
            default -> humanizeKey(path);
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> visualLogic(CiPolicyRuleCandidate r, String meaning) {
        Map<String, Object> expr = r.getExpression() == null ? Map.of() : r.getExpression();
        Map<String, Object> visual = new LinkedHashMap<>();
        String op = expr.get("op") == null ? "" : String.valueOf(expr.get("op")).toUpperCase(Locale.ROOT);

        if ("AND_CHILDREN".equals(op) || (meaning != null && meaning.toLowerCase(Locale.ROOT).contains("overdue exception"))) {
            visual.put("kind", "EXCEPTION_ALL");
            visual.put("title", "Loan overdue exists");
            visual.put("subtitle", "EXCEPTION allowed only if ALL:");
            visual.put("conditions", List.of(
                    "Overdue is older than 12 months",
                    "New credit taken after overdue",
                    "New credit has ≥ 6 months clean history",
                    "Overdue amount < ₹1,500"));
            visual.put("then", "PASS exception / else FAIL");
            return visual;
        }

        if ("AND".equals(op) && expr.get("args") instanceof List<?> args && args.size() >= 2) {
            visual.put("kind", "COMPOUND");
            List<Map<String, Object>> parts = new ArrayList<>();
            for (Object arg : args) {
                if (arg instanceof Map<?, ?> m) {
                    parts.add(simpleCondition((Map<String, Object>) m));
                }
            }
            visual.put("conditions", parts);
            visual.put("then", com.los.core.creditintelligence.policystudio.lineage.PolicyRulePresentationSemantics
                    .passFailPresentation(r).get("resultOnPass"));
            return visual;
        }

        if ("IF".equals(op) || "IFF".equals(op)) {
            visual.put("kind", "BRANCH");
            visual.put("if", simpleCondition(asMap(expr.get("condition") != null ? expr.get("condition") : expr.get("when"))));
            visual.put("then", summarizeNode(expr.get("then")));
            visual.put("else", summarizeNode(expr.get("elseExpr") != null ? expr.get("elseExpr") : expr.get("else")));
            return visual;
        }

        visual.put("kind", "SIMPLE");
        Map<String, Object> cond = simpleCondition(expr);
        visual.put("if", cond);
        visual.put("then", com.los.core.creditintelligence.policystudio.lineage.PolicyRulePresentationSemantics
                .passFailPresentation(r).get("resultOnPass"));
        return visual;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    private static Map<String, Object> simpleCondition(Map<String, Object> expr) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (expr == null || expr.isEmpty()) {
            out.put("left", "Condition");
            out.put("operator", "");
            out.put("right", "");
            return out;
        }
        String op = expr.get("op") == null ? "" : String.valueOf(expr.get("op")).toUpperCase(Locale.ROOT);
        if ("DIV".equals(op) || "DIVIDE".equals(op)) {
            out.put("left", summarizeNode(expr));
            out.put("operator", "");
            out.put("right", "");
            return out;
        }
        out.put("left", summarizeNode(expr.get("left") != null ? expr.get("left") : expr.get("arg")));
        out.put("operator", friendlyOp(op));
        out.put("right", summarizeNode(expr.get("right")));
        return out;
    }

    private static String friendlyOp(String op) {
        return switch (op) {
            case "GTE" -> "≥";
            case "GT" -> ">";
            case "LTE" -> "≤";
            case "LT" -> "<";
            case "EQ" -> "=";
            case "NE" -> "≠";
            case "AND" -> "AND";
            case "OR" -> "OR";
            default -> op;
        };
    }

    @SuppressWarnings("unchecked")
    private static String summarizeNode(Object node) {
        if (node == null) {
            return "—";
        }
        if (node instanceof Number || node instanceof Boolean) {
            if (node instanceof Number n) {
                double d = n.doubleValue();
                if (d == Math.rint(d) && Math.abs(d) >= 1000) {
                    return "₹" + String.format(Locale.US, "%,.0f", d);
                }
                if (d == Math.rint(d)) {
                    return String.format(Locale.ROOT, "%.0f", d);
                }
            }
            return String.valueOf(node);
        }
        if (node instanceof String s) {
            return s;
        }
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> m = (Map<String, Object>) map;
            if (m.get("metric") != null) {
                return friendlyMetric(String.valueOf(m.get("metric")));
            }
            if (m.get("fact") != null) {
                return friendlyMetric(String.valueOf(m.get("fact")));
            }
            if (m.get("applicationField") != null) {
                return friendlyMetric(String.valueOf(m.get("applicationField")));
            }
            if (m.get("path") != null) {
                return friendlyMetric(String.valueOf(m.get("path")));
            }
            String op = m.get("op") == null ? "" : String.valueOf(m.get("op")).toUpperCase(Locale.ROOT);
            if ("DIV".equals(op) || "DIVIDE".equals(op)) {
                return summarizeNode(m.get("left")) + " ÷ " + summarizeNode(m.get("right"));
            }
            if (!op.isBlank()) {
                return summarizeNode(m.get("left")) + " " + friendlyOp(op) + " " + summarizeNode(m.get("right"));
            }
            if (m.get("value") != null) {
                return summarizeNode(m.get("value"));
            }
        }
        return String.valueOf(node);
    }
}
