package com.los.core.creditintelligence.decisionpolicy;

import com.los.core.creditintelligence.decisionpolicy.kyc.KycBusinessOutcome;
import com.los.core.creditintelligence.decisionpolicy.kyc.KycFactCatalog;
import com.los.core.creditintelligence.decisionpolicy.kyc.KycPolicyWorkflowBoundary;
import com.los.core.creditintelligence.decisionpolicy.kyc.KycStepOutcomeSemantics;
import com.los.core.creditintelligence.decisionpolicy.kyc.KycTechnicalStatus;
import com.los.core.creditintelligence.decisionpolicy.kyc.NormalizedKycFactBuilder;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import com.los.core.creditintelligence.policystudio.service.BusinessDataSourceCatalog;
import com.los.core.creditintelligence.policystudio.service.PolicyImplementabilityService;
import com.los.core.creditintelligence.validation.service.PolicyAuthoringRegistry;
import com.los.core.model.enums.KycStepType;
import com.los.core.model.enums.StepOutcome;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KYC-2 domain foundation: normalized facts, technical vs business outcomes,
 * DSL consumability, safety assertions. Does not change production KYC→UW gate.
 */
class Kyc2DomainFoundationTest {

    private final PolicyDslInterpreterV1 interpreter = new PolicyDslInterpreterV1();
    private final PolicyAuthoringRegistry registry = new PolicyAuthoringRegistry();

    @Test
    void panVerifiedMapsToPassFact() {
        var facts = NormalizedKycFactBuilder.buildFacts(List.of(
                new NormalizedKycFactBuilder.StepEvidence(
                        KycStepType.PAN_VERIFY, StepOutcome.SUCCESS, null, Map.of(), false, null)));
        assertThat(facts.get("kyc.pan.verified")).isEqualTo(true);
        assertThat(facts.get("kyc.pan.verification_status")).isEqualTo(KycBusinessOutcome.PASS.name());
        assertThat(facts.get("kyc.overall.outcome")).isEqualTo(KycBusinessOutcome.PASS.name());
    }

    @Test
    void panVerificationFailureMapsToFailFact() {
        var facts = NormalizedKycFactBuilder.buildFacts(List.of(
                new NormalizedKycFactBuilder.StepEvidence(
                        KycStepType.PAN_VERIFY, StepOutcome.FAILURE, "Invalid PAN", Map.of(), false, null)));
        assertThat(facts.get("kyc.pan.verified")).isEqualTo(false);
        assertThat(facts.get("kyc.pan.verification_status")).isEqualTo(KycBusinessOutcome.FAIL.name());
        assertThat(facts.get("kyc.overall.outcome")).isEqualTo(KycBusinessOutcome.FAIL.name());
    }

    @Test
    void providerUnavailableIsNotBorrowerFail() {
        var classified = KycStepOutcomeSemantics.classify(
                StepOutcome.FAILURE, "No registered provider for PAN_VERIFY", Map.of());
        assertThat(classified.technicalStatus()).isEqualTo(KycTechnicalStatus.UNAVAILABLE);
        assertThat(classified.businessOutcome()).isEqualTo(KycBusinessOutcome.MISSING_INFORMATION);
        assertThat(classified.businessOutcome()).isNotEqualTo(KycBusinessOutcome.FAIL);

        var facts = NormalizedKycFactBuilder.buildFacts(List.of(
                new NormalizedKycFactBuilder.StepEvidence(
                        KycStepType.PAN_VERIFY, StepOutcome.FAILURE,
                        "Provider unavailable", Map.of(), false, null)));
        assertThat(facts.get("kyc.pan.verified")).isNull();
        assertThat(facts.get("kyc.pan.verification_status"))
                .isEqualTo(KycBusinessOutcome.MISSING_INFORMATION.name());
        assertThat(facts.get("kyc.overall.outcome"))
                .isEqualTo(KycBusinessOutcome.MISSING_INFORMATION.name());
    }

    @Test
    void timeoutIsNotBorrowerFail() {
        var classified = KycStepOutcomeSemantics.classify(
                StepOutcome.ERROR, "Connection timed out", Map.of());
        assertThat(classified.technicalStatus()).isEqualTo(KycTechnicalStatus.TIMEOUT);
        assertThat(classified.businessOutcome()).isEqualTo(KycBusinessOutcome.MISSING_INFORMATION);
    }

