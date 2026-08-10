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

    private static final CanonicalParameterRegistry REGISTRY = new CanonicalParameterRegistry();
    private static final PolicyMetricLineageService LINEAGE = new PolicyMetricLineageService();

    private PolicyStudioConvergencePresenter() {}

    public static CanonicalParameterRegistry registry() {
        return REGISTRY;
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
        if (joined.contains("application.") || sys.contains("EDI") || sys.contains("LOAN_AMOUNT")) {
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
                var hit = REGISTRY.findById(path);
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
        if (isOverdueExceptionParent(r.getSystemRuleId())) {
            Object cleanMeta = meta.get(CleanHistoryDefinitionSupport.META_KEY);
            @SuppressWarnings("unchecked")
            Map<String, Object> cleanMap = cleanMeta instanceof Map<?, ?>
                    ? (Map<String, Object>) cleanMeta : null;
            visual = compoundOverdueVisual(cleanMap);
            card.put("visualLogic", visual);
            card.put("ruleName", "Overdue Exception Eligibility");
            card.put("businessRule", "Allow only if ALL compound Bureau conditions are met");
            card.put("compoundParent", true);
            card.put("parameterName", "Overdue exception eligibility");
            if (cleanMap == null || CleanHistoryDefinitionSupport.STATUS_UNRESOLVED
                    .equals(String.valueOf(cleanMap.getOrDefault("status", STATUS_UNRESOLVED_FALLBACK)))) {
                card.put("cleanDefinition", CleanHistoryDefinitionSupport.unresolvedCardPayload(REGISTRY));
                card.put("blockedReason", "Clean credit history needs a definition.");
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
                REGISTRY.findById(path).ifPresent(p ->
                        card.put("canonicalParameter", p.toBusinessView()));
            }
        }
        if (lineage != null) {
            Map<String, Object> how = LINEAGE.howCalculated(lineage);
            how.put("source", normalizeEvalSource(String.valueOf(how.getOrDefault("source", evalFrom))));
            card.put("howCalculated", how);
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
        return REGISTRY.findById(path).map(CanonicalParameterDefinition::businessName)
                .orElse(path.replace("bureau.", "").replace("banking.", "").replace('_', ' '));
    }

    public static Map<String, Object> groupCards(List<Map<String, Object>> cards) {
        List<Map<String, Object>> underwriting = new ArrayList<>();
        List<Map<String, Object>> dataCalc = new ArrayList<>();
        List<Map<String, Object>> hiddenChildren = new ArrayList<>();
        for (Map<String, Object> c : cards) {
            String status = String.valueOf(c.get("status"));
            if (Boolean.TRUE.equals(c.get("compoundChild"))) {
                hiddenChildren.add(c);
                continue;
            }
            if (Set.of("Data requirement", "Metric adjustment", "Non-underwriting").contains(status)
                    || Boolean.TRUE.equals(c.get("dataRequirementOnly"))
                    || Boolean.TRUE.equals(c.get("metricAdjustment"))) {
                dataCalc.add(c);
            } else {
                underwriting.add(c);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("underwritingRules", underwriting);
        out.put("dataAndCalculations", dataCalc);
        out.put("compoundChildrenAdvanced", hiddenChildren);
        out.put("underwritingRuleCount", underwriting.size());
        out.put("dataRequirementCount", dataCalc.stream()
                .filter(c -> "Data requirement".equals(c.get("status"))).count());
        out.put("metricAdjustmentCount", dataCalc.stream()
                .filter(c -> "Metric adjustment".equals(c.get("status"))).count());
        return out;
    }
}
