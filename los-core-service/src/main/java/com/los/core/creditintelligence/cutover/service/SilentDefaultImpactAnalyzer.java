package com.los.core.creditintelligence.cutover.service;

import com.los.core.creditintelligence.cutover.domain.CiCutoverComparison;
import com.los.core.creditintelligence.cutover.domain.ComparisonClass;
import com.los.core.creditintelligence.cutover.store.CutoverStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Computes silent-default impact from available dual-run comparisons only.
 * Never fabricates statistics.
 */
@Service
public class SilentDefaultImpactAnalyzer {

    private final CutoverStore store;

    public SilentDefaultImpactAnalyzer(CutoverStore store) {
        this.store = store;
    }

    public Map<String, Object> analyze(UUID cohortId, List<String> legacyKeys) {
        List<CiCutoverComparison> rows = store.listComparisons(cohortId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cohortId", cohortId);
        out.put("sampleSize", rows.size());
        out.put("dataOrigin", "COMPUTED_FROM_AVAILABLE_DUAL_RUN_ROWS");
        out.put("fabricated", false);
        if (rows.isEmpty()) {
            out.put("note", "No dual-run comparisons available — impact not computable");
            out.put("defaults", List.of());
            return out;
        }

        List<Map<String, Object>> defaults = new ArrayList<>();
        List<String> keys = legacyKeys == null || legacyKeys.isEmpty()
                ? List.of("ANNUAL_GST_TURNOVER", "AVERAGE_BANK_BALANCE", "LIVE_UNSECURED_LOAN_COUNT",
                "EMI_OBLIGATION", "MONTHLY_INCOME")
                : legacyKeys;

        long defaultDependent = rows.stream()
                .filter(r -> ComparisonClass.LEGACY_DEFAULT_DEPENDENT.name().equals(r.getComparisonClass()))
                .count();

        for (String key : keys) {
            long invoked = rows.stream()
                    .filter(r -> mentionsDefault(r, key))
                    .count();
            List<CiCutoverComparison> impacted = rows.stream()
                    .filter(r -> mentionsDefault(r, key))
                    .toList();
            long decisionChanges = impacted.stream()
                    .filter(r -> r.getLegacyPolicyOutcome() != null
                            && r.getCanonicalPolicyOutcome() != null
                            && !r.getLegacyPolicyOutcome().equalsIgnoreCase(r.getCanonicalPolicyOutcome()))
                    .count();
            BigDecimal avgImpact = averageAmountDelta(impacted);
            BigDecimal maxImpact = maxAmountDelta(impacted);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("legacyKey", key);
            row.put("casesInvoked", invoked);
            row.put("pctOfCohort", pct(invoked, rows.size()));
            row.put("averageImpactAmount", avgImpact);
            row.put("maxImpactAmount", maxImpact);
            row.put("decisionChanges", decisionChanges);
            row.put("rulesAffected", extractRules(impacted));
            defaults.add(row);
        }

        out.put("legacyDefaultDependentCount", defaultDependent);
        out.put("legacyDefaultDependentPct", pct(defaultDependent, rows.size()));
        out.put("defaults", defaults);
        return out;
    }

    private static boolean mentionsDefault(CiCutoverComparison r, String key) {
        if (ComparisonClass.LEGACY_DEFAULT_DEPENDENT.name().equals(r.getComparisonClass())) {
            if (r.getReasonCodes() != null && r.getReasonCodes().stream().anyMatch(c -> c.contains(key))) {
                return true;
            }
            if (r.getDecisionTrace() != null) {
                Object defs = r.getDecisionTrace().get("legacyDefaultsUsed");
                if (defs instanceof List<?> list) {
                    return list.stream().anyMatch(x -> key.equals(String.valueOf(x)));
                }
                Object single = r.getDecisionTrace().get("legacyDefaultKey");
                if (key.equals(String.valueOf(single))) return true;
            }
            // If class is LEGACY_DEFAULT_DEPENDENT without key detail, count under generic bucket only
            return "ANNUAL_GST_TURNOVER".equals(key) && r.getDecisionTrace() != null
                    && r.getDecisionTrace().containsKey("legacyDefaultsUsed");
        }
        return false;
    }

    private static BigDecimal averageAmountDelta(List<CiCutoverComparison> rows) {
        List<BigDecimal> deltas = new ArrayList<>();
        for (CiCutoverComparison r : rows) {
            if (r.getLegacyAmount() != null && r.getCanonicalAmount() != null) {
                deltas.add(r.getCanonicalAmount().subtract(r.getLegacyAmount()).abs());
            }
        }
        if (deltas.isEmpty()) return null;
        BigDecimal sum = deltas.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(deltas.size()), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal maxAmountDelta(List<CiCutoverComparison> rows) {
        return rows.stream()
                .filter(r -> r.getLegacyAmount() != null && r.getCanonicalAmount() != null)
                .map(r -> r.getCanonicalAmount().subtract(r.getLegacyAmount()).abs())
                .max(BigDecimal::compareTo)
                .orElse(null);
    }

    private static List<String> extractRules(List<CiCutoverComparison> rows) {
        List<String> rules = new ArrayList<>();
        for (CiCutoverComparison r : rows) {
            if (r.getReasonCodes() != null) {
                r.getReasonCodes().stream()
                        .filter(c -> !rules.contains(c))
                        .forEach(rules::add);
            }
        }
        return rules;
    }

    private static BigDecimal pct(long n, int total) {
        if (total == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(n * 100.0 / total).setScale(2, RoundingMode.HALF_UP);
    }
}
