package com.los.core.creditintelligence.cutover.pilot;

import com.los.core.creditintelligence.cutover.domain.CiCutoverComparison;
import com.los.core.creditintelligence.cutover.domain.ComparisonClass;
import com.los.core.creditintelligence.cutover.store.CutoverStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dual-run statistics §11 from persisted comparisons only.
 */
@Service
public class PilotDualRunStatsService {

    private final CutoverStore store;

    public PilotDualRunStatsService(CutoverStore store) {
        this.store = store;
    }

    public Map<String, Object> statistics(UUID cohortId) {
        List<CiCutoverComparison> rows = store.listComparisons(cohortId);
        Map<String, Object> out = new LinkedHashMap<>();
        int total = rows.size();
        int realStored = 0;
        int fixture = 0;
        for (CiCutoverComparison r : rows) {
            Object origin = r.getDecisionTrace() == null ? null : r.getDecisionTrace().get("dataOrigin");
            String o = origin == null ? "UNKNOWN" : String.valueOf(origin);
            if ("REAL_DEV".equals(o) || "STORED_PROVIDER".equals(o)
                    || "ANONYMIZED_REAL_DEV_DATA".equals(o) || "STORED_PROVIDER_FIXTURE".equals(o)) {
                realStored++;
            } else {
                fixture++;
            }
        }
        out.put("totalApplications", total);
        out.put("realStoredApplications", realStored);
        out.put("fixtureApplications", fixture);
        out.put("exactMatches", count(rows, ComparisonClass.EXACT_MATCH));
        out.put("nonMaterialDifferences", count(rows, ComparisonClass.NON_MATERIAL_DIFFERENCE));
        out.put("materialDifferences", materialCount(rows));
        out.put("canonicalStricter", count(rows, ComparisonClass.CANONICAL_STRICTER));
        out.put("canonicalMorePermissive", count(rows, ComparisonClass.CANONICAL_MORE_PERMISSIVE));
        out.put("canonicalRefer", count(rows, ComparisonClass.CANONICAL_REFER));
        out.put("canonicalDi", count(rows, ComparisonClass.CANONICAL_DATA_INSUFFICIENT));
        out.put("legacyDefaultDependent", count(rows, ComparisonClass.LEGACY_DEFAULT_DEPENDENT));
        out.put("exactMatchPct", pct(count(rows, ComparisonClass.EXACT_MATCH), total));
        out.put("materialMismatchPct", pct(materialCount(rows), total));
        out.put("canonicalDiPct", pct(count(rows, ComparisonClass.CANONICAL_DATA_INSUFFICIENT), total));
        out.put("canonicalMorePermissivePct",
                pct(count(rows, ComparisonClass.CANONICAL_MORE_PERMISSIVE), total));
        out.put("canonicalFailureRate", canonicalFailureRate(rows));
        out.put("byClass", byClass(rows));
        out.put("breakdownNote", "Breakdowns by rule/input/source require decisionTrace detail when present");
        out.put("honestyNote", "Computed only from persisted dual-run rows; never fabricated");
        return out;
    }

    private static long count(List<CiCutoverComparison> rows, ComparisonClass cls) {
        return rows.stream().filter(r -> cls.name().equals(r.getComparisonClass())).count();
    }

    private static long materialCount(List<CiCutoverComparison> rows) {
        return rows.stream().filter(r ->
                "MATERIAL".equals(r.getMateriality())
                        || (r.getComparisonClass() != null && r.getComparisonClass().startsWith("MATERIAL_"))
        ).count();
    }

    private static BigDecimal pct(long n, int total) {
        if (total == 0) return null;
        return BigDecimal.valueOf(n * 100.0 / total).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal canonicalFailureRate(List<CiCutoverComparison> rows) {
        if (rows.isEmpty()) return null;
        long fails = rows.stream().filter(r -> r.getDecisionTrace() != null
                && "CANONICAL_EVALUATION_FAILED".equals(
                String.valueOf(r.getDecisionTrace().get("canonicalEvaluationStatus")))).count();
        return BigDecimal.valueOf(fails * 100.0 / rows.size()).setScale(2, RoundingMode.HALF_UP);
    }

    private static Map<String, Long> byClass(List<CiCutoverComparison> rows) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (CiCutoverComparison r : rows) {
            m.merge(r.getComparisonClass(), 1L, Long::sum);
        }
        return m;
    }
}
