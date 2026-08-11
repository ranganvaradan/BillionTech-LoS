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

    private static final Pattern NUM = Pattern.compile(
            "(>=|<=|>|<|=|at least|at most|not exceed|no more than|less than|greater than|more than)?\\s*"
                    + "(\\d+(?:\\.\\d+)?)\\s*(%|percent|months?|m)?",
            Pattern.CASE_INSENSITIVE);
    /** Period window phrases — numbers here must never become condition thresholds. */
    private static final Pattern PERIOD_PHRASE = Pattern.compile(
            "\\b(?:in\\s+)?(?:the\\s+)?last\\s+\\d+(?:\\.\\d+)?\\s*(?:months?|years?|days?|m)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_PERIOD = Pattern.compile(
            "\\b\\d+(?:\\.\\d+)?\\s*(?:months?|years?|days?)\\b",
            Pattern.CASE_INSENSITIVE);

    public Map<String, Object> sources() {
        Map<String, List<Map<String, Object>>> bySource = new LinkedHashMap<>();
        for (CanonicalParameterDefinition d : CanonicalParameterRegistry.shared().all()) {
            String src = d.evaluatedFrom() == null ? "Other" : d.evaluatedFrom();
            bySource.computeIfAbsent(src, k -> new ArrayList<>()).add(paramRow(d));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sources", bySource.keySet().stream().sorted().toList());
        out.put("bySource", bySource);
        out.put("operatorsByType", Map.of(
                "NUMBER", AuthoringValueTypes.operatorsFor(AuthoringValueTypes.CONTROL_NUMBER),
                "PERCENT", AuthoringValueTypes.operatorsFor(AuthoringValueTypes.CONTROL_PERCENTAGE),
                "PERCENTAGE", AuthoringValueTypes.operatorsFor(AuthoringValueTypes.CONTROL_PERCENTAGE),
                "COUNT", AuthoringValueTypes.operatorsFor(AuthoringValueTypes.CONTROL_INTEGER),
                "INTEGER", AuthoringValueTypes.operatorsFor(AuthoringValueTypes.CONTROL_INTEGER),
                "MONEY", AuthoringValueTypes.operatorsFor(AuthoringValueTypes.CONTROL_MONEY),
                "DURATION", AuthoringValueTypes.operatorsFor(AuthoringValueTypes.CONTROL_DURATION),
                "ENUM", AuthoringValueTypes.operatorsFor(AuthoringValueTypes.CONTROL_ENUM),
                "FLAG", AuthoringValueTypes.operatorsFor(AuthoringValueTypes.CONTROL_BOOLEAN),
                "BOOLEAN", AuthoringValueTypes.operatorsFor(AuthoringValueTypes.CONTROL_BOOLEAN)));
        // Failure-oriented treatments only — Approve is not a failure treatment
        out.put("treatments", List.of("Reject", "Manual Review", "Refer", "Info"));
        out.put("treatmentLabel", "If rule fails");
        out.put("allowCanonicalAuthority", false);
        return out;
    }

    /** Preview structured or plain-English rule — does not persist. */
    public Map<String, Object> preview(Map<String, Object> body) {
        String mode = str(body, "mode", "DESCRIBE");
        if ("COMPOUND".equalsIgnoreCase(mode) || body.get("branches") instanceof List<?>) {
            return previewCompound(body);
        }
        if ("PRESERVE".equalsIgnoreCase(mode) && body.get("existingExpression") instanceof Map<?, ?>) {
            return previewPreserve(body);
        }
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
     * Compound IF rules use mode=COMPOUND / PRESERVE — never collapse via flat PE.
     */
    public Map<String, Object> confirm(
            PolicyStudioSession session, Map<String, Object> body) {
        String mode = str(body, "mode", "DESCRIBE");
        if ("COMPOUND".equalsIgnoreCase(mode) || "PRESERVE".equalsIgnoreCase(mode)
                || body.get("branches") instanceof List<?>) {
            return confirmCompound(session, body);
        }

        Map<String, Object> preview = preview(body);
        if (!Boolean.TRUE.equals(preview.get("complete"))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    String.valueOf(preview.getOrDefault("message",
                            "I couldn't turn this into a complete rule. Complete the missing fields.")));
        }
        DraftDraft draft = "BUILD".equalsIgnoreCase(mode)
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
        if (body.get("operator") != null) {
            draft.operator = AuthoringValueTypes.normalizeOperator(
                    String.valueOf(body.get("operator")), draft.valueControl);
        }
        if (body.get("rightParameterId") != null) {
            draft.rightParameterId = String.valueOf(body.get("rightParameterId"));
            draft.valueMode = "PARAMETER";
        }
        if (body.get("value") != null && !"PARAMETER".equalsIgnoreCase(draft.valueMode)) {
            var defOpt = draft.parameterId == null ? java.util.Optional.<CanonicalParameterDefinition>empty()
                    : CanonicalParameterRegistry.shared().findById(draft.parameterId);
            draft.value = AuthoringValueTypes.coerce(body.get("value"), defOpt.orElse(null), draft.durationUnit);
        }
        if (body.get("period") != null) draft.period = String.valueOf(body.get("period"));
        if (body.get("durationUnit") != null) draft.durationUnit = String.valueOf(body.get("durationUnit"));

        // Guard: never let flat authoring destroy an existing compound IF rule
        UUID replaceProbe = parseReplaceId(body);
        if (replaceProbe != null) {
            CiPolicyRuleCandidate existing = session.getRuleCandidates().stream()
                    .filter(r -> replaceProbe.equals(r.getId()))
                    .findFirst().orElse(null);
            if (existing != null && InwardReturnCompoundSupport.isIfExpression(existing.getExpression())) {
                try {
                    InwardReturnCompoundSupport.assertNonDestructiveReplace(
                            existing.getExpression(), buildExpression(draft));
                } catch (IllegalArgumentException ex) {
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
                }
            }
        }

        return persistFlatDraft(session, body, draft);
    }

    private UUID parseReplaceId(Map<String, Object> body) {
        if (body.get("replaceRuleId") == null || String.valueOf(body.get("replaceRuleId")).isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(body.get("replaceRuleId")));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid replaceRuleId");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> confirmCompound(PolicyStudioSession session, Map<String, Object> body) {
        CiPolicyDocument doc = session.getDocument();
        if (doc == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy document not found");
        }
        UUID replaceId = parseReplaceId(body);
        if (replaceId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Compound rule confirm requires replaceRuleId (edit existing rule)");
        }
        CiPolicyRuleCandidate existing = session.getRuleCandidates().stream()
                .filter(r -> replaceId.equals(r.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule to edit not found"));

        Map<String, Object> priorExpr = existing.getExpression() == null
                ? Map.of() : new LinkedHashMap<>(existing.getExpression());
        Map<String, Object> priorMeta = existing.getMetadata() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(existing.getMetadata());
        Map<String, Object> priorLineage = existing.getLineage() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(existing.getLineage());

        Map<String, Object> nextExpr;
        String summary;
        if ("PRESERVE".equalsIgnoreCase(str(body, "mode", ""))
                || Boolean.TRUE.equals(body.get("noChange"))) {
            nextExpr = priorExpr;
            summary = String.valueOf(priorMeta.getOrDefault("businessSummary",
                    InwardReturnCompoundSupport.toEditableModel(priorExpr, priorMeta).get("plainEnglish")));
        } else if (body.get("branches") instanceof List<?>) {
            List<Map<String, Object>> branches = new ArrayList<>();
            for (Object o : (List<?>) body.get("branches")) {
                if (o instanceof Map<?, ?> m) {
                    branches.add(new LinkedHashMap<>((Map<String, Object>) m));
                }
            }
            try {
                nextExpr = InwardReturnCompoundSupport.buildExpressionFromBranches(branches);
                summary = InwardReturnCompoundSupport.businessSummaryFromBranches(branches);
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
            }
        } else if (body.get("boundaryOption") != null) {
            try {
                String opt = String.valueOf(body.get("boundaryOption"));
                nextExpr = InwardReturnCompoundSupport.patchBoundary(priorExpr, opt);
                summary = InwardReturnCompoundSupport.businessSummaryAfterBoundary(opt);
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
            }
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Compound confirm requires branches, boundaryOption, or mode=PRESERVE");
        }

        try {
            InwardReturnCompoundSupport.assertNonDestructiveReplace(priorExpr, nextExpr);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        }

        Map<String, Object> lineage = new LinkedHashMap<>(priorLineage);
        lineage.put("priorExpression", priorExpr);
        lineage.put("priorSystemRuleId", existing.getSystemRuleId());
        lineage.put("editedByCm", true);
        lineage.put("compoundRoundtrip", true);
        if (lineage.get("sourceText") == null && priorLineage.get("sourceText") != null) {
            lineage.put("sourceText", priorLineage.get("sourceText"));
        }
        lineage.put("historicalSourceText", priorLineage.getOrDefault("sourceText",
                priorLineage.get("historicalSourceText")));

        Map<String, Object> meta = new LinkedHashMap<>(priorMeta);
        meta.put("disposition", "EDITED");
        meta.put("businessSummary", summary);
        meta.put("businessTitle", priorMeta.getOrDefault("businessTitle",
                "Inward cheque / ECS / ENACH returns"));
        meta.put("compoundRule", true);
        meta.put("cmAuthored", true);
        meta.put("plainEnglishAdded", false);
        meta.put("replacedNarrative", false);
        meta.put("period", "TRAILING_3M");
        if (body.get("treatment") != null) {
            meta.put("failureTreatment", treatmentCode(String.valueOf(body.get("treatment"))));
        }
        // Keep canonical mapping provenance
        meta.put("parameterId", InwardReturnCompoundSupport.TXN_METRIC);
        meta.put("mappedParameters", List.of(
                InwardReturnCompoundSupport.TXN_METRIC,
                InwardReturnCompoundSupport.RATIO_METRIC,
                InwardReturnCompoundSupport.COUNT_METRIC));

        existing.setExpression(nextExpr);
        existing.setMetadata(meta);
        existing.setLineage(lineage);
        // Do not change systemRuleId — preserve BANK_INWARD_RETURN_BRANCHED_100 binding
        if (existing.getSystemRuleId() == null || existing.getSystemRuleId().startsWith("CM_")) {
            existing.setSystemRuleId("BANK_INWARD_RETURN_BRANCHED_100");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("confirmed", true);
        out.put("ruleId", existing.getId().toString());
        out.put("systemRuleId", existing.getSystemRuleId());
        out.put("replaced", true);
        out.put("compound", true);
        out.put("message", "Compound rule updated without losing branches or mappings.");
        out.put("preview", previewCompound(Map.of(
                "branches", InwardReturnCompoundSupport.toEditableModel(nextExpr, meta).get("branches"),
                "mode", "COMPOUND")));
        out.put("editableModel", InwardReturnCompoundSupport.toEditableModel(nextExpr, meta));
        out.put("gacatMutated", false);
        out.put("allowCanonicalAuthority", false);
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> previewCompound(Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", "COMPOUND");
        List<Map<String, Object>> branches = new ArrayList<>();
        if (body.get("branches") instanceof List<?> raw) {
            for (Object o : raw) {
                if (o instanceof Map<?, ?> m) branches.add(new LinkedHashMap<>((Map<String, Object>) m));
            }
        } else if (body.get("existingExpression") instanceof Map<?, ?> expr) {
            Map<String, Object> model = InwardReturnCompoundSupport.toEditableModel(
                    new LinkedHashMap<>((Map<String, Object>) expr), Map.of());
            Object b = model.get("branches");
            if (b instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) branches.add(new LinkedHashMap<>((Map<String, Object>) m));
                }
            }
        }
        if (branches.size() < 2) {
            out.put("complete", false);
            out.put("message", "Could not safely interpret this rule — compound IF needs two branches.");
            return out;
        }
        Map<String, Object> expr;
        try {
            expr = InwardReturnCompoundSupport.buildExpressionFromBranches(branches);
        } catch (IllegalArgumentException ex) {
            out.put("complete", false);
            out.put("message", ex.getMessage());
            return out;
        }
        out.put("complete", true);
        out.put("expression", expr);
        out.put("branches", branches);
        out.put("plainEnglish", InwardReturnCompoundSupport.businessSummaryFromBranches(branches));
        out.put("period", "Last 3 months");
        out.put("parameterId", InwardReturnCompoundSupport.TXN_METRIC);
        out.put("parameterName", "Transaction count");
        out.put("mappedParameters", List.of(
                InwardReturnCompoundSupport.TXN_METRIC,
                InwardReturnCompoundSupport.RATIO_METRIC,
                InwardReturnCompoundSupport.COUNT_METRIC));
        out.put("treatment", str(body, "treatment", "Reject"));
        out.put("message", "Compound rule preview — both branches preserved");
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> previewPreserve(Map<String, Object> body) {
        Map<String, Object> expr = new LinkedHashMap<>((Map<String, Object>) body.get("existingExpression"));
        Map<String, Object> model = InwardReturnCompoundSupport.toEditableModel(expr, Map.of());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", "PRESERVE");
        out.put("complete", Boolean.TRUE.equals(model.get("complete")));
        out.put("expression", expr);
        out.put("editableModel", model);
        out.put("plainEnglish", model.get("plainEnglish"));
        out.put("parameterId", InwardReturnCompoundSupport.TXN_METRIC);
        out.put("mappedParameters", List.of(
                InwardReturnCompoundSupport.TXN_METRIC,
                InwardReturnCompoundSupport.RATIO_METRIC,
                InwardReturnCompoundSupport.COUNT_METRIC));
        out.put("message", "Existing compound rule preserved — semantically identical");
        return out;
    }

    private Map<String, Object> persistFlatDraft(
            PolicyStudioSession session, Map<String, Object> body, DraftDraft draft) {
        CiPolicyDocument doc = session.getDocument();
        if (doc == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy document not found");
        }

        final UUID replaceId = parseReplaceId(body);

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
        meta.put("businessSummary", ruleDisplay(draft));
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
        meta.put("valueControl", draft.valueControl);
        meta.put("valueMode", draft.valueMode == null ? "FIXED" : draft.valueMode);
        meta.put("durationUnit", draft.durationUnit);
        if (draft.rightParameterId != null) {
            meta.put("rightParameterId", draft.rightParameterId);
            // Parameter-reference rules need runtime RHS resolution — readiness convergence handles this
            if ("application.proposed_edi".equals(draft.rightParameterId)) {
                meta.put("NEEDS_INPUT", false); // operand presenter still marks EDI unresolved
            }
        }
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
        d.period = emptyToNull(str(body, "period", null));
        d.durationUnit = emptyToNull(str(body, "durationUnit", null));
        d.treatment = str(body, "treatment", "Reject");
        d.sourceText = str(body, "text", null);
        d.valueMode = str(body, "valueMode", "FIXED");
        d.rightParameterId = emptyToNull(str(body, "rightParameterId", null));
        if (d.parameterId == null || d.parameterId.isBlank()) {
            d.complete = false;
            d.message = "Parameter is required";
            d.missing.add("parameter");
            return d;
        }
        var def = CanonicalParameterRegistry.shared().findById(d.parameterId);
        if (def.isPresent()) {
            d.businessName = def.get().businessName();
            d.source = def.get().evaluatedFrom();
            d.availability = def.get().availability();
            d.unit = def.get().unit();
            d.valueControl = AuthoringValueTypes.valueControl(def.get());
            if (!AuthoringValueTypes.allowedValues(def.get().id()).isEmpty()) {
                d.valueControl = AuthoringValueTypes.CONTROL_ENUM;
            }
            if (d.period == null) d.period = def.get().period();
        } else {
            d.businessName = friendly(d.parameterId);
            d.source = str(body, "source", "Application");
            d.availability = "AVAILABLE_AUTOMATICALLY";
            d.valueControl = AuthoringValueTypes.CONTROL_NUMBER;
        }
        d.operator = AuthoringValueTypes.normalizeOperator(
                str(body, "operator", AuthoringValueTypes.CONTROL_BOOLEAN.equals(d.valueControl)
                        || AuthoringValueTypes.CONTROL_ENUM.equals(d.valueControl) ? "is" : ">="),
                d.valueControl);

        if ("PARAMETER".equalsIgnoreCase(d.valueMode) || d.rightParameterId != null) {
            if (d.rightParameterId == null || d.rightParameterId.isBlank()) {
                d.complete = false;
                d.message = "Right-hand parameter is required";
                d.missing.add("rightParameterId");
                return d;
            }
            var right = CanonicalParameterRegistry.shared().findById(d.rightParameterId);
            if (right.isEmpty()) {
                d.complete = false;
                d.message = "Right-hand parameter not found in registry";
                d.missing.add("rightParameterId");
                return d;
            }
            d.valueMode = "PARAMETER";
            d.rightBusinessName = right.get().businessName();
            d.value = null; // RHS is parameter reference — not a fixed const
        } else {
            d.value = AuthoringValueTypes.coerce(body.get("value"), def.orElse(null), d.durationUnit);
            if (d.value == null) {
                d.complete = false;
                d.message = AuthoringValueTypes.validationMessage(d.valueControl, body.get("value"));
                d.missing.add("value");
                return d;
            }
        }
        if (AuthoringValueTypes.CONTROL_DURATION.equals(d.valueControl)
                && (d.durationUnit == null || d.durationUnit.isBlank())
                && body.get("value") != null) {
            d.durationUnit = "Months";
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

        // POLICY-RULE-EDITOR-ROUNDTRIP-P0 — compound IF wording is not safely flat-parseable
        if (looksLikeCompoundPlainEnglish(lower)) {
            d.complete = false;
            d.message = "Could not safely interpret this rule — it has multiple branches. "
                    + "Use Build (compound) or Define boundary; do not rewrite from free text.";
            d.missing.add("compound");
            return d;
        }

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
                CanonicalParameterRegistry.shared().findById("application.business_vintage_months").ifPresentOrElse(def -> {
                    d.parameterId = def.id();
                    d.businessName = def.businessName();
                    d.source = def.evaluatedFrom();
                    d.availability = def.availability();
                    d.unit = def.unit();
                    d.valueControl = AuthoringValueTypes.CONTROL_DURATION;
                }, () -> {
                    d.parameterId = "application.business_vintage_months";
                    d.businessName = "Business vintage";
                    d.source = "Application";
                    d.availability = "AVAILABLE_AUTOMATICALLY";
                    d.unit = "MONTHS";
                    d.valueControl = AuthoringValueTypes.CONTROL_DURATION;
                });
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

        if (d.parameterId != null) {
            CanonicalParameterRegistry.shared().findById(d.parameterId).ifPresent(def -> {
                d.valueControl = AuthoringValueTypes.valueControl(def);
                if (!AuthoringValueTypes.allowedValues(def.id()).isEmpty()) {
                    d.valueControl = AuthoringValueTypes.CONTROL_ENUM;
                }
            });
        }
        fillOpValue(d, lower);
        if (d.operator == null) {
            d.missing.add("operator");
        }
        if (d.value == null && !"PARAMETER".equalsIgnoreCase(d.valueMode)) {
            d.missing.add("value");
        }
        d.complete = d.missing.isEmpty();
        d.message = d.complete
                ? "Ready to confirm"
                : (d.missing.contains("value")
                ? AuthoringValueTypes.validationMessage(d.valueControl, null)
                : "I couldn't turn this into a complete rule. Complete the missing fields.");
        return d;
    }

    private void fillOpValue(DraftDraft d, String lower) {
        boolean boolCtrl = AuthoringValueTypes.CONTROL_BOOLEAN.equals(d.valueControl);
        // Boolean plain-English: "PAN must be verified" / "PAN verified is Yes"
        if (boolCtrl || (d.parameterId != null && (d.parameterId.contains("verified")
                || d.parameterId.contains(".present")))) {
            d.valueControl = AuthoringValueTypes.CONTROL_BOOLEAN;
            if (lower.contains("is not") || lower.contains("not verified") || lower.contains("unverified")
                    || lower.contains("must not")) {
                d.operator = "is not";
            } else {
                d.operator = "is";
            }
            if (lower.contains(" no") || lower.contains("= no") || lower.contains("is no")
                    || lower.contains("false") || lower.contains("unverified") || lower.contains("not verified")) {
                d.value = false;
            } else if (lower.contains("verified") || lower.contains(" yes") || lower.contains("= yes")
                    || lower.contains("is yes") || lower.contains("true") || lower.contains("must be")
                    || lower.contains("should be")) {
                d.value = true;
            } else {
                Object b = AuthoringValueTypes.coerceBoolean(
                        lower.contains("no") ? "no" : (lower.contains("yes") ? "yes" : null));
                if (b != null) d.value = b;
                else if (lower.contains("verified") || lower.contains("pass")) d.value = true;
            }
            return;
        }
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
        } else if (lower.contains("=") || lower.contains(" equal ") || lower.contains(" is ")) {
            d.operator = AuthoringValueTypes.CONTROL_ENUM.equals(d.valueControl) ? "is" : "=";
        }
        if (d.value == null) {
            CanonicalParameterDefinition def = d.parameterId == null ? null
                    : CanonicalParameterRegistry.shared().findById(d.parameterId).orElse(null);
            Object num = extractNumber(lower, null);
            if (num != null) {
                String dur = null;
                if (AuthoringValueTypes.CONTROL_DURATION.equals(d.valueControl)
                        || lower.matches(".*\\b\\d+(?:\\.\\d+)?\\s*months?\\b.*")
                        && (lower.contains("at least") || lower.contains("vintage")
                        || lower.contains(">= ") || lower.contains("minimum"))) {
                    dur = lower.contains("year") ? "Years" : "Months";
                } else if (AuthoringValueTypes.CONTROL_DURATION.equals(d.valueControl)
                        && lower.contains("year")) {
                    dur = "Years";
                }
                d.durationUnit = dur;
                d.value = AuthoringValueTypes.coerce(num, def, dur);
            }
        }
        // Bounce default operator =
        if (d.parameterId != null && d.parameterId.contains("cheque_return") && d.operator == null) {
            d.operator = "=";
        }
        if (d.parameterId != null && d.parameterId.contains("obligation") && d.operator == null) {
            d.operator = "<=";
        }
        // Enum codes from plain English (borrower type)
        if (d.value == null && AuthoringValueTypes.CONTROL_ENUM.equals(d.valueControl)) {
            for (Map<String, String> opt : AuthoringValueTypes.allowedValues(d.parameterId)) {
                if (lower.contains(opt.get("label").toLowerCase(Locale.ROOT))
                        || lower.contains(opt.get("value").toLowerCase(Locale.ROOT).replace('_', ' '))) {
                    d.value = opt.get("value");
                    if (d.operator == null) d.operator = "is";
                    break;
                }
            }
        }
    }

    private List<CanonicalParameterDefinition> matchParameters(String lower) {
        List<CanonicalParameterDefinition> hits = new ArrayList<>();
        // Priority phrase map
        if (lower.contains("bureau score") || lower.contains("cibil") || (lower.contains("score")
                && (lower.contains("bureau") || lower.contains("credit score")))) {
            CanonicalParameterRegistry.shared().findById("bureau.score").ifPresent(hits::add);
            return hits;
        }
        if (lower.contains("foir") || lower.contains("obligation ratio") || lower.contains("dti")) {
            CanonicalParameterRegistry.shared().findById("obligation.ratio").ifPresent(hits::add);
            return hits;
        }
        if (lower.contains("bounce") || lower.contains("cheque return") || lower.contains("ecs return")) {
            CanonicalParameterRegistry.shared().findById("banking.cheque_return_count_3m").ifPresent(hits::add);
            return hits;
        }
        if (lower.contains("average daily balance") || lower.contains("adb")) {
            CanonicalParameterRegistry.shared().findById("banking.avg_daily_balance_3m").ifPresent(hits::add);
            return hits;
        }
        if (lower.contains("proposed edi") || (lower.contains("edi") && !lower.contains("credit"))) {
            CanonicalParameterRegistry.shared().findById("application.proposed_edi").ifPresent(hits::add);
            return hits;
        }
        if (lower.contains("pan") && (lower.contains("verif") || lower.contains("must be"))) {
            CanonicalParameterRegistry.shared().findById("kyc.pan.verified").ifPresent(hits::add);
            return hits;
        }
        if (lower.contains("borrower type") || lower.contains("entity type")) {
            CanonicalParameterRegistry.shared().findById("application.borrower_type").ifPresent(hits::add);
            return hits;
        }
        if (lower.contains("requested amount") || lower.contains("loan amount")) {
            CanonicalParameterRegistry.shared().findById("application.requested_amount").ifPresent(hits::add);
            return hits;
        }
        if (lower.contains("vintage") || lower.contains("years in business")) {
            CanonicalParameterRegistry.shared().findById("application.business_vintage_months").ifPresent(hits::add);
            return hits;
        }
        // Alias search across registry
        for (CanonicalParameterDefinition def : CanonicalParameterRegistry.shared().all()) {
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
        if ("PARAMETER".equalsIgnoreCase(d.valueMode) && d.rightParameterId != null) {
            right = PolicyDsl.metric(d.rightParameterId);
        } else {
            right = Map.of("const", d.value == null ? 0 : d.value);
        }
        String op = d.operator == null ? ">=" : d.operator;
        return switch (op) {
            case ">" -> PolicyDsl.gt(left, right);
            case "<" -> PolicyDsl.lt(left, right);
            case "<=" -> PolicyDsl.lte(left, right);
            case "=", "is" -> PolicyDsl.eq(left, right);
            case "!=", "is not" -> PolicyDsl.ne(left, right);
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
        out.put("valueDisplay", formatValue(d));
        out.put("valueControl", d.valueControl);
        out.put("valueMode", d.valueMode == null ? "FIXED" : d.valueMode);
        out.put("rightParameterId", d.rightParameterId);
        out.put("rightParameter", d.rightBusinessName);
        out.put("durationUnit", d.durationUnit);
        boolean showPeriod = d.period != null && !d.period.isBlank()
                && !"Not applicable".equalsIgnoreCase(d.period)
                && !AuthoringValueTypes.CONTROL_BOOLEAN.equals(d.valueControl)
                && !AuthoringValueTypes.CONTROL_ENUM.equals(d.valueControl);
        out.put("period", showPeriod ? d.period : null);
        out.put("showPeriod", showPeriod);
        out.put("treatment", d.treatment == null ? "Reject" : d.treatment);
        out.put("treatmentLabel", "If rule fails");
        out.put("availability", availabilityLabel(d.availability));
        out.put("ruleDisplay", ruleDisplay(d));
        out.put("failureDisplay", "If not → " + (d.treatment == null ? "Reject" : d.treatment));
        out.put("fromPlainEnglish", d.fromPlainEnglish);
        out.put("allowCanonicalAuthority", false);
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
        m.put("kind", CanonicalParameterDefinition.RAW.equals(d.type()) ? "RAW"
                : (CanonicalParameterDefinition.MANUAL.equals(d.type()) ? "MANUAL" : "DERIVED"));
        m.putAll(AuthoringValueTypes.controlMeta(d));
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
        if (u.contains("MANUAL")) return "REFER";
        if (u.equals("REFER") || u.contains("REFER")) return "REFER";
        if (u.contains("APPROVE") || u.contains("PASS")) return "PASS";
        if (u.contains("INFO") || u.contains("WARNING")) return "INFO";
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

    private static boolean looksLikeCompoundPlainEnglish(String lower) {
        if (lower == null) return false;
        boolean multiIf = lower.split("\\bif\\b").length > 2;
        boolean semiBranches = lower.contains(";") && (lower.contains("if <") || lower.contains("if >")
                || lower.contains("if ≤") || lower.contains("if ≥") || lower.contains("if <=")
                || lower.contains("if >="));
        boolean ratioAndCount = (lower.contains("ratio") || lower.contains("%"))
                && lower.contains("count")
                && (lower.contains("100") || lower.contains("transaction"));
        boolean colonBranches = lower.contains(":") && lower.contains(";")
                && (lower.contains("return") || lower.contains("ratio"));
        return multiIf || semiBranches || ratioAndCount || colonBranches;
    }

    private static Object extractNumber(String lower, Object defaultVal) {
        // Strip period windows so "last 3 months" never becomes the threshold
        String scrubbed = PERIOD_PHRASE.matcher(lower == null ? "" : lower).replaceAll(" ");
        Matcher m = NUM.matcher(scrubbed);
        Double last = null;
        Double lastWithOp = null;
        while (m.find()) {
            try {
                double v = Double.parseDouble(m.group(2));
                String unit = m.group(3);
                // Skip bare "3 months" only when it is a period residue after "last …" already stripped;
                // keep "at least 24 months" for duration thresholds (op group present or valueControl duration).
                if (unit != null) {
                    String u = unit.toLowerCase(Locale.ROOT);
                    String op = m.group(1);
                    boolean periodUnit = u.startsWith("month") || u.equals("m") || u.startsWith("year") || u.startsWith("day");
                    if (periodUnit && (op == null || op.isBlank())) {
                        // No comparison operator attached — likely leftover period text; skip
                        continue;
                    }
                }
                last = v;
                String op = m.group(1);
                if (op != null && !op.isBlank()) {
                    lastWithOp = v;
                }
            } catch (Exception ignored) {
                // continue
            }
        }
        Double chosen = lastWithOp != null ? lastWithOp : last;
        if (chosen == null) return defaultVal;
        if (chosen == Math.rint(chosen)) return chosen.longValue();
        return chosen;
    }

    private static String opCode(String op) {
        return switch (op == null ? "GTE" : op) {
            case ">" -> "GT";
            case "<" -> "LT";
            case "<=" -> "LTE";
            case "=", "is" -> "EQ";
            case "!=", "is not" -> "NE";
            default -> "GTE";
        };
    }

    private static String formatValue(DraftDraft d) {
        if ("PARAMETER".equalsIgnoreCase(d.valueMode) && d.rightBusinessName != null) {
            return d.rightBusinessName;
        }
        if (d.value == null) return "?";
        if (d.ratioPercent) return d.value + "%";
        return AuthoringValueTypes.formatDisplay(d.value, d.valueControl, d.unit, d.durationUnit);
    }

    private static String ruleDisplay(DraftDraft d) {
        if (d.businessName == null) return null;
        String op = nullTo(d.operator, "?");
        if ("is".equals(op) || "is not".equals(op)) {
            return d.businessName + " " + op + " " + formatValue(d);
        }
        return d.businessName + " " + op + " " + formatValue(d);
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
        String valueControl;
        String valueMode = "FIXED";
        String durationUnit;
        String sourceText;
        String rightMetricId;
        String rightParameterId;
        String rightBusinessName;
        boolean ratioPercent;
        List<String> missing = new ArrayList<>();
        List<Map<String, Object>> candidates;
    }

    private record TreatmentPair(String onTrue, String onFalse) {}
}
