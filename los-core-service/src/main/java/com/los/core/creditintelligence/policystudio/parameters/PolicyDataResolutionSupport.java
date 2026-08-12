package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * POLICY-DATA-RESOLUTION-UX-1 — typed policy-version-scoped Data & Calculations resolutions.
 * SESSION_DRAFT_ONLY on document metadata. Does not mutate GACAT. No new metric engine.
 */
public final class PolicyDataResolutionSupport {

    public static final String DOC_META_KEY = "policyDataResolutions";
    public static final String RULE_META_KEY = "dataCalcResolution";

    public static final String TYPE_PARAMETER_MAPPING = "PARAMETER_MAPPING";
    public static final String TYPE_POLICY_THRESHOLD = "POLICY_THRESHOLD_DEFINITION";
    public static final String TYPE_POLICY_CLASSIFICATION = "POLICY_CLASSIFICATION_DEFINITION";
    public static final String TYPE_CALCULATION_CONFIGURATION = "CALCULATION_CONFIGURATION";
    public static final String TYPE_MANUAL_INPUT = "MANUAL_INPUT_DEFINITION";
    public static final String TYPE_POLICY_ADJUSTMENT = "POLICY_ADJUSTMENT_CONFIGURATION";

    public static final String BIZ_DEFINED = "BUSINESS_DEFINITION_PROVIDED";
    public static final String BIZ_UNDEFINED = "NEEDS_DEFINITION";
    public static final String EXEC_READY = "READY";
    public static final String EXEC_NEEDS_CONFIG = "NEEDS_CONFIGURATION";
    public static final String EXEC_INFORMATION_ONLY = "INFORMATION_ONLY";
    public static final String EXEC_MANUAL = "MANUAL_INPUT";
    public static final String EXEC_UNAVAILABLE = "UNAVAILABLE";

    public static final String IMPACT_BLOCKING = "BLOCKING";
    public static final String IMPACT_NON_BLOCKING = "NON_BLOCKING";

    private PolicyDataResolutionSupport() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> fromDocument(PolicyStudioSession session) {
        if (session == null || session.getDocument() == null || session.getDocument().getMetadata() == null) {
            return new LinkedHashMap<>();
        }
        Object raw = session.getDocument().getMetadata().get(DOC_META_KEY);
        if (raw instanceof Map<?, ?> m) {
            return new LinkedHashMap<>((Map<String, Object>) m);
        }
        return new LinkedHashMap<>();
    }

    public static void stampOnDocument(PolicyStudioSession session, String dataItemId, Map<String, Object> resolution) {
        if (session == null || session.getDocument() == null || dataItemId == null || resolution == null) return;
        Map<String, Object> docMeta = session.getDocument().getMetadata() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(session.getDocument().getMetadata());
        Map<String, Object> all = fromDocument(session);
        all.put(dataItemId, resolution);
        docMeta.put(DOC_META_KEY, all);
        session.getDocument().setMetadata(docMeta);
    }

    public static Map<String, Object> thresholdDefinition(
            String dataItemId,
            BigDecimal amountInr,
            String notes,
            String actor,
            String documentId) {
        if (amountInr == null || amountInr.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Threshold amount must be a positive INR amount");
        }
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("definitionType", "ABSOLUTE_TRANSACTION_AMOUNT");
        def.put("operator", ">=");
        def.put("amountInr", amountInr);
        def.put("currency", "INR");
        def.put("unit", "INR");
        def.put("period", "TRAILING_3M");
        def.put("plainEnglish", "Transaction amount >= ₹" + formatInr(amountInr));
        if (notes != null && !notes.isBlank()) def.put("notes", notes.trim());

        Map<String, Object> out = base(dataItemId, TYPE_POLICY_THRESHOLD, documentId, actor);
        out.put("definition", def);
        out.put("businessDefinitionStatus", BIZ_DEFINED);
        out.put("executionStatus", EXEC_INFORMATION_ONLY);
        out.put("executionImpact", IMPACT_NON_BLOCKING);
        out.put("cmStatus", "READY");
        out.put("displayStatus", "READY FOR ANALYST INFORMATION");
        out.put("howDefined", def.get("plainEnglish") + " — Defined in this policy version");
        out.put("gacatMutated", false);
        return out;
    }

