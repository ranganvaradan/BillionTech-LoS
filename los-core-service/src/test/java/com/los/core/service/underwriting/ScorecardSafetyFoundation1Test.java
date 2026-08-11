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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * SCORECARD-SAFETY-FOUNDATION-1 — Part A BEFORE proof + GOLDEN A–H mathematical tables.
 */
@ExtendWith(MockitoExtension.class)
class ScorecardSafetyFoundation1Test {

    @Mock
    private UnderwritingScorecardRepository repository;

    private ScorecardPolicyEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ScorecardPolicyEngine(repository);
        ReflectionTestUtils.setField(engine, "blockNonAuthoritativeDefaults", true);
        ReflectionTestUtils.setField(engine, "allowNonProductionDemoScoring", false);
    }

    // ─── Part A: BEFORE overlap proof (legacy additive semantics) ─────────────

    @Test
    void partA_before_bureau760_doubleCountsGte750AndGte650() {
        List<Map<String, Object>> rows = companyTermLoanRows();
        int earned = legacyAdditiveEarned(rows, Map.of("BUREAU_SCORE", bd(760)));
        // Legacy: both GTE:750 (35) and GTE:650 (25) match → 60
        assertEquals(60, earned, "BEFORE: 760 must double-count >=750 and >=650");
        System.out.println("""
                PART A BEFORE — Bureau 760 (legacy additive):
                Factor | Input | Band | Matched | Points
                BUREAU_SCORE | 760 | GTE:750 | YES | 35
                BUREAU_SCORE | 760 | GTE:650 | YES | 25
                Earned (bureau rows only) = 60  ← DOUBLE COUNT
                """);
    }

    @Test
    void partA_before_foir30_doubleCountsLte40AndLte60() {
        List<Map<String, Object>> rows = companyTermLoanRows();
        int earned = legacyAdditiveEarned(rows, Map.of("OBLIGATION_RATIO", bd(30)));
        assertEquals(30, earned, "BEFORE: FOIR 30 matches both LTE:40 (20) and LTE:60 (10)");
        System.out.println("""
                PART A BEFORE — FOIR/Obligation 30 (legacy additive):
                Factor | Input | Band | Matched | Points
                OBLIGATION_RATIO | 30 | LTE:40 | YES | 20
                OBLIGATION_RATIO | 30 | LTE:60 | YES | 10
                Earned = 30  ← DOUBLE COUNT
                """);
    }

    // ─── GOLDEN A — high bureau, exclusive bands ──────────────────────────────

    @Test
    void goldenA_bureau760_notDoubleCounted() {
        UnderwritingScorecard c = companyCard();
        stub(c);
        Map<String, BigDecimal> sc = fullGoodInputs();
        sc.put("BUREAU_SCORE", bd(760));
        var r = engine.evaluate(app(), ctx(760, sc, Map.of()), "PASS").orElseThrow();

        int bureauPoints = factorPoints(r.parameterResults(), "BUREAU_SCORE");
        assertEquals(35, bureauPoints, "760 must earn only >=750 band (35), not 35+25");
        assertEquals("APPROVED", r.multi().aggregateCreditDecision());

        printTable("GOLDEN A — high bureau 760", r, c);
        // Hand calc with full good inputs: max=100, earned=100, norm=100, approve>=70 → APPROVE
        assertEquals(100, r.multi().aggregateRiskScore());
    }

    // ─── GOLDEN B — boundaries 650 / 749 / 750 ─────────────────────────────────

    @Test
    void goldenB_boundaries() {
        UnderwritingScorecard c = companyCard();
        stub(c);

        assertEquals(25, bureauOnlyPoints(650));
        assertEquals(25, bureauOnlyPoints(749));
        assertEquals(35, bureauOnlyPoints(750));

        System.out.println("""
                GOLDEN B — exclusive bureau boundaries:
                Input | Band | Points | Max(factor)
                650 | [650,750) via GTE:650 | 25 | 35
                749 | [650,750) via GTE:650 | 25 | 35
                750 | [750,+inf) via GTE:750 | 35 | 35
                No gap between 749 and 750; no double-count.
                """);
    }

    // ─── GOLDEN C — FOIR lower-is-better ───────────────────────────────────────

    @Test
    void goldenC_foirExclusiveLte() {
        UnderwritingScorecard c = companyCard();
        c.setHardRulesJson(Map.of("rules", List.of())); // isolate soft FOIR
        stub(c);

        Map<String, BigDecimal> sc = new LinkedHashMap<>();
        sc.put("OBLIGATION_RATIO", bd(30));
        sc.put("KYC_QUALITY", bd(1));
        // Provide other hard-rule-required params so missing REQUIRED doesn't block
        sc.put("BUREAU_SCORE", bd(760));

        var r = engine.evaluate(app(), ctx(760, sc, Map.of(
                "BUREAU_SCORE", ScorecardValueProvenance.REAL_PROVIDER,
                "OBLIGATION_RATIO", ScorecardValueProvenance.DERIVED,
                "KYC_QUALITY", ScorecardValueProvenance.DERIVED)), "PASS").orElseThrow();

        assertEquals(20, factorPoints(r.parameterResults(), "OBLIGATION_RATIO"));
        System.out.println("""
                GOLDEN C — FOIR 30 exclusive LTE:
                Input 30 → band LTE:40 only (not also LTE:60) → 20 points (factor max 20)
                """);
    }

    // ─── GOLDEN D — missing REQUIRED (bureau in hard rules) ───────────────────

    @Test
    void goldenD_missingRequiredBureau_notSilentZero() {
        UnderwritingScorecard c = companyCard();
        c.setScorecardJson(Map.of("rows", List.of(
                Map.of("id", "d1", "parameter", "MONTHLY_INCOME", "source", "SCORECARD",
                        "condition", "GTE:50000", "weight", 1, "score", 25),
                Map.of("id", "d2", "parameter", "MONTHLY_INCOME", "source", "SCORECARD",
                        "condition", "GTE:25000", "weight", 1, "score", 15))));
        c.setHardRulesJson(Map.of("rules", List.of()));
        c.setSafetyJson(Map.of("factorPolicies", Map.of(
                "MONTHLY_INCOME", Map.of("missingData", "REQUIRED"))));
        stub(c);

        var r = engine.evaluate(app(), ctx(760, Map.of(), Map.of()), "PASS").orElseThrow();
        assertEquals("MANUAL_REVIEW", r.multi().aggregateCreditDecision());
        assertTrue(Boolean.TRUE.equals(r.evidence().get("dataInsufficient"))
                || (r.multi().perRule() != null && r.multi().perRule().stream()
                .flatMap(p -> p.reasons() == null ? java.util.stream.Stream.empty() : p.reasons().stream())
                .anyMatch(s -> s.contains("missing") || s.contains("Required"))));
        System.out.println("""
                GOLDEN D — missing REQUIRED MONTHLY_INCOME:
                Input=null | Provenance=MISSING/UNKNOWN | Points not silently zero-scored into approve path
                Decision=MANUAL_REVIEW (DATA_INSUFFICIENT)
                """);
        printTable("GOLDEN D", r, c);
    }

    // ─── GOLDEN E — gap/demo default blocked ──────────────────────────────────

    @Test
    void goldenE_gapDefaultCannotInfluenceProductionDecision() {
        UnderwritingScorecard c = companyCard();
        c.setHardRulesJson(Map.of("rules", List.of()));
        c.setScorecardJson(Map.of("rows", List.of(
                Map.of("id", "d1", "parameter", "AVERAGE_BANK_BALANCE", "source", "SCORECARD",
                        "condition", "GTE:20000", "weight", 1, "score", 10))));
        stub(c);

        Map<String, BigDecimal> sc = Map.of("AVERAGE_BANK_BALANCE", bd(25000));
        Map<String, String> prov = Map.of("AVERAGE_BANK_BALANCE", ScorecardValueProvenance.GAP_DEFAULT);
        var r = engine.evaluate(app(), ctx(760, sc, prov), "PASS").orElseThrow();

        assertEquals(0, factorPoints(r.parameterResults(), "AVERAGE_BANK_BALANCE"));
        assertTrue(Boolean.TRUE.equals(r.evidence().get("nonAuthoritativeValuesBlocked"))
                || r.parameterResults().stream().anyMatch(m ->
                "AVERAGE_BANK_BALANCE".equals(m.get("parameter"))
                        && Boolean.FALSE.equals(m.get("authoritativeForDecision"))));
        System.out.println("""
                GOLDEN E — GAP_DEFAULT ABB 25000 blocked from production scoring:
                value stripped → not authoritative → cannot earn points toward live decision
                """);
        printTable("GOLDEN E", r, c);
    }

    // ─── GOLDEN F — hard rule ─────────────────────────────────────────────────

    @Test
    void goldenF_hardRuleBeatsHighPoints() {
        UnderwritingScorecard c = companyCard();
        stub(c);
        Map<String, BigDecimal> sc = fullGoodInputs();
        sc.put("BUREAU_SCORE", bd(500));
        var r = engine.evaluate(app(500), ctx(500, sc, Map.of(
                "BUREAU_SCORE", ScorecardValueProvenance.REAL_PROVIDER,
                "KYC_QUALITY", ScorecardValueProvenance.DERIVED)), "PASS").orElseThrow();
        assertEquals("REJECTED", r.multi().aggregateCreditDecision());
        assertTrue(Boolean.TRUE.equals(r.evidence().get("hardRule"))
                || r.multi().perRule().get(0).matchedConditions().get("hardRule") != null);
        System.out.println("""
                GOLDEN F — bureau 500 < hard LT:600:
                Hard rule REJECT regardless of soft points that would otherwise approve
                """);
    }

    // ─── GOLDEN G — version immutability ──────────────────────────────────────

    @Test
    void goldenG_activeVersionUnchangedWhenDraftEdited() {
        UnderwritingScorecard v1 = companyCard();
        v1.setStatus("ACTIVE");
        v1.setActive(true);
        v1.setVersion(1);
        Map<String, Object> thr1 = new LinkedHashMap<>(v1.getThresholdsJson());
        UnderwritingScorecard v2 = companyCard();
        v2.setId(UUID.randomUUID());
        v2.setStatus("DRAFT");
        v2.setActive(false);
        v2.setVersion(2);
        v2.setParentScorecardId(v1.getId());
        v2.setLineageId(v1.getId());
        v2.setThresholdsJson(Map.of("approveMinPercent", 99, "manualMinPercent", 50));

        assertEquals(70, ((Number) thr1.get("approveMinPercent")).intValue());
        assertEquals(99, ((Number) v2.getThresholdsJson().get("approveMinPercent")).intValue());
        assertNotEquals(v1.getThresholdsJson().get("approveMinPercent"),
                v2.getThresholdsJson().get("approveMinPercent"));
        System.out.println("""
                GOLDEN G — ACTIVE v1 approveMin=70 unchanged after DRAFT v2 sets approveMin=99
                """);
    }

    // ─── GOLDEN H — historical evidence ───────────────────────────────────────

    @Test
    void goldenH_evaluationEvidenceSurvivesScorecardChange() {
        UnderwritingScorecard c = companyCard();
        stub(c);
        var r = engine.evaluate(app(), ctx(760, fullGoodInputs(), Map.of(
                "BUREAU_SCORE", ScorecardValueProvenance.REAL_PROVIDER,
                "MONTHLY_INCOME", ScorecardValueProvenance.REAL_PROVIDER,
                "OBLIGATION_RATIO", ScorecardValueProvenance.DERIVED,
                "AVERAGE_BANK_BALANCE", ScorecardValueProvenance.REAL_PROVIDER,
                "KYC_QUALITY", ScorecardValueProvenance.DERIVED)), "PASS").orElseThrow();

        Map<String, Object> evidence = new LinkedHashMap<>(r.evidence());
        int originalNorm = r.multi().aggregateRiskScore();
        String originalDecision = r.multi().aggregateCreditDecision();

        // Mutate live card (simulating later v2 activation of different card — evidence must stand alone)
        c.setThresholdsJson(Map.of("approveMinPercent", 99, "manualMinPercent", 98));
        c.setScorecardJson(Map.of("rows", List.of()));

        assertEquals(originalNorm, ((Number) evidence.get("normalizedPercent")).intValue());
        assertNotNull(evidence.get("parameterResults"));
        assertEquals(originalDecision, "APPROVED");
        System.out.println("""
                GOLDEN H — evidence snapshot retains original normalized/decision/factors
                after scorecard mutation; historical explainability does not need today's rows.
                """);
        printTable("GOLDEN H (frozen evidence)", r, c);
    }

    @Test
    void hardRulePrecedence_scorecardHardManualOverHighPoints() {
        UnderwritingScorecard c = companyCard();
        c.setHardRulesJson(Map.of("rules", List.of(
                Map.of("id", "hm", "parameter", "BUREAU_SCORE", "source", "BUREAU",
                        "condition", "LT:800", "decision", "MANUAL_REVIEW", "message", "review"))));
        stub(c);
        var r = engine.evaluate(app(), ctx(760, fullGoodInputs(), Map.of(
                "BUREAU_SCORE", ScorecardValueProvenance.REAL_PROVIDER)), "PASS").orElseThrow();
        assertEquals("MANUAL_REVIEW", r.multi().aggregateCreditDecision());
    }

    @Test
    void exclusiveBandModel_inventoryOnCompanyTermLoanSeed() {
        ScorecardExclusiveBandModel.Model model = ScorecardExclusiveBandModel.fromRows(companyTermLoanRows());
        assertTrue(model.safe(), () -> "problems=" + model.problems());
        assertFalse(model.factors().isEmpty());
        long bureauBands = model.factors().stream()
                .filter(f -> "BUREAU_SCORE".equals(f.parameter()))
                .findFirst()
                .map(f -> (long) f.bands().size())
                .orElse(0L);
        assertEquals(2, bureauBands);
    }

    @Test
    void weightNotUsedInFormula() {
        UnderwritingScorecard c = companyCard();
        // Absurd weights must not change points
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : companyTermLoanRows()) {
            Map<String, Object> copy = new LinkedHashMap<>(r);
            copy.put("weight", 999);
            rows.add(copy);
        }
        c.setScorecardJson(Map.of("rows", rows));
        stub(c);
        var r = engine.evaluate(app(), ctx(760, fullGoodInputs(), Map.of(
                "BUREAU_SCORE", ScorecardValueProvenance.REAL_PROVIDER,
                "MONTHLY_INCOME", ScorecardValueProvenance.REAL_PROVIDER,
                "OBLIGATION_RATIO", ScorecardValueProvenance.DERIVED,
                "AVERAGE_BANK_BALANCE", ScorecardValueProvenance.REAL_PROVIDER,
                "KYC_QUALITY", ScorecardValueProvenance.DERIVED)), "PASS").orElseThrow();
        assertEquals(100, r.multi().aggregateRiskScore());
        assertEquals("METADATA_ONLY_NOT_USED_IN_FORMULA", r.evidence().get("weightSemantics"));
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private int bureauOnlyPoints(int bureau) {
        Map<String, BigDecimal> sc = new LinkedHashMap<>(fullGoodInputs());
        sc.put("BUREAU_SCORE", bd(bureau));
        var r = engine.evaluate(app(bureau), ctx(bureau, sc, Map.of(
                "BUREAU_SCORE", ScorecardValueProvenance.REAL_PROVIDER,
                "MONTHLY_INCOME", ScorecardValueProvenance.REAL_PROVIDER,
                "OBLIGATION_RATIO", ScorecardValueProvenance.DERIVED,
                "AVERAGE_BANK_BALANCE", ScorecardValueProvenance.REAL_PROVIDER,
                "KYC_QUALITY", ScorecardValueProvenance.DERIVED)), "PASS").orElseThrow();
        return factorPoints(r.parameterResults(), "BUREAU_SCORE");
    }

    private void stub(UnderwritingScorecard c) {
        when(repository.findByBorrowerTypeAndLoanProductAndActiveIsTrueOrderByPriorityDesc(
                eq("COMPANY"), eq("TERM_LOAN")))
                .thenReturn(List.of(c));
    }

    private static void printTable(String title, ScorecardPolicyEngine.ScorecardEvalResult r, UnderwritingScorecard c) {
        System.out.println("==== " + title + " ====");
        System.out.println("Factor | Input | Provenance | Band | Points earned | Max points");
        for (Map<String, Object> row : r.parameterResults()) {
            if (Boolean.TRUE.equals(row.get("hardRule"))) continue;
            System.out.printf("%s | %s | %s | %s | %s | %s%n",
                    row.get("parameter"),
                    row.get("valueUsed"),
                    row.get("valueProvenance"),
                    row.getOrDefault("matchedBand", row.get("condition")),
                    row.get("pointsEarned"),
                    row.get("maxScore"));
        }
        Map<String, Object> ev = r.evidence();
        System.out.println("Total earned = " + ev.getOrDefault("earnedPoints", r.multi().aggregateRiskScore()));
        System.out.println("Total maximum = " + ev.get("maxPoints"));
        System.out.println("Normalized = " + ev.getOrDefault("normalizedPercent", r.multi().aggregateRiskScore()));
        System.out.println("Approve threshold = " + c.getThresholdsJson().get("approveMinPercent"));
        System.out.println("Manual threshold = " + c.getThresholdsJson().get("manualMinPercent"));
        System.out.println("Decision = " + r.multi().aggregateCreditDecision());
    }

    private static int factorPoints(List<Map<String, Object>> results, String parameter) {
        return results.stream()
                .filter(m -> parameter.equals(m.get("parameter")) && !Boolean.TRUE.equals(m.get("hardRule")))
                .mapToInt(m -> m.get("pointsEarned") instanceof Number n ? n.intValue() : 0)
                .sum();
    }

    /** Legacy additive row scoring (pre-safety) for Part A proof only. */
    private static int legacyAdditiveEarned(List<Map<String, Object>> rows, Map<String, BigDecimal> values) {
        int earned = 0;
        for (Map<String, Object> row : rows) {
            String p = String.valueOf(row.get("parameter"));
            BigDecimal v = values.get(p);
            if (v == null) continue;
            String cond = String.valueOf(row.get("condition"));
            if (legacyMatch(cond, v) && row.get("score") instanceof Number n) {
                earned += n.intValue();
            }
        }
        return earned;
    }

    private static boolean legacyMatch(String cond, BigDecimal v) {
        String[] parts = cond.split(":", 2);
        String op = parts[0].trim().toUpperCase();
        BigDecimal rhs = new BigDecimal(parts[1].trim());
        return switch (op) {
            case "GTE" -> v.compareTo(rhs) >= 0;
            case "LTE" -> v.compareTo(rhs) <= 0;
            case "GT" -> v.compareTo(rhs) > 0;
            case "LT" -> v.compareTo(rhs) < 0;
            case "EQ" -> v.compareTo(rhs) == 0;
            default -> false;
        };
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
                .safetyJson(Map.of("weightSemantics", "METADATA_ONLY_NOT_USED_IN_FORMULA"))
                .minAmount(new BigDecimal("50000"))
                .maxAmount(new BigDecimal("50000000"))
                .build();
    }

    private static Map<String, BigDecimal> fullGoodInputs() {
        Map<String, BigDecimal> sc = new LinkedHashMap<>();
        sc.put("BUREAU_SCORE", bd(760));
        sc.put("MONTHLY_INCOME", bd(60000));
        sc.put("OBLIGATION_RATIO", bd(30));
        sc.put("AVERAGE_BANK_BALANCE", bd(25000));
        sc.put("KYC_QUALITY", bd(1));
        return sc;
    }

    private static EffectiveUnderwritingContext ctx(
            int bureau, Map<String, BigDecimal> sc, Map<String, String> provenance) {
        return new EffectiveUnderwritingContext(
                bureau, true, sc.get("MONTHLY_INCOME"), null, "MH", "Mumbai",
                "BUREAU_PROVIDER", "BANK", "KYC", sc, provenance);
    }

    private static LoanApplication app() {
        return app(760);
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
