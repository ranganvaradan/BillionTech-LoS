package com.los.core.creditintelligence.decisionpolicy;

import com.los.core.creditintelligence.decisionpolicy.kyc.KycBusinessOutcome;
import com.los.core.creditintelligence.decisionpolicy.kyc.NormalizedKycFactBuilder;
import com.los.core.creditintelligence.decisionpolicy.kyc.shadow.GoldenKycShadowPackageFactory;
import com.los.core.creditintelligence.decisionpolicy.kyc.shadow.KycPolicyOutcomeAggregator;
import com.los.core.creditintelligence.decisionpolicy.kyc.shadow.KycShadowComparisonClassifier;
import com.los.core.creditintelligence.decisionpolicy.kyc.shadow.ShadowKycEvaluationRequest;
import com.los.core.creditintelligence.decisionpolicy.kyc.shadow.ShadowKycPolicyEvaluationService;
import com.los.core.creditintelligence.policy.domain.CiExecutablePolicyPackage;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import com.los.core.model.enums.KycStepType;
import com.los.core.model.enums.StepOutcome;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KYC-5 — versioned Decision Policy KYC shadow evaluation.
 */
class Kyc5ShadowEvaluationTest {

    private final ShadowKycPolicyEvaluationService service = new ShadowKycPolicyEvaluationService();
    private final UUID tenant = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void applicableImmutablePackageResolvesAndEvaluates() {
        CiExecutablePolicyPackage pkg = GoldenKycShadowPackageFactory.decisionPolicyKycV1(tenant);
        Map<String, Object> r = eval(pkg, List.of(ShadowKycPolicyEvaluationService.panPass()),
                Map.of("requested_amount", 100_000, "borrower_type", "INDIVIDUAL"), "PASS");
        assertThat(r.get("routingOutcome")).isEqualTo("EXACTLY_ONE");
        assertThat(r.get("authoritative")).isEqualTo(false);
        assertThat(r.get("shadow")).isEqualTo(true);
        assertThat(r.get("allowCanonicalAuthority")).isEqualTo(false);
        assertThat(r.get("providerCallsMade")).isEqualTo(false);
        assertThat(r.get("overallOutcome")).isEqualTo("PASS");
    }

    @Test
    void noApplicablePolicyRecorded() {
        CiExecutablePolicyPackage pkg = GoldenKycShadowPackageFactory.decisionPolicyKycV1(tenant);
        Map<String, Object> r = service.evaluate(ShadowKycEvaluationRequest.builder()
                .tenantId(tenant)
                .policyPackage(pkg)
                .routingOutcome("NO_APPLICABLE_POLICY")
                .routingReason("none")
                .persist(false)
                .build());
        assertThat(r.get("status")).isEqualTo("BLOCKED");
        assertThat(r.get("routingOutcome")).isEqualTo("NO_APPLICABLE_POLICY");
    }

    @Test
    void ambiguousPolicyRecorded() {
        CiExecutablePolicyPackage pkg = GoldenKycShadowPackageFactory.decisionPolicyKycV1(tenant);
        Map<String, Object> r = service.evaluate(ShadowKycEvaluationRequest.builder()
                .tenantId(tenant)
                .policyPackage(pkg)
                .routingOutcome("AMBIGUOUS_POLICY_CONFIGURATION")
                .persist(false)
                .build());
        assertThat(r.get("status")).isEqualTo("BLOCKED");
    }

    @Test
    void kycOnlyRuleSelectionExcludesCredit() {
        CiExecutablePolicyPackage pkg = GoldenKycShadowPackageFactory.decisionPolicyKycV1(tenant);
        List<Map<String, Object>> rules = ShadowKycPolicyEvaluationService.selectKycEligibilityRules(pkg);
        assertThat(rules).isNotEmpty();
        assertThat(rules).allMatch(ShadowKycPolicyEvaluationService::isKycEligibilityRule);
        assertThat(rules).noneMatch(r -> "CREDIT".equals(String.valueOf(r.get("decisionDomain"))));
    }

    @Test
    void normalizedFactsFrozenNoInferenceToFalse() {
        Map<String, Object> facts = NormalizedKycFactBuilder.buildFacts(
                List.of(ShadowKycPolicyEvaluationService.panProviderOutage()), Map.of("panPresent", true), "FAIL");
        assertThat(facts.containsKey("kyc.pan.verified")).isFalse();
        assertThat(facts.get("kyc.pan.technical_status")).isNotNull();
    }

    @Test
    void panPass() {
        Map<String, Object> r = eval(v1(), List.of(ShadowKycPolicyEvaluationService.panPass()),
                Map.of("requested_amount", 100_000, "borrower_type", "INDIVIDUAL"), "PASS");
        assertThat(r.get("overallOutcome")).isEqualTo("PASS");
    }

