package com.los.core.service.underwriting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DP-3 — POLICY_WEIGHTED_V2 relative-weight normalization + missing-data semantics.
 * Does not mutate stored raw weights. Legacy scorecards must not call this path.
 */
public final class PolicyWeightedScorecardEngine {

    public static final String MODE = "POLICY_WEIGHTED_V2";

    public static final String STATE_PRESENT = "PRESENT";
    public static final String STATE_NOT_APPLICABLE = "NOT_APPLICABLE";
    public static final String STATE_MISSING = "MISSING";
    public static final String STATE_DATA_INSUFFICIENT = "DATA_INSUFFICIENT";
    public static final String STATE_INVALID = "INVALID";

    public enum FactorDataState {
        PRESENT, NOT_APPLICABLE, MISSING, DATA_INSUFFICIENT, INVALID
    }

    public record FactorInput(
            String canonicalParameterId,
            BigDecimal rawWeight,
            boolean required,
            FactorDataState dataState,
            BigDecimal bandPointsEarned,
            BigDecimal bandPointsMax
    ) {}

    public record WeightPreview(
            String canonicalParameterId,
            BigDecimal rawWeight,
            BigDecimal normalizedWeight,
            boolean applicable
    ) {}

    public record ScoreResult(
            boolean valid,
            boolean dataInsufficient,
            String outcome,
            BigDecimal weightedScore,
            List<WeightPreview> weights,
            List<Map<String, Object>> factorEvidence,
            List<String> errors
    ) {}

    private PolicyWeightedScorecardEngine() {}

    public static List<WeightPreview> normalizeApplicable(List<FactorInput> factors) {
        validateRawWeights(factors);
        BigDecimal sum = BigDecimal.ZERO;
        for (FactorInput f : factors) {
            if (isApplicable(f)) {
                sum = sum.add(nz(f.rawWeight()));
            }
        }
        if (sum.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("ALL_APPLICABLE_WEIGHTS_ZERO");
        }
        List<WeightPreview> out = new ArrayList<>();
        for (FactorInput f : factors) {
            boolean applicable = isApplicable(f);
            BigDecimal norm = applicable
                    ? nz(f.rawWeight()).multiply(BigDecimal.valueOf(100))
                    .divide(sum, 10, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            out.add(new WeightPreview(f.canonicalParameterId(), nz(f.rawWeight()), norm, applicable));
        }
        return out;
    }

    public static ScoreResult score(List<FactorInput> factors) {
        List<String> errors = new ArrayList<>();
        try {
            validateRawWeights(factors);
        } catch (IllegalArgumentException e) {
            return new ScoreResult(false, false, "VALIDATION_FAILED", null, List.of(), List.of(),
                    List.of(e.getMessage()));
        }

        // REQUIRED missing / DI → fail closed; do NOT drop from denominator / renormalize
        for (FactorInput f : factors) {
            if (f.required() && (f.dataState() == FactorDataState.MISSING
                    || f.dataState() == FactorDataState.DATA_INSUFFICIENT
                    || f.dataState() == FactorDataState.INVALID)) {
                List<WeightPreview> weights = safeNormalizeKeepingRequired(factors);
                List<Map<String, Object>> evidence = evidenceRows(factors, weights);
                return new ScoreResult(true, true, "DATA_INSUFFICIENT", null, weights, evidence,
                        List.of("REQUIRED_FACTOR_" + f.dataState().name() + ":" + f.canonicalParameterId()));
            }
        }

        List<WeightPreview> weights;
        try {
            weights = normalizeApplicable(factors);
        } catch (IllegalArgumentException e) {
            return new ScoreResult(false, false, "VALIDATION_FAILED", null, List.of(), List.of(),
                    List.of(e.getMessage()));
        }

        BigDecimal score = BigDecimal.ZERO;
        Map<String, WeightPreview> byId = new LinkedHashMap<>();
        for (WeightPreview w : weights) byId.put(w.canonicalParameterId(), w);
        for (FactorInput f : factors) {
            WeightPreview w = byId.get(f.canonicalParameterId());
            if (w == null || !w.applicable()) continue;
            BigDecimal max = f.bandPointsMax() == null || f.bandPointsMax().compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.valueOf(100) : f.bandPointsMax();
            BigDecimal earned = f.bandPointsEarned() == null ? BigDecimal.ZERO : f.bandPointsEarned();
            BigDecimal factorPct = earned.multiply(BigDecimal.valueOf(100))
                    .divide(max, 10, RoundingMode.HALF_UP);
            score = score.add(factorPct.multiply(w.normalizedWeight())
                    .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP));
        }

        return new ScoreResult(true, false, "SCORED", score.setScale(6, RoundingMode.HALF_UP),
                weights, evidenceRows(factors, weights), errors);
    }

