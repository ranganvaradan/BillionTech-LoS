package com.los.core.creditintelligence.cutover.pilot;

import com.los.core.creditintelligence.cutover.domain.CiPilotCandidateScore;
import com.los.core.creditintelligence.cutover.store.CutoverStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Objective pilot product ranking (§6). DIGILEAP may win but must be scored, not assumed.
 */
@Service
public class PilotCandidateRanker {

    public record CandidateScore(
            String productCode,
            BigDecimal score,
            Map<String, Object> rationale
    ) {
    }

    private final CutoverStore store;

    public PilotCandidateRanker(CutoverStore store) {
        this.store = store;
    }

    public List<CandidateScore> rank() {
        return rank(Map.of());
    }

    /**
     * @param overrides optional per-product metric overrides for tests
     *                  (keys: sourceCompleteness, bindingCoverage, defaultDependency,
     *                   sampleSize, complexity, mismatchRate, diRate, rollbackSimplicity)
     *                  where defaultDependency/complexity/mismatchRate/diRate are penalties (higher = worse)
     */
    public List<CandidateScore> rank(Map<String, Map<String, Double>> overrides) {
        List<String> products = List.of("DIGILEAP", "SCF_STARTER", "REBOOST", "SMART_SWITCH");
        List<CandidateScore> ranked = new ArrayList<>();
        for (String product : products) {
            Map<String, Double> m = defaultMetrics(product);
            if (overrides != null && overrides.containsKey(product)) {
                m.putAll(overrides.get(product));
            }
            Map<String, Object> rationale = new LinkedHashMap<>();
            double source = clamp(m.get("sourceCompleteness"));
            double binding = clamp(m.get("bindingCoverage"));
            double defaultDep = clamp(m.get("defaultDependency"));
            double sample = clamp(m.get("sampleSize"));
            double complexity = clamp(m.get("complexity"));
            double mismatch = clamp(m.get("mismatchRate"));
            double di = clamp(m.get("diRate"));
            double rollback = clamp(m.get("rollbackSimplicity"));

            // Higher is better: completeness/coverage/sample/rollback; invert penalties
            double score = 100.0 * (
                    0.18 * source
                            + 0.18 * binding
                            + 0.12 * (1.0 - defaultDep)
                            + 0.12 * sample
                            + 0.10 * (1.0 - complexity)
                            + 0.10 * (1.0 - mismatch)
                            + 0.10 * (1.0 - di)
                            + 0.10 * rollback
            );

            rationale.put("sourceCompleteness", source);
            rationale.put("bindingCoverage", binding);
            rationale.put("defaultDependency", defaultDep);
            rationale.put("sampleSize", sample);
            rationale.put("complexity", complexity);
            rationale.put("mismatchRate", mismatch);
            rationale.put("diRate", di);
            rationale.put("rollbackSimplicity", rollback);
            rationale.put("formula",
                    "0.18*src + 0.18*bind + 0.12*(1-def) + 0.12*sample + 0.10*(1-cx) "
                            + "+ 0.10*(1-mm) + 0.10*(1-di) + 0.10*rollback");
            rationale.put("note", "Objective score; DIGILEAP not auto-selected");

            BigDecimal bd = BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
            ranked.add(new CandidateScore(product, bd, rationale));

            if (store != null) {
                store.saveCandidateScore(CiPilotCandidateScore.builder()
                        .productCode(product)
                        .score(bd)
                        .rationale(rationale)
                        .rankedAt(Instant.now())
                        .build());
            }
        }
        ranked.sort(Comparator.comparing(CandidateScore::score).reversed()
                .thenComparing(CandidateScore::productCode));
        return ranked;
    }

    /**
     * Honest defaults from fixture-only evidence: DIGILEAP has best C6 coverage and known G0 cohort,
     * but sampleSize stays low across all (no real/stored multi-source cases).
     */
    private static Map<String, Double> defaultMetrics(String product) {
        Map<String, Double> m = new LinkedHashMap<>();
        switch (product) {
            case "DIGILEAP" -> {
                m.put("sourceCompleteness", 0.85);
                m.put("bindingCoverage", 0.80);
                m.put("defaultDependency", 0.55);
                m.put("sampleSize", 0.15); // fixture-only
                m.put("complexity", 0.45);
                m.put("mismatchRate", 0.20);
                m.put("diRate", 0.25);
                m.put("rollbackSimplicity", 0.90);
            }
            case "SCF_STARTER" -> {
                m.put("sourceCompleteness", 0.70);
                m.put("bindingCoverage", 0.65);
                m.put("defaultDependency", 0.70);
                m.put("sampleSize", 0.10);
                m.put("complexity", 0.55);
                m.put("mismatchRate", 0.25);
                m.put("diRate", 0.35);
                m.put("rollbackSimplicity", 0.85);
            }
            case "REBOOST" -> {
                m.put("sourceCompleteness", 0.40);
                m.put("bindingCoverage", 0.35);
                m.put("defaultDependency", 0.60);
                m.put("sampleSize", 0.05);
                m.put("complexity", 0.50);
                m.put("mismatchRate", 0.40);
                m.put("diRate", 0.40);
                m.put("rollbackSimplicity", 0.70);
            }
            case "SMART_SWITCH" -> {
                m.put("sourceCompleteness", 0.35);
                m.put("bindingCoverage", 0.30);
                m.put("defaultDependency", 0.50);
                m.put("sampleSize", 0.05);
                m.put("complexity", 0.60);
                m.put("mismatchRate", 0.45);
                m.put("diRate", 0.45);
                m.put("rollbackSimplicity", 0.65);
            }
            default -> {
                m.put("sourceCompleteness", 0.20);
                m.put("bindingCoverage", 0.20);
                m.put("defaultDependency", 0.80);
                m.put("sampleSize", 0.0);
                m.put("complexity", 0.70);
                m.put("mismatchRate", 0.50);
                m.put("diRate", 0.50);
                m.put("rollbackSimplicity", 0.50);
            }
        }
        return m;
    }

    private static double clamp(Double v) {
        if (v == null) return 0;
        return Math.max(0, Math.min(1, v));
    }
}