    @Test
    void missingPanResultIsMissingInformation() {
        var facts = NormalizedKycFactBuilder.buildFacts(List.of(
                new NormalizedKycFactBuilder.StepEvidence(
                        KycStepType.PAN_VERIFY, StepOutcome.PENDING, null, null, false, null)));
        assertThat(facts.containsKey("kyc.pan.verified")).isFalse();
        assertThat(facts.get("kyc.pan.verification_status"))
                .isEqualTo(KycBusinessOutcome.MISSING_INFORMATION.name());
        assertThat(facts.get("kyc.overall.outcome"))
                .isEqualTo(KycBusinessOutcome.MISSING_INFORMATION.name());
    }

    @Test
    void manualReviewStepMapsToRefer() {
        var classified = KycStepOutcomeSemantics.classify(
                StepOutcome.MANUAL_REVIEW, null, Map.of("note", "name discrepancy"));
        assertThat(classified.businessOutcome()).isEqualTo(KycBusinessOutcome.REFER);
        assertThat(classified.businessOutcome()).isNotEqualTo(KycBusinessOutcome.FAIL);

        var facts = NormalizedKycFactBuilder.buildFacts(List.of(
                new NormalizedKycFactBuilder.StepEvidence(
                        KycStepType.PAN_VERIFY, StepOutcome.MANUAL_REVIEW, null, Map.of(), false, false)));
        assertThat(facts.get("kyc.overall.outcome")).isEqualTo(KycBusinessOutcome.REFER.name());
        assertThat(facts.get("kyc.pan.name_match")).isEqualTo(false);
    }

    @Test
    void companyCinGstFacts() {
        var facts = NormalizedKycFactBuilder.buildFacts(
                List.of(
                        new NormalizedKycFactBuilder.StepEvidence(
                                KycStepType.CIN_MCA21, StepOutcome.SUCCESS, null, Map.of(), false, null),
                        new NormalizedKycFactBuilder.StepEvidence(
                                KycStepType.GSTIN_VERIFY, StepOutcome.SUCCESS, null, Map.of(), false, null)),
                Map.of("cinPresent", true, "gstinPresent", true),
                null);
        assertThat(facts.get("kyc.cin.present")).isEqualTo(true);
        assertThat(facts.get("kyc.cin.verified")).isEqualTo(true);
        assertThat(facts.get("kyc.gstin.present")).isEqualTo(true);
        assertThat(facts.get("kyc.gstin.verified")).isEqualTo(true);
    }

    @Test
    void vkycCompletedFact() {
        var facts = NormalizedKycFactBuilder.buildFacts(
                List.of(),
                Map.of("vkycStatus", "COMPLETED"),
                null);
        assertThat(facts.get("kyc.vkyc.completed")).isEqualTo(true);
        assertThat(facts.get("kyc.vkyc.result")).isEqualTo("COMPLETED");
    }

    @Test
    void fallbackUsedDoesNotAlterVerifiedBusinessResult() {
        var facts = NormalizedKycFactBuilder.buildFacts(List.of(
                new NormalizedKycFactBuilder.StepEvidence(
                        KycStepType.PAN_VERIFY, StepOutcome.SUCCESS, null, Map.of(), true, null)));
        assertThat(facts.get("kyc.pan.verified")).isEqualTo(true);
        assertThat(facts.get("kyc.pan.fallback_used")).isEqualTo(true);
        assertThat(facts.get("kyc.pan.technical_status")).isEqualTo(KycTechnicalStatus.FALLBACK_USED.name());
        assertThat(facts.get("kyc.pan.verification_status")).isEqualTo(KycBusinessOutcome.PASS.name());
    }

