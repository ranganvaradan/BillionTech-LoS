package com.los.core.creditintelligence.validation.service;

import com.los.core.creditintelligence.validation.domain.CiPolicyComparison;
import com.los.core.creditintelligence.validation.domain.PolicyDifferenceClass;
import com.los.core.creditintelligence.validation.domain.ValidationCaseCode;
import com.los.core.creditintelligence.validation.model.ValidationBundle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dual non-authoritative policy shadow: LEGACY_POLICY_SHADOW vs CANONICAL_POLICY_SHADOW.
 */
@Service
public class DualPolicyEvaluator {

    public record DualPolicyResult(
            List<CiPolicyComparison> comparisons,
            Map<String, Object> summary
    ) {
    }

    public DualPolicyResult evaluate(
            UUID tenantId,
            UUID applicationId,
            UUID validationRunId,
            UUID evaluationContextId,
            ValidationBundle bundle) {
        List<CiPolicyComparison> comparisons = new ArrayList<>();
        Map<String, BigDecimal> metrics = bundle.metricStubs();
        Map<String, Object> legacy = bundle.legacyScorecardStub();
        boolean hasBureau = bundle.sources().containsKey("bureau");
        boolean hasBank = bundle.sources().containsKey("bank");
        boolean hasGst = bundle.sources().containsKey("gst");

        // LIVE_UNSECURED
        comparisons.add(compareRule(
                tenantId, applicationId, validationRunId, evaluationContextId,
                "LIVE_UNSECURED_LOAN_COUNT",
                legacyOutcome(legacy, "LIVE_UNSECURED_LOAN_COUNT", "PASS"),
                hasBureau && metrics.containsKey("bureau.live_unsecured_count") ? "PASS" : "DATA_INSUFFICIENT",
                mapOf("value", legacy.getOrDefault("LIVE_UNSECURED_LOAN_COUNT", 2), "defaultsAllowed", true),
                mapOf("value", metrics.get("bureau.live_unsecured_count"), "defaultsAllowed", false),
                !hasBureau ? PolicyDifferenceClass.LEGACY_DEFAULT_DEPENDENT
                        : PolicyDifferenceClass.MATCH,
                !hasBureau
                        ? "Legacy gap-defaults LIVE_UNSECURED=2; canonical DI without bureau tradelines"
                        : "Both paths have live unsecured count"));

        // GST turnover
        Object legacyGst = legacy.get("ANNUAL_GST_TURNOVER");
        BigDecimal canonGst = metrics.get("gst.turnover.trailing_12m");
        PolicyDifferenceClass gstClass = PolicyDifferenceClass.MATCH;
        String gstCanonOutcome = hasGst && canonGst != null ? "PASS" : "DATA_INSUFFICIENT";
        if (!hasGst) {
            gstClass = PolicyDifferenceClass.LEGACY_DEFAULT_DEPENDENT;
        } else if (legacyGst != null && canonGst != null
                && new BigDecimal(String.valueOf(legacyGst)).compareTo(canonGst) != 0
                && bundle.caseCode() == ValidationCaseCode.CASE_B_LEGACY_DEFAULT) {
            gstClass = PolicyDifferenceClass.LEGACY_DEFAULT_DEPENDENT;
            gstCanonOutcome = "PASS"; // evidence value differs from SCF gap default
        }
        comparisons.add(compareRule(
                tenantId, applicationId, validationRunId, evaluationContextId,
                "ANNUAL_GST_TURNOVER",
                "PASS",
                gstCanonOutcome,
                mapOf("value", legacyGst != null ? legacyGst : "52000000", "defaultsAllowed", true),
                mapOf("value", canonGst, "defaultsAllowed", false),
                gstClass,
                gstClass == PolicyDifferenceClass.LEGACY_DEFAULT_DEPENDENT
                        ? "Legacy SCF gap default vs canonical GST metric / DI"
                        : "GST turnover aligned"));

        // ABB
        comparisons.add(compareRule(
                tenantId, applicationId, validationRunId, evaluationContextId,
                "AVERAGE_BANK_BALANCE",
                "PASS",
                hasBank ? "PASS" : "DATA_INSUFFICIENT",
                mapOf("value", legacy.getOrDefault("AVERAGE_BANK_BALANCE", 120000), "defaultsAllowed", true),
                mapOf("value", metrics.get("bank.abb.average"), "defaultsAllowed", false),
                hasBank ? PolicyDifferenceClass.MATCH : PolicyDifferenceClass.LEGACY_DEFAULT_DEPENDENT,
                hasBank ? "ABB present" : "Canonical DI; legacy gap-defaults ABB=120000"));

        // EMI / obligation
        BigDecimal bureauEmi = metrics.get("bureau.emi.monthly");
        BigDecimal bankEmi = metrics.get("bank.emi.monthly");
        PolicyDifferenceClass emiClass = PolicyDifferenceClass.MATCH;
        String emiCanon = "PASS";
        if (bureauEmi != null && bankEmi != null) {
            BigDecimal pct = bureauEmi.subtract(bankEmi).abs()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(bureauEmi.max(bankEmi), 4, java.math.RoundingMode.HALF_UP);
            if (pct.compareTo(BigDecimal.valueOf(20)) > 0) {
                emiClass = PolicyDifferenceClass.CANONICAL_EVIDENCE_CONFLICT;
                emiCanon = "REFER";
            }
        } else if (!hasBureau && !hasBank) {
            emiClass = PolicyDifferenceClass.LEGACY_DEFAULT_DEPENDENT;
            emiCanon = "DATA_INSUFFICIENT";
        } else if (bureauEmi == null && bankEmi == null) {
            emiClass = PolicyDifferenceClass.CANONICAL_DATA_INSUFFICIENT;
            emiCanon = "DATA_INSUFFICIENT";
        }
        comparisons.add(compareRule(
                tenantId, applicationId, validationRunId, evaluationContextId,
                "EMI_OBLIGATION",
                "PASS",
                emiCanon,
                mapOf("value", legacy.getOrDefault("EMI_OBLIGATION", 15000), "defaultsAllowed", true),
                mapOf("bureauEmi", bureauEmi, "bankEmi", bankEmi, "defaultsAllowed", false),
                emiClass,
                "Legacy may gap-default EMI; canonical uses bureau/bank with conflict detection"));

        // Turnover triangulation conflict case
        if (bundle.caseCode() == ValidationCaseCode.CASE_C_TURNOVER_CONFLICT) {
            comparisons.add(compareRule(
                    tenantId, applicationId, validationRunId, evaluationContextId,
                    "TURNOVER_TRIANGULATION",
                    "PASS",
                    "REFER",
                    mapOf("gst", legacy.get("ANNUAL_GST_TURNOVER"), "defaultsAllowed", true),
                    mapOf(
                            "gst", metrics.get("gst.turnover.trailing_12m"),
                            "itr", metrics.get("itr.turnover.trailing_12m"),
                            "bank", metrics.get("bank.turnover.trailing_12m"),
                            "defaultsAllowed", false),
                    PolicyDifferenceClass.CANONICAL_EVIDENCE_CONFLICT,
                    "Material GST vs Bank vs ITR variance — REFER with explanation, not auto-fraud"));
        }

        // Incomplete case
        if (bundle.caseCode() == ValidationCaseCode.CASE_E_INCOMPLETE
                || bundle.caseCode() == ValidationCaseCode.CASE_B_LEGACY_DEFAULT) {
            comparisons.add(compareRule(
                    tenantId, applicationId, validationRunId, evaluationContextId,
                    "BANKING_DEPENDENT_RULES",
                    "PASS",
                    "DATA_INSUFFICIENT",
                    mapOf("defaultsAllowed", true, "gapDefaults", true),
                    mapOf("defaultsAllowed", false, "bankPresent", hasBank),
                    PolicyDifferenceClass.LEGACY_DEFAULT_DEPENDENT,
                    "Legacy fills bank gaps; canonical refuses silent defaults"));
        }

        Map<String, Long> byClass = new LinkedHashMap<>();
        for (CiPolicyComparison c : comparisons) {
            byClass.merge(c.getDifferenceClass(), 1L, Long::sum);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("ruleCount", comparisons.size());
        summary.put("byDifferenceClass", byClass);
        summary.put("legacyPolicyMode", "LEGACY_POLICY_SHADOW");
        summary.put("canonicalPolicyMode", "CANONICAL_POLICY_SHADOW");
        summary.put("authoritative", false);
        return new DualPolicyResult(comparisons, summary);
    }

    private static String legacyOutcome(Map<String, Object> legacy, String key, String fallback) {
        return legacy != null && legacy.containsKey(key) ? fallback : fallback;
    }

    private static CiPolicyComparison compareRule(
            UUID tenantId, UUID applicationId, UUID runId, UUID ctxId,
            String ruleId, String legacyOutcome, String canonicalOutcome,
            Map<String, Object> legacyInput, Map<String, Object> canonicalInput,
            PolicyDifferenceClass diff, String explanation) {
        return CiPolicyComparison.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .applicationId(applicationId)
                .validationRunId(runId)
                .evaluationContextId(ctxId)
                .ruleId(ruleId)
                .legacyOutcome(legacyOutcome)
                .canonicalOutcome(canonicalOutcome)
                .differenceClass(diff.name())
                .legacyInput(legacyInput)
                .canonicalInput(canonicalInput)
                .explanation(explanation)
                .build();
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
