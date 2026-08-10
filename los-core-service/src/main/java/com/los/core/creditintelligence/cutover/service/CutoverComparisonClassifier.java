package com.los.core.creditintelligence.cutover.service;

import com.los.core.creditintelligence.cutover.domain.ComparisonClass;
import com.los.core.creditintelligence.cutover.domain.RootCauseClass;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Materiality rules + comparison / root-cause classification for dual-run rows.
 */
@Service
public class CutoverComparisonClassifier {

    public record MaterialityConfig(
            BigDecimal amountPctThreshold,
            BigDecimal pricingBpsThreshold,
            int tenureMonthsThreshold
    ) {
        public static MaterialityConfig defaults() {
            return new MaterialityConfig(
                    new BigDecimal("5"),
                    new BigDecimal("25"),
                    1);
        }
    }

    public record ClassificationResult(
            ComparisonClass comparisonClass,
            String materiality,
            RootCauseClass rootCause,
            Map<String, Object> detail
    ) {
    }

    public ClassificationResult classify(
            Map<String, Object> legacy,
            Map<String, Object> canonical,
            boolean legacyUsedDefault) {
        return classify(legacy, canonical, legacyUsedDefault, MaterialityConfig.defaults());
    }

    public ClassificationResult classify(
            Map<String, Object> legacy,
            Map<String, Object> canonical,
            boolean legacyUsedDefault,
            MaterialityConfig config) {
        Map<String, Object> L = legacy == null ? Map.of() : legacy;
        Map<String, Object> C = canonical == null ? Map.of() : canonical;
        Map<String, Object> detail = new LinkedHashMap<>();

        String legacyOutcome = str(L.get("policyOutcome"), str(L.get("outcome"), "PASS"));
        String canonOutcome = str(C.get("policyOutcome"), str(C.get("outcome"), null));

        if (legacyUsedDefault) {
            detail.put("legacyUsedDefault", true);
            return new ClassificationResult(
                    ComparisonClass.LEGACY_DEFAULT_DEPENDENT,
                    "MATERIAL",
                    RootCauseClass.DEFAULT_REMOVAL,
                    detail);
        }

        if ("DATA_INSUFFICIENT".equalsIgnoreCase(canonOutcome)
                || "DI".equalsIgnoreCase(canonOutcome)) {
            return new ClassificationResult(
                    ComparisonClass.CANONICAL_DATA_INSUFFICIENT,
                    "MATERIAL",
                    RootCauseClass.DATA_INSUFFICIENT,
                    detail);
        }
        if ("REFER".equalsIgnoreCase(canonOutcome)) {
            return new ClassificationResult(
                    ComparisonClass.CANONICAL_REFER,
                    "MATERIAL",
                    RootCauseClass.POLICY_SEMANTICS,
                    detail);
        }

        if (legacyOutcome != null && canonOutcome != null
                && !legacyOutcome.equalsIgnoreCase(canonOutcome)) {
            ComparisonClass cls = severityRank(canonOutcome) > severityRank(legacyOutcome)
                    ? ComparisonClass.CANONICAL_STRICTER
                    : ComparisonClass.CANONICAL_MORE_PERMISSIVE;
            return new ClassificationResult(
                    cls.equals(ComparisonClass.CANONICAL_STRICTER)
                            ? ComparisonClass.CANONICAL_STRICTER
                            : ComparisonClass.MATERIAL_POLICY_DIFFERENCE,
                    "MATERIAL",
                    RootCauseClass.POLICY_SEMANTICS,
                    detail);
        }

        BigDecimal la = num(L.get("amount"));
        BigDecimal ca = num(C.get("amount"));
        if (la != null && ca != null && la.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal pct = ca.subtract(la).abs()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(la.abs(), 4, RoundingMode.HALF_UP);
            detail.put("amountDiffPct", pct);
            if (pct.compareTo(config.amountPctThreshold()) > 0) {
                return new ClassificationResult(
                        ComparisonClass.MATERIAL_AMOUNT_DIFFERENCE,
                        "MATERIAL",
                        RootCauseClass.DECISION_STRATEGY,
                        detail);
            }
        }

        BigDecimal lp = num(L.get("pricing"));
        BigDecimal cp = num(C.get("pricing"));
        if (lp != null && cp != null) {
            BigDecimal bps = cp.subtract(lp).abs().multiply(BigDecimal.valueOf(10000));
            detail.put("pricingDiffBps", bps);
            if (bps.compareTo(config.pricingBpsThreshold()) > 0) {
                return new ClassificationResult(
                        ComparisonClass.MATERIAL_PRICING_DIFFERENCE,
                        "MATERIAL",
                        RootCauseClass.DECISION_STRATEGY,
                        detail);
            }
        }

        Integer lt = intVal(L.get("tenure"));
        Integer ct = intVal(C.get("tenure"));
        if (lt != null && ct != null && Math.abs(lt - ct) > config.tenureMonthsThreshold()) {
            detail.put("tenureDiff", Math.abs(lt - ct));
            return new ClassificationResult(
                    ComparisonClass.NON_MATERIAL_DIFFERENCE,
                    "NON_MATERIAL",
                    RootCauseClass.DECISION_STRATEGY,
                    detail);
        }

        if ((la != null && ca != null && la.compareTo(ca) != 0)
                || (lp != null && cp != null && lp.compareTo(cp) != 0)
                || (lt != null && ct != null && !lt.equals(ct))) {
            return new ClassificationResult(
                    ComparisonClass.NON_MATERIAL_DIFFERENCE,
                    "NON_MATERIAL",
                    RootCauseClass.INPUT_DIFFERENCE,
                    detail);
        }

        if (legacyOutcome != null && canonOutcome != null
                && severityRank(canonOutcome) > severityRank(legacyOutcome)) {
            return new ClassificationResult(
                    ComparisonClass.CANONICAL_STRICTER,
                    "MATERIAL",
                    RootCauseClass.POLICY_SEMANTICS,
                    detail);
        }

        return new ClassificationResult(
                ComparisonClass.EXACT_MATCH,
                "NONE",
                null,
                detail);
    }

    private static int severityRank(String outcome) {
        if (outcome == null) return 0;
        return switch (outcome.toUpperCase()) {
            case "PASS", "APPROVE", "APPROVE_WITH_CONDITIONS" -> 1;
            case "REFER" -> 2;
            case "FAIL", "DECLINE", "REJECT" -> 3;
            case "DATA_INSUFFICIENT" -> 2;
            default -> 1;
        };
    }

    private static String str(Object v, String d) {
        return v == null ? d : String.valueOf(v);
    }

    private static BigDecimal num(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer intVal(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }
}
