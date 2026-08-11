package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyClause;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.lineage.PolicyMetricLineage;
import com.los.core.creditintelligence.policystudio.lineage.PolicyMetricLineageService;
import com.los.core.creditintelligence.policystudio.lineage.PolicyRulePresentationSemantics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * POLICY-CONVERGENCE-1 — Live-Rules-shaped CM fields for Policy Studio cards.
 * Presentation only; does not change engines.
 */
public final class PolicyStudioConvergencePresenter {

    private static final PolicyMetricLineageService LINEAGE = new PolicyMetricLineageService();

    private PolicyStudioConvergencePresenter() {}

    /** GACAT-PERSISTENCE-1 — shared DB-backed registry() (seed only in unit-test fallback). */
    public static CanonicalParameterRegistry registry() {
        return CanonicalParameterRegistry.shared();
    }

    public static String evaluatedFrom(
            List<String> dataUsed,
            String systemRuleId,
            Map<String, Object> meta,
            PolicyMetricLineage lineage) {
        if (lineage != null && lineage.source() != null && !lineage.source().isBlank()) {
            return normalizeEvalSource(lineage.source());
        }
        if (meta != null && meta.get("evaluatedFrom") != null) {
            String e = String.valueOf(meta.get("evaluatedFrom"));
            if (!e.isBlank() && !isPolicyDataLabel(e)) {
                return normalizeEvalSource(e);
            }
        }
        String sys = systemRuleId == null ? "" : systemRuleId.toUpperCase(Locale.ROOT);
        String joined = dataUsed == null ? "" : String.join(" ", dataUsed).toLowerCase(Locale.ROOT);
        if (sys.contains("BUREAU") || joined.contains("bureau.") || joined.contains("cibil")
                || sys.contains("OVERDUE") || sys.contains("DPD") || sys.contains("NTC")
                || sys.contains("WRITEOFF") || sys.contains("INQUIR")) {
            return "Bureau";
        }
        if (sys.contains("BANK") || sys.contains("ADB") || sys.contains("SETTLEMENT")
                || sys.contains("TXN") || joined.contains("banking.") || joined.contains("settlement")) {
            return "Bank Statement";
        }
        if (joined.contains("gst.") || sys.contains("GST")) return "GST";
        if (joined.contains("kyc.") || sys.contains("KYC")) return "KYC";
        if (joined.contains("application.")
                || SystemRuleIdTokens.hasProposedEdiToken(systemRuleId)
                || sys.contains("LOAN_AMOUNT")) {
            return "Application";
        }
        if (meta != null && Boolean.TRUE.equals(meta.get("manualInput"))) {
            return "Manual Input";
        }
        // Never show "Policy data" as evaluation source
        return "Needs confirmation";
    }

    public static String fromPolicy(CiPolicyClause clause, Map<String, Object> meta, String fileName) {
        if (meta != null && "MANUAL_CATALOGUE_ADD".equals(String.valueOf(meta.getOrDefault("source", "")))) {
            return "Credit Manager added";
        }
        if (clause != null && clause.getSourceText() != null && !clause.getSourceText().isBlank()) {
            String section = clause.getSection() == null ? "" : clause.getSection().trim();
            String preview = clause.getSourceText().trim();
            if (preview.length() > 120) {
                preview = preview.substring(0, 117) + "…";
            }
            if (!section.isBlank()) {
                return section + " — " + preview;
            }
            return preview;
        }
        if (fileName != null && !fileName.isBlank()) {
            return "Uploaded policy: " + fileName;
        }
        return "Uploaded policy";
    }

    public static String normalizeEvalSource(String raw) {
        if (raw == null || raw.isBlank() || isPolicyDataLabel(raw)) {
            return "Needs confirmation";
        }
        String l = raw.toLowerCase(Locale.ROOT);
        if (l.contains("bureau") || l.contains("credit report") || l.contains("cibil")) return "Bureau";
        if (l.contains("bank")) return "Bank Statement";
        if (l.contains("gst")) return "GST";
        if (l.contains("kyc") || l.contains("identity")) return "KYC";
        if (l.contains("financial") || l.contains("itr")) return "Financial Statements";
        if (l.contains("manual")) return "Manual Input";
        if (l.contains("application") || l.contains("computed") || l.contains("derived")) {
            if (l.contains("computed") || l.contains("derived")) return "Computed / Derived";
            return "Application";
        }
        return raw;
    }

