package com.los.core.creditintelligence.decisionpolicy;

import com.los.core.creditintelligence.decisionpolicy.kyc.DecisionPolicyDesignReadiness;
import com.los.core.creditintelligence.decisionpolicy.kyc.KycCapabilityEvidence;
import com.los.core.creditintelligence.decisionpolicy.kyc.KycIntegrationRoutingProbe;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.service.PolicyImplementabilityService;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import com.los.core.model.enums.KycStepType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KYC-4 — Unified Decision Policy design readiness.
 */
class Kyc4DecisionPolicyReadinessTest {

    @Test
    void panWorkflowAndImplementationReady() {
        CiPolicyRuleCandidate rule = kycRule("PAN_VERIFY", "VERIFICATION", false, false);
        Map<String, Object> assessed = DecisionPolicyDesignReadiness.assessKycPath(
                "kyc.pan.verified", rule, KycIntegrationRoutingProbe.designTimeOnly());
        assertThat(assessed.get("status")).isEqualTo(PolicyImplementabilityService.READY);
        assertThat(assessed.get("workflowCapability")).isEqualTo("PAN_VERIFY");
        assertThat(assessed.get("primarySource")).isEqualTo("Karza");
        assertThat(assessed.get("providerImplementationPresent")).isEqualTo(true);
    }

    @Test
    void workflowMissingWhenNoStepCapability() {
        CiPolicyRuleCandidate rule = kycRule("UNKNOWN_KYC", "VERIFICATION", false, false);
        Map<String, Object> assessed = DecisionPolicyDesignReadiness.assessKycPath(
                "kyc.unknown.verified", rule, KycIntegrationRoutingProbe.designTimeOnly());
        assertThat(assessed.get("status")).isEqualTo(PolicyImplementabilityService.MAPPING_REQUIRED);
    }

    @Test
    void emailOtpMappedButNoWorkflowImplementation() {
        CiPolicyRuleCandidate rule = kycRule("EMAIL_OTP", "VERIFICATION", false, false);
        Map<String, Object> assessed = DecisionPolicyDesignReadiness.assessKycPath(
                "kyc.email.verified", rule, KycIntegrationRoutingProbe.designTimeOnly());
        assertThat(assessed.get("status")).isIn(
                DecisionPolicyDesignReadiness.ENGINEERING_REQUIRED,
                DecisionPolicyDesignReadiness.WORKFLOW_CONFIGURATION_REQUIRED);
        assertThat(assessed.get("status")).isNotEqualTo(PolicyImplementabilityService.READY);
    }

    @Test
    void workflowExistsButIntegrationMissing() {
        KycIntegrationRoutingProbe liveEmpty = new KycIntegrationRoutingProbe() {
            @Override
            public boolean isLiveConfigurationProbe() {
                return true;
            }

            @Override
            public Optional<Routing> routingFor(KycStepType step) {
                return Optional.empty();
            }
        };
        CiPolicyRuleCandidate rule = kycRule("PAN_VERIFY", "VERIFICATION", false, false);
        Map<String, Object> assessed = DecisionPolicyDesignReadiness.assessKycPath(
                "kyc.pan.verified", rule, liveEmpty);
        assertThat(assessed.get("status"))
                .isEqualTo(DecisionPolicyDesignReadiness.INTEGRATION_CONFIGURATION_REQUIRED);
    }

    @Test
    void compatibleFallbackConfiguredReadyWithFallback() {
        KycIntegrationRoutingProbe liveFallback = new KycIntegrationRoutingProbe() {
            @Override
            public boolean isLiveConfigurationProbe() {
                return true;
            }

            @Override
            public Optional<Routing> routingFor(KycStepType step) {
                return Optional.of(new Routing(false, true, null, "Authbridge"));
            }
        };
        CiPolicyRuleCandidate rule = kycRule("PAN_VERIFY", "VERIFICATION", false, false);
        Map<String, Object> assessed = DecisionPolicyDesignReadiness.assessKycPath(
                "kyc.pan.verified", rule, liveFallback);
        assertThat(assessed.get("status")).isEqualTo(PolicyImplementabilityService.READY_WITH_FALLBACK);
    }

    @Test
    void emailOtpEnumWithoutImplementationNotReady() {
        assertThat(KycCapabilityEvidence.hasProviderImplementation(KycStepType.EMAIL_OTP)).isFalse();
        Map<String, Object> desc = KycCapabilityEvidence.describeStep(KycStepType.EMAIL_OTP);
        assertThat(desc.get("enumOnlyWithoutImplementation")).isEqualTo(true);
        assertThat(desc.get("providerImplementationPresent")).isEqualTo(false);
    }

