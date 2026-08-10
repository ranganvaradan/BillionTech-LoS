package com.los.core.creditintelligence.decision.service;

import com.los.core.creditintelligence.decision.domain.CiCreditRecommendation;
import com.los.core.creditintelligence.decision.domain.DecisionComparisonClass;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DecisionComparisonService {

    public Map<String, Object> compare(
            Map<String, Object> productionRecommendation,
            Map<String, Object> legacyShadow,
            CiCreditRecommendation canonical) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<String> classes = new ArrayList<>();

        BigDecimal prodAmount = num(productionRecommendation, "amount", "recommendedAmount");
        BigDecimal canonAmount = canonical == null ? null : canonical.getRecommendedAmount();
        Integer prodTenure = intVal(productionRecommendation, "tenure", "recommendedTenureMonths");
        Integer canonTenure = canonical == null ? null : canonical.getRecommendedTenureMonths();
        BigDecimal prodRate = num(productionRecommendation, "rate", "recommendedFinalRate");
        BigDecimal canonRate = canonical == null ? null : canonical.getRecommendedFinalRate();
        String prodAuth = str(productionRecommendation, "authority", "approvalAuthorityLevel");
        String canonAuth = canonical == null ? null : canonical.getApprovalAuthorityLevel();

        boolean legacyDefault = Boolean.TRUE.equals(
                productionRecommendation != null ? productionRecommendation.get("legacyUsedDefault") : null)
                || Boolean.TRUE.equals(legacyShadow != null ? legacyShadow.get("legacyUsedDefault") : null);

        if (legacyDefault) {
            classes.add(DecisionComparisonClass.LEGACY_DEFAULT_DEPENDENT.name());
        }
        if (canonical != null && "DATA_INSUFFICIENT".equals(canonical.getRecommendationOutcome())) {
            classes.add(DecisionComparisonClass.DATA_INSUFFICIENT.name());
        }
        if (prodAmount != null && canonAmount != null) {
            int cmp = canonAmount.compareTo(prodAmount);
            if (cmp < 0) classes.add(DecisionComparisonClass.CANONICAL_AMOUNT_LOWER.name());
            else if (cmp > 0) classes.add(DecisionComparisonClass.CANONICAL_AMOUNT_HIGHER.name());
        }
        if (prodTenure != null && canonTenure != null && !prodTenure.equals(canonTenure)) {
            classes.add(DecisionComparisonClass.TENURE_DIFFERENT.name());
        }
        if (prodRate != null && canonRate != null && prodRate.compareTo(canonRate) != 0) {
            classes.add(DecisionComparisonClass.PRICING_DIFFERENT.name());
        }
        if (prodAuth != null && canonAuth != null && !prodAuth.equalsIgnoreCase(canonAuth)) {
            classes.add(DecisionComparisonClass.AUTHORITY_DIFFERENT.name());
        }
        if (canonical != null && canonical.getConditionsPrecedent() != null
                && !canonical.getConditionsPrecedent().isEmpty()) {
            Object prodConds = productionRecommendation == null ? null : productionRecommendation.get("conditions");
            if (prodConds == null || (prodConds instanceof List<?> l && l.isEmpty())) {
                classes.add(DecisionComparisonClass.CONDITION_ADDED.name());
            }
        }
        if (classes.isEmpty()) {
            classes.add(DecisionComparisonClass.MATCH.name());
        }

        out.put("comparisonClasses", classes);
        out.put("primaryClass", classes.get(0));
        out.put("production", productionRecommendation == null ? Map.of() : productionRecommendation);
        out.put("legacyShadow", legacyShadow == null ? Map.of() : legacyShadow);
        out.put("canonical", summary(canonical));
        out.put("shadowOnly", true);
        return out;
    }

    private Map<String, Object> summary(CiCreditRecommendation r) {
        if (r == null) return Map.of();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("outcome", r.getRecommendationOutcome());
        m.put("amount", r.getRecommendedAmount());
        m.put("tenure", r.getRecommendedTenureMonths());
        m.put("rate", r.getRecommendedFinalRate());
        m.put("authority", r.getApprovalAuthorityLevel());
        m.put("hash", r.getDeterministicDecisionHash());
        m.put("authoritative", false);
        return m;
    }

    private BigDecimal num(Map<String, Object> m, String... keys) {
        if (m == null) return null;
        for (String k : keys) {
            Object v = m.get(k);
            if (v instanceof BigDecimal bd) return bd;
            if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        }
        return null;
    }

    private Integer intVal(Map<String, Object> m, String... keys) {
        if (m == null) return null;
        for (String k : keys) {
            Object v = m.get(k);
            if (v instanceof Number n) return n.intValue();
        }
        return null;
    }

    private String str(Map<String, Object> m, String... keys) {
        if (m == null) return null;
        for (String k : keys) {
            if (m.get(k) != null) return String.valueOf(m.get(k));
        }
        return null;
    }
}
