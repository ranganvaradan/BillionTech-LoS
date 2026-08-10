package com.los.core.creditintelligence.policystudio;

import com.los.core.creditintelligence.core.clock.FixedEvaluationClock;
import com.los.core.creditintelligence.policystudio.domain.AmbiguityResolutionAction;
import com.los.core.creditintelligence.policystudio.domain.AuthoringReadinessGrade;
import com.los.core.creditintelligence.policystudio.domain.ReviewState;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslSchemaValidator;
import com.los.core.creditintelligence.policystudio.metrics.PolicyBankingMetricService;
import com.los.core.creditintelligence.policystudio.metrics.PolicyBureauMetricService;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.service.DraftPolicyDiffService;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import com.los.core.creditintelligence.bureau.service.BureauStatusNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyStudioHardeningP01Test {

    private PolicyStudioOrchestrator orch;

    @BeforeEach
    void setUp() {
        orch = new PolicyStudioOrchestrator();
        orch.persistence().clearAllForTests();
    }

    @Test
    void persistentSessionReloadAfterCacheClear() throws Exception {
        String text = fixture("/policy-fixtures/banking-bre/Banking_BRE.txt");
        PolicyStudioSession s = orch.processUpload(UUID.randomUUID(), "Banking", "TXT", text, "author", null);
        UUID docId = s.getDocument().getId();
        assertThat(s.sessionId()).isNotNull();
        orch.persistence().clearCache();
        PolicyStudioSession reloaded = orch.requireSession(docId);
        assertThat(reloaded.getClauses()).hasSameSizeAs(s.getClauses());
        assertThat(reloaded.getAmbiguities()).isNotEmpty();
        assertThat(reloaded.getAuthoringSession().getId()).isEqualTo(s.sessionId());
    }

    @Test
    void optimisticVersionConflict() throws Exception {
        String text = fixture("/policy-fixtures/banking-bre/Banking_BRE.txt");
        PolicyStudioSession s = orch.processUpload(UUID.randomUUID(), "Banking", "TXT", text, "author", null);
        Long v = s.getAuthoringSession().getVersion();
        orch.persistence().bumpOptimisticVersion(s, v);
        assertThatThrownBy(() -> orch.persistence().bumpOptimisticVersion(s, v))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Optimistic lock");
    }

    @Test
    void vocabularyApproveTermAndProposeNotFinalize() {
        var vocab = orch.vocabularyService();
        UUID tenant = UUID.randomUUID();
        var approved = vocab.approveTerm(tenant, "TENANT", null, "CLEAN",
                "No overdue after settlement window", "bureau.credit_after_overdue.clean_history_months",
                "METRIC", "cm", "initial");
        assertThat(approved.getVersion()).isEqualTo(1);
        assertThat(approved.getMetadata().get("autoFinalize")).isEqualTo(false);
        assertThat(vocab.resolve(tenant, null, "CLEAN")).isNotNull();
        assertThat(vocab.proposePriorMappings(tenant, null, "CLEAN")).isNotEmpty();
    }

    @Test
    void ediUnresolvedVsProposedEdiParameter() throws Exception {
        String text = fixture("/policy-fixtures/banking-bre/Banking_BRE.txt");
        PolicyStudioSession s = orch.processUpload(UUID.randomUUID(), "Banking", "TXT", text, "author", null);
        assertThat(orch.vocabularyService().resolve("EDI")).isNull();
        var amb = s.getAmbiguities().stream().filter(a -> "EDI".equalsIgnoreCase(a.getPhrase())).findFirst().orElseThrow();
        var result = orch.resolveAmbiguity(s.getDocument().getId(), amb.getId(),
                AmbiguityResolutionAction.CREATE_POLICY_PARAMETER, null, "cm", "create param", Map.of());
        assertThat(result.get("resolvedOption")).isEqualTo("POLICY_PARAMETER:PROPOSED_EDI");
        assertThat(s.getParameters()).anyMatch(p -> "PROPOSED_EDI".equals(p.getCode()));
        assertThat(amb.getPreviousResolution()).isNotNull();
    }

    @Test
    void qrSettlementDataInsufficientWithoutTaxonomy() {
        var svc = new PolicyBankingMetricService();
        var suite = svc.qrSettlementSuite(List.of(), false);
        @SuppressWarnings("unchecked")
        Map<String, Object> qr = (Map<String, Object>) suite.get("banking.qr_settlement.amount_total_3m");
        assertThat(qr.get("outcome")).isEqualTo("DATA_INSUFFICIENT");
    }

    @Test
    void adjustedAdbDoesNotMutateGlobalAndBulkNeedsDefinition() {
        var svc = new PolicyBankingMetricService();
        var di = svc.adjustedAdb3m(new BigDecimal("100000"),
                List.of(Map.of("type", "BULK_DEPOSIT")), false, new BigDecimal("10000"), null);
        assertThat(di.get("outcome")).isEqualTo("DATA_INSUFFICIENT");
        assertThat(di.get("executable")).isEqualTo(false);
        var ok = svc.adjustedAdb3m(new BigDecimal("100000"),
                List.of(Map.of("type", "BULK_DEPOSIT")), true, new BigDecimal("10000"), null);
        assertThat(ok.get("outcome")).isEqualTo("PASS");
        assertThat(ok.get("raw")).isEqualTo(new BigDecimal("100000"));
        assertThat(ok.get("adjusted")).isEqualTo(new BigDecimal("90000"));
        assertThat(String.valueOf(((Map<?, ?>) ok.get("evidence")).get("doesNotMutate")))
                .isEqualTo("banking.avg_daily_balance_3m");
    }

    @Test
    void overdueMetricsAndCleanUnresolvedVsResolved() {
        var bureau = new PolicyBureauMetricService();
        var tls = List.of(new PolicyBureauMetricService.TradelineInput(
                "PERSONAL", "Active", false, new BigDecimal("1000"), null, List.of(), null, false));
        @SuppressWarnings("unchecked")
        Map<String, Object> overdueNonCc = (Map<String, Object>) bureau.overdueMetrics(tls)
                .get("bureau.accounts.overdue_non_cc");
        assertThat(overdueNonCc.get("outcome")).isEqualTo("PASS");
        var cleanDi = bureau.cleanHistoryMonths(6, false);
        assertThat(cleanDi.get("outcome")).isEqualTo("DATA_INSUFFICIENT");
        assertThat(cleanDi.get("placeholder")).isEqualTo("CUSTOMER_CONFIRMATION_REQUIRED");
        var cleanOk = bureau.cleanHistoryMonths(6, true);
        assertThat(cleanOk.get("outcome")).isEqualTo("PASS");
        assertThat(cleanOk.get("v")).isEqualTo(6);
    }

    @Test
    void exactly100BoundaryRegeneratesTests() throws Exception {
        String text = fixture("/policy-fixtures/banking-bre/Banking_BRE.txt");
        PolicyStudioSession s = orch.processUpload(UUID.randomUUID(), "Banking", "TXT", text, "author", null);
        var amb = s.getAmbiguities().stream()
                .filter(a -> a.getPhrase() != null && a.getPhrase().toLowerCase().contains("100"))
                .findFirst().orElseThrow();
        orch.resolveAmbiguity(s.getDocument().getId(), amb.getId(),
                AmbiguityResolutionAction.SELECT_CANDIDATE,
                "treat_100_as_percentage", "cm", "human selection", Map.of());
        assertThat(s.getTestCases()).anyMatch(t ->
                t.getName() != null && t.getName().contains("EXACTLY_100"));
    }

    @Test
    void dslTypeUnitPeriodAndDiPropagation() {
        var validator = new PolicyDslSchemaValidator();
        var bad = validator.validate(
                PolicyDsl.gte(Map.of("metric", "banking.avg_daily_balance_3m", "unit", "INR"),
                        Map.of("const", 20, "unit", "COUNT")),
                "HARD", "DATA_INSUFFICIENT", "INR", "COUNT", "TRAILING_3M", "TRAILING_6M");
        assertThat(bad.valid()).isFalse();
        assertThat(bad.errors()).anyMatch(e -> "UNIT_MISMATCH".equals(e.get("code")));
        assertThat(bad.errors()).anyMatch(e -> "PERIOD_MISMATCH".equals(e.get("code")));

        var interpreter = new PolicyDslInterpreterV1();
        var clock = new FixedEvaluationClock(LocalDate.of(2024, 6, 15), ZoneId.of("Asia/Kolkata"));
        var ctx = PolicyDslInterpreterV1.EvaluationContext.of(
                Map.of("bureau.score", 700), Map.of(), clock);
        // AND(PASS, DI)=DI
        assertThat(interpreter.evaluate(PolicyDsl.and(
                PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)),
                PolicyDsl.gte(PolicyDsl.metric("missing.metric"), Map.of("const", 1))
        ), ctx)).isEqualTo("DATA_INSUFFICIENT");
        // OR(PASS, DI)=PASS
        assertThat(interpreter.evaluate(PolicyDsl.or(
                PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)),
                PolicyDsl.gte(PolicyDsl.metric("missing.metric"), Map.of("const", 1))
        ), ctx)).isEqualTo("PASS");
        // OR(FAIL, DI)=DI
        assertThat(interpreter.evaluate(PolicyDsl.or(
                PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 800)),
                PolicyDsl.gte(PolicyDsl.metric("missing.metric"), Map.of("const", 1))
        ), ctx)).isEqualTo("DATA_INSUFFICIENT");
        // GTE(missing,x)=DI
        assertThat(interpreter.evaluate(
                PolicyDsl.gte(PolicyDsl.metric("missing"), Map.of("const", 1)), ctx))
                .isEqualTo("DATA_INSUFFICIENT");
    }

    @Test
    void inquiryUsesEvaluationContextClock() {
        var bureau = new PolicyBureauMetricService();
        var clock = new FixedEvaluationClock(LocalDate.of(2024, 6, 15), ZoneId.of("Asia/Kolkata"));
        var result = bureau.inquiriesCurrentMonth(List.of(
                new PolicyBureauMetricService.InquiryInput(LocalDate.of(2024, 6, 2)),
                new PolicyBureauMetricService.InquiryInput(LocalDate.of(2024, 5, 30))
        ), clock);
        assertThat(result.get("outcome")).isEqualTo("PASS");
        assertThat(result.get("v")).isEqualTo(1);
        assertThat(result.get("asOf")).isEqualTo("2024-06-15");
    }

    @Test
    void makerCheckerInvalidationAndDraftRebuild() throws Exception {
        String text = fixture("/policy-fixtures/bureau-bre/Bureau_BRE.txt");
        PolicyStudioSession s = orch.processUpload(UUID.randomUUID(), "Bureau", "TXT", text, "alice", null);
        orch.buildDraft(s.getDocument().getId(), "alice");
        var rule = s.getRuleCandidates().get(0);
        orch.reviewService().review(s, "RULE", rule.getId(), "bob",
                "POLICY_CHECKER", ReviewState.CHECKER_APPROVED.name(), Map.of(), "ok");
        assertThat(s.getDraftPackage().getCheckerApprovedAt()).isNotNull();
        orch.reviewService().review(s, "RULE", rule.getId(), "alice",
                "POLICY_AUTHOR", ReviewState.CREDIT_MANAGER_APPROVED.name(),
                Map.of("expression", "edited"), "material");
        assertThat(s.getDraftPackage().getInvalidatedByEdit()).isTrue();
        orch.buildDraft(s.getDocument().getId(), "alice");
        assertThat(s.getDraftPackage().getPackageVersion()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void versionDiffPersisted() throws Exception {
        String text = fixture("/policy-fixtures/banking-bre/Banking_BRE.txt");
        PolicyStudioSession s = orch.processUpload(UUID.randomUUID(), "Banking", "TXT", text, "author", null);
        orch.buildDraft(s.getDocument().getId(), "author");
        var from = s.getDraftPackage();
        s.getRuleCandidates().get(0).getMetadata();
        orch.reviewService().invalidateCheckerApproval(s, "editor");
        orch.buildDraft(s.getDocument().getId(), "author");
        var to = s.getDraftPackage();
        Map<String, Object> diff = new DraftPolicyDiffService(orch.persistence()).diff(s, from, to);
        assertThat(diff).containsKeys("addedRule", "removedRule", "changedThreshold");
        assertThat(s.getDraftDiffs()).isNotEmpty();
    }

    @Test
    void bureauStatusNormalizerCanonical() {
        var n = new BureauStatusNormalizer();
        assertThat(n.normalize("Settled")).isEqualTo(BureauStatusNormalizer.CanonicalStatus.SETTLED);
        assertThat(n.normalize("DBT")).isEqualTo(BureauStatusNormalizer.CanonicalStatus.DBT);
        assertThat(n.normalize("Account Sold")).isEqualTo(BureauStatusNormalizer.CanonicalStatus.ACCOUNT_SOLD);
        assertThat(n.normalize("NTC")).isEqualTo(BureauStatusNormalizer.CanonicalStatus.NTC);
        assertThat(n.normalize("weird")).isEqualTo(BureauStatusNormalizer.CanonicalStatus.UNKNOWN);
    }

    @Test
    void maxDpd6mFromPaymentHistory() {
        var bureau = new PolicyBureauMetricService();
        var tls = List.of(new PolicyBureauMetricService.TradelineInput(
                "PL", "Active", false, null, null,
                List.of(
                        new PolicyBureauMetricService.PaymentMonth(YearMonth.of(2024, 5), 45),
                        new PolicyBureauMetricService.PaymentMonth(YearMonth.of(2023, 1), 90)
                ),
                null, false));
        var r = bureau.maxDpd6m(tls, LocalDate.of(2024, 6, 15));
        assertThat(r.get("v")).isEqualTo(45);
    }

    @Test
    void bankingGoldenRemainsBlockedWithoutCustomerResolution() throws Exception {
        String text = fixture("/policy-fixtures/banking-bre/Banking_BRE.txt");
        PolicyStudioSession s = orch.processUpload(UUID.randomUUID(), "Banking", "TXT", text, "author", null);
        assertThat(s.getCompleteness().get("status")).isEqualTo("BLOCKED");
        assertThat(s.getReadiness().get("grade")).isEqualTo(AuthoringReadinessGrade.BLOCKED.name());
    }

    @Test
    void bureauGoldenRemainsBlockedWithoutCustomerResolution() throws Exception {
        String text = fixture("/policy-fixtures/bureau-bre/Bureau_BRE.txt");
        PolicyStudioSession s = orch.processUpload(UUID.randomUUID(), "Bureau", "TXT", text, "author", null);
        assertThat(s.getCompleteness().get("status")).isEqualTo("BLOCKED");
        assertThat(s.getReadiness().get("grade")).isEqualTo(AuthoringReadinessGrade.BLOCKED.name());
    }

    @Test
    void readyForPolicyBuildOnlyAfterExplicitResolutions() throws Exception {
        String text = fixture("/policy-fixtures/banking-bre/Banking_BRE.txt");
        PolicyStudioSession s = orch.processUpload(UUID.randomUUID(), "Banking", "TXT", text, "author", null);
        // Explicit resolve each OPEN material ambiguity — no silent fake
        for (var amb : List.copyOf(s.getAmbiguities())) {
            if (!"OPEN".equals(amb.getResolutionStatus())) {
                continue;
            }
            if ("EDI".equalsIgnoreCase(amb.getPhrase())) {
                orch.resolveAmbiguity(s.getDocument().getId(), amb.getId(),
                        AmbiguityResolutionAction.CREATE_POLICY_PARAMETER, null, "cm",
                        "CUSTOMER_CONFIRMATION_REQUIRED", Map.of());
            } else if (amb.getPhrase() != null && amb.getPhrase().toLowerCase().contains("100")) {
                orch.resolveAmbiguity(s.getDocument().getId(), amb.getId(),
                        AmbiguityResolutionAction.SELECT_CANDIDATE,
                        "treat_100_as_percentage", "cm", "CUSTOMER_CONFIRMATION_REQUIRED", Map.of());
            } else {
                orch.resolveAmbiguity(s.getDocument().getId(), amb.getId(),
                        AmbiguityResolutionAction.REQUEST_CLARIFICATION,
                        "CUSTOMER_CONFIRMATION_REQUIRED", "cm",
                        "Placeholder until customer confirms", Map.of());
            }
        }
        // Clarification-requested still blocks READY; only RESOLVED clears
        long stillOpen = s.getAmbiguities().stream()
                .filter(a -> "OPEN".equals(a.getResolutionStatus()) || "CLARIFICATION_REQUESTED".equals(a.getResolutionStatus()))
                .filter(a -> "MATERIAL".equals(a.getSeverity()))
                .count();
        assertThat(stillOpen).isGreaterThan(0);
        assertThat(s.getReadiness().get("grade")).isNotEqualTo(AuthoringReadinessGrade.READY_FOR_POLICY_BUILD.name());

        // Resolve remaining to RESOLVED via SELECT_CANDIDATE with explicit option
        for (var amb : List.copyOf(s.getAmbiguities())) {
            if ("CLARIFICATION_REQUESTED".equals(amb.getResolutionStatus())
                    || "OPEN".equals(amb.getResolutionStatus())) {
                orch.resolveAmbiguity(s.getDocument().getId(), amb.getId(),
                        AmbiguityResolutionAction.SELECT_CANDIDATE,
                        amb.getRecommendedOption() == null ? "CUSTOMER_CONFIRMATION_REQUIRED" : amb.getRecommendedOption(),
                        "cm", "explicit fixture resolution", Map.of());
            }
        }
        for (var t : s.getTestCases()) {
            orch.reviewService().review(s, "TEST", t.getId(), "cm",
                    "CREDIT_MANAGER", ReviewState.CREDIT_MANAGER_APPROVED.name(), Map.of(), "approve");
            orch.reviewService().review(s, "TEST", t.getId(), "checker",
                    "POLICY_CHECKER", ReviewState.CHECKER_APPROVED.name(), Map.of(), "approve");
        }
        for (var r : s.getRuleCandidates()) {
            orch.reviewService().review(s, "RULE", r.getId(), "cm",
                    "CREDIT_MANAGER", ReviewState.CREDIT_MANAGER_APPROVED.name(), Map.of(), "approve");
            orch.reviewService().review(s, "RULE", r.getId(), "checker",
                    "POLICY_CHECKER", ReviewState.CHECKER_APPROVED.name(), Map.of(), "approve");
        }
        s.setReadiness(new com.los.core.creditintelligence.policystudio.service.PolicyAuthoringProgressScorer().score(s));
        assertThat(s.getAmbiguities().stream().noneMatch(a ->
                "OPEN".equals(a.getResolutionStatus()) && "MATERIAL".equals(a.getSeverity()))).isTrue();
        assertThat(s.getReadiness().get("grade")).isEqualTo(AuthoringReadinessGrade.READY_FOR_POLICY_BUILD.name());
    }

    private String fixture(String path) throws Exception {
        return new String(getClass().getResourceAsStream(path).readAllBytes(), StandardCharsets.UTF_8);
    }
}
