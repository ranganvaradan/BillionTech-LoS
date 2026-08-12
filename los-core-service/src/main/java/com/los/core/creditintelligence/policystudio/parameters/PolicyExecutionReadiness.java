package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyAmbiguity;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * POLICY-READINESS-CONVERGENCE-1 — single semantic answer:
 * can this included underwriting rule execute at runtime as authored?
 *
 * Not a new engine: consolidates RuleOperandPresenter + PolicyAuthoringCompleteness
 * + activation exclusion for Rules and Lifecycle to share identical blockers.
 */
public final class PolicyExecutionReadiness {

    public static final String BLOCKER_UNRESOLVED_OPERAND = "UNRESOLVED_OPERAND";
    public static final String BLOCKER_UNAVAILABLE_OPERAND = "UNAVAILABLE_OPERAND";
    public static final String BLOCKER_NEEDS_CONFIGURATION = "NEEDS_CONFIGURATION";
    public static final String BLOCKER_BOUNDARY_AMBIGUITY = "BOUNDARY_AMBIGUITY";
    public static final String BLOCKER_THRESHOLD_MISSING = "THRESHOLD_MISSING";
    public static final String BLOCKER_REQUIRED_ADJUSTMENT = "REQUIRED_POLICY_ADJUSTMENT";
    public static final String BLOCKER_MATERIAL_AMBIGUITY = "MATERIAL_AMBIGUITY";

    public static final String ROLE_REQUIRED_OPERAND = "REQUIRED_OPERAND";
    public static final String ROLE_REQUIRED_DEPENDENCY = "REQUIRED_DEPENDENCY";
    public static final String ROLE_REQUIRED_POLICY_ADJUSTMENT = "REQUIRED_POLICY_ADJUSTMENT";
    public static final String ROLE_REPORT_OR_ANALYST = "REPORT_OR_ANALYST_INFORMATION";
    public static final String ROLE_OPTIONAL = "OPTIONAL_DATA_REQUIREMENT";
    public static final String ROLE_UNUSED = "UNUSED";

    private PolicyExecutionReadiness() {}

    public static boolean isIncludedExecutableRule(CiPolicyRuleCandidate r) {
        if (r == null) return false;
        if (PolicyStudioConvergencePresenter.isCompoundChild(r.getSystemRuleId())) return false;
        Map<String, Object> m = r.getMetadata() == null ? Map.of() : r.getMetadata();
        if (Boolean.TRUE.equals(m.get("classificationOnly"))
                && !Boolean.TRUE.equals(m.get("cmAuthored"))) {
            return false;
        }
        if (Boolean.TRUE.equals(m.get("dataRequirementOnly"))
                || Boolean.TRUE.equals(m.get("metricAdjustment"))) {
            return false;
        }
        if (Boolean.TRUE.equals(m.get("deleted"))) return false;
        if (Boolean.TRUE.equals(m.get("excludedFromActivation"))) return false;
        String disposition = String.valueOf(m.getOrDefault("disposition", ""));
        if ("IGNORED".equalsIgnoreCase(disposition)
                || "DELETED".equalsIgnoreCase(disposition)
                || "KEEP_AS_POLICY_REQUIREMENT".equalsIgnoreCase(disposition)
                || "IGNORE_FOR_AUTOMATION".equalsIgnoreCase(disposition)) {
            return false;
        }
        return true;
    }

    /**
     * POLICY-STUDIO-UX-CLOSURE-1 — count items that genuinely need business/configuration action.
     * Excludes Ignore / Keep-as-requirement / deleted / non-executable excluded items.
     */
    public static long countNeedsBusinessInput(PolicyStudioSession session) {
        if (session == null || session.getRuleCandidates() == null) {
            return 0L;
        }
        long n = 0L;
        for (CiPolicyRuleCandidate r : session.getRuleCandidates()) {
            if (!isIncludedExecutableRule(r)) {
                continue;
            }
            if (!isExecutionReady(r)) {
                n++;
            }
        }
        return n;
    }

