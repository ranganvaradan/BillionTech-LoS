package com.los.core.creditintelligence.validation.service;

import com.los.core.creditintelligence.reconciliation.domain.ReconciliationConstants;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationOutcome;
import com.los.core.creditintelligence.validation.model.ValidationBundle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic investigation questions from MATERIAL_VARIANCE / CONFLICT / DI reconciliations.
 */
@Component
public class InvestigationQuestionGenerator {

    public List<String> generate(Map<String, Object> reconciliations, ValidationBundle bundle) {
        List<String> questions = new ArrayList<>();
        if (reconciliations == null) {
            return questions;
        }
        Object gstBank = reconciliations.get(ReconciliationConstants.XSRC_GST_BANK_TURNOVER);
        if (gstBank instanceof Map<?, ?> m) {
            String outcome = String.valueOf(m.get("outcome"));
            if (isMaterial(outcome)) {
                Object pct = m.get("percentageVariance");
                questions.add("Bank credits are " + pct + "% below/above GST turnover. "
                        + "Are collections routed through another business account?");
            }
        }
        Object bureauBank = reconciliations.get(ReconciliationConstants.XSRC_BUREAU_BANK_OBLIGATION);
        if (bureauBank instanceof Map<?, ?> m) {
            String outcome = String.valueOf(m.get("outcome"));
            if (isMaterial(outcome)) {
                questions.add("Bureau reports ₹" + m.get("left") + " monthly obligation, "
                        + "but bank data detects only ₹" + m.get("right") + ". "
                        + "Has one facility recently closed?");
            }
        }
        Object tri = reconciliations.get(ReconciliationConstants.TURNOVER_TRIANGULATION);
        if (tri instanceof Map<?, ?> m && isMaterial(String.valueOf(m.get("outcome")))) {
            questions.add("GST / ITR / Bank turnover triangulation shows material variance. "
                    + "Which source best reflects actual collections for the assessment period?");
        }
        if (bundle != null && bundle.sources() != null) {
            if (!bundle.sources().containsKey("bank")) {
                questions.add("Banking source is absent. Provide AA consent or BSA statement "
                        + "before bank-dependent rules can be evaluated without gap defaults.");
            }
            if (!bundle.sources().containsKey("bureau")) {
                questions.add("Bureau tradelines are absent. Live unsecured count and bureau EMI "
                        + "cannot be verified — do not rely on LIVE_UNSECURED gap default of 2.");
            }
        }
        Object di = reconciliations.get("DATA_INSUFFICIENT_FLAGS");
        if (di instanceof List<?> list) {
            for (Object o : list) {
                questions.add("Data insufficient for: " + o + ". Obtain source evidence before decision.");
            }
        }
        return questions;
    }

    private static boolean isMaterial(String outcome) {
        return ReconciliationOutcome.MATERIAL_VARIANCE.name().equals(outcome)
                || ReconciliationOutcome.CONFLICT.name().equals(outcome)
                || "REFER".equals(outcome);
    }

    public static Map<String, Object> pair(
            String outcome, BigDecimal left, BigDecimal right, BigDecimal pct) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("outcome", outcome);
        m.put("left", left);
        m.put("right", right);
        m.put("percentageVariance", pct);
        return m;
    }

    public static BigDecimal variancePct(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return null;
        }
        BigDecimal base = a.abs().max(b.abs()).max(BigDecimal.ONE);
        return a.subtract(b).abs().multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
    }
}
