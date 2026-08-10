package com.los.core.creditintelligence.decision.service;

import com.los.core.creditintelligence.decision.domain.DecisionRuntimeInput;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuthorityMatrixEngine {

    public record AuthorityResult(
            String level,
            String role,
            int approverCount,
            boolean makerCheckerRequired,
            Map<String, Object> detail,
            List<String> reasonCodes
    ) {}

    public AuthorityResult compute(Map<String, Object> authorityStrategy,
                                   DecisionRuntimeInput input,
                                   BigDecimal recommendedAmount,
                                   int deviationCount,
                                   boolean materialException) {
        Map<String, Object> s = authorityStrategy == null ? Map.of() : authorityStrategy;
        String escalation = DecisionValueHelper.str(s.getOrDefault("exceptionEscalation", "CREDIT_COMMITTEE"));
        String grade = DecisionValueHelper.str(input.scoreResult().getOrDefault("grade", "C"));

        if (materialException) {
            return result(escalation, "CREDIT_COMMITTEE", 2, true,
                    List.of("AUTHORITY_EXCEPTION_ESCALATION"),
                    Map.of("reason", "MATERIAL_EXCEPTION", "escalation", escalation));
        }

        BigDecimal amount = recommendedAmount == null ? BigDecimal.ZERO : recommendedAmount;
        List<Map<String, Object>> bands = new ArrayList<>();
        if (s.get("bands") instanceof List<?> l) {
            for (Object item : l) {
                if (item instanceof Map<?, ?> m) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> band = (Map<String, Object>) m;
                    bands.add(band);
                }
            }
        }

        for (Map<String, Object> band : bands) {
            BigDecimal maxAmount = DecisionValueHelper.bd(band.get("maxAmount"));
            if (maxAmount == null) continue;
            if (amount.compareTo(maxAmount) > 0) continue;

            Object gradesObj = band.get("grades");
            if (gradesObj instanceof List<?> grades) {
                boolean ok = grades.stream().map(String::valueOf).anyMatch(g -> g.equalsIgnoreCase(grade));
                if (!ok) continue;
            }
            int maxDev = band.get("maxDeviations") instanceof Number n ? n.intValue() : Integer.MAX_VALUE;
            if (deviationCount > maxDev) continue;

            String level = String.valueOf(band.getOrDefault("level", "CREDIT_MANAGER_L1"));
            String role = String.valueOf(band.getOrDefault("role", "CREDIT_MANAGER"));
            int count = band.get("approverCount") instanceof Number n ? n.intValue() : 1;
            boolean mc = Boolean.TRUE.equals(band.get("makerCheckerRequired"));
            Map<String, Object> detail = new LinkedHashMap<>(band);
            detail.put("matchedAmount", amount);
            detail.put("grade", grade);
            detail.put("deviationCount", deviationCount);
            return result(level, role, count, mc, List.of("AUTHORITY_BAND_MATCH"), detail);
        }

        // fallback last band without grade filter
        for (Map<String, Object> band : bands) {
            BigDecimal maxAmount = DecisionValueHelper.bd(band.get("maxAmount"));
            if (maxAmount == null || amount.compareTo(maxAmount) > 0) continue;
            if (band.containsKey("grades")) continue; // already tried grade-specific
            String level = String.valueOf(band.getOrDefault("level", "CREDIT_MANAGER_L2"));
            String role = String.valueOf(band.getOrDefault("role", "CREDIT_MANAGER"));
            int count = band.get("approverCount") instanceof Number n ? n.intValue() : 1;
            boolean mc = Boolean.TRUE.equals(band.get("makerCheckerRequired"));
            return result(level, role, count, mc, List.of("AUTHORITY_AMOUNT_BAND"), band);
        }

        return result(escalation, "CREDIT_COMMITTEE", 2, true,
                List.of("AUTHORITY_DEFAULT_ESCALATION"), Map.of("reason", "NO_BAND_MATCH"));
    }

    private AuthorityResult result(String level, String role, int count, boolean mc,
                                   List<String> reasons, Map<String, Object> detail) {
        Map<String, Object> d = new LinkedHashMap<>(detail);
        d.put("requiredAuthorityLevel", level);
        d.put("requiredApproverRole", role);
        d.put("requiredApproverCount", count);
        d.put("makerCheckerRequired", mc);
        return new AuthorityResult(level, role, count, mc, d, reasons);
    }
}
