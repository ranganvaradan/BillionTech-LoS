package com.los.core.creditintelligence.reconciliation.service;

import com.los.core.creditintelligence.reconciliation.domain.ReconciliationConstants;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * CONFIDENCE_METHOD_V1 = min(left, right, periodCompleteness, classificationCoverage).
 */
@Component
public class ReconciliationConfidenceCalculator {

    public record ConfidenceInput(
            BigDecimal leftConfidence,
            BigDecimal rightConfidence,
            BigDecimal periodCompleteness,
            BigDecimal classificationCoverage) {
    }

    public BigDecimal calculate(ConfidenceInput input) {
        BigDecimal left = clamp(input != null ? input.leftConfidence() : null);
        BigDecimal right = clamp(input != null ? input.rightConfidence() : null);
        BigDecimal period = clamp(input != null ? input.periodCompleteness() : null);
        BigDecimal coverage = clamp(input != null ? input.classificationCoverage() : null);
        return left.min(right).min(period).min(coverage).setScale(4, RoundingMode.HALF_UP);
    }

    public String methodVersion() {
        return ReconciliationConstants.CONFIDENCE_METHOD_V1;
    }

    private static BigDecimal clamp(BigDecimal v) {
        if (v == null) {
            return BigDecimal.ONE;
        }
        if (v.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (v.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return v;
    }
}
