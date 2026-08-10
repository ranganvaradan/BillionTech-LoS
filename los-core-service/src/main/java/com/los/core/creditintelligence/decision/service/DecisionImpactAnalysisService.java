package com.los.core.creditintelligence.decision.service;

import com.los.core.creditintelligence.decision.domain.CiCreditRecommendation;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DecisionImpactAnalysisService {

    public Map<String, Object> analyze(List<Map<String, Object>> pairs) {
        Map<String, Object> out = new LinkedHashMap<>();
        int amountLower = 0, amountHigher = 0, amountMatch = 0;
        int outcomeDiff = 0, authorityDiff = 0;
        List<Map<String, Object>> rows = new ArrayList<>();

        for (Map<String, Object> pair : pairs == null ? List.<Map<String, Object>>of() : pairs) {
            Map<String, Object> legacy = asMap(pair.get("legacy"));
            Object canonObj = pair.get("canonical");
            BigDecimal legacyAmount = bd(legacy.get("amount"));
            BigDecimal canonAmount = null;
            String legacyOutcome = str(legacy.get("outcome"));
            String canonOutcome = null;
            String legacyAuth = str(legacy.get("authority"));
            String canonAuth = null;

            if (canonObj instanceof CiCreditRecommendation rec) {
                canonAmount = rec.getRecommendedAmount();
                canonOutcome = rec.getRecommendationOutcome();
                canonAuth = rec.getApprovalAuthorityLevel();
            } else if (canonObj instanceof Map<?, ?> cm) {
                canonAmount = bd(cm.get("amount"));
                canonOutcome = str(cm.get("outcome"));
                canonAuth = str(cm.get("authority"));
            }

            if (legacyAmount != null && canonAmount != null) {
                int cmp = canonAmount.compareTo(legacyAmount);
                if (cmp < 0) amountLower++;
                else if (cmp > 0) amountHigher++;
                else amountMatch++;
            }
            if (legacyOutcome != null && canonOutcome != null && !legacyOutcome.equals(canonOutcome)) {
                outcomeDiff++;
            }
            if (legacyAuth != null && canonAuth != null && !legacyAuth.equalsIgnoreCase(canonAuth)) {
                authorityDiff++;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("legacyAmount", legacyAmount);
            row.put("canonicalAmount", canonAmount);
            row.put("legacyOutcome", legacyOutcome);
            row.put("canonicalOutcome", canonOutcome);
            row.put("legacyAuthority", legacyAuth);
            row.put("canonicalAuthority", canonAuth);
            rows.add(row);
        }

        out.put("count", rows.size());
        out.put("legacyAmountVsCanonical", Map.of(
                "lower", amountLower, "higher", amountHigher, "match", amountMatch));
        out.put("legacyDecisionVsCanonical", Map.of("different", outcomeDiff));
        out.put("legacyAuthorityVsCanonical", Map.of("different", authorityDiff));
        out.put("rows", rows);
        out.put("shadowOnly", true);
        out.put("cutoverReady", false);
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return Map.of();
    }

    private BigDecimal bd(Object o) {
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return null;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
