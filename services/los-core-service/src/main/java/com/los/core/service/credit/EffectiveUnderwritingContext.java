package com.los.core.service.credit;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Values used by the underwriting engine after applying decision source selection.
 */
public record EffectiveUnderwritingContext(
        int effectiveBureauScore,
        boolean kycPassEffective,
        BigDecimal effectiveIncome,
        BigDecimal effectiveObligation,
        String effectiveState,
        String effectiveCity,
        String bureauSource,
        String incomeSource,
        String kycSource,
        /** Scorecard parameters (GST income, bank income, ABB, ratio, etc.). */
        Map<String, BigDecimal> scorecard) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("effectiveBureauScore", effectiveBureauScore);
        m.put("kycPassEffective", kycPassEffective);
        m.put("effectiveIncome", effectiveIncome != null ? effectiveIncome.toPlainString() : null);
        m.put("effectiveObligation", effectiveObligation != null ? effectiveObligation.toPlainString() : null);
        m.put("effectiveState", effectiveState);
        m.put("effectiveCity", effectiveCity);
        m.put("bureauScoreSource", bureauSource);
        m.put("incomeSource", incomeSource);
        m.put("kycSource", kycSource);
        if (scorecard != null && !scorecard.isEmpty()) {
            Map<String, String> sc = new LinkedHashMap<>();
            for (var e : scorecard.entrySet()) {
                if (e.getValue() != null) {
                    sc.put(e.getKey(), e.getValue().toPlainString());
                }
            }
            m.put("scorecard", sc);
        }
        return m;
    }
}