    @Test
    void applicationInputExistsReady() {
        CiPolicyRuleCandidate rule = kycRule("APP_AMT", "ELIGIBILITY_CONDITION", false, false);
        Map<String, Object> assessed = DecisionPolicyDesignReadiness.assessKycPath(
                "application.requested_amount", rule, KycIntegrationRoutingProbe.designTimeOnly());
        assertThat(assessed.get("status")).isEqualTo(PolicyImplementabilityService.READY);
    }

    @Test
    void applicationInputAbsentRequiresField() {
        CiPolicyRuleCandidate rule = kycRule("APP_X", "ELIGIBILITY_CONDITION", false, false);
        Map<String, Object> assessed = DecisionPolicyDesignReadiness.assessKycPath(
                "application.unknown_custom_field_xyz", rule, KycIntegrationRoutingProbe.designTimeOnly());
        assertThat(assessed.get("status"))
                .isEqualTo(DecisionPolicyDesignReadiness.APPLICATION_INPUT_REQUIRED);
    }

    @Test
    void matchRuleWithoutMatcher() {
        CiPolicyRuleCandidate rule = kycRule("PAN_NAME", "MATCH_REQUIREMENT", true, false);
        Map<String, Object> assessed = DecisionPolicyDesignReadiness.assessKycPath(
                "kyc.pan.name_match", rule, KycIntegrationRoutingProbe.designTimeOnly());
        assertThat(assessed.get("status"))
                .isEqualTo(DecisionPolicyDesignReadiness.MATCH_CAPABILITY_REQUIRED);
    }

    @Test
    void governedManualVerification() {
        CiPolicyRuleCandidate rule = kycRule("MANUAL_KYC", "MANUAL_VERIFICATION", false, false);
        rule.getMetadata().put("verificationMode", "MANUAL");
        rule.getMetadata().put("requiredActor", "KYC Reviewer");
        rule.getMetadata().put("requiredEvidence", "KYC evidence pack");
        rule.getMetadata().put("outcomeOnSuccess", "PASS");
        Map<String, Object> assessed = DecisionPolicyDesignReadiness.assessKycPath(
                "kyc.pan.verified", rule, KycIntegrationRoutingProbe.designTimeOnly());
        assertThat(assessed.get("status")).isEqualTo(PolicyImplementabilityService.MANUAL_VERIFICATION);
    }

    @Test
    void unconfiguredManualVerificationBlocked() {
        CiPolicyRuleCandidate rule = kycRule("MANUAL_BAD", "MANUAL_VERIFICATION", false, false);
        // requirement type alone without evidence/actor/outcomes → incomplete when verificationMode not set
        // Stamp only type — isGovernedManual checks outcomeOnSuccess from KYC-3 stamp
        Map<String, Object> assessed = DecisionPolicyDesignReadiness.assessKycPath(
                "kyc.pan.verified", rule, KycIntegrationRoutingProbe.designTimeOnly());
        // Without outcome stamps, MANUAL_CONFIGURATION_REQUIRED
        assertThat(assessed.get("status"))
                .isIn(DecisionPolicyDesignReadiness.MANUAL_CONFIGURATION_REQUIRED,
                        PolicyImplementabilityService.MANUAL_VERIFICATION);
        // Force ungoverned
        rule.setMetadata(new LinkedHashMap<>(Map.of(
                "decisionDomain", "KYC",
                "kycRequirementType", "MANUAL_VERIFICATION",
                "verificationMode", "MANUAL"
        )));
        assessed = DecisionPolicyDesignReadiness.assessKycPath(
                "kyc.pan.verified", rule, KycIntegrationRoutingProbe.designTimeOnly());
        assertThat(assessed.get("status"))
                .isEqualTo(DecisionPolicyDesignReadiness.MANUAL_CONFIGURATION_REQUIRED);
    }

    @Test
    void platformGuardrailUnavailableBlocks() {
        CiPolicyRuleCandidate rule = kycRule("GUARD", "REGULATORY_GUARDRAIL", false, false);
        rule.getMetadata().put("guardrailClass", "PLATFORM_GUARDRAIL");
        rule.getMetadata().put("unsupportedCapability", true);
        Map<String, Object> assessed = DecisionPolicyDesignReadiness.assessKycPath(
                "kyc.pep.screened", rule, KycIntegrationRoutingProbe.designTimeOnly());
        assertThat(DecisionPolicyDesignReadiness.isBlockedDesignStatus(String.valueOf(assessed.get("status"))))
                .isTrue();
    }