    public static Map<String, Object> classificationDefinition(
            String dataItemId,
            String identificationMethod,
            String notes,
            String actor,
            String documentId) {
        String method = identificationMethod == null ? "" : identificationMethod.trim().toUpperCase(Locale.ROOT);
        // Only mechanisms honestly available today
        boolean executable = "NARRATION_PATTERN_HINT".equals(method);
        boolean businessOk = List.of(
                "NARRATION_PATTERN_HINT",
                "MANUAL_INSTITUTIONAL_CLASSIFICATION",
                "RELATED_PARTY_LIST_NOT_CONFIGURED").contains(method);
        if (!businessOk) {
            throw new IllegalArgumentException(
                    "Unsupported identification method. Available: NARRATION_PATTERN_HINT, "
                            + "MANUAL_INSTITUTIONAL_CLASSIFICATION, RELATED_PARTY_LIST_NOT_CONFIGURED");
        }

        Map<String, Object> def = new LinkedHashMap<>();
        def.put("identificationMethod", method);
        def.put("supportedMechanisms", List.of(
                Map.of("code", "NARRATION_PATTERN_HINT",
                        "label", "Narration pattern hint (demo classifier)",
                        "executionCapable", true,
                        "productionBound", false),
                Map.of("code", "MANUAL_INSTITUTIONAL_CLASSIFICATION",
                        "label", "Manual institutional classification",
                        "executionCapable", false,
                        "productionBound", false),
                Map.of("code", "RELATED_PARTY_LIST_NOT_CONFIGURED",
                        "label", "Related-party / merchant-group master",
                        "executionCapable", false,
                        "productionBound", false,
                        "reason", "No production relationship classifier is currently configured")));
        def.put("fallback", "Manual review / not classified");
        if (notes != null && !notes.isBlank()) def.put("notes", notes.trim());

        Map<String, Object> out = base(dataItemId, TYPE_POLICY_CLASSIFICATION, documentId, actor);
        out.put("definition", def);
        out.put("businessDefinitionStatus", BIZ_DEFINED);
        out.put("executionStatus", executable ? EXEC_NEEDS_CONFIG : EXEC_NEEDS_CONFIG);
        out.put("executionCapabilityAvailable", executable);
        out.put("executionImpact", IMPACT_NON_BLOCKING);
        out.put("cmStatus", "NEEDS_CONFIGURATION");
        out.put("displayStatus", "BUSINESS DEFINITION PROVIDED — EXECUTION NEEDS CONFIGURATION");
        out.put("howDefined", "Identification method: " + method.replace('_', ' ')
                + ". No production merchant-group master is configured.");
        out.put("message", executable
                ? "Narration hint exists but is not a production relationship classifier."
                : "No production relationship classifier is currently configured.");
        out.put("gacatMutated", false);
        return out;
    }

    /**
     * POLICY-DATA-CALC-FUNCTIONAL-COMPLETION-1 — real EMI Bounce Count configuration.
     * Binding {@link com.los.core.creditintelligence.policystudio.metrics.EmiBounceCountCalculator}
     * makes the item executable/Ready when saved with confirmed configuration.
     */
    public static Map<String, Object> calculationConfiguration(
            String dataItemId,
            String actor,
            String documentId) {
        return calculationConfiguration(dataItemId, actor, documentId, Map.of());
    }

