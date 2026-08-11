package com.los.core.service.underwriting;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.UnderwritingScorecardRepository;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * SCORECARD-SAFETY-CLOSURE-1 — denominator, missing-data semantics, hard-rule missing, provenance.
 */
@ExtendWith(MockitoExtension.class)
class ScorecardSafetyClosure1Test {

    @Mock
    UnderwritingScorecardRepository repository;

    ScorecardPolicyEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ScorecardPolicyEngine(repository);
        ReflectionTestUtils.setField(engine, "blockNonAuthoritativeDefaults", true);
        ReflectionTestUtils.setField(engine, "allowNonProductionDemoScoring", false);
    }

    @Test
    void denominator_factorMaxIsMaxNotSumOfAlternativeBands() {
        ScorecardExclusiveBandModel.Model model =
                ScorecardExclusiveBandModel.fromRows(companyTermLoanRows());
        assertTrue(model.safe());
        Map<String, Integer> factorMax = new LinkedHashMap<>();
        for (var f : model.factors()) {
            if (ScorecardExclusiveBandModel.MODE_EXCLUSIVE.equals(f.mode())) {
                factorMax.put(f.parameter(), f.maxPoints());
                int sum = f.bands().stream().mapToInt(ScorecardExclusiveBandModel.ExclusiveBand::score).sum();
                int max = f.bands().stream().mapToInt(ScorecardExclusiveBandModel.ExclusiveBand::score).max().orElse(0);
                assertEquals(max, f.maxPoints(), f.parameter() + " factorMax must equal MAX(bands)");
                if (f.bands().size() > 1) {
                    assertTrue(sum > max, f.parameter() + " must have SUM>MAX proving exclusivity needed");
                }
            }
        }
        assertEquals(35, factorMax.get("BUREAU_SCORE"));
        assertEquals(25, factorMax.get("MONTHLY_INCOME"));
        assertEquals(20, factorMax.get("OBLIGATION_RATIO"));
        assertEquals(10, factorMax.get("AVERAGE_BANK_BALANCE"));
        assertEquals(10, factorMax.get("KYC_QUALITY"));
        int totalMax = factorMax.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(100, totalMax);
        assertEquals(100, totalMax); // NOT 35+25+20+10+10+25+15+10 = row sum
        int rowSum = companyTermLoanRows().stream()
                .mapToInt(r -> ((Number) r.get("score")).intValue()).sum();
        assertEquals(150, rowSum, "row-sum of alternatives is 150; denominator must use 100");
        assertTrue(totalMax < rowSum);
    }

    @Test
    void goldenPartial_bureau650_foir50_earns80of100() {
        UnderwritingScorecard c = companyCard();
        stub(c);
        Map<String, BigDecimal> sc = new LinkedHashMap<>();
        sc.put("BUREAU_SCORE", bd(650));
        sc.put("MONTHLY_INCOME", bd(60000));
        sc.put("OBLIGATION_RATIO", bd(50));
        sc.put("AVERAGE_BANK_BALANCE", bd(25000));
        sc.put("KYC_QUALITY", bd(1));
        Map<String, String> prov = Map.of(
                "BUREAU_SCORE", ScorecardValueProvenance.REAL_PROVIDER,
                "MONTHLY_INCOME", ScorecardValueProvenance.REAL_PROVIDER,
                "OBLIGATION_RATIO", ScorecardValueProvenance.DERIVED,
                "AVERAGE_BANK_BALANCE", ScorecardValueProvenance.REAL_PROVIDER,
                "KYC_QUALITY", ScorecardValueProvenance.DERIVED);

        var r = engine.evaluate(app(650), ctx(650, "BUREAU_PROVIDER", sc, prov), "PASS").orElseThrow();

        assertEquals(25, pts(r, "BUREAU_SCORE"));
        assertEquals(35, max(r, "BUREAU_SCORE"));
        assertEquals(25, pts(r, "MONTHLY_INCOME"));
        assertEquals(25, max(r, "MONTHLY_INCOME"));
        assertEquals(10, pts(r, "OBLIGATION_RATIO"));
        assertEquals(20, max(r, "OBLIGATION_RATIO"));
        assertEquals(10, pts(r, "AVERAGE_BANK_BALANCE"));
        assertEquals(10, max(r, "KYC_QUALITY"));

        assertEquals(80, ((Number) r.evidence().get("earnedPoints")).intValue());
        assertEquals(100, ((Number) r.evidence().get("maxPoints")).intValue());
        assertEquals(80, r.multi().aggregateRiskScore());
        assertEquals("APPROVED", r.multi().aggregateCreditDecision());

        System.out.println("""
                CLOSURE PARTIAL GOLDEN
                Factor | Earned | Max
                BUREAU_SCORE | 25 | 35
                MONTHLY_INCOME | 25 | 25
                OBLIGATION_RATIO | 10 | 20
                AVERAGE_BANK_BALANCE | 10 | 10
                KYC_QUALITY | 10 | 10
                Earned=80 Maximum=100 Normalized=80% Decision=APPROVED
                """);
    }

    @Test
    void optionalDepress_missingFactorKeepsMaxInDenominator() {
        UnderwritingScorecard c = companyCard();
        c.setHardRulesJson(Map.of("rules", List.of()));
        // Earn 80/100 when ABB(20) missing with OPTIONAL_DEPRESS
        c.setScorecardJson(Map.of("rows", List.of(
                Map.of("id", "a", "parameter", "BUREAU_SCORE", "source", "BUREAU",
                        "condition", "GTE:700", "weight", 1, "score", 40),
                Map.of("id", "b", "parameter", "MONTHLY_INCOME", "source", "SCORECARD",
                        "condition", "GTE:50000", "weight", 1, "score", 40),
                Map.of("id", "c", "parameter", "AVERAGE_BANK_BALANCE", "source", "SCORECARD",
                        "condition", "GTE:20000", "weight", 1, "score", 20))));
        c.setSafetyJson(Map.of(
                "factorPolicies", Map.of(
                        "BUREAU_SCORE", Map.of("missingData", "OPTIONAL_DEPRESS"),
                        "MONTHLY_INCOME", Map.of("missingData", "OPTIONAL_DEPRESS"),
                        "AVERAGE_BANK_BALANCE", Map.of("missingData", "OPTIONAL_DEPRESS")),
                "missingDataPoliciesConfirmed", true));
        stub(c);

        Map<String, BigDecimal> sc = Map.of(
                "BUREAU_SCORE", bd(750),
                "MONTHLY_INCOME", bd(60000));
        // ABB missing
        Map<String, String> prov = Map.of(
                "BUREAU_SCORE", ScorecardValueProvenance.REAL_PROVIDER,
                "MONTHLY_INCOME", ScorecardValueProvenance.REAL_PROVIDER,
                "AVERAGE_BANK_BALANCE", ScorecardValueProvenance.MISSING);
        var r = engine.evaluate(app(750), ctx(750, "BUREAU_PROVIDER", sc, prov), "PASS").orElseThrow();
        assertEquals(80, ((Number) r.evidence().get("earnedPoints")).intValue());
        assertEquals(100, ((Number) r.evidence().get("maxPoints")).intValue());
        assertEquals(80, r.multi().aggregateRiskScore());
        // 80/100 = 80%
    }

    @Test
    void optionalSkip_missingFactorRemovedFromDenominator() {
        UnderwritingScorecard c = companyCard();
        c.setHardRulesJson(Map.of("rules", List.of()));
        c.setScorecardJson(Map.of("rows", List.of(
                Map.of("id", "a", "parameter", "BUREAU_SCORE", "source", "BUREAU",
                        "condition", "GTE:700", "weight", 1, "score", 40),
                Map.of("id", "b", "parameter", "MONTHLY_INCOME", "source", "SCORECARD",
                        "condition", "GTE:50000", "weight", 1, "score", 40),
                Map.of("id", "c", "parameter", "AVERAGE_BANK_BALANCE", "source", "SCORECARD",
                        "condition", "GTE:20000", "weight", 1, "score", 20))));
        c.setSafetyJson(Map.of(
                "factorPolicies", Map.of(
                        "BUREAU_SCORE", Map.of("missingData", "OPTIONAL_SKIP"),
                        "MONTHLY_INCOME", Map.of("missingData", "OPTIONAL_SKIP"),
                        "AVERAGE_BANK_BALANCE", Map.of("missingData", "OPTIONAL_SKIP")),
                "missingDataPoliciesConfirmed", true));
        stub(c);

        Map<String, BigDecimal> sc = Map.of(
                "BUREAU_SCORE", bd(750),
                "MONTHLY_INCOME", bd(60000));
        Map<String, String> prov = Map.of(
                "BUREAU_SCORE", ScorecardValueProvenance.REAL_PROVIDER,
                "MONTHLY_INCOME", ScorecardValueProvenance.REAL_PROVIDER,
                "AVERAGE_BANK_BALANCE", ScorecardValueProvenance.MISSING);
        var r = engine.evaluate(app(750), ctx(750, "BUREAU_PROVIDER", sc, prov), "PASS").orElseThrow();
        assertEquals(80, ((Number) r.evidence().get("earnedPoints")).intValue());
        assertEquals(80, ((Number) r.evidence().get("maxPoints")).intValue());
        // 80/80 = 100%; HALF_UP integer
        assertEquals(100, r.multi().aggregateRiskScore());
        // Document 70/80 case separately
        int earned = 70;
        int max = 80;
        int normalized = BigDecimal.valueOf(100L * earned)
                .divide(BigDecimal.valueOf(max), 0, RoundingMode.HALF_UP)
                .intValue();
        assertEquals(88, normalized, "70/80 → 87.5 HALF_UP → 88");
    }

    @Test
    void requiredMissing_dataInsufficientNotSilentZero() {
        UnderwritingScorecard c = companyCard();
        c.setHardRulesJson(Map.of("rules", List.of()));
        c.setScorecardJson(Map.of("rows", List.of(
                Map.of("id", "a", "parameter", "MONTHLY_INCOME", "source", "SCORECARD",
                        "condition", "GTE:50000", "weight", 1, "score", 40),
                Map.of("id", "b", "parameter", "BUREAU_SCORE", "source", "BUREAU",
                        "condition", "GTE:700", "weight", 1, "score", 60))));
        c.setSafetyJson(Map.of("factorPolicies", Map.of(
                "MONTHLY_INCOME", Map.of("missingData", "REQUIRED"),
                "BUREAU_SCORE", Map.of("missingData", "OPTIONAL_SKIP"))));
        stub(c);
        Map<String, BigDecimal> sc = Map.of("BUREAU_SCORE", bd(750));
        var r = engine.evaluate(app(750), ctx(750, "BUREAU_PROVIDER", sc, Map.of(
                "BUREAU_SCORE", ScorecardValueProvenance.REAL_PROVIDER,
                "MONTHLY_INCOME", ScorecardValueProvenance.MISSING)), "PASS").orElseThrow();
        assertEquals("MANUAL_REVIEW", r.multi().aggregateCreditDecision());
        assertTrue(Boolean.TRUE.equals(r.evidence().get("dataInsufficient")));
    }

    @Test
    void hardRuleMissingBureau_dataInsufficientNotRejectOrPass() {
        UnderwritingScorecard c = companyCard();
        stub(c);
        // Bureau unavailable: provenance MISSING; must not treat as <600 reject or as pass
        Map<String, BigDecimal> sc = Map.of("KYC_QUALITY", bd(1));
        Map<String, String> prov = Map.of(
                "BUREAU_SCORE", ScorecardValueProvenance.MISSING,
                "KYC_QUALITY", ScorecardValueProvenance.DERIVED);
        var r = engine.evaluate(app(0), ctx(0, null, sc, prov), "PASS").orElseThrow();
        assertEquals("MANUAL_REVIEW", r.multi().aggregateCreditDecision());
        assertTrue(r.multi().perRule().get(0).reasons().stream()
                .anyMatch(s -> s.contains("DATA_INSUFFICIENT")));
        assertFalse("REJECTED".equals(r.multi().aggregateCreditDecision()));
    }

    @Test
    void unknownProvenance_cannotSatisfyRequired() {
        assertFalse(ScorecardValueProvenance.canSatisfyRequired(
                ScorecardValueProvenance.UNKNOWN, false));
        assertFalse(ScorecardValueProvenance.canSatisfyRequired(
                ScorecardValueProvenance.MISSING, false));
        assertFalse(ScorecardValueProvenance.canSatisfyRequired(
                ScorecardValueProvenance.DEMO_DEFAULT, false));
        assertFalse(ScorecardValueProvenance.canSatisfyRequired(
                ScorecardValueProvenance.GAP_DEFAULT, false));
        assertTrue(ScorecardValueProvenance.canSatisfyRequired(
                ScorecardValueProvenance.REAL_PROVIDER, false));
        assertTrue(ScorecardValueProvenance.canSatisfyRequired(
                ScorecardValueProvenance.DEMO_DEFAULT, true));
    }

    @Test
    void activationRequiresExplicitConfirmedMissingPolicies() {
        UnderwritingScorecard legacy = companyCard();
        // legacy ACTIVE has no explicit policies — still historically executable
        var runtime = ScorecardSafetyValidator.validate(legacy);
        assertTrue(runtime.bandsSafe());
        assertTrue(runtime.denominatorSafe());
        assertFalse(runtime.missingPoliciesExplicit());

        Map<String, Object> inherited = ScorecardSafetyValidator.inheritedSafetyForNewVersion(legacy);
        UnderwritingScorecard draft = companyCard();
        draft.setId(UUID.randomUUID());
        draft.setActive(false);
        draft.setStatus("DRAFT");
        draft.setVersion(2);
        draft.setSafetyJson(inherited);
        var beforeConfirm = ScorecardSafetyValidator.validateForActivation(draft);
        assertFalse(beforeConfirm.ok());
        assertTrue(beforeConfirm.missingPoliciesExplicit());
        assertFalse(beforeConfirm.missingPoliciesConfirmed());

        Map<String, Object> confirmed = new LinkedHashMap<>(inherited);
        confirmed.put("missingDataPoliciesConfirmed", true);
        draft.setSafetyJson(confirmed);
        var after = ScorecardSafetyValidator.validateForActivation(draft);
        assertTrue(after.ok(), () -> String.valueOf(after.problems()));
    }

    @Test
    void inventory_companyTermLoanFactorsRecommendClassifications() {
        UnderwritingScorecard c = companyCard();
        var v = ScorecardSafetyValidator.validate(c);
        assertTrue(v.factors().size() >= 5, "company term loan has at least 5 factors");
        long implicit = v.factors().stream().filter(f -> Boolean.TRUE.equals(f.get("implicitLegacy"))).count();
        assertTrue(implicit >= 3, "legacy soft factors are implicit OPTIONAL_DEPRESS");
        long hardRequired = v.factors().stream()
                .filter(f -> Boolean.TRUE.equals(f.get("hardRule")))
                .filter(f -> "REQUIRED".equals(f.get("recommendedMissingData")))
                .count();
        assertTrue(hardRequired >= 2, "KYC + BUREAU hard-rule operands recommend REQUIRED");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void stub(UnderwritingScorecard c) {
        when(repository.findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(
                eq("COMPANY"), eq("TERM_LOAN")))
                .thenReturn(List.of(c));
    }

    private static int pts(ScorecardPolicyEngine.ScorecardEvalResult r, String p) {
        return r.parameterResults().stream()
                .filter(m -> p.equals(m.get("parameter")) && !Boolean.TRUE.equals(m.get("hardRule")))
                .mapToInt(m -> m.get("pointsEarned") instanceof Number n ? n.intValue() : 0)
                .sum();
    }

    private static int max(ScorecardPolicyEngine.ScorecardEvalResult r, String p) {
        return r.parameterResults().stream()
                .filter(m -> p.equals(m.get("parameter")) && !Boolean.TRUE.equals(m.get("hardRule")))
                .mapToInt(m -> m.get("maxScore") instanceof Number n ? n.intValue() : 0)
                .findFirst().orElse(0);
    }

    private static List<Map<String, Object>> companyTermLoanRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(Map.of("id", "d1", "parameter", "BUREAU_SCORE", "source", "BUREAU",
                "condition", "GTE:750", "weight", 1, "score", 35));
        rows.add(Map.of("id", "d2", "parameter", "BUREAU_SCORE", "source", "BUREAU",
                "condition", "GTE:650", "weight", 1, "score", 25));
        rows.add(Map.of("id", "d3", "parameter", "MONTHLY_INCOME", "source", "MANUAL_OR_PROVIDER",
                "condition", "GTE:50000", "weight", 1, "score", 25));
        rows.add(Map.of("id", "d4", "parameter", "MONTHLY_INCOME", "source", "MANUAL_OR_PROVIDER",
                "condition", "GTE:25000", "weight", 1, "score", 15));
        rows.add(Map.of("id", "d5", "parameter", "OBLIGATION_RATIO", "source", "SYSTEM",
                "condition", "LTE:40", "weight", 1, "score", 20));
        rows.add(Map.of("id", "d6", "parameter", "OBLIGATION_RATIO", "source", "SYSTEM",
                "condition", "LTE:60", "weight", 1, "score", 10));
        rows.add(Map.of("id", "d7", "parameter", "AVERAGE_BANK_BALANCE", "source", "BANK_STATEMENT",
                "condition", "GTE:20000", "weight", 1, "score", 10));
        rows.add(Map.of("id", "d8", "parameter", "KYC_QUALITY", "source", "SYSTEM",
                "condition", "EQ:PASS", "weight", 1, "score", 10));
        return rows;
    }

    private static UnderwritingScorecard companyCard() {
        return UnderwritingScorecard.builder()
                .id(UUID.fromString("d3320000-0000-4000-a000-000000000029"))
                .name("Default scorecard — COMPANY — TERM_LOAN")
                .borrowerType("COMPANY")
                .loanProduct("TERM_LOAN")
                .version(1)
                .priority(100)
                .active(true)
                .status("ACTIVE")
                .lineageId(UUID.fromString("d3320000-0000-4000-a000-000000000029"))
                .scorecardJson(Map.of("rows", companyTermLoanRows()))
                .thresholdsJson(Map.of("approveMinPercent", 70, "manualMinPercent", 50))
                .hardRulesJson(Map.of("rules", List.of(
                        Map.of("id", "h1", "parameter", "KYC_QUALITY", "source", "SYSTEM",
                                "condition", "NE:PASS", "decision", "REJECT", "message", "KYC fail"),
                        Map.of("id", "h2", "parameter", "BUREAU_SCORE", "source", "BUREAU",
                                "condition", "LT:600", "decision", "REJECT", "message", "Bureau low"))))
                .safetyJson(Map.of())
                .minAmount(new BigDecimal("50000"))
                .maxAmount(new BigDecimal("50000000"))
                .build();
    }

    private static EffectiveUnderwritingContext ctx(
            int bureau, String bureauSource, Map<String, BigDecimal> sc, Map<String, String> provenance) {
        return new EffectiveUnderwritingContext(
                bureau, true, sc.get("MONTHLY_INCOME"), null, "MH", "Mumbai",
                bureauSource, "BANK", "KYC", sc, provenance);
    }

    private static LoanApplication app(int bureau) {
        LoanApplication a = new LoanApplication();
        a.setStatus(ApplicationStatus.UNDERWRITING);
        a.setBorrowerType(BorrowerType.COMPANY);
        a.setLoanProduct("TERM_LOAN");
        a.setBureauScore(bureau);
        a.setRequestedAmount(new BigDecimal("500000"));
        a.setTenureMonths(36);
        a.setPersonalInfo(new LinkedHashMap<>(Map.of("state", "MH", "city", "Mumbai")));
        return a;
    }

    private static BigDecimal bd(int v) {
        return BigDecimal.valueOf(v);
    }
}