    @Test
    void unsupportedPepUboNotMarkedReady() {
        CiPolicyRuleCandidate rule = kycRule("PEP", "VERIFICATION", false, true);
        Map<String, Object> assessed = DecisionPolicyDesignReadiness.assessKycPath(
                "kyc.pep.screened", rule, KycIntegrationRoutingProbe.designTimeOnly());
        assertThat(assessed.get("status")).isNotEqualTo(PolicyImplementabilityService.READY);
        assertThat(assessed.get("status")).isEqualTo(PolicyImplementabilityService.NOT_IMPLEMENTABLE);
    }

    @Test
    void creditReadinessRegressionBankingBre() throws Exception {
        PolicyStudioSession session = load("policy-fixtures/banking-bre/Banking_BRE.txt", "Banking_BRE");
        Map<String, Object> result = new PolicyImplementabilityService().assess(session);
        Map<String, Object> summary = cast(result.get("summary"));
        assertThat(summary.get("allowCanonicalAuthority")).isEqualTo(false);
        int pct = ((Number) summary.get("implementationReadinessPercent")).intValue();
        assertThat(pct).isLessThan(100);
        assertThat(((Number) summary.get("rulesIdentified")).intValue()).isGreaterThan(0);
    }

    @Test
    void scorecardLinkageHonestWhenScoreMentioned() throws Exception {
        PolicyStudioSession session = load("policy-fixtures/bureau-bre/Bureau_BRE.txt", "Bureau_BRE");
        Map<String, Object> result = new PolicyImplementabilityService().assess(session);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> domains = (List<Map<String, Object>>) result.get("decisionPolicyDomainReadiness");
        assertThat(domains).isNotNull();
        // Bureau may surface Risk/Score or Credit — never claim Policy Studio owns scorecard execution
        boolean anyScoreOwned = domains.stream().anyMatch(d ->
                Boolean.TRUE.equals(d.get("scorecardOwnedByPolicyStudio")));
        assertThat(anyScoreOwned).isFalse();
    }

    @Test
    void kycSampleProducesDomainCardsAndGaps() throws Exception {
        PolicyStudioSession session = load(
                "policy-fixtures/kyc-bre/Kyc_Eligibility_Validation_Sample.txt",
                "Kyc_Eligibility_Validation_Sample");
        Map<String, Object> result = new PolicyImplementabilityService().assess(session);
        assertThat(result.get("allowCanonicalAuthority")).isEqualTo(false);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> kycReqs = (List<Map<String, Object>>) result.get("kycRequirementReadiness");
        assertThat(kycReqs).isNotEmpty();

        boolean hasMatchGap = kycReqs.stream().anyMatch(r ->
                DecisionPolicyDesignReadiness.MATCH_CAPABILITY_REQUIRED.equals(String.valueOf(r.get("status"))));
        boolean hasReadyPan = kycReqs.stream().anyMatch(r ->
                String.valueOf(r.get("dataElementCode")).contains("pan.verified")
                        && PolicyImplementabilityService.READY.equals(String.valueOf(r.get("status"))));
        assertThat(hasMatchGap || hasReadyPan).as("KYC sample should show realistic mix").isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Object> overall = (Map<String, Object>) result.get("decisionPolicyOverall");
        assertThat(overall.get("status")).isIn(
                DecisionPolicyDesignReadiness.OVERALL_NEEDS_ATTENTION,
                DecisionPolicyDesignReadiness.OVERALL_BLOCKED,
                DecisionPolicyDesignReadiness.OVERALL_IMPLEMENTATION_READY);
        // We WANT realistic gaps for demo sample
        assertThat(overall.get("demoLabel")).isEqualTo("DEMO POLICY / VALIDATION SAMPLE");

        assertThat(String.valueOf(result.get("analystMessage")))
                .contains("configured application fields");
        assertThat(result.get("nextActions")).isInstanceOf(List.class);
        assertThat(result.get("designTimeVsRuntimeNote")).asString().contains("runtime");
    }

