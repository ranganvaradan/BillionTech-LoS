package com.los.core.creditintelligence.evaluation;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UnderwritingRuleSet;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import com.los.core.service.underwriting.MultiRuleEvalResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reconstructs transient underwriting artifacts from frozen {@code CiPolicyVersion.policyContent}
 * and evaluates without live DB rule lookups.
 */
@Service
@RequiredArgsConstructor
public class FrozenPolicyExecutionAdapter {

    private final FrozenUnderwritingRuleEngine frozenUnderwritingRuleEngine;

    @SuppressWarnings("unchecked")
    public List<UnderwritingRuleSet> reconstructRuleSets(Map<String, Object> policyContent) {
        List<UnderwritingRuleSet> out = new ArrayList<>();
        if (policyContent == null) {
            return out;
        }
        Object raw = policyContent.get("ruleSets");
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> map = (Map<String, Object>) m;
            UnderwritingRuleSet rs = UnderwritingRuleSet.builder()
                    .id(parseUuid(map.get("id")))
                    .name(str(map.get("name")))
                    .borrowerType(str(map.get("borrowerType")))
                    .loanProduct(str(map.get("loanProduct")))
                    .minAmount(toBd(map.get("minAmount")))
                    .maxAmount(toBd(map.get("maxAmount")))
                    .geography(asStringObjectMap(map.get("geography")))
                    .minTenureMonths(intOrNull(map.get("minTenureMonths")))
                    .maxTenureMonths(intOrNull(map.get("maxTenureMonths")))
                    .priority(intOrNull(map.get("priority")) != null ? intOrNull(map.get("priority")) : 0)
                    .active(boolOrDefault(map.get("active"), true))
                    .rulesJson(asStringObjectMap(map.get("rulesJson")))
                    .build();
            out.add(rs);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public List<UnderwritingScorecard> reconstructScorecards(Map<String, Object> policyContent) {
        List<UnderwritingScorecard> out = new ArrayList<>();
        if (policyContent == null) {
            return out;
        }
        Object raw = policyContent.get("scorecards");
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> map = (Map<String, Object>) m;
            UnderwritingScorecard sc = UnderwritingScorecard.builder()
                    .id(parseUuid(map.get("id")))
                    .name(str(map.get("name")))
                    .borrowerType(str(map.get("borrowerType")))
                    .loanProduct(str(map.get("loanProduct")))
                    .version(intOrNull(map.get("version")) != null ? intOrNull(map.get("version")) : 1)
                    .priority(intOrNull(map.get("priority")) != null ? intOrNull(map.get("priority")) : 0)
                    .minAmount(toBd(map.get("minAmount")))
                    .maxAmount(toBd(map.get("maxAmount")))
                    .geography(asStringObjectMap(map.get("geography")))
                    .scorecardJson(asStringObjectMap(map.get("scorecardJson")))
                    .thresholdsJson(asStringObjectMap(map.get("thresholdsJson")))
                    .hardRulesJson(asStringObjectMap(map.get("hardRulesJson")))
                    .active(boolOrDefault(map.get("active"), true))
                    .build();
            out.add(sc);
        }
        return out;
    }

    public MultiRuleEvalResult evaluateHardRules(
            LoanApplication stub,
            EffectiveUnderwritingContext ctx,
            String kyc,
            Map<String, Object> policyContent) {
        List<UnderwritingRuleSet> ruleSets = reconstructRuleSets(policyContent);
        return evaluateAll(stub, ctx, kyc, ruleSets);
    }

    public MultiRuleEvalResult evaluateAll(
            LoanApplication app,
            EffectiveUnderwritingContext ctx,
            String kyc,
            List<UnderwritingRuleSet> ruleSets) {
        return frozenUnderwritingRuleEngine.evaluateAll(app, ctx, kyc, ruleSets);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringObjectMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() != null) {
                    out.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            return out;
        }
        return Map.of();
    }

    private static UUID parseUuid(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return UUID.fromString(o.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static Integer intOrNull(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean boolOrDefault(Object o, boolean def) {
        if (o instanceof Boolean b) {
            return b;
        }
        if (o == null) {
            return def;
        }
        return Boolean.parseBoolean(o.toString());
    }

    private static BigDecimal toBd(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal b) {
            return b;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(o.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }
}
