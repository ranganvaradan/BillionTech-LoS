package com.los.core.service.underwriting;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UnderwritingRuleSet;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.KycStepResultRepository;
import com.los.core.repository.UnderwritingRuleSetRepository;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import com.los.core.service.underwriting.MultiRuleEvalResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnderwritingRuleEngineTest {

    @Mock
    private UnderwritingRuleSetRepository ruleSetRepository;
    @Mock
    private KycStepResultRepository kycStepResultRepository;

    private UnderwritingRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new UnderwritingRuleEngine(ruleSetRepository, kycStepResultRepository);
    }

    @Test
    void matchesBorrowerAndProduct_highestPriorityWins() {
        UUID lowId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        UUID highId = UUID.fromString("b0000000-0000-0000-0000-000000000002");
        UnderwritingRuleSet low = rule("Low", lowId, 10, Map.of(
                "minBureauScore", 600,
                "decision", "MANUAL_REVIEW"));
        UnderwritingRuleSet high = rule("High", highId, 100, Map.of(
                "minBureauScore", 600,
                "decision", "APPROVE",
                "reasons", List.of("ok")));
        when(ruleSetRepository.findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(
                eq("INDIVIDUAL"), eq("PERSONAL_LOAN")))
                .thenReturn(List.of(high, low));
        when(ruleSetRepository.findById(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            if (id.equals(highId)) {
                return Optional.of(high);
            }
            if (id.equals(lowId)) {
                return Optional.of(low);
            }
            return Optional.<UnderwritingRuleSet>empty();
        });

        LoanApplication app = app(BorrowerType.INDIVIDUAL, "PERSONAL_LOAN", new BigDecimal("100000"), 12, 720, null);
        app.setId(UUID.fromString("f0000000-0000-0000-0000-000000000011"));
        // Bureau score on application — no KycStepResult lookup

        var r = engine.findAndApply(app, "PASS");
        assertThat(r).isPresent();
        assertThat(r.get().rule().getId()).isEqualTo(highId);
        assertThat(r.get().creditDecision()).isEqualTo("APPROVED");
    }

    @Test
    void amountRange_excludesRule() {
        UnderwritingRuleSet r = rule("Amt", UUID.randomUUID(), 50,
                Map.of("minBureauScore", 500, "decision", "APPROVE"));
        r.setMinAmount(new BigDecimal("200000"));
        r.setMaxAmount(new BigDecimal("500000"));
        when(ruleSetRepository.findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(
                eq("INDIVIDUAL"), eq("PERSONAL_LOAN")))
                .thenReturn(List.of(r));

        LoanApplication app = app(BorrowerType.INDIVIDUAL, "PERSONAL_LOAN", new BigDecimal("50"), 12, 720, null);
        app.setId(UUID.fromString("f0000000-0000-0000-0000-000000000012"));

        assertThat(engine.findAndApply(app, "PASS")).isEmpty();
    }

    @Test
    void noRules_returnsEmpty() {
        when(ruleSetRepository.findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(
                eq("COMPANY"), eq("X")))
                .thenReturn(List.of());
        LoanApplication app = app(BorrowerType.COMPANY, "X", new BigDecimal("1"), 12, 700, null);
        app.setId(UUID.fromString("f0000000-0000-0000-0000-000000000013"));
        assertThat(engine.findAndApply(app, "PASS")).isEmpty();
    }

    @Test
    void scorecard_lte_obligationRatio_approves() {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("parameter", "OBLIGATION_RATIO");
        line.put("weight", 10);
        line.put("mode", "LTE");
        line.put("approveAt", 0.4);
        line.put("manualAt", 0.6);
        UUID ruleId = UUID.fromString("c0000000-0000-0000-0000-000000000099");
        UnderwritingRuleSet r = rule("Sc", ruleId, 50, Map.of(
                "minBureauScore", 500,
                "scorecardRules", List.of(line)));
        when(ruleSetRepository.findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(
                eq("INDIVIDUAL"), eq("PERSONAL_LOAN")))
                .thenReturn(List.of(r));

        LoanApplication app = app(BorrowerType.INDIVIDUAL, "PERSONAL_LOAN", new BigDecimal("100000"), 12, 720, null);
        app.setId(UUID.fromString("f0000000-0000-0000-0000-000000000020"));
        Map<String, BigDecimal> sc = Map.of("OBLIGATION_RATIO", new BigDecimal("0.2"));
        var ctx = new EffectiveUnderwritingContext(
                720, true, new BigDecimal("100000"), new BigDecimal("20000"),
                null, null, "PROVIDER", "MANUAL", "PROVIDER", sc);
        MultiRuleEvalResult m = engine.evaluateAll(app, ctx, "PASS");
        assertThat(m.aggregatePolicyRecommendation()).isEqualTo("APPROVE");
    }

    private static UnderwritingRuleSet rule(String name, UUID id, int pri, Map<String, Object> json) {
        UnderwritingRuleSet r = new UnderwritingRuleSet();
        r.setId(id);
        r.setName(name);
        r.setBorrowerType("INDIVIDUAL");
        r.setLoanProduct("PERSONAL_LOAN");
        r.setPriority(pri);
        r.setActive(true);
        r.setRulesJson(json);
        return r;
    }

    private static LoanApplication app(
            BorrowerType bt, String product, BigDecimal amt, int tenure, int bureau, Map<String, Object> pi) {
        return LoanApplication.builder()
                .applicationNumber("T-1")
                .customerId(UUID.randomUUID())
                .borrowerType(bt)
                .loanProduct(product)
                .requestedAmount(amt)
                .tenureMonths(tenure)
                .bureauScore(bureau)
                .personalInfo(pi)
                .build();
    }
}