    @Test
    void normalizedFactsConsumableByDsl() {
        Map<String, Object> facts = Map.of(
                "kyc.pan.verified", true,
                "kyc.gstin.verified", true,
                "kyc.cin.verified", true,
                "kyc.vkyc.completed", true,
                "kyc.pan.name_match", false
        );
        Map<String, Object> app = Map.of(
                "borrower_type", "COMPANY",
                "requested_amount", 600000
        );
        var ctx = new PolicyDslInterpreterV1.EvaluationContext(
                Map.of(), facts, Map.of(), app, null, "DATA_INSUFFICIENT");

        assertThat(interpreter.evaluate(
                PolicyDsl.eq(PolicyDsl.fact("kyc.pan.verified"), Map.of("const", true)), ctx))
                .isEqualTo("PASS");
        assertThat(interpreter.evaluate(
                PolicyDsl.eq(PolicyDsl.fact("kyc.gstin.verified"), Map.of("const", true)), ctx))
                .isEqualTo("PASS");
        assertThat(interpreter.evaluate(PolicyDsl.and(
                PolicyDsl.eq(PolicyDsl.appField("borrower_type"), Map.of("const", "COMPANY")),
                PolicyDsl.eq(PolicyDsl.fact("kyc.cin.verified"), Map.of("const", true))
        ), ctx)).isEqualTo("PASS");
        assertThat(interpreter.evaluate(PolicyDsl.and(
                PolicyDsl.gt(PolicyDsl.appField("requested_amount"), Map.of("const", 500000)),
                PolicyDsl.eq(PolicyDsl.fact("kyc.vkyc.completed"), Map.of("const", true))
        ), ctx)).isEqualTo("PASS");

        // name mismatch → conceptual REFER via IF
        assertThat(interpreter.evaluate(PolicyDsl.iff(
                PolicyDsl.eq(PolicyDsl.fact("kyc.pan.name_match"), Map.of("const", false)),
                Map.of("const", "REFER"),
                Map.of("const", "PASS")
        ), ctx)).isEqualTo("REFER");

        // missing fact → DATA_INSUFFICIENT (MISSING_INFORMATION semantic)
        var ctxMissing = PolicyDslInterpreterV1.EvaluationContext.of(Map.of(), Map.of(), null);
        assertThat(interpreter.evaluate(
                PolicyDsl.eq(PolicyDsl.fact("kyc.pan.verified"), Map.of("const", true)), ctxMissing))
                .isEqualTo("DATA_INSUFFICIENT");
    }

    @Test
    void platformGuardrailCannotBeSilentlyMadeEditable() {
        Map<String, Object> meta = DecisionPolicyRuleMetadata.stamp(
                Map.of(),
                DecisionPolicyDomain.KYC,
                KycRequirementType.REGULATORY_GUARDRAIL,
                PolicyGuardrailClass.PLATFORM_GUARDRAIL);
        assertThat(DecisionPolicyRuleMetadata.isStudioEditable(meta)).isFalse();
        assertThat(DecisionPolicyRuleMetadata.guardrailOf(meta))
                .isEqualTo(PolicyGuardrailClass.PLATFORM_GUARDRAIL);
        // Presentation flag must not flip editability
        meta.put("displayedInStudio", true);
        assertThat(DecisionPolicyRuleMetadata.isStudioEditable(meta)).isFalse();
    }

    @Test
    void unsupportedCapabilityNotMarkedAvailable() {
        for (String code : KycFactCatalog.unsupportedCapabilityCodes()) {
            assertThat(registry.hasCanonicalPath(code)).isFalse();
        }
        List<Map<String, Object>> readiness = DecisionPolicySectionReadiness.kycElementReadiness(registry);
        assertThat(readiness).anySatisfy(row -> {
            assertThat(row.get("dataElementCode")).isEqualTo("kyc.pep.screened");
            assertThat(row.get("status")).isEqualTo("NOT_AVAILABLE");
            assertThat(row.get("implementability")).isEqualTo("BLOCKED");
        });
    }