    public static Map<String, Object> calculationConfiguration(
            String dataItemId,
            String actor,
            String documentId,
            Map<String, Object> body) {
        String id = dataItemId == null ? "" : dataItemId.trim();
        boolean emiBounce = "banking.emi_bounce_count_3m".equals(id)
                || id.toLowerCase(Locale.ROOT).contains("emi_bounce");
        if (!emiBounce) {
            // Unknown calculation — honest proposal-only stamp
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("metric", id);
            def.put("executableMetric", false);
            def.put("binding", null);
            def.put("reason", "No executable calculation binding for this data item yet");
            Map<String, Object> out = base(id, TYPE_CALCULATION_CONFIGURATION, documentId, actor);
            out.put("definition", def);
            out.put("businessDefinitionStatus", BIZ_DEFINED);
            out.put("executionStatus", EXEC_NEEDS_CONFIG);
            out.put("executionCapabilityAvailable", false);
            out.put("executionImpact", IMPACT_NON_BLOCKING);
            out.put("cmStatus", "NEEDS_CONFIGURATION");
            out.put("displayStatus", "NEEDS IMPLEMENTATION");
            out.put("message", "Configuration proposal saved — executable calculation not implemented for this item");
            out.put("gacatMutated", false);
            return out;
        }

        var cfg = com.los.core.creditintelligence.policystudio.metrics.EmiBounceCountCalculator.Config
                .fromBody(body == null ? Map.of() : body);
        boolean confirm = body != null && (Boolean.TRUE.equals(body.get("confirmExecutable"))
                || Boolean.TRUE.equals(body.get("enableExecutableBinding"))
                || "SAVE_EXECUTABLE".equalsIgnoreCase(String.valueOf(body.getOrDefault("saveMode", ""))));
        // Default save from Configure UI enables the real binding (user completed configuration)
        if (body == null || body.isEmpty() || body.get("confirmExecutable") == null) {
            confirm = true;
        }

        Map<String, Object> def = new LinkedHashMap<>();
        def.put("metric", "EMI Bounce Count");
        def.put("canonicalId", com.los.core.creditintelligence.policystudio.metrics
                .EmiBounceCountCalculator.METRIC_ID);
        def.put("inputsAvailable", List.of(
                Map.of("name", "EMI classifier", "available", true, "code", "EXISTING_EMI_CLASSIFIER"),
                Map.of("name", "Bounce/return classifier", "available", true,
                        "code", "EXISTING_BOUNCE_RETURN_CLASSIFIER")));
        def.put("periodMonths", cfg.periodMonths());
        def.put("window", "Last " + cfg.periodMonths() + " months");
        def.put("emiIdentification", cfg.emiIdentification());
        def.put("bounceIdentification", cfg.bounceIdentification());
        def.put("excludeDuplicates", cfg.excludeDuplicates());
        def.put("outputType", "NUMERIC_COUNT");
        def.put("calculation",
                "Count EMI repayment events with matched return/bounce events during the period");
        def.put("missingDataTreatment", "DATA_INSUFFICIENT");
        def.put("binding", com.los.core.creditintelligence.policystudio.metrics
                .EmiBounceCountCalculator.BINDING);
        def.put("executableMetric", confirm);
        def.put("plainEnglish", "EMI Bounce Count over last " + cfg.periodMonths()
                + " months using existing EMI and bounce/return classifiers");

        Map<String, Object> out = base(
                com.los.core.creditintelligence.policystudio.metrics.EmiBounceCountCalculator.METRIC_ID,
                TYPE_CALCULATION_CONFIGURATION, documentId, actor);
        out.put("definition", def);
        out.put("businessDefinitionStatus", BIZ_DEFINED);
        out.put("gacatMutated", false);
        out.put("howCalculated", Map.of(
                "inputs", List.of("EMI classifier", "Bounce/return classifier", "Bank statement"),
                "window", def.get("window"),
                "method", def.get("calculation"),
                "implementation", com.los.core.creditintelligence.policystudio.metrics
                        .EmiBounceCountCalculator.BINDING,
                "missingData", "DATA_INSUFFICIENT when bank data/classification unavailable"));
        out.put("howDefined", def.get("plainEnglish") + " · Binding "
                + com.los.core.creditintelligence.policystudio.metrics.EmiBounceCountCalculator.BINDING);
        if (confirm) {
            out.put("executionStatus", EXEC_READY);
            out.put("executionCapabilityAvailable", true);
            out.put("executionImpact", IMPACT_NON_BLOCKING);
            out.put("cmStatus", "READY");
            out.put("displayStatus", "READY");
            out.put("message", "EMI Bounce Count configuration saved — executable binding active for Policy Test / preview");
            out.put("willBecomeReady", true);
        } else {
            out.put("executionStatus", EXEC_NEEDS_CONFIG);
            out.put("executionCapabilityAvailable", true);
            out.put("executionImpact", IMPACT_NON_BLOCKING);
            out.put("cmStatus", "NEEDS_CONFIGURATION");
            out.put("displayStatus", "NEEDS CONFIGURATION");
            out.put("message", "Design proposal saved — confirm executable binding to mark Ready");
            out.put("willBecomeReady", false);
        }
        return out;
    }

