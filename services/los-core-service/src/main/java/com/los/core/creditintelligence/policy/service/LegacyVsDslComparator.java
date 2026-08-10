package com.los.core.creditintelligence.policy.service;

import com.los.core.creditintelligence.policy.domain.ComparisonClass;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class LegacyVsDslComparator {

    public Map<String, Object> compare(String legacyOutcome, String dslOutcome, Map<String, Object> context) {
        Map<String, Object> out = new LinkedHashMap<>();
        ComparisonClass cls = classify(legacyOutcome, dslOutcome, context == null ? Map.of() : context);
        out.put("legacyOutcome", legacyOutcome);
        out.put("dslOutcome", dslOutcome);
        out.put("comparisonClass", cls.name());
        out.put("context", context == null ? Map.of() : context);
        return out;
    }

    public ComparisonClass classify(String legacyOutcome, String dslOutcome, Map<String, Object> context) {
        if (legacyOutcome != null && legacyOutcome.equals(dslOutcome)) {
            return ComparisonClass.MATCH;
        }
        if (Boolean.TRUE.equals(context.get("legacyUsedDefault"))) {
            return ComparisonClass.LEGACY_DEFAULT_DEPENDENT;
        }
        if ("DATA_INSUFFICIENT".equals(dslOutcome) && !"DATA_INSUFFICIENT".equals(legacyOutcome)) {
            return ComparisonClass.CANONICAL_DATA_INSUFFICIENT;
        }
        if (Boolean.TRUE.equals(context.get("policyMappingDifference"))) {
            return ComparisonClass.POLICY_MAPPING_DIFFERENCE;
        }
        if (isStricter(dslOutcome, legacyOutcome)) {
            return ComparisonClass.DSL_STRICTER;
        }
        if (isStricter(legacyOutcome, dslOutcome)) {
            return ComparisonClass.DSL_MORE_PERMISSIVE;
        }
        return ComparisonClass.LEGACY_SEMANTIC_DIFFERENCE;
    }

    private boolean isStricter(String a, String b) {
        return rank(a) > rank(b);
    }

    private int rank(String o) {
        if (o == null) {
            return 0;
        }
        return switch (o) {
            case "PASS" -> 1;
            case "REFER", "DATA_INSUFFICIENT", "NOT_APPLICABLE" -> 2;
            case "FAIL", "ERROR" -> 3;
            default -> 0;
        };
    }
}