    @Test
    void kycFactsRegisteredAndPanReadinessReady() {
        assertThat(registry.hasCanonicalPath("kyc.pan.verified")).isTrue();
        assertThat(registry.hasCanonicalPath("kyc.vkyc.completed")).isTrue();
        Map<String, Object> pan = BusinessDataSourceCatalog.describe("kyc.pan.verified");
        assertThat(pan.get("category")).isEqualTo("KYC");
        assertThat(pan.get("businessName")).isEqualTo("PAN Verification");
        assertThat(String.valueOf(pan.get("primarySource"))).containsIgnoringCase("PAN");
        assertThat((List<?>) pan.get("fallbackSources")).isNotEmpty();

        Map<String, Object> impl = new PolicyImplementabilityService(registry).assess(
                new com.los.core.creditintelligence.policystudio.model.PolicyStudioSession());
        assertThat(impl.get("allowCanonicalAuthority")).isEqualTo(false);
        assertThat(impl.get("decisionPolicySections")).isInstanceOf(List.class);
        assertThat(impl.get("kycDataReadiness")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> kyc = (List<Map<String, Object>>) impl.get("kycDataReadiness");
        assertThat(kyc).anySatisfy(row -> {
            assertThat(row.get("dataElementCode")).isEqualTo("kyc.pan.verified");
            assertThat(row.get("status")).isEqualTo("READY");
            assertThat(row.get("automation")).isEqualTo("AUTOMATABLE");
        });
    }

    @Test
    void policyDoesNotSelectProvidersOrEnqueueWorkflow() {
        Map<String, Object> boundary = KycPolicyWorkflowBoundary.ownershipManifest();
        assertThat(boundary.get("policySelectsProvider")).isEqualTo(false);
        assertThat(boundary.get("policyEnqueuesWorkflowStep")).isEqualTo(false);
        assertThat(boundary.get("kyc2ChangesProductionUwGate")).isEqualTo(false);
        assertThat(boundary.get("kyc2AutoStartsUnderwriting")).isEqualTo(false);
        assertThat(boundary.get("allowCanonicalAuthority")).isEqualTo(false);

        Map<String, Object> meta = DecisionPolicyRuleMetadata.stamp(
                null, DecisionPolicyDomain.KYC, KycRequirementType.VERIFICATION,
                PolicyGuardrailClass.NBFC_CONFIGURABLE);
        assertThat(meta.get(DecisionPolicyRuleMetadata.KEY_POLICY_SELECTS_PROVIDER)).isEqualTo(false);
        assertThat(meta.get(DecisionPolicyRuleMetadata.KEY_POLICY_ENQUEUES_WORKFLOW)).isEqualTo(false);
    }

    @Test
    void creditPolicyDefaultAndDecisionPolicyAdditive() {
        assertThat(DecisionPolicyType.normalize(null)).isEqualTo(DecisionPolicyType.CREDIT_POLICY);
        assertThat(DecisionPolicyType.supportsCreditSection(DecisionPolicyType.CREDIT_POLICY)).isTrue();
        assertThat(DecisionPolicyType.supportsKycEligibilitySection(DecisionPolicyType.CREDIT_POLICY)).isFalse();
        assertThat(DecisionPolicyType.supportsKycEligibilitySection(DecisionPolicyType.DECISION_POLICY)).isTrue();
        assertThat(DecisionPolicyStages.isKycEligibilityStage("IDENTITY_KYC")).isTrue();
        assertThat(DecisionPolicyStages.stageForDomain(DecisionPolicyDomain.KYC))
                .isEqualTo(DecisionPolicyStages.KYC_ELIGIBILITY);
        assertThat(DecisionPolicyDomain.fromMetadata(null)).isEqualTo(DecisionPolicyDomain.CREDIT);
    }

    @Test
    void allowCanonicalAuthorityRemainsFalseInRegistry() {
        assertThat(registry.registry().get("allowCanonicalAuthority")).isEqualTo(false);
    }

    @Test
    void productionUwGateAndComputeKycOutcomeUnchangedInSource() throws Exception {
        Path flow = Path.of("src/main/java/com/los/core/service/loan/LoanApplicationFlowService.java");
        if (!Files.exists(flow)) {
            flow = Path.of("los-core-service/src/main/java/com/los/core/service/loan/LoanApplicationFlowService.java");
        }
        String flowSrc = Files.readString(flow);
        assertThat(flowSrc).contains("underwriteApplication");
        assertThat(flowSrc).contains("computeKycOutcome");
        assertThat(flowSrc).contains("kycPassEffective");
        // KYC-2 must not introduce auto-underwrite from KYC completion
        assertThat(flowSrc).doesNotContain("autoUnderwriteAfterKyc");
        assertThat(flowSrc).doesNotContain("NormalizedKycFactBuilder");

        Path kyc = Path.of("src/main/java/com/los/core/service/kyc/KycOrchestrationServiceImpl.java");
        if (!Files.exists(kyc)) {
            kyc = Path.of("los-core-service/src/main/java/com/los/core/service/kyc/KycOrchestrationServiceImpl.java");
        }
        String kycSrc = Files.readString(kyc);
        assertThat(kycSrc).contains("computeKycOutcome");
        // production aggregate still PASS/FAIL/INCOMPLETE — not replaced by REFER model
        assertThat(kycSrc).doesNotContain("KycBusinessOutcome");
        assertThat(kycSrc).doesNotContain("allowCanonicalAuthority=true");
    }

    @Test
    void referIsNotFailAndMissingNotZero() {
        assertThat(KycBusinessOutcome.REFER).isNotEqualTo(KycBusinessOutcome.FAIL);
        assertThat(KycBusinessOutcome.MISSING_INFORMATION.toDslOutcome()).isEqualTo("DATA_INSUFFICIENT");
        // missing verified fact must not coerce to false
        var empty = NormalizedKycFactBuilder.buildFacts(List.of());
        assertThat(empty.get("kyc.pan.verified")).isNull();
        assertThat(Boolean.FALSE.equals(empty.get("kyc.pan.verified"))).isFalse();
    }
}
