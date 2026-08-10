package com.los.core.creditintelligence.decisionpolicy.kyc.shadow;

import com.los.core.creditintelligence.decisionpolicy.kyc.KycBusinessOutcome;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic KYC shadow aggregate semantics.
 *
 * <pre>
 * Precedence (highest first):
 * 1. Any hard/knockout/platform-guardrail FAIL → FAIL
 * 2. Else any REFER (incl. manual-verification unresolved as REFER when designated) → REFER
 * 3. Else any mandatory/hard MISSING_INFORMATION (DATA_INSUFFICIENT) → MISSING_INFORMATION
 * 4. Else PASS
 *
 * Soft / informational rules that FAIL do not force overall FAIL unless ruleType is HARD/KNOCKOUT
 * or guardrailClass=PLATFORM_GUARDRAIL.
 * Soft DATA_INSUFFICIENT does not force MISSING_INFORMATION.
 * </pre>
 */
public final class KycPolicyOutcomeAggregator {

    private KycPolicyOutcomeAggregator() {}

    public record Aggregate(
            KycBusinessOutcome overall,
            List<String> failReasons,
            List<String> referReasons,
            List<String> missingFacts,
            String semanticsVersion
    ) {}

    public static Aggregate aggregate(List<Map<String, Object>> ruleResults) {
        List<String> fails = new ArrayList<>();
        List<String> refers = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        boolean hardFail = false;
        boolean anyRefer = false;
        boolean mandatoryMissing = false;

        if (ruleResults != null) {
            for (Map<String, Object> r : ruleResults) {
                String outcome = normalize(String.valueOf(r.get("outcome")));
                String ruleId = String.valueOf(r.getOrDefault("ruleId", "rule"));
                String reason = String.valueOf(r.getOrDefault("reason", r.getOrDefault("businessRuleName", ruleId)));
                boolean hard = isHardBlocking(r);
                boolean mandatory = hard || Boolean.TRUE.equals(r.get("mandatory"));

                if (PolicyDslInterpreterV1.FAIL.equals(outcome) || "FAIL".equals(outcome)) {
                    if (hard) {
                        hardFail = true;
                        fails.add(reason);
                    }
                } else if (PolicyDslInterpreterV1.REFER.equals(outcome) || "REFER".equals(outcome)) {
                    anyRefer = true;
                    refers.add(reason);
                } else if (PolicyDslInterpreterV1.DATA_INSUFFICIENT.equals(outcome)
                        || "MISSING_INFORMATION".equals(outcome)) {
                    if (mandatory) {
                        mandatoryMissing = true;
                        Object mf = r.get("missingFacts");
                        if (mf instanceof List<?> list && !list.isEmpty()) {
                            list.forEach(x -> missing.add(String.valueOf(x)));
                        } else {
                            missing.add(String.valueOf(r.getOrDefault("primaryFact", ruleId)));
                        }
                    }
                }
            }
        }

        KycBusinessOutcome overall;
        if (hardFail) {
            overall = KycBusinessOutcome.FAIL;
        } else if (anyRefer) {
            overall = KycBusinessOutcome.REFER;
        } else if (mandatoryMissing) {
            overall = KycBusinessOutcome.MISSING_INFORMATION;
        } else {
            overall = KycBusinessOutcome.PASS;
        }

        return new Aggregate(overall, fails, refers, missing, "KYC5_AGGREGATE_V1");
    }

    static boolean isHardBlocking(Map<String, Object> r) {
        String type = String.valueOf(r.getOrDefault("ruleType", "")).toUpperCase(Locale.ROOT);
        if ("KNOCKOUT".equals(type) || "HARD".equals(type) || "HARD_RULE".equals(type)
                || "CRITICAL".equals(type)) {
            return true;
        }
        String guard = String.valueOf(r.getOrDefault("guardrailClass", "")).toUpperCase(Locale.ROOT);
        if ("PLATFORM_GUARDRAIL".equals(guard) || "REGULATORY_GUARDRAIL".equals(guard)) {
            return true;
        }
        String req = String.valueOf(r.getOrDefault("requirementType", "")).toUpperCase(Locale.ROOT);
        return "VERIFICATION".equals(req) || "COMPLETION_REQUIREMENT".equals(req)
                || "REGULATORY_GUARDRAIL".equals(req);
    }

    static String normalize(String outcome) {
        if (outcome == null) {
            return PolicyDslInterpreterV1.DATA_INSUFFICIENT;
        }
        String o = outcome.trim().toUpperCase(Locale.ROOT);
        if ("MISSING_INFORMATION".equals(o) || "INCOMPLETE".equals(o)) {
            return PolicyDslInterpreterV1.DATA_INSUFFICIENT;
        }
        return o;
    }

    public static KycBusinessOutcome fromDsl(String dsl) {
        if (dsl == null) {
            return KycBusinessOutcome.MISSING_INFORMATION;
        }
        return switch (dsl.trim().toUpperCase(Locale.ROOT)) {
            case "PASS" -> KycBusinessOutcome.PASS;
            case "FAIL" -> KycBusinessOutcome.FAIL;
            case "REFER" -> KycBusinessOutcome.REFER;
            default -> KycBusinessOutcome.MISSING_INFORMATION;
        };
    }

    public static Map<String, Object> documentedSemantics() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", "KYC5_AGGREGATE_V1");
        m.put("precedence", List.of(
                "HARD/KNOCKOUT/PLATFORM_GUARDRAIL FAIL → FAIL",
                "Any REFER → REFER",
                "Mandatory DATA_INSUFFICIENT → MISSING_INFORMATION",
                "Else → PASS"));
        m.put("softRulesDoNotForceFail", true);
        m.put("providerUnavailableIsNotFail", true);
        return m;
    }
}