    /** Authoring complete AND all required runtime operands resolved for execution. */
    public static boolean isExecutionReady(CiPolicyRuleCandidate r) {
        if (!isIncludedExecutableRule(r)) return false;
        if (!PolicyAuthoringCompleteness.isAuthoringComplete(r)) return false;
        return executionBlockingOperands(r).isEmpty();
    }

    /** Operands that block execution (unresolved / unavailable / incomplete manual / needs config). */
    public static List<Map<String, Object>> executionBlockingOperands(CiPolicyRuleCandidate r) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> op : operandsOf(r)) {
            if (operandBlocksExecution(op)) {
                out.add(op);
            }
        }
        return out;
    }

    public static boolean operandBlocksExecution(Map<String, Object> op) {
        if (op == null) return false;
        if (Boolean.TRUE.equals(op.get("unresolved"))) return true;
        if (Boolean.TRUE.equals(op.get("unavailable"))) return true;
        if (Boolean.TRUE.equals(op.get("needsConfiguration"))) return true;
        String status = String.valueOf(op.getOrDefault("status", ""));
        String avail = String.valueOf(op.getOrDefault("availability", ""));
        // Incomplete derivation/config — not auto-bound registry faces that are already MAPPED
        if (ParameterResolutionSupport.AVAIL_NEEDS_CONFIG.equals(avail)
                && !Boolean.TRUE.equals(op.get("autoBoundFromRegistry"))
                && !ParameterResolutionSupport.STATUS_MANUAL.equals(status)) {
            return true;
        }
        if (ParameterResolutionSupport.STATUS_MANUAL.equals(status)
                && !manualCaptureConfigured(op)) {
            return true;
        }
        return false;
    }

    /** MANUAL is valid only when capture path fields exist (existing model — no new engine). */
    public static boolean manualCaptureConfigured(Map<String, Object> op) {
        if (op == null) return false;
        String label = String.valueOf(op.getOrDefault("businessName", op.get("label")));
        if (label == null || label.isBlank() || "null".equals(label)) return false;
        Object enteredBy = op.get("enteredBy");
        Object dataType = op.get("dataType");
        Object evaluatedFrom = op.get("evaluatedFrom");
        boolean actorOk = enteredBy != null && !String.valueOf(enteredBy).isBlank()
                || "Manual Input".equalsIgnoreCase(String.valueOf(evaluatedFrom));
        boolean typeOk = dataType != null && !String.valueOf(dataType).isBlank()
                || op.get("unit") != null
                || Boolean.TRUE.equals(op.get("factSource"))
                || Boolean.TRUE.equals(op.get("manualInput"));
        return actorOk && typeOk;
    }

    public static boolean isAuthoringValueComplete(CiPolicyRuleCandidate r) {
        return PolicyAuthoringCompleteness.isAuthoringComplete(r);
    }

    public static List<Map<String, Object>> unresolvedOperands(CiPolicyRuleCandidate r) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> op : operandsOf(r)) {
            if (Boolean.TRUE.equals(op.get("unresolved"))) {
                out.add(op);
            }
        }
        return out;
    }

    public static List<Map<String, Object>> unavailableOperands(CiPolicyRuleCandidate r) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> op : operandsOf(r)) {
            if (Boolean.TRUE.equals(op.get("unavailable"))) {
                out.add(op);
            }
        }
        return out;
    }

    public static List<Map<String, Object>> operandsOf(CiPolicyRuleCandidate r) {
        if (r == null) return List.of();
        Map<String, Object> meta = r.getMetadata() == null ? Map.of() : r.getMetadata();
        List<String> dataUsed = dataUsedHints(r, meta);
        List<Map<String, Object>> ops =
                RuleOperandPresenter.buildOperands(r.getSystemRuleId(), dataUsed, meta, Map.of());
        for (Map<String, Object> op : ops) {
            if (ParameterResolutionSupport.STATUS_MANUAL.equals(String.valueOf(op.get("status")))
                    && !manualCaptureConfigured(op)) {
                op.put("needsConfiguration", true);
                op.put("unresolved", false);
                op.put("message", "Manual input is selected but runtime capture is not fully configured.");
            }
            if (ParameterResolutionSupport.AVAIL_NEEDS_CONFIG.equals(
                    String.valueOf(op.get("availability")))
                    && !ParameterResolutionSupport.isResolved(op)) {
                op.put("needsConfiguration", true);
            }
        }
        return ops;
    }

    private static List<String> dataUsedHints(CiPolicyRuleCandidate r, Map<String, Object> meta) {
        List<String> hints = new ArrayList<>();
        if (meta.get("parameterId") != null) {
            hints.add(String.valueOf(meta.get("parameterId")));
        }
        Object uploaded = meta.get("uploadedPolicyParameters");
        if (uploaded instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) hints.add(String.valueOf(o));
            }
        }
        String sys = r.getSystemRuleId() == null ? "" : r.getSystemRuleId().toUpperCase(Locale.ROOT);
        // Token-safe — never use sys.contains("EDI") (matches MONTHLY_CREDITS)
        if (SystemRuleIdTokens.hasAdbToken(r.getSystemRuleId())) {
            hints.add("banking.avg_daily_balance_3m");
        }
        if (SystemRuleIdTokens.hasProposedEdiToken(r.getSystemRuleId())) {
            hints.add("application.proposed_edi");
        }
        if (sys.contains("OVERDUE") || sys.contains("CLEAN")) {
            hints.add("bureau.credit_after_overdue.clean_history_months");
        }
        // Expression metric paths (authoritative for operand association)
        collectMetricPaths(r.getExpression(), hints);
        return hints;
    }

    @SuppressWarnings("unchecked")
    private static void collectMetricPaths(Object node, List<String> out) {
        if (!(node instanceof Map<?, ?> raw)) return;
        Map<String, Object> m = (Map<String, Object>) raw;
        if (m.get("metric") != null) out.add(String.valueOf(m.get("metric")));
        if (m.get("path") != null) out.add(String.valueOf(m.get("path")));
        for (Object v : m.values()) {
            if (v instanceof Map<?, ?> || v instanceof List<?>) {
                if (v instanceof List<?> list) {
                    for (Object item : list) collectMetricPaths(item, out);
                } else {
                    collectMetricPaths(v, out);
                }
            }
        }
    }

    public static List<Map<String, Object>> executionBlockersForRule(CiPolicyRuleCandidate r) {
        List<Map<String, Object>> blockers = new ArrayList<>();
        if (!isIncludedExecutableRule(r)) return blockers;
        String ruleName = ruleDisplayName(r);
        String ruleId = r.getId() == null ? r.getSystemRuleId() : r.getId().toString();

        Map<String, Object> meta = r.getMetadata() == null ? Map.of() : r.getMetadata();
        // Only surface threshold gap when authoring explicitly needs a threshold (not scratch/true rules)
        if (!PolicyAuthoringCompleteness.isAuthoringComplete(r)
                && PolicyAuthoringCompleteness.authoringThreshold(r) == null
                && unresolvedOperands(r).isEmpty()
                && (Boolean.TRUE.equals(meta.get("NEEDS_INPUT"))
                || meta.get("businessCapabilityId") != null
                || Boolean.TRUE.equals(meta.get("catalogueBacked")))) {
            blockers.add(blocker(BLOCKER_THRESHOLD_MISSING, ruleId, ruleName, null,
                    "Policy threshold missing — set the value before Accept",
                    "Edit rule"));
        }
        for (Map<String, Object> op : executionBlockingOperands(r)) {
            String label = String.valueOf(op.getOrDefault("label", op.get("businessName")));
            String paramId = String.valueOf(op.getOrDefault("suggestedParameterId",
                    op.getOrDefault("parameterId", op.get("operandKey"))));
            if (Boolean.TRUE.equals(op.get("unavailable"))) {
                blockers.add(blocker(BLOCKER_UNAVAILABLE_OPERAND, ruleId, ruleName, paramId,
                        label + " is unavailable from current data sources.",
                        "Configure source or exclude rule"));
            } else if (Boolean.TRUE.equals(op.get("needsConfiguration"))) {
                blockers.add(blocker(BLOCKER_NEEDS_CONFIGURATION, ruleId, ruleName, paramId,
                        label + " needs configuration before it can execute.",
                        "Configure"));
            } else {
                blockers.add(blocker(BLOCKER_UNRESOLVED_OPERAND, ruleId, ruleName, paramId,
                        label + " has no runtime source.",
                        "Resolve parameter"));
            }
        }
        return blockers;
    }

    public static List<Map<String, Object>> sessionExecutionBlockers(PolicyStudioSession session) {
        List<Map<String, Object>> blockers = new ArrayList<>();
        if (session == null) return blockers;
        for (CiPolicyRuleCandidate r : session.getRuleCandidates()) {
            blockers.addAll(executionBlockersForRule(r));
        }
        // Required policy adjustments for ADB when ADB is consumed by included rules
        blockers.addAll(requiredAdjustmentBlockers(session));
        // Genuine boundary / material ambiguities attached to included rules
        blockers.addAll(ambiguityBlockers(session));
        // Deduplicate by key; drop stale MATERIAL "more/less than 100" when BOUNDARY already covers the rule
        Set<String> boundaryRules = new java.util.LinkedHashSet<>();
        for (Map<String, Object> b : blockers) {
            if (BLOCKER_BOUNDARY_AMBIGUITY.equals(b.get("blockerType"))) {
                boundaryRules.add(String.valueOf(b.get("ruleId")));
            }
        }
        LinkedHashMap<String, Map<String, Object>> dedup = new LinkedHashMap<>();
        for (Map<String, Object> b : blockers) {
            if (BLOCKER_MATERIAL_AMBIGUITY.equals(b.get("blockerType"))
                    && boundaryRules.contains(String.valueOf(b.get("ruleId")))) {
                String reason = String.valueOf(b.getOrDefault("reason", "")).toLowerCase(Locale.ROOT);
                if (reason.contains("100") || reason.contains("more than") || reason.contains("less than")) {
                    continue; // stale duplicate of BOUNDARY_AMBIGUITY
                }
            }
            dedup.putIfAbsent(blockerKey(b), b);
        }
        return new ArrayList<>(dedup.values());
    }

    public static List<Map<String, Object>> sessionGovernanceBlockers(
            boolean scopeOk, boolean testsOk, boolean cmOk, boolean checkerOk) {
        List<Map<String, Object>> blockers = new ArrayList<>();
        if (!scopeOk) {
            blockers.add(gov("SCOPE", "Scope missing product", "Complete Scope"));
        }
        if (!testsOk) {
            blockers.add(gov("POLICY_TEST", "Test not completed — run Policy Test", "Run Policy Test"));
        }
        if (!cmOk) {
            blockers.add(gov("CM_APPROVAL", "Credit Manager approval required", "Approve as Credit Manager"));
        }
        if (!checkerOk) {
            blockers.add(gov("CHECKER_APPROVAL", "Checker approval required", "Checker approve"));
        }
        return blockers;
    }

    private static Map<String, Object> gov(String type, String reason, String action) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("blockerType", type);
        b.put("category", "GOVERNANCE");
        b.put("reason", reason);
        b.put("action", action);
        b.put("blockerKey", "GOV:" + type);
        return b;
    }

    private static List<Map<String, Object>> requiredAdjustmentBlockers(PolicyStudioSession session) {
        List<Map<String, Object>> out = new ArrayList<>();
        boolean adbConsumed = session.getRuleCandidates().stream()
                .filter(PolicyExecutionReadiness::isIncludedExecutableRule)
                .anyMatch(r -> {
                    String sys = r.getSystemRuleId() == null ? "" : r.getSystemRuleId().toUpperCase(Locale.ROOT);
                    String blob = String.valueOf(r.getExpression());
                    return sys.contains("ADB") || blob.contains("avg_daily_balance");
                });
        if (!adbConsumed) return out;
        if (hasExecutableAdbBulkAdjustment(session)) {
            return out;
        }
        for (CiPolicyRuleCandidate r : session.getRuleCandidates()) {
            Map<String, Object> m = r.getMetadata() == null ? Map.of() : r.getMetadata();
            if (!Boolean.TRUE.equals(m.get("metricAdjustment"))) continue;
            String affected = String.valueOf(m.getOrDefault("affectedMetric", ""));
            if (!"banking.avg_daily_balance_3m".equals(affected)) continue;
            String text = String.valueOf(m.getOrDefault("businessSummary",
                    m.getOrDefault("sourceClause", ""))).toLowerCase(Locale.ROOT);
            // Loan / gaming classifiers exist → not blocking. Bulk >10× needs config.
            if (text.contains("bulk") || text.contains("10 times") || text.contains("10×")) {
                out.add(blocker(BLOCKER_REQUIRED_ADJUSTMENT,
                        r.getId() == null ? r.getSystemRuleId() : r.getId().toString(),
                        "Adjusted Average Daily Balance",
                        "banking.avg_daily_balance_3m",
                        "Bulk deposit >10× adjustment cannot currently be calculated.",
                        "Configure"));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static boolean hasExecutableAdbBulkAdjustment(PolicyStudioSession session) {
        if (session == null || session.getDocument() == null || session.getDocument().getMetadata() == null) {
            return false;
        }
        Object raw = session.getDocument().getMetadata().get(PolicyDataResolutionSupport.DOC_META_KEY);
        if (!(raw instanceof Map<?, ?> maps)) return false;
        Object res = maps.get(com.los.core.creditintelligence.policystudio.metrics
                .AdbBulkDepositAdjustmentCalculator.ADJUSTMENT_ID);
        if (!(res instanceof Map<?, ?> m)) return false;
        if (!"READY".equals(String.valueOf(m.get("cmStatus")))
                && !"READY".equals(String.valueOf(m.get("executionStatus")))) {
            return false;
        }
        Object def = m.get("definition");
        if (def instanceof Map<?, ?> d) {
            return Boolean.TRUE.equals(d.get("executable"))
                    || Boolean.TRUE.equals(d.get("executableMetric"))
                    || com.los.core.creditintelligence.policystudio.metrics
                    .AdbBulkDepositAdjustmentCalculator.BINDING
                    .equals(String.valueOf(d.get("binding")));
        }
        return Boolean.TRUE.equals(m.get("executionCapabilityAvailable"));
    }

    private static List<Map<String, Object>> ambiguityBlockers(PolicyStudioSession session) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (session.getAmbiguities() == null) return out;
        for (CiPolicyAmbiguity a : session.getAmbiguities()) {
            if (a == null || !"OPEN".equals(a.getResolutionStatus())) continue;
            boolean material = "MATERIAL".equals(a.getSeverity())
                    || "BOUNDARY_AMBIGUITY".equalsIgnoreCase(a.getAmbiguityType())
                    || "BOUNDARY".equalsIgnoreCase(a.getAmbiguityType());
            if (!material) continue;
            // Skip if only affects excluded/ignored rules
            if (ambiguityOnlyAffectsExcluded(session, a)) continue;
            String phrase = a.getPhrase() == null ? "" : a.getPhrase();
            String lower = phrase.toLowerCase(Locale.ROOT);
            // Report-only / unused phrases that should not block activation
            if (isNonBlockingAmbiguityPhrase(lower)) continue;
            CiPolicyRuleCandidate affected = findAffectedRule(session, a);
            if (affected != null && !isIncludedExecutableRule(affected)) continue;
            String ruleName = affected == null ? "Policy rule" : ruleDisplayName(affected);
            String ruleId = affected == null ? null
                    : (affected.getId() == null ? affected.getSystemRuleId() : affected.getId().toString());
            String type = lower.contains("exactly 100") || lower.contains("= 100") || lower.contains("boundary")
                    ? BLOCKER_BOUNDARY_AMBIGUITY : BLOCKER_MATERIAL_AMBIGUITY;
            String reason = lower.contains("exactly 100") || lower.contains("100 transactions")
                    ? "Behaviour when transaction count = 100 is not defined."
                    : ("Unresolved: " + phrase);
            Map<String, Object> b = blocker(type, ruleId, ruleName, null, reason,
                    type.equals(BLOCKER_BOUNDARY_AMBIGUITY) ? "Define boundary" : "Edit rule / Define boundary");
            if (a.getId() != null) {
                b.put("ambiguityId", a.getId().toString());
            }
            if (type.equals(BLOCKER_BOUNDARY_AMBIGUITY)
                    && (lower.contains("exactly 100") || lower.contains("100 transactions"))) {
                b.putAll(com.los.core.creditintelligence.policystudio.parameters
                        .InwardReturnCompoundSupport.boundaryResolverPayload(
                                a.getId() == null ? null : a.getId().toString(), ruleId));
                b.put("action", "Define boundary");
            }
            out.add(b);
        }
        return out;
    }

    private static boolean isNonBlockingAmbiguityPhrase(String lower) {
        // Analyst/report-only or unused data concepts
        return lower.contains("large credit")
                || lower.contains("intercompany")
                || lower.contains("merchant group")
                || lower.contains("party wise")
                || (lower.contains("emi bounce") && !lower.contains("reject") && !lower.contains("rule"));
    }

    private static boolean ambiguityOnlyAffectsExcluded(PolicyStudioSession session, CiPolicyAmbiguity a) {
        // If we can find any included rule that references the phrase/clause, it is blocking
        CiPolicyRuleCandidate hit = findAffectedRule(session, a);
        return hit != null && !isIncludedExecutableRule(hit);
    }

    private static CiPolicyRuleCandidate findAffectedRule(PolicyStudioSession session, CiPolicyAmbiguity a) {
        UUID clauseId = a.getClauseId();
        String phrase = a.getPhrase() == null ? "" : a.getPhrase().toLowerCase(Locale.ROOT);
        for (CiPolicyRuleCandidate r : session.getRuleCandidates()) {
            if (clauseId != null && clauseId.equals(r.getClauseId())) return r;
        }
        if (phrase.contains("100") || phrase.contains("inward") || phrase.contains("return")) {
            for (CiPolicyRuleCandidate r : session.getRuleCandidates()) {
                String sys = r.getSystemRuleId() == null ? "" : r.getSystemRuleId().toUpperCase(Locale.ROOT);
                String title = String.valueOf((r.getMetadata() == null ? Map.of() : r.getMetadata())
                        .getOrDefault("businessTitle", ""));
                if (sys.contains("INWARD") || sys.contains("RETURN") || sys.contains("CHEQUE")
                        || title.toLowerCase(Locale.ROOT).contains("inward")
                        || title.toLowerCase(Locale.ROOT).contains("return")) {
                    return r;
                }
            }
        }
        if (phrase.contains("edi") || phrase.contains("proposed edi")) {
            for (CiPolicyRuleCandidate r : session.getRuleCandidates()) {
                if (SystemRuleIdTokens.hasProposedEdiToken(r.getSystemRuleId())
                        || SystemRuleIdTokens.hasAdbToken(r.getSystemRuleId())
                        || String.valueOf(r.getExpression()).toLowerCase(Locale.ROOT)
                        .contains("proposed_edi")) {
                    return r;
                }
            }
        }
        return null;
    }

    public static String classifyDataCalcRole(String parameterId, String itemKind, String clauseText,
                                              PolicyStudioSession session) {
        String kind = itemKind == null ? "" : itemKind;
        String text = clauseText == null ? "" : clauseText.toLowerCase(Locale.ROOT);
        if ("REPORT_ANALYST_INFORMATION".equals(kind)
                || text.contains("party wise") || text.contains("large credit")
                || text.contains("shown separately")) {
            return ROLE_REPORT_OR_ANALYST;
        }
        if ("CALCULATION_ADJUSTMENT".equals(kind) || text.contains("removed from average daily")) {
            boolean adbUsed = session != null && session.getRuleCandidates().stream()
                    .filter(PolicyExecutionReadiness::isIncludedExecutableRule)
                    .anyMatch(r -> {
                        String sys = r.getSystemRuleId() == null ? "" : r.getSystemRuleId().toUpperCase(Locale.ROOT);
                        return sys.contains("ADB") || String.valueOf(r.getExpression()).contains("avg_daily_balance");
                    });
            return adbUsed ? ROLE_REQUIRED_POLICY_ADJUSTMENT : ROLE_UNUSED;
        }
        if (parameterId != null && session != null) {
            boolean consumed = session.getRuleCandidates().stream()
                    .filter(PolicyExecutionReadiness::isIncludedExecutableRule)
                    .anyMatch(r -> {
                        String blob = (r.getSystemRuleId() + " " + r.getExpression()).toLowerCase(Locale.ROOT);
                        return blob.contains(parameterId.toLowerCase(Locale.ROOT))
                                || operandsOf(r).stream().anyMatch(op ->
                                parameterId.equals(String.valueOf(op.get("parameterId")))
                                        || parameterId.equals(String.valueOf(op.get("suggestedParameterId"))));
                    });
            if (consumed) {
                if (parameterId.startsWith("banking.") || parameterId.startsWith("bureau.")
                        || parameterId.startsWith("gst.")) {
                    return ROLE_REQUIRED_OPERAND;
                }
                return ROLE_REQUIRED_DEPENDENCY;
            }
        }
        if ("banking.emi_bounce_count_3m".equals(parameterId)
                || "banking.intercompany_transactions".equals(parameterId)
                || "banking.large_credit_transactions".equals(parameterId)) {
            return ROLE_REPORT_OR_ANALYST;
        }
        return ROLE_OPTIONAL;
    }

    public static boolean roleBlocksActivation(String role) {
        return ROLE_REQUIRED_OPERAND.equals(role)
                || ROLE_REQUIRED_DEPENDENCY.equals(role)
                || ROLE_REQUIRED_POLICY_ADJUSTMENT.equals(role);
    }

    /** Required executable inputs resolved / total — for data-readiness %. */
    public static Map<String, Object> executionReadinessStats(PolicyStudioSession session) {
        List<Map<String, Object>> blockers = sessionExecutionBlockers(session);
        long included = session.getRuleCandidates().stream().filter(PolicyExecutionReadiness::isIncludedExecutableRule).count();
        long ready = session.getRuleCandidates().stream()
                .filter(PolicyExecutionReadiness::isIncludedExecutableRule)
                .filter(PolicyExecutionReadiness::isExecutionReady)
                .count();
        // Denominator = distinct required operand/dependency keys from included rules + required adjustments
        Set<String> required = new java.util.LinkedHashSet<>();
        Set<String> resolved = new java.util.LinkedHashSet<>();
        for (CiPolicyRuleCandidate r : session.getRuleCandidates()) {
            if (!isIncludedExecutableRule(r)) continue;
            for (Map<String, Object> op : operandsOf(r)) {
                String key = String.valueOf(op.getOrDefault("operandKey", op.get("parameterId")));
                required.add(key);
                if (!operandBlocksExecution(op)) {
                    resolved.add(key);
                }
            }
        }
        for (Map<String, Object> b : blockers) {
            if (BLOCKER_REQUIRED_ADJUSTMENT.equals(b.get("blockerType"))) {
                required.add("adj:" + b.get("parameterId"));
            }
        }
        int reqN = Math.max(required.size(), 1);
        int resN = (int) resolved.stream().filter(required::contains).count();
        // adjustments unresolved reduce resolved count
        long adjBlock = blockers.stream().filter(b -> BLOCKER_REQUIRED_ADJUSTMENT.equals(b.get("blockerType"))).count();
        int percent = (int) Math.round(100.0 * Math.max(0, resN) / reqN);
        if (adjBlock > 0 && required.contains("adj:banking.avg_daily_balance_3m")) {
            // already reflected if we add adj keys as unresolved
            percent = (int) Math.round(100.0 * resN / Math.max(required.size(), 1));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("includedExecutableRules", included);
        out.put("executionReadyRules", ready);
        out.put("requiredInputCount", required.size());
        out.put("resolvedInputCount", resN);
        out.put("executionBlockerCount", blockers.size());
        out.put("dataReadinessPercent", percent);
        out.put("executionBlockers", blockers);
        out.put("allowCanonicalAuthority", false);
        return out;
    }

    public static void applyToCard(Map<String, Object> card, CiPolicyRuleCandidate r) {
        if (card == null || r == null) return;
        boolean included = isIncludedExecutableRule(r);
        boolean execReady = included && isExecutionReady(r);
        card.put("executionReady", execReady);
        card.put("includedForActivation", included);
        List<Map<String, Object>> blockers = executionBlockersForRule(r);
        card.put("executionBlockers", blockers);
        Map<String, Object> meta = r.getMetadata() == null ? Map.of() : r.getMetadata();
        String disposition = String.valueOf(meta.getOrDefault("disposition", ""));
        if ("ACCEPTED".equalsIgnoreCase(disposition) || "EDITED".equalsIgnoreCase(disposition)) {
            card.put("reviewDisposition", disposition.toUpperCase(Locale.ROOT));
        }
        boolean boundaryIncomplete = Boolean.TRUE.equals(card.get("boundaryIncomplete"))
                && !Boolean.TRUE.equals(meta.get("boundaryResolved"));
        if (("ACCEPTED".equalsIgnoreCase(disposition) || "EDITED".equalsIgnoreCase(disposition))
                && boundaryIncomplete) {
            card.put("reviewBadge", "Accepted · Boundary incomplete");
            card.put("executionReadinessLabel", "NEEDS RULE COMPLETION");
            card.put("boundaryIncomplete", true);
        } else if ("ACCEPTED".equalsIgnoreCase(disposition) && execReady) {
            card.put("reviewBadge", "Accepted");
            card.put("executionReadinessLabel", "READY");
        } else if ("ACCEPTED".equalsIgnoreCase(disposition) || "EDITED".equalsIgnoreCase(disposition)) {
            card.put("reviewBadge", disposition.equalsIgnoreCase("EDITED") ? "Edited" : "Accepted");
            card.put("executionReadinessLabel", execReady ? "READY" : "NEEDS RULE COMPLETION");
        }
        if (InwardReturnCompoundSupport.isIfExpression(r.getExpression())) {
            card.put("compoundEditable", true);
            card.put("editableModel", InwardReturnCompoundSupport.toEditableModel(r.getExpression(), meta));
            card.put("technicalExpression", r.getExpression());
        }
        if (!included) {
            return;
        }
        if (!execReady) {
            // ACCEPTED must not appear as READY
            if (!Set.of("Ignored", "Deleted", "Data requirement", "Metric adjustment", "Non-underwriting")
                    .contains(String.valueOf(card.get("status")))) {
                card.put("status", "Needs your input");
            }
            if (!blockers.isEmpty()) {
                card.put("blockedReason", String.valueOf(blockers.get(0).get("reason")));
            }
        }
    }

    private static Map<String, Object> blocker(
            String type, String ruleId, String ruleName, String parameterId, String reason, String action) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("blockerType", type);
        b.put("category", "EXECUTION");
        b.put("ruleId", ruleId);
        b.put("ruleName", ruleName);
        b.put("parameterId", parameterId);
        b.put("reason", reason);
        b.put("action", action);
        b.put("blockerKey", type + ":" + Objects.toString(ruleId, "") + ":" + Objects.toString(parameterId, "")
                + ":" + reason);
        return b;
    }

    private static String blockerKey(Map<String, Object> b) {
        return String.valueOf(b.getOrDefault("blockerKey", b.get("reason")));
    }

    private static String ruleDisplayName(CiPolicyRuleCandidate r) {
        Map<String, Object> m = r.getMetadata() == null ? Map.of() : r.getMetadata();
        if (m.get("businessTitle") != null) return String.valueOf(m.get("businessTitle"));
        return r.getSystemRuleId() == null ? "Underwriting rule" : r.getSystemRuleId();
    }
}
