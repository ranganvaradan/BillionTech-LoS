package com.los.core.creditintelligence.cutover.pilot;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bureau/GST/bank/ITR readiness percentages for pilot applications (§16).
 */
@Service
public class SourceReadinessStatsService {

    public record AppSourceReadiness(
            String applicationLabel,
            boolean bureauReady,
            boolean gstReady,
            boolean bankReady,
            boolean itrReady
    ) {
        public boolean allCriticalReady() {
            return bureauReady && gstReady && bankReady && itrReady;
        }
    }

    public Map<String, Object> compute(List<AppSourceReadiness> apps) {
        List<AppSourceReadiness> list = apps == null ? List.of() : apps;
        int n = list.size();
        Map<String, Object> out = new LinkedHashMap<>();
        if (n == 0) {
            out.put("sampleSize", 0);
            out.put("bureauReadyPct", null);
            out.put("gstReadyPct", null);
            out.put("bankReadyPct", null);
            out.put("itrReadyPct", null);
            out.put("allCriticalSourcesReadyPct", null);
            out.put("note", "No applications — rates not computable");
            return out;
        }
        long bureau = list.stream().filter(AppSourceReadiness::bureauReady).count();
        long gst = list.stream().filter(AppSourceReadiness::gstReady).count();
        long bank = list.stream().filter(AppSourceReadiness::bankReady).count();
        long itr = list.stream().filter(AppSourceReadiness::itrReady).count();
        long all = list.stream().filter(AppSourceReadiness::allCriticalReady).count();
        out.put("sampleSize", n);
        out.put("bureauReadyPct", pct(bureau, n));
        out.put("gstReadyPct", pct(gst, n));
        out.put("bankReadyPct", pct(bank, n));
        out.put("itrReadyPct", pct(itr, n));
        out.put("allCriticalSourcesReadyPct", pct(all, n));
        return out;
    }

    /** Fixture-honest defaults for C6 CASE_A–E compositions. */
    public static List<AppSourceReadiness> c6FixtureReadiness() {
        return List.of(
                new AppSourceReadiness("CASE_A", true, true, true, true),
                new AppSourceReadiness("CASE_B", true, true, true, false),
                new AppSourceReadiness("CASE_C", true, true, true, true),
                new AppSourceReadiness("CASE_D", true, false, true, true),
                new AppSourceReadiness("CASE_E", false, false, false, false)
        );
    }

    private static BigDecimal pct(long hit, int total) {
        return BigDecimal.valueOf(hit * 100.0 / total).setScale(2, RoundingMode.HALF_UP);
    }
}
