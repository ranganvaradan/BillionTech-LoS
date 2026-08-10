package com.los.core.creditintelligence.evaluation;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UnderwritingRuleSet;
import com.los.core.model.enums.BorrowerType;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import com.los.core.service.underwriting.MultiRuleEvalResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FrozenPolicyExecutionAdapterTest {

    private FrozenPolicyExecutionAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new FrozenPolicyExecutionAdapter(new FrozenUnderwritingRuleEngine());
    }

    @Test
    void reconstructsRulesAndFrozenEvalIgnoresLiveRulesMutation() {
        UUID ruleId = UUID.randomUUID();
        Map<String, Object> originalRulesJson = new LinkedHashMap<>();
        originalRulesJson.put("decision", "APPROVE");
        originalRulesJson.put("minBureauScore", 700);

        Map<String, Object> ruleSetMap = new LinkedHashMap<>();
        ruleSetMap.put("id", ruleId.toString());
        ruleSetMap.put("name", "FrozenApprove");
        ruleSetMap.put("active", true);
        ruleSetMap.put("priority", 1);
        ruleSetMap.put("rulesJson", originalRulesJson);

        Map<String, Object> policyContent = new LinkedHashMap<>();
        policyContent.put("ruleSets", List.of(ruleSetMap));

        List<UnderwritingRuleSet> reconstructed = adapter.reconstructRuleSets(policyContent);
        assertThat(reconstructed).hasSize(1);
        assertThat(reconstructed.get(0).getName()).isEqualTo("FrozenApprove");
        assertThat(reconstructed.get(0).getRulesJson()).containsEntry("decision", "APPROVE");

        LoanApplication app = LoanApplication.builder()
                .id(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PERSONAL_LOAN")
                .requestedAmount(new BigDecimal("200000"))
                .tenureMonths(24)
                .build();
        EffectiveUnderwritingContext ctx = new EffectiveUnderwritingContext(
                750, true, new BigDecimal("50000"), new BigDecimal("10000"),
                "KA", "Bengaluru", "BUREAU", "DECLARED", "KYC", Map.of());

        MultiRuleEvalResult frozen = adapter.evaluateHardRules(app, ctx, "PASS", policyContent);
        assertThat(frozen.aggregatePolicyRecommendation()).isEqualTo("APPROVE");
        assertThat(frozen.aggregateCreditDecision()).isEqualTo("APPROVED");

        // Mutate a "live" copy of rulesJson differently — must not affect re-eval from original policyContent
        Map<String, Object> liveRulesJson = new LinkedHashMap<>(originalRulesJson);
        liveRulesJson.put("decision", "REJECT");
        liveRulesJson.put("minBureauScore", 900);
        List<UnderwritingRuleSet> liveSets = new ArrayList<>();
        liveSets.add(UnderwritingRuleSet.builder()
                .id(ruleId)
                .name("LiveMutated")
                .active(true)
                .rulesJson(liveRulesJson)
                .build());

        MultiRuleEvalResult liveEval = adapter.evaluateAll(app, ctx, "PASS", liveSets);
        assertThat(liveEval.aggregatePolicyRecommendation()).isEqualTo("REJECT");

        MultiRuleEvalResult frozenAgain = adapter.evaluateHardRules(app, ctx, "PASS", policyContent);
        assertThat(frozenAgain.aggregatePolicyRecommendation()).isEqualTo("APPROVE");
        assertThat(frozenAgain.aggregateCreditDecision()).isEqualTo("APPROVED");
        assertThat(frozenAgain.aggregatePolicyRecommendation())
                .isEqualTo(frozen.aggregatePolicyRecommendation());
    }
}
