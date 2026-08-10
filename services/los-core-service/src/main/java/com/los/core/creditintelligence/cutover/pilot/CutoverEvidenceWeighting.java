package com.los.core.creditintelligence.cutover.pilot;

import com.los.core.creditintelligence.cutover.domain.ValidationDataOrigin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CUTOVER_VALIDATION_EVIDENCE_V1 — validation-strength weights only (§5).
 * Does not affect borrower credit risk or AI scoring.
 */
public final class CutoverEvidenceWeighting {

    public static final String VERSION = "CUTOVER_VALIDATION_EVIDENCE_V1";

    private static final Map<String, BigDecimal> WEIGHTS = Map.of(
            "ANONYMIZED_REAL_DEV_DATA", bd("1.00"),
            ValidationDataOrigin.REAL_DEV.name(), bd("1.00"),
            "STORED_PROVIDER_FIXTURE", bd("0.90"),
            ValidationDataOrigin.STORED_PROVIDER.name(), bd("0.90"),
            "USER_SUPPLIED_SAMPLE", bd("0.60"),
            "REPRESENTATIVE_PROVIDER_FIXTURE", bd("0.40"),
            ValidationDataOrigin.REPRESENTATIVE_FIXTURE.name(), bd("0.40"),
            ValidationDataOrigin.SYNTHETIC.name(), bd("0.20")
    );

    private CutoverEvidenceWeighting() {
    }

    public static BigDecimal weightFor(ValidationDataOrigin origin) {
        if (origin == null) return bd("0.20");
        return WEIGHTS.getOrDefault(origin.name(), bd("0.20"));
    }

    public static BigDecimal weightForLabel(String label) {
        if (label == null || label.isBlank()) return bd("0.20");
        return WEIGHTS.getOrDefault(label.toUpperCase(), bd("0.20"));
    }

    /** Counts REAL_DEV + STORED_PROVIDER toward minRealOrStoredCases. */
    public static boolean countsAsRealOrStored(ValidationDataOrigin origin) {
        return origin == ValidationDataOrigin.REAL_DEV
                || origin == ValidationDataOrigin.STORED_PROVIDER;
    }

    public static BigDecimal weightedScore(Map<ValidationDataOrigin, Integer> countsByOrigin) {
        if (countsByOrigin == null || countsByOrigin.isEmpty()) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal sum = BigDecimal.ZERO;
        int n = 0;
        for (Map.Entry<ValidationDataOrigin, Integer> e : countsByOrigin.entrySet()) {
            int c = e.getValue() == null ? 0 : e.getValue();
            if (c <= 0) continue;
            sum = sum.add(weightFor(e.getKey()).multiply(BigDecimal.valueOf(c)));
            n += c;
        }
        if (n == 0) return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        return sum.divide(BigDecimal.valueOf(n), 4, RoundingMode.HALF_UP);
    }

    public static Map<String, Object> descriptor() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", VERSION);
        out.put("weights", Map.of(
                "ANONYMIZED_REAL_DEV_DATA", "1.00",
                "STORED_PROVIDER_FIXTURE", "0.90",
                "USER_SUPPLIED_SAMPLE", "0.60",
                "REPRESENTATIVE_PROVIDER_FIXTURE", "0.40",
                "SYNTHETIC", "0.20"));
        out.put("note", "Validation-strength only; does not affect credit risk or AI certification score");
        return out;
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