    public static boolean isPolicyDataLabel(String s) {
        if (s == null) return false;
        String l = s.toLowerCase(Locale.ROOT);
        return l.equals("policy data") || l.equals("policy data element") || l.contains("policy data");
    }

    public static String treatmentDisplay(CiPolicyRuleCandidate r) {
        Map<String, Object> pf = PolicyRulePresentationSemantics.passFailPresentation(r);
        String t = String.valueOf(pf.getOrDefault("failureTreatment", pf.get("resultOnFailure")));
        return alignTreatment(t);
    }

    public static String alignTreatment(String raw) {
        if (raw == null || raw.isBlank()) return "Reject";
        String u = raw.toUpperCase(Locale.ROOT);
        if (u.contains("MANUAL") || u.contains("REFER") || u.contains("REVIEW")) return "Manual Review";
        if (u.contains("APPROVE") || u.equals("PASS") || u.equals("PASSING")) return "Approve";
        if (u.contains("INFO") || u.contains("WARNING")) return "Info";
        if (u.contains("REJECT") || u.contains("FAIL")) return "Reject";
        return raw;
    }

    public static Map<String, Object> operatorValue(CiPolicyRuleCandidate r, Map<String, Object> visual) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (visual != null && "EXCEPTION_ALL".equals(String.valueOf(visual.get("kind")))) {
            out.put("operator", "ALL");
            out.put("value", "compound conditions");
            out.put("compound", true);
            return out;
        }
        if (visual != null && visual.get("if") instanceof Map<?, ?> m) {
            out.put("operator", m.get("operator"));
            out.put("value", m.get("right"));
            out.put("parameterLabel", m.get("left"));
            return out;
        }
        Map<String, Object> expr = r.getExpression() == null ? Map.of() : r.getExpression();
        String op = expr.get("op") == null ? "" : String.valueOf(expr.get("op"));
        out.put("operator", friendlyOp(op));
        Object thr = expr.get("threshold") != null ? expr.get("threshold")
                : expr.get("value") != null ? expr.get("value") : expr.get("right");
        // Authoring threshold only (unwrap DSL {const: N}) — never an applicant runtime fact
        thr = PolicyAuthoringCompleteness.unwrapConst(thr);
        out.put("value", thr);
        return out;
    }

    public static String resolveParameterName(
            List<String> dataUsed, String systemRuleId, Map<String, Object> meta) {
        if (meta != null && meta.get("businessParameterName") != null) {
            return String.valueOf(meta.get("businessParameterName"));
        }
        String sys = systemRuleId == null ? "" : systemRuleId.toUpperCase(Locale.ROOT);
        if (sys.contains("SETTLEMENT_COUNT")) return "Average monthly settlements";
        if (sys.contains("SETTLEMENT_DIV") || sys.contains("SETTLEMENT_AVG")) return "Average daily settlements";
        if (sys.contains("OVERDUE_EXCEPTION") || sys.contains("NO_OVERDUE_EXCEPT")) {
            return "Overdue exception eligibility";
        }
        if (dataUsed != null) {
            for (String path : dataUsed) {
                var hit = registry().findById(path);
                if (hit.isPresent()) return hit.get().businessName();
            }
            if (!dataUsed.isEmpty()) {
                return friendlyPath(dataUsed.get(0));
            }
        }
        return "Parameter";
    }

    public static boolean isCompoundChild(String systemRuleId) {
        if (systemRuleId == null) return false;
        String sys = systemRuleId.toUpperCase(Locale.ROOT);
        // Children + the thin "no overdue except" pointer — parent OVERDUE_EXCEPTION_PARENT is the CM card
        return sys.contains("OVERDUE_CHILD_")
                || sys.matches(".*_CHILD_\\d+$")
                || sys.contains("NO_OVERDUE_EXCEPT_DOCUMENTED");
    }

    public static boolean isOverdueExceptionParent(String systemRuleId) {
        if (systemRuleId == null) return false;
        String sys = systemRuleId.toUpperCase(Locale.ROOT);
        return sys.contains("OVERDUE_EXCEPTION_PARENT");
    }

    public static Map<String, Object> compoundOverdueVisual(Map<String, Object> cleanDefMeta) {
        Map<String, Object> visual = new LinkedHashMap<>();
        visual.put("kind", "EXCEPTION_ALL");
        visual.put("title", "Overdue Exception Eligibility");
        visual.put("subtitle", "Allow only if ALL:");
        List<Map<String, Object>> conditions = new ArrayList<>();
        conditions.add(cond("Overdue age", ">", "12 months", false));
        conditions.add(cond("New credit after overdue", "=", "Yes", false));
        boolean cleanResolved = cleanDefMeta != null
                && cleanDefMeta.get("status") != null
                && !CleanHistoryDefinitionSupport.STATUS_UNRESOLVED.equals(String.valueOf(cleanDefMeta.get("status")));
        conditions.add(cond("Clean history", ">=", "6 months", !cleanResolved));
        conditions.add(cond("Overdue amount", "<", "₹1,500", false));
        visual.put("conditions", conditions);
        visual.put("then", "Reject");
        visual.put("logic", "ALL");
        return visual;
    }

    public static void applyConvergenceFields(
            Map<String, Object> card,
            CiPolicyRuleCandidate r,
            CiPolicyClause clause,
            List<String> dataUsed,
            String fileName) {
        Map<String, Object> meta = r.getMetadata() == null ? Map.of() : r.getMetadata();
        PolicyMetricLineage lineage = LINEAGE.resolveFromInputs(dataUsed, r.getSystemRuleId());
        String evalFrom = evaluatedFrom(dataUsed, r.getSystemRuleId(), meta, lineage);
        card.put("evaluatedFrom", evalFrom);
        card.put("fromPolicy", fromPolicy(clause, meta, fileName));
        // Never leave Policy data as evaluation source
        if (isPolicyDataLabel(String.valueOf(card.get("dataSource")))
                || isPolicyDataLabel(String.valueOf(card.get("dataFamily")))) {
            card.put("dataSource", evalFrom);
        } else if (card.get("dataSource") != null) {
            card.put("dataSource", normalizeEvalSource(String.valueOf(card.get("dataSource"))));
        } else {
            card.put("dataSource", evalFrom);
        }
        if (isPolicyDataLabel(String.valueOf(card.get("dataFamily")))) {
            card.put("dataFamily", evalFrom);
        }
        card.put("parameterName", resolveParameterName(dataUsed, r.getSystemRuleId(), meta));
        @SuppressWarnings("unchecked")
        Map<String, Object> visual = card.get("visualLogic") instanceof Map<?, ?>
                ? (Map<String, Object>) card.get("visualLogic") : Map.of();
        Object cleanMetaObj = meta.get(CleanHistoryDefinitionSupport.META_KEY);
        @SuppressWarnings("unchecked")
        Map<String, Object> cleanMap = cleanMetaObj instanceof Map<?, ?>
                ? (Map<String, Object>) cleanMetaObj : null;
        if (isOverdueExceptionParent(r.getSystemRuleId())) {
            Map<String, Object> cleanForVisual = cleanMap;
            Map<String, Object> cleanRes = ParameterResolutionSupport.resolutionFor(meta, "clean_history");
            if (ParameterResolutionSupport.isResolved(cleanRes)) {
                cleanForVisual = ParameterResolutionSupport.toCleanHistoryBridge(cleanRes);
            }
            visual = compoundOverdueVisual(cleanForVisual);
            card.put("visualLogic", visual);
            card.put("ruleName", "Overdue Exception Eligibility");
            card.put("businessRule", "Allow only if ALL compound Bureau conditions are met");
            card.put("compoundParent", true);
            card.put("parameterName", "Overdue exception eligibility");
            if (cleanMap == null || CleanHistoryDefinitionSupport.STATUS_UNRESOLVED
                    .equals(String.valueOf(cleanMap.getOrDefault("status", STATUS_UNRESOLVED_FALLBACK)))) {
                Map<String, Object> cleanPayload = CleanHistoryDefinitionSupport.unresolvedCardPayload(registry());
                cleanPayload.put("useGenericResolver", true);
                cleanPayload.put("headline", "Clean credit history — Not yet mapped");
                card.put("cleanDefinition", cleanPayload);
                // Primary message is operand "Not yet mapped" — avoid stacked "needs a definition"
                card.put("blockedReason", null);
                if (!"Ignored".equals(card.get("status")) && !"Deleted".equals(card.get("status"))) {
                    card.put("status", "Needs your input");
                }
            } else {
                card.put("cleanDefinition", cleanMap);
            }
        }
        Map<String, Object> ov = operatorValue(r, visual);
        card.put("operator", ov.get("operator"));
        card.put("thresholdValue", ov.get("value"));
        card.put("operatorValueLabel", formatOpValue(ov));
        String treatment = treatmentDisplay(r);
        card.put("treatment", treatment);
        card.put("failureTreatmentDisplay", treatment);
        card.put("resultOnFailure", treatment);
        boolean child = isCompoundChild(r.getSystemRuleId());
        card.put("compoundChild", child);
        card.put("primaryUnderwritingRule", !child
                && !Boolean.TRUE.equals(card.get("dataRequirementOnly"))
                && !"Data requirement".equals(card.get("status"))
                && !"Metric adjustment".equals(card.get("status"))
                && !"Non-underwriting".equals(card.get("status")));
        // Parameter binding from read model
        if (dataUsed != null) {
            for (String path : dataUsed) {
                registry().findById(path).ifPresent(p ->
                        card.put("canonicalParameter", p.toBusinessView()));
            }
        }
        if (lineage != null) {
            Map<String, Object> how = LINEAGE.howCalculated(lineage);
            how.put("source", normalizeEvalSource(String.valueOf(how.getOrDefault("source", evalFrom))));
            card.put("howCalculated", how);
        }
        // POLICY-DATA-UX-1 — promote canonical binding for Data & calculations grouping
        enrichDataCalcCanonicalBinding(card, clause, meta);
        // POLICY-PARAMETER-RESOLVER-1 — independent operands (ADB/EDI, CLEAN, …)
        @SuppressWarnings("unchecked")
        Map<String, Object> visualForOperands = card.get("visualLogic") instanceof Map<?, ?>
                ? (Map<String, Object>) card.get("visualLogic") : visual;
        List<Map<String, Object>> operands = RuleOperandPresenter.buildOperands(
                r.getSystemRuleId(), dataUsed, meta, visualForOperands);
        if (!operands.isEmpty()) {
            card.put("operands", operands);
            card.put("parameterResolver", true);
            boolean anyUnresolved = operands.stream()
                    .anyMatch(o -> Boolean.TRUE.equals(o.get("unresolved")));
            boolean anyUnavailable = operands.stream()
                    .anyMatch(o -> Boolean.TRUE.equals(o.get("unavailable")));
            if (anyUnresolved) {
                card.put("blockedReason", null);
                if (!"Ignored".equals(card.get("status")) && !"Deleted".equals(card.get("status"))) {
                    card.put("status", "Needs your input");
                }
                // CLEAN still exposes legacy payload for advanced, but primary UX is Resolve parameter
                if (isOverdueExceptionParent(r.getSystemRuleId())
                        && (cleanMap == null || CleanHistoryDefinitionSupport.STATUS_UNRESOLVED
                        .equals(String.valueOf(
                                cleanMap == null ? STATUS_UNRESOLVED_FALLBACK
                                        : cleanMap.getOrDefault("status", STATUS_UNRESOLVED_FALLBACK))))) {
                    Map<String, Object> cleanPayload = CleanHistoryDefinitionSupport.unresolvedCardPayload(registry());
                    cleanPayload.put("useGenericResolver", true);
                    cleanPayload.put("headline", "Clean credit history — Not yet mapped");
                    card.put("cleanDefinition", cleanPayload);
                }
            } else if (anyUnavailable) {
                card.put("blockedReason",
                        "Parameter understood but unavailable from current data sources.");
            }
        }
    }

    private static final String STATUS_UNRESOLVED_FALLBACK = CleanHistoryDefinitionSupport.STATUS_UNRESOLVED;

    private static Map<String, Object> cond(String param, String op, String value, boolean needsDef) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("parameter", param);
        m.put("operator", op);
        m.put("value", value);
        m.put("needsDefinition", needsDef);
        if (needsDef) {
            m.put("badge", "Needs definition");
        }
        return m;
    }

    private static String friendlyOp(String op) {
        return switch (op == null ? "" : op.toUpperCase(Locale.ROOT)) {
            case "GTE", ">=" -> ">=";
            case "GT", ">" -> ">";
            case "LTE", "<=" -> "<=";
            case "LT", "<" -> "<";
            case "EQ", "=" -> "=";
            case "NE", "!=" -> "≠";
            case "AND_CHILDREN", "ALL" -> "ALL";
            default -> op;
        };
    }

    private static String formatOpValue(Map<String, Object> ov) {
        if (Boolean.TRUE.equals(ov.get("compound"))) {
            return "ALL compound conditions";
        }
        Object op = ov.get("operator");
        Object val = ov.get("value");
        if (op == null && val == null) return "—";
        return (op == null ? "" : op) + (val == null ? "" : " " + val);
    }

    private static String friendlyPath(String path) {
        if (path == null) return "Parameter";
        return registry().findById(path).map(CanonicalParameterDefinition::businessName)
                .orElse(path.replace("bureau.", "").replace("banking.", "").replace('_', ' '));
    }

    /**
     * POLICY-DATA-UX-1 — attach canonicalParameterId / itemKind for parameter-oriented Data & calc UI.
     * Does not mutate CanonicalParameterRegistry definitions (policy-scoped metadata only on the card).
     */
    public static void enrichDataCalcCanonicalBinding(
            Map<String, Object> card,
            CiPolicyClause clause,
            Map<String, Object> meta) {
        boolean dataReq = Boolean.TRUE.equals(card.get("dataRequirementOnly"))
                || "Data requirement".equals(String.valueOf(card.get("status")));
        boolean metricAdj = Boolean.TRUE.equals(card.get("metricAdjustment"))
                || "Metric adjustment".equals(String.valueOf(card.get("status")));
        if (!dataReq && !metricAdj) {
            return;
        }
        String clauseText = clause != null && clause.getSourceText() != null
                ? clause.getSourceText()
                : String.valueOf(card.getOrDefault("sourceClause",
                card.getOrDefault("businessRule", "")));
        String paramId = firstNonBlank(
                stringOrNull(meta.get("affectedMetric")),
                stringOrNull(card.get("parameterId")),
                stringOrNull(meta.get("parameterId")),
                card.get("canonicalParameter") instanceof Map<?, ?> cp
                        ? stringOrNull(((Map<?, ?>) cp).get("id")) : null,
                resolveDataCalcParameterId(clauseText, metricAdj));
        if (paramId != null) {
            card.put("parameterId", paramId);
            card.put("canonicalParameterId", paramId);
            registry().findById(paramId).ifPresent(p -> {
                card.put("canonicalParameter", p.toBusinessView());
                if (card.get("howCalculated") == null && p.calculationSummary() != null) {
                    Map<String, Object> how = new LinkedHashMap<>();
                    how.put("source", p.evaluatedFrom());
                    how.put("metric", p.businessName());
                    how.put("calculation", p.calculationSummary());
                    how.put("assessmentPeriod", p.period());
                    how.put("policyScoped", false);
                    how.put("note", "Enterprise/base definition from Data & Parameters — policy adjustments listed separately");
                    card.put("howCalculated", how);
                }
                if (card.get("parameterType") == null) {
                    card.put("parameterType", p.type());
                }
                if (card.get("period") == null || "—".equals(String.valueOf(card.get("period")))
                        || String.valueOf(card.get("period")).isBlank()) {
                    if (p.period() != null) {
                        card.put("period", friendlyRegistryPeriod(p.period()));
                    }
                }
            });
        }
        if (metricAdj) {
            card.put("itemKind", "CALCULATION_ADJUSTMENT");
            if (meta.get("affectedMetric") != null) {
                card.put("affectedParameterId", String.valueOf(meta.get("affectedMetric")));
            } else if (paramId != null) {
                card.put("affectedParameterId", paramId);
            }
        } else if (isReportOnlyClause(clauseText)) {
            card.put("itemKind", "REPORT_ANALYST_INFORMATION");
        } else if (paramId != null && registry().findById(paramId).map(p ->
                CanonicalParameterDefinition.DERIVED.equals(p.type())).orElse(false)) {
            card.put("itemKind", "DERIVED_PARAMETER");
        } else if (paramId != null && registry().findById(paramId).map(p ->
                CanonicalParameterDefinition.RAW.equals(p.type())).orElse(false)) {
            card.put("itemKind", "RAW_DATA_REQUIRED");
        } else {
            card.put("itemKind", "RAW_DATA_REQUIRED");
        }
        // Prefer clause wording over generic "Data requirement" / "Metric adjustment"
        String generic = String.valueOf(card.getOrDefault("ruleName", ""));
        if (generic.isBlank() || "Data requirement".equalsIgnoreCase(generic)
                || "Metric adjustment".equalsIgnoreCase(generic)
                || "Non-underwriting".equalsIgnoreCase(generic)) {
            String title = shortClauseTitle(clauseText);
            if (title != null) {
                card.put("ruleName", title);
            }
        }
        card.put("missingDefinition", missingDefinitionHint(clauseText, paramId, metricAdj));
    }

    private static String resolveDataCalcParameterId(String clauseText, boolean metricAdj) {
        if (clauseText == null || clauseText.isBlank()) return null;
        String l = clauseText.toLowerCase(Locale.ROOT);
        if (metricAdj || l.contains("average daily balance") || l.contains("removed from average daily")) {
            if (l.contains("average daily balance") || l.contains("removed from average daily")) {
                return "banking.avg_daily_balance_3m";
            }
        }
        if (l.contains("average monthly settlement") || l.contains("settlement count")) {
            return "banking.settlement.count_monthly_avg_3m";
        }
        if (l.contains("average daily qr") || l.contains("daily qr settlement")
                || l.contains("average daily settlement")) {
            return "banking.settlement.avg_daily_3m";
        }
        if (l.contains("average monthly transaction")) {
            return "banking.transaction_count.average_monthly_3m";
        }
        if (l.contains("inward cheque") || l.contains("ecs") || l.contains("enach")) {
            return "banking.inward_return.ratio_3m";
        }
        if (l.contains("emi bounce")) {
            return "banking.emi_bounce_count_3m"; // may be unresolved in registry() — frontend handles
        }
        if (l.contains("large credit")) {
            return "banking.large_credit_transactions";
        }
        if (l.contains("intercompany") || l.contains("merchant group")) {
            return "banking.intercompany_transactions";
        }
        if (l.contains("online gaming") && !l.contains("removed from")) {
            return "bank.transaction.classification";
        }
        if (l.contains("loans disbursed") && !l.contains("removed from")) {
            return "bank.transaction.classification";
        }
        // Prefer registry() alias resolve for known phrases
        return registry().resolve(clauseText.length() > 80 ? clauseText.substring(0, 80) : clauseText)
                .map(CanonicalParameterDefinition::id)
                .orElse(null);
    }

    private static boolean isReportOnlyClause(String clauseText) {
        if (clauseText == null) return false;
        String l = clauseText.toLowerCase(Locale.ROOT);
        return l.contains("party wise") || l.contains("party-wise")
                || l.contains("shown separately")
                || l.contains("with the name of the bank")
                || l.contains("name and amount");
    }

    private static Map<String, Object> missingDefinitionHint(String clauseText, String paramId, boolean metricAdj) {
        if (clauseText == null) return null;
        String l = clauseText.toLowerCase(Locale.ROOT);
        Map<String, Object> m = new LinkedHashMap<>();
        if (l.contains("large credit")) {
            m.put("field", "largeThreshold");
            m.put("question", "What qualifies as a \"Large\" credit?");
            m.put("hint", "Define an amount threshold (₹). Relative thresholds only if your institution confirms them.");
            m.put("action", "DEFINE");
            return m;
        }
        if (l.contains("intercompany") || l.contains("merchant group")) {
            m.put("field", "merchantGroupDefinition");
            m.put("question", "How should \"Intercompany / Within Merchant group\" relationships be identified?");
            m.put("hint", "Institution-specific relationship / group definition is required.");
            m.put("action", "DEFINE");
            return m;
        }
        if (l.contains("emi bounce")) {
            m.put("field", "emiBounceDerivation");
            m.put("question", "EMI bounce derivation needs configuration");
            m.put("hint", "Bank classifier has EMI and bounce/return flags separately; a combined EMI-bounce metric is not production-bound.");
            m.put("action", "CONFIGURE");
            return m;
        }
        if (metricAdj && (l.contains("10 times") || l.contains("bulk"))) {
            m.put("field", "bulkDepositMultiple");
            m.put("question", "Bulk deposit exclusion uses policy multiple (10× average deposits)");
            m.put("hint", "Confirm average-deposit baseline window and whether 10× is fixed for this policy.");
            m.put("action", "CONFIRM");
            return m;
        }
        if (paramId == null) {
            m.put("field", "parameterMapping");
            m.put("question", "Which canonical parameter does this clause map to?");
            m.put("action", "RESOLVE");
            return m;
        }
        return null;
    }

    private static String shortClauseTitle(String clauseText) {
        if (clauseText == null || clauseText.isBlank()) return null;
        String t = clauseText.trim().replaceAll("\\s+", " ");
        if (t.startsWith("- ")) t = t.substring(2);
        if (t.length() > 90) t = t.substring(0, 87) + "…";
        if (t.endsWith(".")) t = t.substring(0, t.length() - 1);
        return t;
    }

    private static String friendlyRegistryPeriod(String period) {
        if (period == null) return null;
        return switch (period) {
            case "TRAILING_3M" -> "Last 3 months";
            case "TRAILING_6M" -> "Last 6 months";
            case "TRAILING_12M" -> "Last 12 months";
            case "PIT" -> "Point in time";
            default -> period.replace('_', ' ');
        };
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank() && !"null".equalsIgnoreCase(v)) return v;
        }
        return null;
    }

    private static String stringOrNull(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    public static Map<String, Object> groupCards(List<Map<String, Object>> cards) {
        List<Map<String, Object>> underwriting = new ArrayList<>();
        List<Map<String, Object>> dataCalc = new ArrayList<>();
        List<Map<String, Object>> otherContent = new ArrayList<>();
        List<Map<String, Object>> hiddenChildren = new ArrayList<>();
        for (Map<String, Object> c : cards) {
            String status = String.valueOf(c.get("status"));
            if (Boolean.TRUE.equals(c.get("compoundChild"))) {
                hiddenChildren.add(c);
                continue;
            }
            String cls = String.valueOf(c.getOrDefault("classification",
                    asRecord(c.get("metadata")).getOrDefault("classification", "")));
            boolean classificationOnly = Boolean.TRUE.equals(c.get("classificationOnly"))
                    || "CLASSIFICATION".equalsIgnoreCase(String.valueOf(
                    asRecord(c.get("technicalExpression")).getOrDefault("op",
                            asRecord(c.get("expression")).get("op"))));
            boolean narrativeOrOther = classificationOnly
                    || Set.of("NARRATIVE", "AMBIGUOUS", "SERVICING_RULE", "PORTFOLIO_CONTROL",
                    "PRODUCT_CONFIG", "DOCUMENT_REQUIREMENT").contains(cls)
                    || "Narrative / Excluded".equals(String.valueOf(c.get("businessGroup")))
                    || "Other policy content".equals(String.valueOf(c.get("businessGroup")));
            if (Set.of("Data requirement", "Metric adjustment", "Non-underwriting").contains(status)
                    || Boolean.TRUE.equals(c.get("dataRequirementOnly"))
                    || Boolean.TRUE.equals(c.get("metricAdjustment"))) {
                dataCalc.add(c);
            } else if (narrativeOrOther && !Boolean.TRUE.equals(c.get("cmAuthored"))) {
                // POLICY-RULE-AUTHORING-FIX-1 — do not count narrative/classification as UW rules
                Map<String, Object> mutable = new LinkedHashMap<>(c);
                mutable.put("businessGroup", "Other policy content");
                otherContent.add(mutable);
            } else {
                underwriting.add(c);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("underwritingRules", underwriting);
        out.put("dataAndCalculations", dataCalc);
        out.put("otherPolicyContent", otherContent);
        out.put("compoundChildrenAdvanced", hiddenChildren);
        out.put("underwritingRuleCount", underwriting.size());
        out.put("otherPolicyContentCount", otherContent.size());
        out.put("dataRequirementCount", dataCalc.stream()
                .filter(c -> "Data requirement".equals(c.get("status"))).count());
        out.put("metricAdjustmentCount", dataCalc.stream()
                .filter(c -> "Metric adjustment".equals(c.get("status"))).count());
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asRecord(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }
}
