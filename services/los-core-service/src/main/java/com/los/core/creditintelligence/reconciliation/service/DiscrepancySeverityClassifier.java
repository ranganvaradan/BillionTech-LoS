package com.los.core.creditintelligence.reconciliation.service;

import com.los.core.creditintelligence.reconciliation.domain.DiscrepancySeverity;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationOutcome;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Severity considers percentage and absolute amount (₹10k ≠ ₹5 Cr).
 */
@Component
public class DiscrepancySeverityClassifier {

    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");
    private static final BigDecimal ONE_LAKH = new BigDecimal("100000");
    private static final BigDecimal ONE_CRORE = new BigDecimal("10000000");
    private static final BigDecimal FIVE_CRORE = new BigDecimal("50000000");

    public DiscrepancySeverity classify(
            ReconciliationOutcome outcome,
            BigDecimal percentageVariance,
            BigDecimal absoluteVariance) {

        if (outcome == null
                || outcome == ReconciliationOutcome.MATCH
                || outcome == ReconciliationOutcome.DATA_INSUFFICIENT
                || outcome == ReconciliationOutcome.NOT_APPLICABLE
                || outcome == ReconciliationOutcome.ERROR) {
            return DiscrepancySeverity.LOW;
        }

        BigDecimal abs = absoluteVariance != null ? absoluteVariance.abs() : BigDecimal.ZERO;
        BigDecimal pct = percentageVariance != null ? percentageVariance.abs() : BigDecimal.ZERO;

        if (outcome == ReconciliationOutcome.ACCEPTABLE_VARIANCE) {
            if (abs.compareTo(ONE_CRORE) >= 0) {
                return DiscrepancySeverity.MEDIUM;
            }
            return DiscrepancySeverity.LOW;
        }

        if (outcome == ReconciliationOutcome.MATERIAL_VARIANCE) {
            if (abs.compareTo(FIVE_CRORE) >= 0 || pct.compareTo(new BigDecimal("40")) >= 0) {
                return DiscrepancySeverity.CRITICAL;
            }
            if (abs.compareTo(ONE_CRORE) >= 0 || pct.compareTo(new BigDecimal("25")) >= 0) {
                return DiscrepancySeverity.HIGH;
            }
            if (abs.compareTo(ONE_LAKH) >= 0) {
                return DiscrepancySeverity.MEDIUM;
            }
            return abs.compareTo(TEN_THOUSAND) <= 0 ? DiscrepancySeverity.LOW : DiscrepancySeverity.MEDIUM;
        }

        // CONFLICT
        if (abs.compareTo(ONE_CRORE) >= 0 || pct.compareTo(new BigDecimal("30")) >= 0) {
            return DiscrepancySeverity.CRITICAL;
        }
        if (abs.compareTo(ONE_LAKH) >= 0) {
            return DiscrepancySeverity.HIGH;
        }
        return DiscrepancySeverity.MEDIUM;
    }
}