    /**
     * When REQUIRED is missing, still compute what normalized weights would have been
     * including the required factor (no silent renormalize of remaining).
     */
    private static List<WeightPreview> safeNormalizeKeepingRequired(List<FactorInput> factors) {
        BigDecimal sum = BigDecimal.ZERO;
        for (FactorInput f : factors) {
            // Include REQUIRED even if missing; exclude only NOT_APPLICABLE
            if (f.dataState() != FactorDataState.NOT_APPLICABLE) {
                sum = sum.add(nz(f.rawWeight()));
            }
        }
        if (sum.compareTo(BigDecimal.ZERO) == 0) {
            sum = BigDecimal.ONE; // avoid div0 in evidence-only path
        }
        List<WeightPreview> out = new ArrayList<>();
        for (FactorInput f : factors) {
            boolean applicable = f.dataState() != FactorDataState.NOT_APPLICABLE;
            BigDecimal norm = applicable
                    ? nz(f.rawWeight()).multiply(BigDecimal.valueOf(100)).divide(sum, 10, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            out.add(new WeightPreview(f.canonicalParameterId(), nz(f.rawWeight()), norm, applicable));
        }
        return out;
    }

    private static boolean isApplicable(FactorInput f) {
        return f.dataState() != FactorDataState.NOT_APPLICABLE;
    }

    private static void validateRawWeights(List<FactorInput> factors) {
        if (factors == null || factors.isEmpty()) {
            throw new IllegalArgumentException("NO_FACTORS");
        }
        boolean anyApplicable = false;
        boolean anyPositiveApplicable = false;
        for (FactorInput f : factors) {
            BigDecimal w = nz(f.rawWeight());
            if (w.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("NEGATIVE_WEIGHT:" + f.canonicalParameterId());
            }
            if (isApplicable(f)) {
                anyApplicable = true;
                if (w.compareTo(BigDecimal.ZERO) > 0) anyPositiveApplicable = true;
            }
        }
        if (!anyApplicable || !anyPositiveApplicable) {
            throw new IllegalArgumentException("ALL_APPLICABLE_WEIGHTS_ZERO");
        }
    }

    private static List<Map<String, Object>> evidenceRows(List<FactorInput> factors, List<WeightPreview> weights) {
        Map<String, WeightPreview> byId = new LinkedHashMap<>();
        for (WeightPreview w : weights) byId.put(w.canonicalParameterId(), w);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (FactorInput f : factors) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("canonicalParameterId", f.canonicalParameterId());
            row.put("rawWeight", nz(f.rawWeight()));
            WeightPreview w = byId.get(f.canonicalParameterId());
            row.put("normalizedWeight", w != null ? w.normalizedWeight() : null);
            row.put("dataState", f.dataState().name());
            row.put("required", f.required());
            row.put("bandPointsEarned", f.bandPointsEarned());
            row.put("bandPointsMax", f.bandPointsMax());
            rows.add(row);
        }
        return rows;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Reject scorecard factors not in the Policy parameter set. */
    public static void assertFactorsSubsetOfPolicy(Set<String> policyParams, List<String> scorecardFactors) {
        for (String f : scorecardFactors) {
            if (f == null || f.isBlank()) continue;
            if (!policyParams.contains(f)) {
                throw new IllegalArgumentException("SCORECARD_FACTOR_NOT_IN_POLICY:" + f);
            }
        }
    }
}
