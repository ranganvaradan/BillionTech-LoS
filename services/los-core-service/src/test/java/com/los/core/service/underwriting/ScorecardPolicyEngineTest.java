package com.los.core.service.underwriting;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.UnderwritingScorecardRepository;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScorecardPolicyEngineTest {

    @Mock
    private UnderwritingScorecardRepository repository;

    @InjectMocks
    private ScorecardPolicyEngine engine;

    @Test
    void approvesWhenNormalizedMeetsThreshold() {
        UnderwritingScorecard c = baseCard();
        c.setScorecardJson(Map.of("rows", List.of(
                row("1", "BUREAU_SCORE", "BUREAU", "GTE:700", 1, 50),
                row("2", "KYC_PASS", "KYC", "EQ:1", 1, 50)
        )));
        c.setThresholdsJson(Map.of("approveMinPercent", 80, "manualMinPercent", 40));
        c.setHardRulesJson(Map.of("rules", List.of()));
        when(repository.findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(
                        eq("INDIVIDUAL"), eq("PERSONAL_LOAN")))
                .thenReturn(List.of(c));

        LoanApplication app = app(750, true);
        EffectiveUnderwritingContext ctx = new EffectiveUnderwritingContext(
                750, true, null, null, "MH", "Mumbai", "x", "y", "z", Map.of());
        var r = engine.evaluate(app, ctx, "PASS");
        assertTrue(r.isPresent());
        assertEquals("APPROVED", r.get().multi().aggregateCreditDecision());
        assertEquals(100, r.get().multi().aggregateRiskScore());
        assertTrue(r.get().parameterResults() != null && r.get().parameterResults().size() == 2);
    }

    @Test
    void hardRuleRejectsBeforeScoring() {
        UnderwritingScorecard c = baseCard();
        c.setScorecardJson(Map.of("rows", List.of(row("1", "BUREAU_SCORE", "BUREAU", "GTE:1", 1, 100))));
        c.setThresholdsJson(Map.of("approveMinPercent", 0, "manualMinPercent", 0));
        c.setHardRulesJson(
                Map.of("rules", List.of(Map.of("parameter", "BUREAU_SCORE", "source", "BUREAU", "condition", "LT:600", "decision", "REJECT", "message", "nope"))));
        when(repository.findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(
                        eq("INDIVIDUAL"), eq("PERSONAL_LOAN")))
                .thenReturn(List.of(c));

        LoanApplication app = app(500, true);
        EffectiveUnderwritingContext ctx = new EffectiveUnderwritingContext(
                500, true, null, null, "MH", "Mumbai", "x", "y", "z", Map.of());
        var r = engine.evaluate(app, ctx, "PASS");
        assertTrue(r.isPresent());
        assertEquals("REJECTED", r.get().multi().aggregateCreditDecision());
    }

    private static Map<String, Object> row(
            String id, String p, String src, String cond, int w, int score) {
        return Map.of("id", id, "parameter", p, "source", src, "condition", cond, "weight", w, "score", score);
    }

    private static UnderwritingScorecard baseCard() {
        return UnderwritingScorecard.builder()
                .id(UUID.randomUUID())
                .name("T")
                .borrowerType("INDIVIDUAL")
                .loanProduct("PERSONAL_LOAN")
                .version(1)
                .priority(1)
                .active(true)
                .build();
    }

    private static LoanApplication app(int bureau, boolean kyc) {
        LoanApplication a = new LoanApplication();
        a.setStatus(ApplicationStatus.UNDERWRITING);
        a.setBorrowerType(BorrowerType.INDIVIDUAL);
        a.setLoanProduct("PERSONAL_LOAN");
        a.setBureauScore(bureau);
        a.setRequestedAmount(new BigDecimal("100000"));
        a.setTenureMonths(12);
        a.setPersonalInfo(new java.util.HashMap<>(Map.of("state", "MH", "city", "Mumbai")));
        return a;
    }
}
