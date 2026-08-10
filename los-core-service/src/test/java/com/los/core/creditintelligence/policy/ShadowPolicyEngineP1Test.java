package com.los.core.creditintelligence.policy;

import com.los.core.creditintelligence.policy.domain.CiExecutablePolicyPackage;
import com.los.core.creditintelligence.policy.domain.CiPolicyEvaluation;
import com.los.core.creditintelligence.policy.domain.ComparisonClass;
import com.los.core.creditintelligence.policy.domain.DataStatus;
import com.los.core.creditintelligence.policy.domain.ExecutablePackageStatus;
import com.los.core.creditintelligence.policy.domain.PolicyEvaluationInput;
import com.los.core.creditintelligence.policy.domain.PolicyRuleType;
import com.los.core.creditintelligence.policy.domain.ResolvedInput;
import com.los.core.creditintelligence.policy.fixture.GoldenShadowPackageFactory;
import com.los.core.creditintelligence.policy.service.DeclarativeOrchestrator;
import com.los.core.creditintelligence.policy.service.LegacyVsDslComparator;
import com.los.core.creditintelligence.policy.service.PolicyEngineEvidenceIntegrator;
import com.los.core.creditintelligence.policy.service.PolicyHistoricalReplayService;
import com.los.core.creditintelligence.policy.service.PolicyImpactAnalysisService;
import com.los.core.creditintelligence.policy.service.PolicyInputResolver;
import com.los.core.creditintelligence.policy.service.PolicyPackagePublisher;
import com.los.core.creditintelligence.policy.service.PolicyRegistryMetricService;
import com.los.core.creditintelligence.policy.service.PolicyReplayService;
import com.los.core.creditintelligence.policy.service.ScorecardDefinitionExecutor;
import com.los.core.creditintelligence.policy.service.ShadowPolicyEngine;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDraftPackage;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShadowPolicyEngineP1Test {

    private ShadowPolicyEngine engine;
    private PolicyDslInterpreterV1 interpreter;
    private PolicyInputResolver resolver;
    private UUID tenant;

    @BeforeEach
    void setUp() {
        engine = new ShadowPolicyEngine();
        interpreter = new PolicyDslInterpreterV1();
        resolver = new PolicyInputResolver();
        tenant = UUID.fromString("00000000-0000-0000-0000-000000000001");
    }

    @Test
    void diTruthTableAndOrCombinations() {
        var ctx = PolicyDslInterpreterV1.EvaluationContext.of(Map.of(), Map.of(), null);
        assertThat(interpreter.evaluate(PolicyDsl.and(Map.of("const", true), Map.of("const", "DATA_INSUFFICIENT")), ctx))
                .isEqualTo("DATA_INSUFFICIENT");
        assertThat(interpreter.evaluate(PolicyDsl.or(Map.of("const", true), Map.of("const", "DATA_INSUFFICIENT")), ctx))
                .isEqualTo("PASS");
        assertThat(interpreter.evaluate(PolicyDsl.or(Map.of("const", false), Map.of("const", "DATA_INSUFFICIENT")), ctx))
                .isEqualTo("DATA_INSUFFICIENT");
        assertThat(interpreter.evaluate(PolicyDsl.and(Map.of("const", false), Map.of("const", "DATA_INSUFFICIENT")), ctx))
                .isEqualTo("FAIL");
        assertThat(interpreter.evaluate(PolicyDsl.and(Map.of("const", "FAIL"), Map.of("const", "ERROR")), ctx))
                .isEqualTo("FAIL");
        assertThat(interpreter.evaluate(PolicyDsl.and(Map.of("const", true), Map.of("const", "ERROR")), ctx))
                .isEqualTo("ERROR");
        assertThat(interpreter.evaluate(PolicyDsl.and(Map.of("const", true), Map.of("const", "NOT_APPLICABLE")), ctx))
                .isEqualTo("PASS");
        assertThat(interpreter.evaluate(PolicyDsl.and(Map.of("const", "NOT_APPLICABLE"), Map.of("const", "NOT_APPLICABLE")), ctx))
                .isEqualTo("NOT_APPLICABLE");
        assertThat(interpreter.evaluate(PolicyDsl.or(Map.of("const", "NOT_APPLICABLE"), Map.of("const", false)), ctx))
                .isEqualTo("FAIL");
    }

    @Test
    void operatorsInBetweenIsMissing() {
        var ctx = PolicyDslInterpreterV1.EvaluationContext.of(
                Map.of("score", 700), Map.of(), null);
        assertThat(interpreter.evaluate(Map.of(
                "op", "BETWEEN", "left", PolicyDsl.metric("score"),
                "low", Map.of("const", 650), "high", Map.of("const", 800)), ctx))
                .isEqualTo("PASS");
        assertThat(interpreter.evaluate(Map.of(
                "op", "IN", "left", PolicyDsl.metric("score"), "set", List.of(600, 700, 800)), ctx))
                .isEqualTo("PASS");
        assertThat(interpreter.evaluate(Map.of(
                "op", "NOT_IN", "left", PolicyDsl.metric("score"), "set", List.of(1, 2)), ctx))
                .isEqualTo("PASS");
        assertThat(interpreter.evaluate(Map.of(
                "op", "IS_MISSING", "arg", PolicyDsl.metric("missing.path")), ctx))
                .isEqualTo("PASS");
        assertThat(interpreter.evaluate(PolicyDsl.eq(
                Map.of("op", "SUBTRACT", "left", Map.of("const", 10), "right", Map.of("const", 3)),
                Map.of("const", 7)), ctx)).isEqualTo("PASS");
        assertThat(interpreter.evaluate(PolicyDsl.eq(
                Map.of("op", "MULTIPLY", "left", Map.of("const", 2), "right", Map.of("const", 3)),
                Map.of("const", 6)), ctx)).isEqualTo("PASS");
        assertThat(interpreter.evaluate(PolicyDsl.eq(
                Map.of("op", "SUM", "args", List.of(Map.of("const", 1), Map.of("const", 2), Map.of("const", 3))),
                Map.of("const", 6)), ctx)).isEqualTo("PASS");
    }

    @Test
    void inputResolverDefaultedToDiForHard() {
        PolicyEvaluationInput input = PolicyEvaluationInput.ofMaps(tenant, UUID.randomUUID(),
                Map.of(),
                Map.of("bureau.score", Map.of("value", 700, "dataStatus", "DEFAULTED")),
                Map.of());
        ResolvedInput ri = resolver.resolve("METRIC", "bureau.score", input, PolicyRuleType.HARD, false);
        assertThat(ri.dataStatus()).isEqualTo(DataStatus.DATA_INSUFFICIENT);

        ResolvedInput soft = resolver.resolve("METRIC", "bureau.score", input, PolicyRuleType.SOFT, true);
        assertThat(soft.dataStatus()).isEqualTo(DataStatus.DEFAULTED);
        assertThat(soft.value()).isEqualTo(700);
    }

    @Test
    void orchestrationContinueOnFail() {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("rules", List.of(
                Map.of("ruleId", "R1", "ruleType", "HARD", "stageCode", "BUREAU",
                        "expression", PolicyDsl.eq(Map.of("const", 1), Map.of("const", 2)),
                        "onTrue", "PASS", "onFalse", "FAIL", "onMissing", "DATA_INSUFFICIENT"),
                Map.of("ruleId", "R2", "ruleType", "HARD", "stageCode", "BANKING",
                        "expression", PolicyDsl.eq(Map.of("const", 1), Map.of("const", 1)),
                        "onTrue", "PASS", "onFalse", "FAIL", "onMissing", "DATA_INSUFFICIENT")));
        content.put("stageDefinitions", List.of(
                Map.of("stageCode", "BUREAU", "sequence", 1, "rules", List.of("R1"),
                        "continueOnFail", false, "continueOnRefer", true, "required", true),
                Map.of("stageCode", "BANKING", "sequence", 2, "rules", List.of("R2"),
                        "continueOnFail", true, "continueOnRefer", true, "required", true)));
        CiExecutablePolicyPackage pkg = CiExecutablePolicyPackage.builder()
                .id(UUID.randomUUID()).tenantId(tenant).policyCode("ORCH").version("1")
                .status("SHADOW").content(content).contentHash("x").build();
        PolicyEvaluationInput input = PolicyEvaluationInput.ofMaps(tenant, UUID.randomUUID(), Map.of(), Map.of(), Map.of());
        CiPolicyEvaluation eval = engine.evaluate(pkg, input);
        assertThat(eval.getStageResults()).hasSize(1);
        assertThat(eval.getStageResults().get(0).getContinueFlag()).isFalse();
        assertThat(eval.getOverallOutcome()).isEqualTo("FAIL");
    }

    @Test
    void scorecardBandsAndMissingCritical() {
        ScorecardDefinitionExecutor exec = new ScorecardDefinitionExecutor();
        Map<String, Object> def = Map.of(
                "scorecardCode", "SC1",
                "components", List.of(
                        Map.of("metric", "bureau.score", "weight", 1, "critical", true,
                                "bands", List.of(Map.of("min", 650, "max", 900, "points", 10)))),
                "bands", List.of(Map.of("min", 8, "max", 100, "grade", "A")));
        var missing = exec.execute(def, PolicyEvaluationInput.ofMaps(tenant, UUID.randomUUID(), Map.of(), Map.of(), Map.of()));
        assertThat(missing.getGrade()).isEqualTo("DATA_INSUFFICIENT");
        assertThat(missing.getScore()).isNull();

        var scored = exec.execute(def, PolicyEvaluationInput.ofMaps(tenant, UUID.randomUUID(), Map.of(),
                Map.of("bureau.score", 720), Map.of()));
        assertThat(scored.getGrade()).isEqualTo("A");
        assertThat(scored.getScore()).isNotNull();
    }

    @Test
    void bankingBreShadowEvaluateCaseFixtures() {
        CiExecutablePolicyPackage pkg = GoldenShadowPackageFactory.bankingBreShadowV1(tenant);
        assertThat(pkg.getStatus()).isEqualTo(ExecutablePackageStatus.SHADOW.name());

        PolicyEvaluationInput pass = new PolicyEvaluationInput(
                tenant, UUID.randomUUID(),
                Map.of(),
                Map.of(
                        "banking.avg_daily_balance_3m", 100000,
                        "banking.avg_monthly_txn_count_3m", 40,
                        "banking.avg_daily_settlement_3m", 200000,
                        "banking.avg_monthly_settlement_count_3m", 25),
                Map.of(),
                Map.of("PROPOSED_EDI", 100),
                Map.of(),
                null,
                Map.of("exactly100Resolution", true));
        CiPolicyEvaluation eval = engine.evaluate(pkg, pass);
        assertThat(eval.getOverallOutcome()).isEqualTo("PASS");
        assertThat(eval.getExplanation().get("productionActive")).isEqualTo(false);

        PolicyEvaluationInput fail = new PolicyEvaluationInput(
                tenant, UUID.randomUUID(), Map.of(),
                Map.of("banking.avg_daily_balance_3m", 50,
                        "banking.avg_monthly_txn_count_3m", 5,
                        "banking.avg_daily_settlement_3m", 50,
                        "banking.avg_monthly_settlement_count_3m", 5),
                Map.of(), Map.of("PROPOSED_EDI", 100), Map.of(), null, Map.of());
        assertThat(engine.evaluate(pkg, fail).getOverallOutcome()).isEqualTo("FAIL");
    }

    @Test
    void bureauBreShadow() {
        CiExecutablePolicyPackage pkg = GoldenShadowPackageFactory.bureauBreShadowV1(tenant);
        PolicyEvaluationInput ok = PolicyEvaluationInput.ofMaps(tenant, UUID.randomUUID(), Map.of(),
                Map.of(
                        "bureau.score", 720,
                        "bureau.ntc", false,
                        "bureau.writeoff.non_cc_count", 0,
                        "bureau.overdue.amount", 0,
                        "bureau.credit_card.overdue_amount", 0,
                        "bureau.max_dpd_6m", 0,
                        "bureau.worst_status", "CURRENT",
                        "bureau.inquiries.current_month_count", 1),
                Map.of());
        assertThat(engine.evaluate(pkg, ok).getOverallOutcome()).isEqualTo("PASS");

        PolicyEvaluationInput lowScore = PolicyEvaluationInput.ofMaps(tenant, UUID.randomUUID(), Map.of(),
                Map.of(
                        "bureau.score", 500,
                        "bureau.ntc", false,
                        "bureau.writeoff.non_cc_count", 0,
                        "bureau.overdue.amount", 0,
                        "bureau.credit_card.overdue_amount", 0,
                        "bureau.max_dpd_6m", 0,
                        "bureau.worst_status", "CURRENT",
                        "bureau.inquiries.current_month_count", 1),
                Map.of());
        assertThat(engine.evaluate(pkg, lowScore).getOverallOutcome()).isEqualTo("FAIL");
    }

    @Test
    void legacyVsDslComparisonClasses() {
        LegacyVsDslComparator cmp = new LegacyVsDslComparator();
        assertThat(cmp.classify("PASS", "PASS", Map.of())).isEqualTo(ComparisonClass.MATCH);
        assertThat(cmp.classify("PASS", "FAIL", Map.of())).isEqualTo(ComparisonClass.DSL_STRICTER);
        assertThat(cmp.classify("FAIL", "PASS", Map.of())).isEqualTo(ComparisonClass.DSL_MORE_PERMISSIVE);
        assertThat(cmp.classify("PASS", "FAIL", Map.of("legacyUsedDefault", true)))
                .isEqualTo(ComparisonClass.LEGACY_DEFAULT_DEPENDENT);
        assertThat(cmp.classify("PASS", "DATA_INSUFFICIENT", Map.of()))
                .isEqualTo(ComparisonClass.CANONICAL_DATA_INSUFFICIENT);
        assertThat(cmp.classify("PASS", "REFER", Map.of("policyMappingDifference", true)))
                .isEqualTo(ComparisonClass.POLICY_MAPPING_DIFFERENCE);

        var legacy = GoldenShadowPackageFactory.legacyEquivalentPolicyV1(tenant);
        var canonical = GoldenShadowPackageFactory.canonicalShadowPolicyV1(tenant);
        assertThat(legacy.getContent().get("eligibility")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> elig = (Map<String, Object>) legacy.getContent().get("eligibility");
        assertThat(elig.get("flag")).isEqualTo("NOT_ELIGIBLE_FOR_NEW_LENDER_USE");
        assertThat(canonical.getContent().get("metadata")).isInstanceOf(Map.class);
    }

    @Test
    void frozenReplaySameHash() {
        CiExecutablePolicyPackage pkg = GoldenShadowPackageFactory.canonicalShadowPolicyV1(tenant);
        PolicyEvaluationInput input = PolicyEvaluationInput.ofMaps(tenant, UUID.randomUUID(), Map.of(),
                Map.of("bureau.live_unsecured_loan_count", 2), Map.of());
        Map<String, Object> replay = new PolicyReplayService(engine).replay(pkg, input);
        assertThat(replay.get("replayIdentical")).isEqualTo(true);
        assertThat(replay.get("firstHash")).isEqualTo(replay.get("secondHash"));
    }

    @Test
    void crossTenantRejection() {
        CiExecutablePolicyPackage pkg = GoldenShadowPackageFactory.bankingBreShadowV1(tenant);
        PolicyEvaluationInput other = PolicyEvaluationInput.ofMaps(
                UUID.randomUUID(), UUID.randomUUID(), Map.of(), Map.of(), Map.of());
        assertThatThrownBy(() -> engine.evaluate(pkg, other))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cross-tenant");
    }

    @Test
    void foirLtvFromRegistry() {
        PolicyRegistryMetricService metrics = new PolicyRegistryMetricService();
        PolicyEvaluationInput input = PolicyEvaluationInput.ofMaps(tenant, UUID.randomUUID(),
                Map.of("application.loan_amount", 500000),
                Map.of(
                        "obligation.total_emi", 20000,
                        "obligation.proposed_emi", 5000,
                        "income.eligible_monthly", 100000,
                        "loan.amount", 500000,
                        "collateral.value", 1000000,
                        "banking.avg_daily_balance_3m", 150000),
                Map.of());
        assertThat(((BigDecimal) metrics.computeFoir(input).get("value")).compareTo(new BigDecimal("0.250000"))).isZero();
        assertThat(((BigDecimal) metrics.computeLtv(input).get("value")).compareTo(new BigDecimal("0.500000"))).isZero();
        assertThat(((BigDecimal) metrics.computeBankPolicyAdjustedAdb3m(input).get("value"))
                .compareTo(new BigDecimal("150000"))).isZero();
    }

    @Test
    void publicationBlockedWithoutChecker() {
        PolicyPackagePublisher publisher = new PolicyPackagePublisher();
        CiPolicyDraftPackage draft = CiPolicyDraftPackage.builder()
                .id(UUID.randomUUID())
                .tenantId(tenant)
                .policyDocumentId(UUID.randomUUID())
                .content(Map.of("policyCode", "X", "rules", List.of()))
                .checkerApprovedAt(null)
                .build();
        assertThatThrownBy(() -> publisher.fromDraft(draft))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Checker approval");

        draft.setCheckerApprovedAt(Instant.now());
        draft.setCheckerApprovedBy("checker1");
        var published = publisher.fromDraft(draft);
        assertThat(published.getStatus()).isEqualTo(ExecutablePackageStatus.SHADOW.name());
        assertThat(published.getStatus()).isNotEqualTo(ExecutablePackageStatus.ACTIVE.name());
        assertThatThrownBy(() -> publisher.refuseActive(published))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ACTIVE");
    }

    @Test
    void neverActiveOnGoldenPackages() {
        for (CiExecutablePolicyPackage pkg : List.of(
                GoldenShadowPackageFactory.bankingBreShadowV1(tenant),
                GoldenShadowPackageFactory.bureauBreShadowV1(tenant),
                GoldenShadowPackageFactory.legacyEquivalentPolicyV1(tenant),
                GoldenShadowPackageFactory.canonicalShadowPolicyV1(tenant))) {
            assertThat(pkg.getStatus()).isEqualTo("SHADOW");
            assertThat(pkg.getStatus()).isNotEqualTo("ACTIVE");
            assertThat(pkg.getApprovalMetadata().get("neverActive")).isEqualTo(true);
        }
    }

    @Test
    void defaultedHardRuleInEngineYieldsDi() {
        Map<String, Object> content = Map.of(
                "rules", List.of(Map.of(
                        "ruleId", "H1", "ruleType", "HARD", "stageCode", "BUREAU",
                        "allowsDefaulted", false,
                        "inputRefs", List.of(Map.of("kind", "METRIC", "reference", "bureau.score")),
                        "expression", PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)),
                        "onTrue", "PASS", "onFalse", "FAIL", "onMissing", "DATA_INSUFFICIENT")),
                "stageDefinitions", List.of(Map.of(
                        "stageCode", "BUREAU", "sequence", 1, "rules", List.of("H1"),
                        "continueOnFail", true, "continueOnRefer", true)));
        CiExecutablePolicyPackage pkg = CiExecutablePolicyPackage.builder()
                .id(UUID.randomUUID()).tenantId(tenant).policyCode("DI").version("1")
                .status("SHADOW").content(content).build();
        PolicyEvaluationInput input = PolicyEvaluationInput.ofMaps(tenant, UUID.randomUUID(), Map.of(),
                Map.of("bureau.score", Map.of("value", 700, "dataStatus", "DEFAULTED")), Map.of());
        assertThat(engine.evaluate(pkg, input).getOverallOutcome()).isEqualTo("DATA_INSUFFICIENT");
    }

    @Test
    void reconRefResolution() {
        var ctx = PolicyDslInterpreterV1.EvaluationContext.of(
                Map.of(), Map.of(), Map.of(), Map.of(), null, "DATA_INSUFFICIENT",
                Map.of("gst_bank_turnover", Map.of("value", "MATCH")));
        assertThat(interpreter.evaluate(PolicyDsl.eq(
                Map.of("RECON_REF", "gst_bank_turnover"), Map.of("const", "MATCH")), ctx))
                .isEqualTo("PASS");
    }

    @Test
    void declarativeDefaultStagesPresent() {
        DeclarativeOrchestrator orch = new DeclarativeOrchestrator();
        assertThat(DeclarativeOrchestrator.DEFAULT_STAGE_CODES).hasSize(14);
        assertThat(orch.resolveStages(Map.of())).isNotEmpty();
    }

    @Test
    void c6CasesAgainstBankingAndBureauShadow() {
        CiExecutablePolicyPackage banking = GoldenShadowPackageFactory.bankingBreShadowV1(tenant);
        CiExecutablePolicyPackage bureau = GoldenShadowPackageFactory.bureauBreShadowV1(tenant);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String caseCode : List.of("CASE_A", "CASE_B", "CASE_C", "CASE_D", "CASE_E")) {
            PolicyEvaluationInput input = c6FixtureInput(caseCode);
            CiPolicyEvaluation bankEval = engine.evaluate(banking, input);
            CiPolicyEvaluation burEval = engine.evaluate(bureau, input);
            rows.add(Map.of(
                    "case", caseCode,
                    "bankingOutcome", bankEval.getOverallOutcome(),
                    "bureauOutcome", burEval.getOverallOutcome(),
                    "bankingRules", bankEval.getRuleCount(),
                    "bureauRules", burEval.getRuleCount()));
            assertThat(bankEval.getExplanation().get("shadowOnly")).isEqualTo(true);
            assertThat(burEval.getExplanation().get("productionActive")).isEqualTo(false);
        }
        assertThat(rows).hasSize(5);
    }

    @Test
    void historicalReplayAndImpactAnalysis() {
        var legacy = GoldenShadowPackageFactory.legacyEquivalentPolicyV1(tenant);
        var canonical = GoldenShadowPackageFactory.canonicalShadowPolicyV1(tenant);
        List<PolicyEvaluationInput> inputs = List.of(
                PolicyEvaluationInput.ofMaps(tenant, UUID.randomUUID(), Map.of(),
                        Map.of("bureau.live_unsecured_loan_count", 2), Map.of()),
                PolicyEvaluationInput.ofMaps(tenant, UUID.randomUUID(), Map.of(),
                        Map.of("bureau.live_unsecured_loan_count", Map.of("value", 0, "dataStatus", "DEFAULTED")), Map.of()),
                PolicyEvaluationInput.ofMaps(tenant, UUID.randomUUID(), Map.of(),
                        Map.of("bureau.live_unsecured_loan_count", 9), Map.of()));
        var replay = new PolicyHistoricalReplayService(engine).replay(canonical, inputs, "test");
        assertThat(replay.getSummary().get("count")).isEqualTo(3);
        assertThat(replay.getSummary().get("shadowOnly")).isEqualTo(true);

        Map<String, Object> impact = new PolicyImpactAnalysisService(engine, new LegacyVsDslComparator())
                .analyze(legacy, canonical, inputs);
        assertThat(impact.get("aggregate")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> agg = (Map<String, Object>) impact.get("aggregate");
        assertThat(agg.get("total")).isEqualTo(3);
    }

    @Test
    void performanceRuleThroughput() {
        for (int n : List.of(10, 100, 500, 1000)) {
            List<Map<String, Object>> rules = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String id = "R" + i;
                ids.add(id);
                rules.add(Map.of(
                        "ruleId", id, "ruleType", "SOFT", "stageCode", "ELIGIBILITY",
                        "expression", PolicyDsl.gte(PolicyDsl.metric("x"), Map.of("const", 0)),
                        "onTrue", "PASS", "onFalse", "FAIL", "onMissing", "DATA_INSUFFICIENT"));
            }
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("rules", rules);
            content.put("stageDefinitions", List.of(Map.of(
                    "stageCode", "ELIGIBILITY", "sequence", 1, "rules", ids,
                    "continueOnFail", true, "continueOnRefer", true)));
            CiExecutablePolicyPackage pkg = CiExecutablePolicyPackage.builder()
                    .id(UUID.randomUUID()).tenantId(tenant).policyCode("PERF_" + n).version("1")
                    .status("SHADOW").content(content).contentHash("perf" + n).build();
            PolicyEvaluationInput input = PolicyEvaluationInput.ofMaps(tenant, UUID.randomUUID(), Map.of(),
                    Map.of("x", 1), Map.of());
            long t0 = System.nanoTime();
            CiPolicyEvaluation eval = engine.evaluate(pkg, input);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            assertThat(eval.getRuleCount()).isEqualTo(n);
            assertThat(eval.getOverallOutcome()).isEqualTo("PASS");
            // Soft budget: even 1000 rules should finish under 5s on CI
            assertThat(ms).isLessThan(5000L);
        }
    }

    @Test
    void evidenceIntegratorAttachesShadowSection() {
        CiExecutablePolicyPackage pkg = GoldenShadowPackageFactory.canonicalShadowPolicyV1(tenant);
        PolicyEvaluationInput input = PolicyEvaluationInput.ofMaps(tenant, UUID.randomUUID(), Map.of(),
                Map.of("bureau.live_unsecured_loan_count", 1), Map.of());
        CiPolicyEvaluation eval = engine.evaluate(pkg, input);
        Map<String, Object> view = new PolicyEngineEvidenceIntegrator().attach(Map.of("DataCoverage", Map.of()), eval);
        assertThat(view.get("PolicyEngineShadow")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> pe = (Map<String, Object>) view.get("PolicyEngineShadow");
        assertThat(pe.get("shadowOnly")).isEqualTo(true);
        assertThat(pe.get("overallOutcome")).isEqualTo("PASS");
    }

    private PolicyEvaluationInput c6FixtureInput(String caseCode) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        Map<String, Object> params = Map.of("PROPOSED_EDI", 100);
        switch (caseCode) {
            case "CASE_A" -> {
                metrics.put("banking.avg_daily_balance_3m", 200000);
                metrics.put("banking.avg_monthly_txn_count_3m", 40);
                metrics.put("banking.avg_daily_settlement_3m", 300000);
                metrics.put("banking.avg_monthly_settlement_count_3m", 30);
                metrics.put("bureau.score", 740);
                metrics.put("bureau.ntc", false);
                metrics.put("bureau.writeoff.non_cc_count", 0);
                metrics.put("bureau.overdue.amount", 0);
                metrics.put("bureau.credit_card.overdue_amount", 0);
                metrics.put("bureau.max_dpd_6m", 0);
                metrics.put("bureau.worst_status", "CURRENT");
                metrics.put("bureau.inquiries.current_month_count", 1);
            }
            case "CASE_B" -> {
                // Legacy-default-dependent style: DEFAULTED score must not silently pass hard bureau score
                metrics.put("banking.avg_daily_balance_3m", 200000);
                metrics.put("banking.avg_monthly_txn_count_3m", 40);
                metrics.put("banking.avg_daily_settlement_3m", 300000);
                metrics.put("banking.avg_monthly_settlement_count_3m", 30);
                metrics.put("bureau.score", Map.of("value", 700, "dataStatus", "DEFAULTED"));
                metrics.put("bureau.ntc", false);
                metrics.put("bureau.writeoff.non_cc_count", 0);
                metrics.put("bureau.overdue.amount", 0);
                metrics.put("bureau.credit_card.overdue_amount", 0);
                metrics.put("bureau.max_dpd_6m", 0);
                metrics.put("bureau.worst_status", "CURRENT");
                metrics.put("bureau.inquiries.current_month_count", 1);
            }
            case "CASE_C", "CASE_D" -> {
                metrics.put("banking.avg_daily_balance_3m", 80000);
                metrics.put("banking.avg_monthly_txn_count_3m", 25);
                metrics.put("banking.avg_daily_settlement_3m", 100000);
                metrics.put("banking.avg_monthly_settlement_count_3m", 22);
                metrics.put("bureau.score", 680);
                metrics.put("bureau.ntc", false);
                metrics.put("bureau.writeoff.non_cc_count", 0);
                metrics.put("bureau.overdue.amount", 800);
                metrics.put("bureau.overdue.age_days", 400);
                metrics.put("bureau.overdue.has_new_loans_after", true);
                metrics.put("bureau.overdue.clean_months", 8);
                metrics.put("bureau.credit_card.overdue_amount", 0);
                metrics.put("bureau.max_dpd_6m", 15);
                metrics.put("bureau.worst_status", "CURRENT");
                metrics.put("bureau.inquiries.current_month_count", 2);
            }
            default -> { // CASE_E incomplete
                metrics.put("banking.avg_daily_balance_3m", Map.of("outcome", "DATA_INSUFFICIENT"));
                metrics.put("banking.avg_monthly_txn_count_3m", Map.of("outcome", "DATA_INSUFFICIENT"));
                metrics.put("banking.avg_daily_settlement_3m", Map.of("outcome", "DATA_INSUFFICIENT"));
                metrics.put("banking.avg_monthly_settlement_count_3m", Map.of("outcome", "DATA_INSUFFICIENT"));
                metrics.put("bureau.score", Map.of("outcome", "DATA_INSUFFICIENT"));
            }
        }
        return new PolicyEvaluationInput(
                tenant, UUID.randomUUID(), Map.of(), metrics, Map.of(), params, Map.of(), null,
                Map.of("caseCode", caseCode, "fixtureOnly", true));
    }
}