    @Test
    void conclusivePanFail() {
        Map<String, Object> r = eval(v1(), List.of(ShadowKycPolicyEvaluationService.panFail()),
                Map.of("requested_amount", 100_000, "borrower_type", "INDIVIDUAL"), "FAIL");
        assertThat(r.get("overallOutcome")).isEqualTo("FAIL");
    }

    @Test
    void providerOutageMissingNotFail() {
        Map<String, Object> r = eval(v1(), List.of(ShadowKycPolicyEvaluationService.panProviderOutage()),
                Map.of("requested_amount", 100_000, "borrower_type", "INDIVIDUAL"), "FAIL");
        assertThat(r.get("overallOutcome")).isEqualTo("MISSING_INFORMATION");
        assertThat(r.get("comparisonClass")).isIn(
                KycShadowComparisonClassifier.PROVIDER_FAILURE_SEMANTIC_DIFFERENCE,
                KycShadowComparisonClassifier.PRODUCTION_FAIL_SHADOW_MISSING_INFORMATION);
    }

    @Test
    void manualReviewRefer() {
        var step = new NormalizedKycFactBuilder.StepEvidence(
                KycStepType.PAN_VERIFY, StepOutcome.SUCCESS, null, Map.of("nameMatch", false), false, false);
        Map<String, Object> r = eval(v1(), List.of(step),
                Map.of("requested_amount", 100_000, "borrower_type", "INDIVIDUAL"), "PASS");
        assertThat(r.get("overallOutcome")).isEqualTo("REFER");
        assertThat(r.get("referPayload")).isInstanceOf(Map.class);
    }

    @Test
    void missingMandatoryFact() {
        Map<String, Object> r = eval(v1(), List.of(),
                Map.of("requested_amount", 100_000, "borrower_type", "INDIVIDUAL"), "INCOMPLETE");
        assertThat(r.get("overallOutcome")).isEqualTo("MISSING_INFORMATION");
    }

    @Test
    void hardFailPrecedenceOverRefer() {
        List<Map<String, Object>> rows = List.of(
                Map.of("ruleId", "A", "outcome", "REFER", "ruleType", "SOFT", "mandatory", false),
                Map.of("ruleId", "B", "outcome", "FAIL", "ruleType", "HARD", "mandatory", true,
                        "requirementType", "VERIFICATION"));
        assertThat(KycPolicyOutcomeAggregator.aggregate(rows).overall()).isEqualTo(KycBusinessOutcome.FAIL);
    }

    @Test
    void referralPrecedenceOverMissing() {
        List<Map<String, Object>> rows = List.of(
                Map.of("ruleId", "A", "outcome", PolicyDslInterpreterV1.DATA_INSUFFICIENT,
                        "ruleType", "HARD", "mandatory", true, "requirementType", "VERIFICATION"),
                Map.of("ruleId", "B", "outcome", "REFER", "ruleType", "SOFT", "mandatory", false));
        assertThat(KycPolicyOutcomeAggregator.aggregate(rows).overall()).isEqualTo(KycBusinessOutcome.REFER);
    }

