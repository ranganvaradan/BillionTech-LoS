package com.los.core.creditintelligence.reconciliation.service;

import com.los.core.creditintelligence.reconciliation.domain.VarianceMethod;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Deterministic variance calculator. Avoids div-by-zero via max(|denom|, 1) where applicable.
 */
@Component
public class VarianceCalculator {

    public record VarianceResult(
            BigDecimal absoluteVariance,
            BigDecimal percentageVariance,
            BigDecimal ratio,
            VarianceMethod method,
            String denominatorNote) {
    }

    public VarianceResult calculate(BigDecimal left, BigDecimal right, VarianceMethod method) {
        VarianceMethod m = method != null ? method : VarianceMethod.SYMMETRIC_PERCENT_DIFFERENCE;
        BigDecimal l = left != null ? left : BigDecimal.ZERO;
        BigDecimal r = right != null ? right : BigDecimal.ZERO;
        BigDecimal abs = l.subtract(r).abs().setScale(2, RoundingMode.HALF_UP);

        return switch (m) {
            case ABSOLUTE -> new VarianceResult(abs, null, null, m, "ABSOLUTE");
            case PERCENT_OF_LEFT -> pct(abs, l, m, "PERCENT_OF_LEFT=max(|left|,1)");
            case PERCENT_OF_RIGHT -> pct(abs, r, m, "PERCENT_OF_RIGHT=max(|right|,1)");
            case PERCENT_OF_MAX -> pct(abs, l.abs().max(r.abs()), m, "PERCENT_OF_MAX=max(|left|,|right|,1)");
            case PERCENT_OF_AVERAGE -> {
                BigDecimal avg = l.add(r).abs().divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
                yield pct(abs, avg, m, "PERCENT_OF_AVERAGE=max(|avg|,1)");
            }
            case SYMMETRIC_PERCENT_DIFFERENCE -> {
                // |L-R| / ((|L|+|R|)/2) * 100 ; denom floored at 1
                BigDecimal avg = l.abs().add(r.abs()).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
                yield pct(abs, avg, m, "SYMMETRIC_PERCENT_DIFFERENCE=max((|L|+|R|)/2,1)");
            }
            case RATIO -> {
                BigDecimal denom = safeDenom(r);
                BigDecimal ratio = l.divide(denom, 8, RoundingMode.HALF_UP);
                BigDecimal pctVar = ratio.subtract(BigDecimal.ONE).abs()
                        .multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP);
                yield new VarianceResult(abs, pctVar, ratio, m, "RATIO=left/max(|right|,1)");
            }
        };
    }

    private static VarianceResult pct(BigDecimal abs, BigDecimal denomRaw, VarianceMethod m, String note) {
        BigDecimal denom = safeDenom(denomRaw);
        BigDecimal pct = abs.multiply(BigDecimal.valueOf(100)).divide(denom, 4, RoundingMode.HALF_UP);
        return new VarianceResult(abs, pct, null, m, note);
    }

    /** max(|denom|, 1) */
    static BigDecimal safeDenom(BigDecimal denom) {
        if (denom == null) {
            return BigDecimal.ONE;
        }
        BigDecimal a = denom.abs();
        return a.compareTo(BigDecimal.ONE) < 0 ? BigDecimal.ONE : a;
    }
}