    public static Map<String, Object> policyAdjustment(
            String dataItemId,
            BigDecimal multiple,
            String actor,
            String documentId) {
        return policyAdjustment(dataItemId, multiple, actor, documentId, Map.of());
    }

    /**
     * BANKING-BRE-FINAL-CLOSURE-1 — policy-scoped bulk &gt;N× ADB adjustment with executable binding.
     */
    public static Map<String, Object> policyAdjustment(
            String dataItemId,
            BigDecimal multiple,
            String actor,
            String documentId,
            Map<String, Object> body) {
        var cfg = com.los.core.creditintelligence.policystudio.metrics.AdbBulkDepositAdjustmentCalculator.Config
                .fromBody(body == null ? Map.of() : body);
        BigDecimal mult = multiple != null ? multiple : cfg.multiple();
        if (body != null && body.get("multiple") == null && body.get("multipleOfAverageDeposits") == null) {
            // keep explicit multiple arg when body omits it
            cfg = new com.los.core.creditintelligence.policystudio.metrics
                    .AdbBulkDepositAdjustmentCalculator.Config(
                    cfg.periodMonths(), mult, cfg.strictGreaterThan(),
                    cfg.excludeLoanDisbursements(), cfg.excludeOnlineGaming(), cfg.excludeDuplicates());
        }
        boolean confirm = body == null || body.isEmpty()
                || body.get("confirmExecutable") == null
                || Boolean.TRUE.equals(body.get("confirmExecutable"))
                || Boolean.TRUE.equals(body.get("enableExecutableBinding"))
                || "SAVE_EXECUTABLE".equalsIgnoreCase(String.valueOf(body.getOrDefault("saveMode", "")));
        if (body != null && Boolean.FALSE.equals(body.get("confirmExecutable"))) {
            confirm = false;
        }

        String adjKey = dataItemId == null || dataItemId.isBlank()
                || dataItemId.contains("avg_daily") || dataItemId.contains("adb")
                ? com.los.core.creditintelligence.policystudio.metrics
                .AdbBulkDepositAdjustmentCalculator.ADJUSTMENT_ID
                : dataItemId;

        Map<String, Object> def = new LinkedHashMap<>();
        def.put("adjustmentType", "EXCLUDE_BULK_DEPOSITS");
        def.put("multipleOfAverageDeposits", cfg.multiple());
        def.put("multiple", cfg.multiple());
        def.put("periodMonths", cfg.periodMonths());
        def.put("periodLabel", "Last " + cfg.periodMonths() + " months");
        def.put("strictGreaterThan", cfg.strictGreaterThan());
        def.put("comparison", cfg.strictGreaterThan() ? ">" : ">=");
        def.put("depositPopulation", "Qualifying merchant credit deposits");
        def.put("averageDefinition",
                "Average qualifying deposit amount (mean of deposits ≤ 3× median; loan/gaming/duplicates excluded)");
        def.put("excludeLoanDisbursements", cfg.excludeLoanDisbursements());
        def.put("excludeOnlineGaming", cfg.excludeOnlineGaming());
        def.put("excludeDuplicates", cfg.excludeDuplicates());
        def.put("affectedMetric", com.los.core.creditintelligence.policystudio.metrics
                .AdbBulkDepositAdjustmentCalculator.AFFECTED_METRIC);
        def.put("adjustedMetric", com.los.core.creditintelligence.policystudio.metrics
                .AdbBulkDepositAdjustmentCalculator.ADJUSTED_METRIC);
        def.put("binding", com.los.core.creditintelligence.policystudio.metrics
                .AdbBulkDepositAdjustmentCalculator.BINDING);
        def.put("action", "Exclude qualifying bulk deposit from Adjusted ADB (EOD reconstruction)");
        def.put("plainEnglish", "Exclude bulk deposits " + (cfg.strictGreaterThan() ? ">" : ">=")
                + " " + cfg.multiple().toPlainString()
                + "× average deposits from Adjusted ADB");
        def.put("executable", confirm);
        def.put("executableMetric", confirm);
        def.put("gacatMutated", false);

        Map<String, Object> out = base(adjKey, TYPE_POLICY_ADJUSTMENT, documentId, actor);
        out.put("definition", def);
        out.put("businessDefinitionStatus", BIZ_DEFINED);
        out.put("gacatMutated", false);
        out.put("howCalculated", Map.of(
                "inputs", List.of(
                        "Bank statement credits",
                        "Existing loan-disbursement classifier",
                        "Existing online-gaming classifier",
                        "BANK_AVERAGE_DAILY_BALANCE_V1 EOD carry-forward"),
                "window", def.get("periodLabel"),
                "method", "Average qualifying deposit → bulk threshold → reverse excluded credits in EOD series → Adjusted ADB",
                "implementation", com.los.core.creditintelligence.policystudio.metrics
                        .AdbBulkDepositAdjustmentCalculator.BINDING,
                "missingData", "DATA_INSUFFICIENT when bank data/classification unavailable — never invent 0"));
        out.put("howDefined", def.get("plainEnglish") + " · Binding "
                + com.los.core.creditintelligence.policystudio.metrics
                .AdbBulkDepositAdjustmentCalculator.BINDING);
        if (confirm) {
            out.put("executionStatus", EXEC_READY);
            out.put("executionCapabilityAvailable", true);
            out.put("executionImpact", IMPACT_NON_BLOCKING);
            out.put("cmStatus", "READY");
            out.put("displayStatus", "READY");
            out.put("message", "Bulk >10× ADB adjustment saved — executable binding active for Policy Test / preview");
            out.put("willBecomeReady", true);
        } else {
            out.put("executionStatus", EXEC_NEEDS_CONFIG);
            out.put("executionCapabilityAvailable", true);
            out.put("executionImpact", IMPACT_BLOCKING);
            out.put("cmStatus", "NEEDS_CONFIGURATION");
            out.put("displayStatus", "NEEDS CONFIGURATION");
            out.put("message", "Design proposal saved — confirm executable binding to mark Ready");
            out.put("willBecomeReady", false);
        }
        return out;
    }

