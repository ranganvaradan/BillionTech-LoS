package com.los.core.creditintelligence.decision.service;

import com.los.core.creditintelligence.decision.domain.CiPricingComponentResult;
import com.los.core.creditintelligence.decision.domain.DecisionRuntimeInput;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PricingEngine {

    public record PricingResult(
            BigDecimal baseRate,
            BigDecimal riskPremium,
            BigDecimal finalRate,
            List<CiPricingComponentResult> components,
            List<String> reasonCodes,
            List<String> limitations,
            boolean evidenceWeaknessCausesRefer,
            Map<String, Object> detail
    ) {}

    public PricingResult compute(Map<String, Object> pricingStrategy, DecisionRuntimeInput input, Integer tenureMonths) {
        Map<String, Object> s = pricingStrategy == null ? Map.of() : pricingStrategy;
        BigDecimal base = DecisionValueHelper.bd(s.getOrDefault("baseRate", 0.125));
        BigDecimal floor = DecisionValueHelper.bd(s.getOrDefault("floor", 0.11));
        BigDecimal cap = DecisionValueHelper.bd(s.getOrDefault("cap", 0.24));
        boolean evidenceRefer = Boolean.TRUE.equals(s.get("evidenceWeaknessCausesRefer"));

        List<CiPricingComponentResult> components = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        BigDecimal totalPremiumBps = BigDecimal.ZERO;

        String grade = DecisionValueHelper.str(input.scoreResult().get("grade"));
        if (grade == null || grade.isBlank()) {
            grade = "DATA_INSUFFICIENT";
            limitations.add("MISSING_RISK_GRADE");
            reasons.add("PRICING_MISSING_RISK_GRADE");
        }

        Object compsObj = s.get("components");
        if (compsObj instanceof List<?> comps) {
            for (Object c : comps) {
                if (!(c instanceof Map<?, ?> cm)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> comp = (Map<String, Object>) cm;
                String code = String.valueOf(comp.getOrDefault("code", "UNKNOWN"));
                BigDecimal bps = resolveComponentBps(code, comp, grade, tenureMonths, input);
                if (bps == null) {
                    continue;
                }
                components.add(CiPricingComponentResult.builder()
                        .componentCode(code)
                        .valueBps(bps)
                        .basis(basisFor(code, grade, tenureMonths))
                        .ruleVersion("PRICING_V1")
                        .reasonCode(code)
                        .evidenceRefs(List.of(Map.of("kind", "SCORE", "reference", "grade")))
                        .build());
                totalPremiumBps = totalPremiumBps.add(bps);
                reasons.add("PRICING_COMPONENT:" + code);
            }
        }

        BigDecimal riskPremium = totalPremiumBps.divide(BigDecimal.valueOf(10000), 6, RoundingMode.HALF_UP);
        BigDecimal finalRate = base.add(riskPremium);
        if (finalRate.compareTo(floor) < 0) {
            finalRate = floor;
            reasons.add("PRICING_FLOOR_APPLIED");
        }
        if (finalRate.compareTo(cap) > 0) {
            finalRate = cap;
            reasons.add("PRICING_CAP_APPLIED");
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("baseRate", base);
        detail.put("riskPremium", riskPremium);
        detail.put("finalRate", finalRate);
        detail.put("floor", floor);
        detail.put("cap", cap);
        detail.put("components", components.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", c.getComponentCode());
            m.put("valueBps", c.getValueBps());
            m.put("basis", c.getBasis());
            return m;
        }).toList());
        detail.put("grade", grade);

        return new PricingResult(base, riskPremium, finalRate.setScale(6, RoundingMode.HALF_UP),
                components, reasons, limitations, evidenceRefer, detail);
    }

    @SuppressWarnings("unchecked")
    private BigDecimal resolveComponentBps(String code, Map<String, Object> comp, String grade,
                                          Integer tenureMonths, DecisionRuntimeInput input) {
        if (comp.get("bpsByGrade") instanceof Map<?, ?> byGrade) {
            Object v = byGrade.get(grade);
            if (v == null) {
                v = byGrade.get(grade.toUpperCase(Locale.ROOT));
            }
            if (v == null) {
                v = byGrade.get("DATA_INSUFFICIENT");
            }
            return DecisionValueHelper.bd(v != null ? v : 0);
        }
        if ("TENURE_PREMIUM".equals(code)) {
            BigDecimal perYear = DecisionValueHelper.bd(comp.getOrDefault("bpsPerYearOver12", 25));
            int months = tenureMonths == null ? 12 : tenureMonths;
            int yearsOver = Math.max(0, (months - 12 + 11) / 12); // full years over 12
            if (months <= 12) {
                return BigDecimal.ZERO;
            }
            // years over 12: e.g. 18m -> 1 year over? Spec says bpsPerYearOver12
            BigDecimal years = BigDecimal.valueOf(months - 12).divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);
            return perYear.multiply(years).setScale(4, RoundingMode.HALF_UP);
        }
        if ("STRONG_COLLATERAL_DISCOUNT".equals(code) || code.contains("DISCOUNT")) {
            BigDecimal bps = DecisionValueHelper.bd(comp.getOrDefault("bps", 0));
            return bps == null ? BigDecimal.ZERO : bps.negate().abs().negate();
        }
        return DecisionValueHelper.bd(comp.getOrDefault("bps", 0));
    }

    private String basisFor(String code, String grade, Integer tenure) {
        return switch (code) {
            case "RISK_GRADE_PREMIUM" -> "grade=" + grade;
            case "TENURE_PREMIUM" -> "tenureMonths=" + tenure;
            default -> code;
        };
    }
}