    @Test
    void criticalKycGapBlocksDecisionPolicyDraftReadiness() throws Exception {
        PolicyStudioSession session = load(
                "policy-fixtures/kyc-bre/Kyc_Eligibility_Validation_Sample.txt",
                "Kyc_Eligibility_Validation_Sample");
        Map<String, Object> result = new PolicyImplementabilityService().assess(session);
        Map<String, Object> summary = cast(result.get("summary"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> kycReqs = (List<Map<String, Object>>) result.get("kycRequirementReadiness");
        long criticalBlocked = kycReqs.stream()
                .filter(r -> Boolean.TRUE.equals(r.get("critical")))
                .filter(r -> DecisionPolicyDesignReadiness.isBlockedDesignStatus(String.valueOf(r.get("status"))))
                .count();
        if (criticalBlocked > 0) {
            assertThat(summary.get("draftBlockedByCriticalKycGap")).isEqualTo(true);
            assertThat(summary.get("draftBlockedByCriticalDataGap")).isEqualTo(true);
        }
    }

    @Test
    void designTimeReadyDistinctFromRuntimeOutage() {
        CiPolicyRuleCandidate rule = kycRule("PAN_VERIFY", "VERIFICATION", false, false);
        Map<String, Object> assessed = DecisionPolicyDesignReadiness.assessKycPath(
                "kyc.pan.verified", rule, KycIntegrationRoutingProbe.designTimeOnly());
        assertThat(assessed.get("status")).isEqualTo(PolicyImplementabilityService.READY);
        assertThat(assessed.get("runtimeOutageDistinct")).isEqualTo(true);
        assertThat(assessed.get("designTimeAssessment")).isEqualTo(true);
    }

    @Test
    void noProviderSelectionInPolicyStudioMetadata() throws Exception {
        PolicyStudioSession session = load(
                "policy-fixtures/kyc-bre/Kyc_Eligibility_Validation_Sample.txt",
                "Kyc_Eligibility_Validation_Sample");
        for (CiPolicyRuleCandidate r : session.getRuleCandidates()) {
            Map<String, Object> meta = r.getMetadata();
            if (meta == null) {
                continue;
            }
            if (meta.containsKey("policySelectsProvider")) {
                assertThat(meta.get("policySelectsProvider")).isEqualTo(false);
            }
            assertThat(meta).doesNotContainKey("selectedProvider");
            assertThat(meta).doesNotContainKey("providerBean");
        }
    }

    @Test
    void foirDscrMissingInputsSurfaceAsBlockedWhenPresent() {
        // Synthetic rule path for FOIR without income metric
        CiPolicyRuleCandidate rule = CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .clauseId(UUID.randomUUID())
                .systemRuleId("FOIR_LIMIT")
                .ruleType("HARD")
                .expression(Map.of("op", "LT", "metric", "credit.foir", "value", 0.5))
                .metadata(Map.of("decisionDomain", "LIMIT"))
                .scope(Map.of("decisionDomain", "LIMIT"))
                .build();
        PolicyStudioSession session = new PolicyStudioSession();
        session.getRuleCandidates().add(rule);
        Map<String, Object> result = new PolicyImplementabilityService().assess(session);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) result.get("rules");
        assertThat(rules).isNotEmpty();
        String st = String.valueOf(rules.get(0).get("implementability"));
        assertThat(st).isNotEqualTo(PolicyImplementabilityService.READY);
    }

    private static CiPolicyRuleCandidate kycRule(
            String sysId,
            String reqType,
            boolean matchMissing,
            boolean unsupported
    ) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("decisionDomain", "KYC");
        meta.put("kycRequirementType", reqType);
        meta.put("matchCapabilityMissing", matchMissing);
        meta.put("unsupportedCapability", unsupported);
        meta.put("policySelectsProvider", false);
        return CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .clauseId(UUID.randomUUID())
                .systemRuleId(sysId)
                .ruleType("HARD")
                .metadata(meta)
                .scope(Map.of("decisionDomain", "KYC"))
                .expression(Map.of())
                .build();
    }

    @Test
    void bureauBreDoesNotInventKycDomainFromMultiplePanWording() throws Exception {
        PolicyStudioSession session = load("policy-fixtures/bureau-bre/Bureau_BRE.txt", "Bureau_BRE");
        Map<String, Object> result = new PolicyImplementabilityService().assess(session);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> kycReqs = (List<Map<String, Object>>) result.get("kycRequirementReadiness");
        assertThat(kycReqs == null ? List.of() : kycReqs).isEmpty();
        Map<String, Object> summary = cast(result.get("summary"));
        assertThat(summary.get("draftBlockedByCriticalKycGap")).isEqualTo(false);
        assertThat(String.valueOf(result.get("analystMessage"))).doesNotContain("complete Decision Policy");
    }

    private PolicyStudioSession load(String resource, String name) throws Exception {
        String text = new String(
                getClass().getClassLoader().getResourceAsStream(resource).readAllBytes(),
                StandardCharsets.UTF_8);
        return new PolicyStudioOrchestrator().processUpload(
                UUID.randomUUID(), name, "TXT", text, "test", name + ".txt");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object o) {
        return (Map<String, Object>) o;
    }
}
