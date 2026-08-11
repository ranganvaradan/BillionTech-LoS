package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.creditintelligence.decisionpolicy.DecisionPolicyDomain;
import com.los.core.creditintelligence.decisionpolicy.DecisionPolicyRuleMetadata;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyClause;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.ClauseType;
import com.los.core.creditintelligence.policystudio.domain.ReviewState;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * POLICY-RULE-AUTHORING-FIX-1 — Credit Manager rule authoring (structured + plain English).
 * Reuses CanonicalParameterRegistry + PolicyDsl. Does not create a new rule engine.
 * Preview is never silently saved; Confirm persists a draft underwriting rule.
 */
@Service
public class CmRuleAuthoringService {

    private static final CanonicalParameterRegistry REGISTRY = new CanonicalParameterRegistry();

    private static final Pattern NUM = Pattern.compile(
            "(>=|<=|>|<|=|at least|at most|not exceed|no more than|less than|greater than|more than)?\\s*"
                    + "(\\d+(?:\\.\\d+)?)\\s*(%|percent|months?|m)?",
            Pattern.CASE_INSENSITIVE);

    public Map<String, Object> sources() {
        Map<String, List<Map<String, Object>>> bySource = new LinkedHashMap<>();
        for (CanonicalParameterDefinition d : REGISTRY.all()) {
            String src = d.evaluatedFrom() == null ? "Other" : d.evaluatedFrom();
            bySource.computeIfAbsent(src, k -> new ArrayList<>()).add(paramRow(d));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sources", bySource.keySet().stream().sorted().toList());
        out.put("bySource", bySource);
        out.put("operatorsByType", Map.of(
                "NUMBER", List.of(">", ">=", "<", "<=", "="),
                "PERCENT", List.of(">", ">=", "<", "<=", "="),
                "COUNT", List.of(">", ">=", "<", "<=", "="),
                "MONEY", List.of(">", ">=", "<", "<=", "="),
                "FLAG", List.of("=", "is", "is not"),
                "BOOLEAN", List.of("=", "is", "is not")));
        out.put("treatments", List.of("Reject", "Manual Review", "Approve", "Info"));
        return out;
    }

    /** Preview structured or plain-English rule — does not persist. */
    public Map<String, Object> preview(Map<String, Object> body) {
        String mode = str(body, "mode", "DESCRIBE");
        DraftDraft draft;
        if ("BUILD".equalsIgnoreCase(mode) || body.get("parameterId") != null) {
            draft = fromStructured(body);
        } else {
            draft = fromPlainEnglish(str(body, "text", str(body, "businessRule", "")));
        }
        return toPreview(draft);
    }

    /**
     * Confirm & add (or replace) an underwriting rule on the draft session.
     * When replaceRuleId is set, rewrites that candidate in place (provenance retained).
     */
    public Map<String, Object> confirm(
            PolicyStudioSession session, Map<String, Object> body) {
        Map<String, Object> preview = preview(body);
        if (!Boolean.TRUE.equals(preview.get("complete"))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    String.valueOf(preview.getOrDefault("message",
                            "I couldn't turn this into a complete rule. Complete the missing fields.")));
        }
        DraftDraft draft = "BUILD".equalsIgnoreCase(str(body, "mode", ""))
                || body.get("parameterId") != null
                ? fromStructured(mergePreviewDefaults(body, preview))
                : fromPlainEnglish(str(body, "text", str(body, "businessRule", "")));
        if (!draft.complete && body.get("parameterId") != null) {
            draft = fromStructured(mergePreviewDefaults(body, preview));
        }
        if (!draft.complete) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    draft.message == null ? "Incomplete rule" : draft.message);
        }
        // Apply CM overrides from confirm body
        if (body.get("treatment") != null) draft.treatment = String.valueOf(body.get("treatment"));
        if (body.get("operator") != null) draft.operator = String.valueOf(body.get("operator"));
        if (body.get("value") != null) draft.value = coerceNumber(body.get("value"));
        if (body.get("period") != null) draft.period = String.valueOf(body.get("period"));

        CiPolicyDocument doc = session.getDocument();
        if (doc == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy document not found");
        }

        final UUID replaceId;
        if (body.get("replaceRuleId") != null && !String.valueOf(body.get("replaceRuleId")).isBlank()) {
            try {
                replaceId = UUID.fromString(String.valueOf(body.get("replaceRuleId")));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid replaceRuleId");
            }
        } else {
            replaceId = null;
        }

        String sourceText = draft.sourceText != null ? draft.sourceText
                : draft.businessName + " " + draft.operator + " " + draft.value;
        CiPolicyClause clause = CiPolicyClause.builder()
                .id(UUID.randomUUID())
                .policyDocumentId(doc.getId())
                .section("Credit Rules")
                .sourceText(sourceText)
                .normalizedText(sourceText.replaceAll("\\s+", " "))
                .clauseType(ClauseType.HARD_RULE.name())
                .extractionConfidence(new BigDecimal("0.9500"))
                .sortOrder(session.getClauses().size())
                .sourceLocation(replaceId == null ? "cm-authoring:add" : "cm-authoring:edit:" + replaceId)
                .status("EXTRACTED")
                .metadata(Map.of(
                        "cmAuthored", true,
                        "plainEnglishAdded", draft.fromPlainEnglish,
                        "businessGroup", "Credit Rules"))
                .effectiveScope(Map.of("products", List.of("ALL")))
                .build();

        Map<String, Object> expression = buildExpression(draft);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("disposition", "ACCEPTED");
        meta.put("cmAuthored", true);
        meta.put("plainEnglishAdded", draft.fromPlainEnglish);
        meta.put("businessTitle", draft.businessName);
        meta.put("businessParameterName", draft.businessName);
        meta.put("businessSummary", draft.businessName + " " + draft.operator + " " + formatValue(draft));
        meta.put("evaluatedFrom", draft.source);
        meta.put("catalogueBacked", false);
        meta.put("classificationOnly", false);
        meta.put("excludedFromActivation", false);
        meta.put("deleted", false);
        meta.put("NEEDS_INPUT", false);
        meta.put("failureTreatment", treatmentCode(draft.treatment));
        meta.put("parameterId", draft.parameterId);
        meta.put("operator", draft.operator);
        meta.put("threshold", draft.value);
        meta.put("period", draft.period);
        meta.put(DecisionPolicyRuleMetadata.KEY_DOMAIN, DecisionPolicyDomain.CREDIT.name());
        if (replaceId != null) {
            meta.put("replacedRuleId", replaceId.toString());
            meta.put("replacedNarrative", true);
        }

        String systemId = "CM_" + draft.parameterId.replace('.', '_').toUpperCase(Locale.ROOT)
                + "_" + opCode(draft.operator);
        TreatmentPair tp = treatmentPair(draft.treatment);

        CiPolicyRuleCandidate rule;
        if (replaceId != null) {
            CiPolicyRuleCandidate existing = session.getRuleCandidates().stream()
                    .filter(r -> replaceId.equals(r.getId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule to edit not found"));
            // Preserve provenance of original clause text
            Map<String, Object> lineage = existing.getLineage() == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(existing.getLineage());
            lineage.put("priorSystemRuleId", existing.getSystemRuleId());
            lineage.put("priorExpression", existing.getExpression());
            lineage.put("priorSourceText", lineage.getOrDefault("sourceText",
                    existing.getExpression() == null ? null : existing.getExpression().get("sourceText")));
            lineage.put("sourceText", sourceText);
            lineage.put("clauseId", clause.getId().toString());
            lineage.put("editedByCm", true);
            existing.setClauseId(clause.getId());
            existing.setSystemRuleId(systemId);
            existing.setRuleType("HARD");
            existing.setExpression(expression);
            existing.setOnTrue(tp.onTrue);
            existing.setOnFalse(tp.onFalse);
            existing.setOnMissing("DATA_INSUFFICIENT");
            existing.setReviewStatus(ReviewState.CREDIT_MANAGER_APPROVED.name());
            existing.setLineage(lineage);
            existing.setMetadata(meta);
            existing.setScope(Map.of(
                    DecisionPolicyRuleMetadata.KEY_DOMAIN, DecisionPolicyDomain.CREDIT.name(),
                    "products", List.of("ALL")));
            rule = existing;
            session.getClauses().add(clause);
        } else {
            Map<String, Object> lineage = new LinkedHashMap<>();
            lineage.put("documentId", doc.getId().toString());
            lineage.put("documentName", doc.getName());
            lineage.put("clauseId", clause.getId().toString());
            lineage.put("sourceText", sourceText);
            lineage.put("sourceLocation", clause.getSourceLocation());
            lineage.put("cmAuthored", true);
            rule = CiPolicyRuleCandidate.builder()
                    .id(UUID.randomUUID())
                    .clauseId(clause.getId())
                    .systemRuleId(systemId)
                    .ruleVersion("DRAFT")
                    .ruleType("HARD")
                    .scope(Map.of(
                            DecisionPolicyRuleMetadata.KEY_DOMAIN, DecisionPolicyDomain.CREDIT.name(),
                            "products", List.of("ALL")))
                    .expression(expression)
                    .onTrue(tp.onTrue)
                    .onFalse(tp.onFalse)
                    .onMissing("DATA_INSUFFICIENT")
                    .confidence(new BigDecimal("0.9500"))
                    .reviewStatus(ReviewState.CREDIT_MANAGER_APPROVED.name())
                    .lineage(lineage)
                    .metadata(meta)
                    .build();
            session.getClauses().add(clause);
            session.getRuleCandidates().add(rule);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("confirmed", true);
        out.put("ruleId", rule.getId().toString());
        out.put("systemRuleId", rule.getSystemRuleId());
        out.put("replaced", replaceId != null);
        out.put("message", replaceId != null
                ? "Rule updated from Credit Manager authoring."
                : "Underwriting rule added.");
        out.put("preview", toPreview(draft));
        return out;
    }

    // ─── parse ─────────────────────────────────────────────────────────────

    private DraftDraft fromStructured(Map<String, Object> body) {
        DraftDraft d = new DraftDraft();
        d.fromPlainEnglish = false;
        d.parameterId = str(body, "parameterId", null);
        d.operator = normalizeOp(str(body, "operator", ">="));
        d.value = coerceNumber(body.get("value"));
        d.period = emptyToNull(str(body, "period", null));
        d.treatment = str(body, "treatment", "Reject");
        d.sourceText = str(body, "text", null);
        if (d.parameterId == null || d.parameterId.isBlank()) {
            d.complete = false;
            d.message = "Parameter is required";
            d.missing.add("parameter");
            return d;
        }
        var def = REGISTRY.findById(d.parameterId);
        if (def.isPresent()) {
            d.businessName = def.get().businessName();
            d.source = def.get().evaluatedFrom();
            d.availability = def.get().availability();
            d.unit = def.get().unit();
            if (d.period == null) d.period = def.get().period();
        } else {
            d.businessName = friendly(d.parameterId);
            d.source = str(body, "source", "Application");
            d.availability = "AVAILABLE_AUTOMATICALLY";
        }
        if (d.value == null) {
            d.complete = false;
            d.message = "Value is required";
            d.missing.add("value");
            return d;
        }
        if (d.operator == null || d.operator.isBlank()) {
            d.complete = false;
            d.message = "Operator is required";
            d.missing.add("operator");
            return d;
        }
        d.complete = true;
        d.message = "Ready to confirm";
        return d;
    }

    private DraftDraft fromPlainEnglish(String text) {
        DraftDraft d = new DraftDraft();
        d.fromPlainEnglish = true;
        d.sourceText = text == null ? "" : text.trim();
        if (d.sourceText.isBlank()) {
            d.complete = false;
            d.message = "Rule text is required";
            d.missing.add("text");
            return d;
        }
        String lower = d.sourceText.toLowerCase(Locale.ROOT);
        d.treatment = inferTreatment(lower);

        // Multi-parameter phrases first
        if ((lower.contains("bank") || lower.contains("banking") || lower.contains("turnover"))
                && lower.contains("gst")) {
            d.parameterId = "banking.monthly_credits_3m";
            d.businessName = "Bank turnover vs GST";
            d.source = "Bank Statement";
            d.operator = ">=";
            d.value = extractNumber(lower, 75);
            d.period = "TRAILING_3M";
            d.unit = "PERCENT";
            d.availability = "DERIVABLE_FROM_AVAILABLE_DATA";
            d.rightMetricId = "gst.turnover.trailing_12m";
            d.ratioPercent = true;
            d.complete = true;
            d.message = "Interpreted as bank credits ≥ " + d.value + "% of GST turnover";
            return d;
        }

        List<CanonicalParameterDefinition> hits = matchParameters(lower);
        if (hits.size() > 1) {
            d.complete = false;
            d.message = "We found more than one possible parameter.";
            d.candidates = hits.stream().map(this::paramRow).toList();
            d.missing.add("parameter");
            // still try to fill operator/value
            fillOpValue(d, lower);
            return d;
        }
        if (hits.isEmpty()) {
            // Known aliases not fully in registry
            if (lower.contains("vintage") || lower.contains("years in business")) {
                d.parameterId = "application.business_vintage_months";
                d.businessName = "Business vintage";
                d.source = "Application";
                d.availability = "AVAILABLE_AUTOMATICALLY";
                d.unit = "MONTHS";
            } else {
                d.complete = false;
                d.message = "Parameter not yet mapped";
                d.needsResolver = true;
                d.missing.add("parameter");
                fillOpValue(d, lower);
                return d;
            }
        } else {
            CanonicalParameterDefinition def = hits.get(0);
            d.parameterId = def.id();
            d.businessName = def.businessName();
            d.source = def.evaluatedFrom();
            d.availability = def.availability();
            d.unit = def.unit();
            d.period = def.period();
        }

        fillOpValue(d, lower);
        if (d.operator == null) {
            d.missing.add("operator");
        }
        if (d.value == null) {
            d.missing.add("value");
        }
        d.complete = d.missing.isEmpty();
        d.message = d.complete
                ? "Ready to confirm"
                : "I couldn't turn this into a complete rule. Complete the missing fields.";
        return d;
    }

    private void fillOpValue(DraftDraft d, String lower) {
        if (lower.contains("not exceed") || lower.contains("no more than") || lower.contains("at most")) {
            d.operator = "<=";
        } else if (lower.contains("at least") || lower.contains("minimum") || lower.contains("no less")) {
            d.operator = ">=";
        } else if (lower.contains(">=") || lower.contains("greater than or equal")) {
            d.operator = ">=";
        } else if (lower.contains("<=") || lower.contains("less than or equal")) {
            d.operator = "<=";
        } else if (lower.contains(">") || lower.contains("greater than") || lower.contains("more than")
                || lower.contains("above")) {
            d.operator = ">";
        } else if (lower.contains("<") || lower.contains("less than") || lower.contains("below")) {
            d.operator = "<";
        } else if (lower.contains("must be 0") || lower.contains("= 0") || lower.contains("zero")
                || lower.matches(".*\\b0\\b.*") && (lower.contains("bounce") || lower.contains("return"))) {
            d.operator = "=";
            d.value = 0;
        } else if (lower.contains("=") || lower.contains(" equal ")) {
            d.operator = "=";
        }
        if (d.value == null) {
            d.value = extractNumber(lower, null);
        }
        // Bounce default operator =
        if (d.parameterId != null && d.parameterId.contains("cheque_return") && d.operator == null) {
            d.operator = "=";
        }
        if (d.parameterId != null && d.parameterId.contains("obligation") && d.operator == null) {
            d.operator = "<=";
        }
    }

    private List<CanonicalParameterDefinition> matchParameters(String lower) {
        List<CanonicalParameterDefinition> hits = new ArrayList<>();
        // Priority phrase map
        if (lower.contains("bureau score") || lower.contains("cibil") || (lower.contains("score")
                && (lower.contains("bureau") || lower.contains("credit score")))) {
            REGISTRY.findById("bureau.score").ifPresent(hits::add);
            return hits;
        }
        if (lower.contains("foir") || lower.contains("obligation ratio") || lower.contains("dti")) {
            REGISTRY.findById("obligation.ratio").ifPresent(hits::add);
            return hits;
        }
        if (lower.contains("bounce") || lower.contains("cheque return") || lower.contains("ecs return")) {
            REGISTRY.findById("banking.cheque_return_count_3m").ifPresent(hits::add);
            return hits;
        }
        if (lower.contains("average daily balance") || lower.contains("adb")) {
            REGISTRY.findById("banking.avg_daily_balance_3m").ifPresent(hits::add);
            return hits;
        }
        if (lower.contains("proposed edi") || (lower.contains("edi") && !lower.contains("credit"))) {
            REGISTRY.findById("application.proposed_edi").ifPresent(hits::add);
            return hits;
        }
        // Alias search across registry
        for (CanonicalParameterDefinition def : REGISTRY.all()) {
            String name = def.businessName() == null ? "" : def.businessName().toLowerCase(Locale.ROOT);
            if (!name.isBlank() && lower.contains(name)) {
                hits.add(def);
                continue;
            }
            if (def.aliases() != null) {
                for (String a : def.aliases()) {
                    if (a != null && lower.contains(a.toLowerCase(Locale.ROOT))) {
                        hits.add(def);
                        break;
                    }
                }
            }
        }
        return hits;
    }

    private Map<String, Object> buildExpression(DraftDraft d) {
        Object left = PolicyDsl.metric(d.parameterId);
        Object right;
        if (d.ratioPercent && d.rightMetricId != null) {
            // left >= (right * value / 100)
            right = PolicyDsl.op("MULTIPLY",
                    PolicyDsl.metric(d.rightMetricId),
                    Map.of("const", d.value == null ? 0 : ((Number) d.value).doubleValue() / 100.0));
            return PolicyDsl.gte(left, right);
        }
        right = Map.of("const", d.value == null ? 0 : d.value);
        return switch (d.operator == null ? ">=" : d.operator) {
            case ">" -> PolicyDsl.gt(left, right);
            case "<" -> PolicyDsl.lt(left, right);
            case "<=" -> PolicyDsl.lte(left, right);
            case "=" -> PolicyDsl.eq(left, right);
            default -> PolicyDsl.gte(left, right);
        };
    }

    private Map<String, Object> toPreview(DraftDraft d) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("complete", d.complete);
        out.put("message", d.message);
        out.put("needsResolver", d.needsResolver);
        out.put("missing", d.missing);
        if (d.candidates != null) out.put("candidates", d.candidates);
        out.put("evaluatedFrom", d.source);
        out.put("source", d.source);
        out.put("parameter", d.businessName);
        out.put("parameterId", d.parameterId);
        out.put("operator", d.operator);
        out.put("value", d.value);
        out.put("period", d.period == null || d.period.isBlank() ? "Not applicable" : d.period);
        out.put("treatment", d.treatment == null ? "Reject" : d.treatment);
        out.put("availability", availabilityLabel(d.availability));
        out.put("ruleDisplay", d.businessName == null ? null
                : d.businessName + " " + nullTo(d.operator, "?") + " " + formatValue(d));
        out.put("fromPlainEnglish", d.fromPlainEnglish);
        return out;
    }

    private Map<String, Object> paramRow(CanonicalParameterDefinition d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("parameterId", d.id());
        m.put("businessName", d.businessName());
        m.put("source", d.evaluatedFrom());
        m.put("type", d.type());
        m.put("unit", d.unit());
        m.put("period", d.period());
        m.put("availability", availabilityLabel(d.availability()));
        m.put("kind", CanonicalParameterDefinition.RAW.equals(d.type()) ? "RAW" : "DERIVED");
        return m;
    }

    private static String availabilityLabel(String a) {
        if (a == null) return "Available";
        if (a.contains("MANUAL")) return "Manual input";
        if (a.contains("DERIVABLE") || a.contains("AUTOMATIC")) return "Automatically available";
        if (a.contains("NEEDS")) return "Needs configuration";
        return a;
    }

    private static String inferTreatment(String lower) {
        if (lower.contains("refer") || lower.contains("manual review") || lower.contains("manual")) {
            return "Manual Review";
        }
        if (lower.contains("approve") || lower.contains("pass")) return "Approve";
        if (lower.contains("info") || lower.contains("warning")) return "Info";
        return "Reject";
    }

    private static String treatmentCode(String t) {
        if (t == null) return "REJECT";
        String u = t.toUpperCase(Locale.ROOT);
        if (u.contains("MANUAL") || u.contains("REFER")) return "REFER";
        if (u.contains("APPROVE") || u.contains("PASS")) return "PASS";
        if (u.contains("INFO")) return "INFO";
        return "REJECT";
    }

    private static TreatmentPair treatmentPair(String t) {
        String code = treatmentCode(t);
        // Failure-oriented DSL: condition true for "goodness" checks uses onTrue=PASS for eligibility;
        // For reject-on-fail thresholds we use capacity-style: condition satisfied → PASS, else treatment.
        // Standard CM thresholds: expression true means condition met → PASS; false → treatment.
        return switch (code) {
            case "REFER" -> new TreatmentPair("PASS", "REFER");
            case "PASS" -> new TreatmentPair("PASS", "FAIL");
            case "INFO" -> new TreatmentPair("PASS", "INFO");
            default -> new TreatmentPair("PASS", "FAIL"); // Reject on failure
        };
    }

    private static Object extractNumber(String lower, Object defaultVal) {
        Matcher m = NUM.matcher(lower);
        Double last = null;
        while (m.find()) {
            try {
                last = Double.parseDouble(m.group(2));
            } catch (Exception ignored) {
                // continue
            }
        }
        if (last == null) return defaultVal;
        if (last == Math.rint(last)) return last.longValue();
        return last;
    }

    private static Object coerceNumber(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n;
        String s = String.valueOf(v).trim().replace("%", "").replace(",", "");
        try {
            if (s.contains(".")) return Double.parseDouble(s);
            return Long.parseLong(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizeOp(String op) {
        if (op == null) return ">=";
        String o = op.trim().toLowerCase(Locale.ROOT);
        return switch (o) {
            case "gt", "greater than", "more than", "above" -> ">";
            case "gte", "ge", "at least", "minimum" -> ">=";
            case "lt", "less than", "below" -> "<";
            case "lte", "le", "at most", "not exceed", "no more than" -> "<=";
            case "eq", "equals", "equal", "is" -> "=";
            case ">", ">=", "<", "<=", "=" -> o;
            default -> op.trim();
        };
    }

    private static String opCode(String op) {
        return switch (op == null ? "GTE" : op) {
            case ">" -> "GT";
            case "<" -> "LT";
            case "<=" -> "LTE";
            case "=" -> "EQ";
            default -> "GTE";
        };
    }

    private static String formatValue(DraftDraft d) {
        if (d.value == null) return "?";
        if (d.ratioPercent || "PERCENT".equalsIgnoreCase(d.unit)) return d.value + "%";
        if ("MONTHS".equalsIgnoreCase(d.unit) || (d.period != null && d.period.contains("MONTH"))) {
            return d.value + " months";
        }
        return String.valueOf(d.value);
    }

    private static String friendly(String id) {
        int i = id.lastIndexOf('.');
        return (i >= 0 ? id.substring(i + 1) : id).replace('_', ' ');
    }

    private static String str(Map<String, Object> body, String key, String def) {
        if (body == null || body.get(key) == null) return def;
        String v = String.valueOf(body.get(key));
        return v.isBlank() ? def : v.trim();
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() || "Not applicable".equalsIgnoreCase(s) ? null : s;
    }

    private static String nullTo(String v, String d) {
        return v == null || v.isBlank() ? d : v;
    }

    private static Map<String, Object> mergePreviewDefaults(Map<String, Object> body, Map<String, Object> preview) {
        Map<String, Object> m = new LinkedHashMap<>(body == null ? Map.of() : body);
        m.putIfAbsent("parameterId", preview.get("parameterId"));
        m.putIfAbsent("operator", preview.get("operator"));
        m.putIfAbsent("value", preview.get("value"));
        m.putIfAbsent("period", preview.get("period"));
        m.putIfAbsent("treatment", preview.get("treatment"));
        m.putIfAbsent("source", preview.get("source"));
        return m;
    }

    private static final class DraftDraft {
        boolean fromPlainEnglish;
        boolean complete;
        boolean needsResolver;
        String message;
        String parameterId;
        String businessName;
        String source;
        String operator;
        Object value;
        String period;
        String treatment = "Reject";
        String availability;
        String unit;
        String sourceText;
        String rightMetricId;
        boolean ratioPercent;
        List<String> missing = new ArrayList<>();
        List<Map<String, Object>> candidates;
    }

    private record TreatmentPair(String onTrue, String onFalse) {}
}
