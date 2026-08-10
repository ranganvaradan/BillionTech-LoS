package com.los.core.creditintelligence.cutover.pilot;

import com.los.core.creditintelligence.cutover.store.CutoverStore;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Observability dashboard model §29 — no PII.
 */
@Service
public class PilotObservabilityDashboard {

    private final CutoverStore store;
    private final PilotDualRunStatsService dualRunStats;

    public PilotObservabilityDashboard(CutoverStore store, PilotDualRunStatsService dualRunStats) {
        this.store = store;
        this.dualRunStats = dualRunStats;
    }

    public Map<String, Object> metrics(UUID cohortId) {
        Map<String, Object> stats = dualRunStats.statistics(cohortId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cohortId", cohortId);
        out.put("dualRunVolume", stats.get("totalApplications"));
        out.put("canonicalSuccessRate", invertFailure(stats.get("canonicalFailureRate")));
        out.put("canonicalDiRate", stats.get("canonicalDiPct"));
        out.put("mismatchRate", invertExact(stats.get("exactMatchPct")));
        out.put("materialMismatchRate", stats.get("materialMismatchPct"));
        Object legacyDep = stats.get("legacyDefaultDependent");
        int total = stats.get("totalApplications") instanceof Number n ? n.intValue() : 0;
        out.put("legacyDefaultRate", total == 0 || !(legacyDep instanceof Number ld) ? null
                : java.math.BigDecimal.valueOf(ld.doubleValue() * 100.0 / total)
                .setScale(2, java.math.RoundingMode.HALF_UP));
        out.put("canonicalLatencyMs", null); // not instrumented in unit store
        out.put("sourceFailureRate", null);
        out.put("operationalEventCount", store.listOperationalEvents(cohortId).size());
        out.put("piiExcluded", true);
        out.put("aiExcludedFromCertification", true);
        return out;
    }

    private static Object invertFailure(Object failureRate) {
        if (!(failureRate instanceof java.math.BigDecimal bd)) return null;
        return java.math.BigDecimal.valueOf(100).subtract(bd).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static Object invertExact(Object exactPct) {
        if (!(exactPct instanceof java.math.BigDecimal bd)) return null;
        return java.math.BigDecimal.valueOf(100).subtract(bd).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