    public static Map<String, Object> manualInput(
            String dataItemId,
            String label,
            String dataType,
            String actor,
            String captureStage,
            String documentId) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Manual parameter label is required");
        }
        String stage = captureStage == null ? "" : captureStage.trim().toUpperCase(Locale.ROOT);
        boolean captureOk = List.of(
                "APPLICATION", "CAM_UNDERWRITING_REVIEW", "CREDIT_ANALYST_CAPTURE").contains(stage);
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("label", label.trim());
        def.put("dataType", dataType == null || dataType.isBlank() ? "Money" : dataType);
        def.put("actor", actor == null || actor.isBlank() ? "Credit Analyst" : actor);
        def.put("captureStage", stage.isBlank() ? "UNSPECIFIED" : stage);
        def.put("runtimeCapturePathExists", captureOk);

        Map<String, Object> out = base(dataItemId, TYPE_MANUAL_INPUT, documentId, actor);
        out.put("definition", def);
        out.put("businessDefinitionStatus", BIZ_DEFINED);
        if (captureOk) {
            out.put("executionStatus", EXEC_MANUAL);
            out.put("cmStatus", "MANUAL_INPUT");
            out.put("displayStatus", "MANUAL INPUT");
            out.put("executionImpact", IMPACT_NON_BLOCKING);
        } else {
            out.put("executionStatus", EXEC_NEEDS_CONFIG);
            out.put("cmStatus", "NEEDS_CONFIGURATION");
            out.put("displayStatus", "NEEDS CONFIGURATION");
            out.put("executionImpact", IMPACT_BLOCKING);
            out.put("message", "No proven runtime capture stage — select Application / CAM underwriting review");
        }
        out.put("howDefined", "Manual: " + label.trim() + " · Actor: " + def.get("actor")
                + " · Stage: " + def.get("captureStage"));
        out.put("gacatMutated", false);
        return out;
    }

    /** Apply document resolutions onto Data & Calculations rule cards (presentation only). */
    @SuppressWarnings("unchecked")
    public static void applyToDataCalcCards(List<Map<String, Object>> cards, Map<String, Object> resolutions) {
        if (cards == null || resolutions == null || resolutions.isEmpty()) return;
        for (Map<String, Object> card : cards) {
            String paramId = firstParamId(card);
            if (paramId == null) continue;
            Object resObj = resolutions.get(paramId);
            if (!(resObj instanceof Map<?, ?>)) {
                // also try adjustment keys
                String clause = String.valueOf(card.getOrDefault("sourceClause",
                        card.getOrDefault("businessRule", ""))).toLowerCase(Locale.ROOT);
                if (clause.contains("bulk") || clause.contains("10 times") || clause.contains("10×")) {
                    resObj = resolutions.get("banking.adb_bulk_deposit_adjustment");
                }
            }
            if (!(resObj instanceof Map<?, ?> resRaw)) continue;
            Map<String, Object> res = new LinkedHashMap<>((Map<String, Object>) resRaw);
            card.put("dataCalcResolution", res);
            card.put("dataCalcResolved", true);
            if (res.get("howDefined") != null) card.put("howDefined", res.get("howDefined"));
            if (res.get("howCalculated") != null) card.put("howCalculatedResolved", res.get("howCalculated"));
            if (res.get("displayStatus") != null) card.put("dataCalcDisplayStatus", res.get("displayStatus"));
            if (res.get("cmStatus") != null) card.put("dataCalcCmStatus", res.get("cmStatus"));
            // Clear "needs define" question when business definition present (keep exec honesty)
            if (BIZ_DEFINED.equals(String.valueOf(res.get("businessDefinitionStatus")))) {
                Map<String, Object> missing = card.get("missingDefinition") instanceof Map<?, ?> m
                        ? new LinkedHashMap<>((Map<String, Object>) m) : new LinkedHashMap<>();
                missing.put("resolved", true);
                missing.put("resolutionSummary", res.get("howDefined"));
                missing.put("executionStatus", res.get("executionStatus"));
                card.put("missingDefinition", missing);
            }
        }
    }

    private static Map<String, Object> base(String dataItemId, String type, String documentId, String actor) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resolutionId", UUID.randomUUID().toString());
        out.put("resolutionType", type);
        out.put("targetParameterId", dataItemId);
        out.put("dataItemId", dataItemId);
        out.put("policyDocumentId", documentId);
        out.put("actor", actor == null || actor.isBlank() ? "credit_manager" : actor);
        out.put("createdAt", Instant.now().toString());
        out.put("updatedAt", Instant.now().toString());
        out.put("persistence", "POLICY_VERSION_DURABLE");
        out.put("resolutionIdentity", PolicyResolutionIdentity.forAdjustment(
                String.valueOf(out.getOrDefault("dataItemId", ""))));
        out.put("scope", "POLICY_VERSION");
        out.put("provenance", "POLICY_DATA_RESOLUTION_UX_1");
        out.put("allowCanonicalAuthority", false);
        out.put("gacatMutated", false);
        return out;
    }

    private static String firstParamId(Map<String, Object> card) {
        for (String k : List.of("canonicalParameterId", "parameterId", "affectedParameterId")) {
            Object v = card.get(k);
            if (v != null && !String.valueOf(v).isBlank() && !"null".equalsIgnoreCase(String.valueOf(v))) {
                return String.valueOf(v);
            }
        }
        String clause = String.valueOf(card.getOrDefault("sourceClause", "")).toLowerCase(Locale.ROOT);
        if (clause.contains("large credit")) return "banking.large_credit_transactions";
        if (clause.contains("intercompany") || clause.contains("merchant group")) {
            return "banking.intercompany_transactions";
        }
        if (clause.contains("emi bounce")) return "banking.emi_bounce_count_3m";
        return null;
    }

    private static String formatInr(BigDecimal amount) {
        try {
            return String.format(Locale.ENGLISH, "%,.0f", amount);
        } catch (Exception e) {
            return amount.toPlainString();
        }
    }
}