    @Test
    void platformGuardrailBlocks() {
        Map<String, Object> r = eval(v1(), List.of(ShadowKycPolicyEvaluationService.panFail()),
                Map.of("requested_amount", 50_000, "borrower_type", "INDIVIDUAL"), "FAIL");
        assertThat(r.get("overallOutcome")).isEqualTo("FAIL");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) r.get("ruleResults");
        assertThat(rules.stream().anyMatch(x ->
                "PLATFORM_GUARDRAIL".equals(String.valueOf(x.get("guardrailClass"))))).isTrue();
    }

    @Test
    void companyCinGst() {
        Map<String, Object> r = eval(v1(), List.of(
                        ShadowKycPolicyEvaluationService.panPass(),
                        ShadowKycPolicyEvaluationService.step(KycStepType.CIN_MCA21, StepOutcome.SUCCESS, null, Map.of(), false),
                        ShadowKycPolicyEvaluationService.step(KycStepType.GSTIN_VERIFY, StepOutcome.SUCCESS, null, Map.of(), false)),
                Map.of("requested_amount", 200_000, "borrower_type", "COMPANY"), "PASS");
        assertThat(r.get("overallOutcome")).isEqualTo("PASS");
    }

    @Test
    void vkycThresholdMissing() {
        Map<String, Object> r = eval(v1(), List.of(ShadowKycPolicyEvaluationService.panPass()),
                Map.of("requested_amount", 600_000, "borrower_type", "INDIVIDUAL"), "PASS");
        assertThat(r.get("overallOutcome")).isIn("FAIL", "MISSING_INFORMATION");
    }

    @Test
    void vkycThresholdCompleted() {
        Map<String, Object> r = eval(v1(), List.of(
                        ShadowKycPolicyEvaluationService.panPass(),
                        new NormalizedKycFactBuilder.StepEvidence(
                                KycStepType.VIDEO_KYC, StepOutcome.SUCCESS, null, Map.of(), false, null)),
                Map.of("requested_amount", 600_000, "borrower_type", "INDIVIDUAL"), "PASS");
        assertThat(r.get("overallOutcome")).isEqualTo("PASS");
    }

    @Test
    void policyVersionPinningV1ReplayAfterV2Active() {
        CiExecutablePolicyPackage v1 = GoldenKycShadowPackageFactory.decisionPolicyKycV1(tenant);
        CiExecutablePolicyPackage v2 = GoldenKycShadowPackageFactory.decisionPolicyKycV2(tenant);
        Map<String, Object> first = eval(v1, List.of(ShadowKycPolicyEvaluationService.panPass()),
                Map.of("requested_amount", 100_000, "borrower_type", "INDIVIDUAL"), "PASS");
        assertThat(first.get("overallOutcome")).isEqualTo("PASS");
        assertThat(first.get("policyVersion")).isEqualTo("1");

        Map<String, Object> replay = service.replay(first, v1);
        assertThat(replay.get("replayMatch")).isEqualTo(true);
        assertThat(replay.get("overallOutcome")).isEqualTo("PASS");
        assertThat(replay.get("policyVersion")).isEqualTo("1");

        Map<String, Object> withV2 = eval(v2, List.of(ShadowKycPolicyEvaluationService.panPass()),
                Map.of("requested_amount", 100_000, "borrower_type", "INDIVIDUAL"), "PASS");
        // v2 adds CKYC required — without CKYC evidence overall is not PASS
        assertThat(withV2.get("overallOutcome")).isNotEqualTo("PASS");
        assertThat(withV2.get("policyVersion")).isEqualTo("2");
        // historical still v1
        assertThat(service.replay(first, v1).get("policyVersion")).isEqualTo("1");
    }

    @Test
    void replayDeterministic100() {
        Map<String, Object> first = eval(v1(), List.of(ShadowKycPolicyEvaluationService.panPass()),
                Map.of("requested_amount", 100_000, "borrower_type", "INDIVIDUAL"), "PASS");
        Map<String, Object> again = service.replay(first, v1());
        assertThat(again.get("replayMatch")).isEqualTo(true);
        assertThat(again.get("deterministicHash")).isEqualTo(first.get("deterministicHash"));
    }

    @Test
    void shadowMorePermissiveRequiresReview() {
        var c = KycShadowComparisonClassifier.compare("FAIL", KycBusinessOutcome.PASS, false);
        assertThat(c.classification()).isEqualTo(KycShadowComparisonClassifier.SHADOW_MORE_PERMISSIVE);
        assertThat(c.reviewRequired()).isTrue();
        assertThat(c.reviewReason()).isEqualTo("REVIEW_REQUIRED");
    }

    @Test
    void providerFailureSemanticDifference() {
        var c = KycShadowComparisonClassifier.compare("FAIL", KycBusinessOutcome.MISSING_INFORMATION, true);
        assertThat(c.classification()).isEqualTo(
                KycShadowComparisonClassifier.PROVIDER_FAILURE_SEMANTIC_DIFFERENCE);
    }

    @Test
    void computeKycOutcomeSourceUnchanged() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/com/los/core/service/kyc/KycOrchestrationServiceImpl.java"));
        assertThat(src).contains("public Map<String, Object> computeKycOutcome");
        // KYC-5 must not rewrite outcome aggregation to Decision Policy
        assertThat(src).doesNotContain("ShadowKycPolicyEvaluationService");
        assertThat(src).doesNotContain("allowCanonicalAuthority=true");
    }

    @Test
    void shadowExceptionDoesNotBreakProductionHookContract() {
        // afterKycWorkflowSafe swallows — facade with null repos returns blocked map, no throw
        assertThat(service.evaluate(null).get("status")).isEqualTo("BLOCKED");
    }

    @Test
    void certificationNeverProductionReady() {
        Map<String, Object> r = eval(v1(), List.of(ShadowKycPolicyEvaluationService.panPass()),
                Map.of("requested_amount", 100_000, "borrower_type", "INDIVIDUAL"), "PASS");
        assertThat(String.valueOf(r.get("certificationStatus"))).isNotEqualTo("PRODUCTION_READY");
        assertThat(r.get("certificationStatus")).isIn("SHADOW_EVALUATION_READY", "INSUFFICIENT_EVIDENCE", "BLOCKED");
    }

    private CiExecutablePolicyPackage v1() {
        return GoldenKycShadowPackageFactory.decisionPolicyKycV1(tenant);
    }

    private Map<String, Object> eval(
            CiExecutablePolicyPackage pkg,
            List<NormalizedKycFactBuilder.StepEvidence> steps,
            Map<String, Object> fields,
            String production
    ) {
        return service.evaluate(ShadowKycEvaluationRequest.builder()
                .tenantId(tenant)
                .policyPackage(pkg)
                .routingOutcome("EXACTLY_ONE")
                .stepEvidence(steps)
                .applicationFields(fields)
                .applicationHints(Map.of("panPresent", true))
                .productionKycOutcome(production)
                .persist(false)
                .build());
    }
}
