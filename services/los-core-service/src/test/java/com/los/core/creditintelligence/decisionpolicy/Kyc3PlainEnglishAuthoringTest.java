package com.los.core.creditintelligence.decisionpolicy;

import com.los.core.creditintelligence.decisionpolicy.kyc.KycBusinessOutcome;
import com.los.core.creditintelligence.decisionpolicy.kyc.KycPolicyAuthoringSupport;
import com.los.core.creditintelligence.decisionpolicy.kyc.KycPolicyWorkflowBoundary;
import com.los.core.creditintelligence.decisionpolicy.kyc.KycStepOutcomeSemantics;
import com.los.core.creditintelligence.policystudio.service.PolicyClauseExtractor;
import com.los.core.model.enums.StepOutcome;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KYC-3 plain-English authoring — classification, DSL, domains, safety.
 * Does not wire production KYC runtime.
 */
class Kyc3PlainEnglishAuthoringTest {

    private final PolicyClauseExtractor extractor = new PolicyClauseExtractor();

    @Test
    void panVerificationClauseMapsToKycDomain() {
        var c = KycPolicyAuthoringSupport.classify("PAN must be verified for all applicants.");
        assertThat(c.kycRelated()).isTrue();
        assertThat(c.domain()).isEqualTo(DecisionPolicyDomain.KYC);
        assertThat(c.requirementType()).isEqualTo(KycRequirementType.VERIFICATION);
        assertThat(c.primaryFact()).isEqualTo("kyc.pan.verified");
        assertThat(c.dslExpression().get("op")).isEqualTo("EQ");
        assertThat(c.onSuccess()).isEqualTo(KycBusinessOutcome.PASS.name());
        assertThat(c.onFailure()).isEqualTo(KycBusinessOutcome.FAIL.name());
        assertThat(c.onMissing()).isEqualTo(KycBusinessOutcome.MISSING_INFORMATION.name());
    }

    @Test
    void companyCinGstMapsToEligibility() {
        var c = KycPolicyAuthoringSupport.classify("Company borrowers must have valid CIN and GSTIN.");
        assertThat(c.domain()).isIn(DecisionPolicyDomain.KYC, DecisionPolicyDomain.ELIGIBILITY);
        assertThat(c.requirementType()).isEqualTo(KycRequirementType.ELIGIBILITY_CONDITION);
        assertThat(c.factPaths()).contains("kyc.cin.verified", "kyc.gstin.verified");
        assertThat(c.dslExpression().get("op")).isEqualTo("AND");
    }

    @Test
    void vkycAmountThresholdIsBoundaryCondition() {
        var c = KycPolicyAuthoringSupport.classify("Video KYC is mandatory for loans above ₹5 lakh.");
        assertThat(c.requirementType()).isEqualTo(KycRequirementType.BOUNDARY_CONDITION);
        assertThat(c.primaryFact()).isEqualTo("kyc.vkyc.completed");
        assertThat(c.scopeHints()).containsKey("requestedAmount");
        assertThat(c.dslExpression().get("op")).isEqualTo("AND");
    }

    @Test
    void nameMismatchIsMatchRequirement() {
        var c = KycPolicyAuthoringSupport.classify(
                "If PAN name differs from the application name, the case must be referred to KYC Review.");
        assertThat(c.requirementType()).isIn(
                KycRequirementType.MATCH_REQUIREMENT, KycRequirementType.MANUAL_VERIFICATION);
        assertThat(c.matchCapabilityMissing()).isTrue();
        assertThat(c.onFailure()).isEqualTo(KycBusinessOutcome.REFER.name());
    }

    @Test
    void manualReviewClauseIsRefer() {
        var c = KycPolicyAuthoringSupport.classify(
                "If PAN name mismatch cannot be resolved automatically, refer to KYC Reviewer.");
        assertThat(c.requirementType()).isEqualTo(KycRequirementType.MANUAL_VERIFICATION);
        assertThat(c.onFailure()).isEqualTo(KycBusinessOutcome.REFER.name());
    }

    @Test
    void missingKycInformationIsMissingInformationOutcome() {
        var c = KycPolicyAuthoringSupport.classify(
                "If KYC information is incomplete, credit underwriting must not proceed.");
        assertThat(c.domain()).isEqualTo(DecisionPolicyDomain.ELIGIBILITY);
        assertThat(c.onMissing()).isEqualTo(KycBusinessOutcome.MISSING_INFORMATION.name());
        assertThat(c.primaryFact()).isEqualTo("kyc.overall.outcome");
    }

    @Test
    void providerUnavailableSemanticsRemainNonFail() {
        var classified = KycStepOutcomeSemantics.classify(
                StepOutcome.FAILURE, "No registered provider for PAN_VERIFY", Map.of());
        assertThat(classified.businessOutcome()).isEqualTo(KycBusinessOutcome.MISSING_INFORMATION);
        assertThat(classified.businessOutcome()).isNotEqualTo(KycBusinessOutcome.FAIL);

        var authored = KycPolicyAuthoringSupport.classify("PAN must be verified for all applicants.");
        assertThat(authored.onMissing()).isEqualTo(KycBusinessOutcome.MISSING_INFORMATION.name());
        assertThat(authored.onMissing()).isNotEqualTo(KycBusinessOutcome.FAIL.name());
    }

    @Test
    void platformGuardrailNotEditable() {
        Map<String, Object> meta = DecisionPolicyRuleMetadata.stamp(
                Map.of(),
                DecisionPolicyDomain.KYC,
                KycRequirementType.REGULATORY_GUARDRAIL,
                PolicyGuardrailClass.PLATFORM_GUARDRAIL);
        assertThat(DecisionPolicyRuleMetadata.isStudioEditable(meta)).isFalse();
        meta.put("displayedInStudio", true);
        assertThat(DecisionPolicyRuleMetadata.isStudioEditable(meta)).isFalse();
    }

    @Test
    void unsupportedPepMapsToDataSourceRequired() {
        var c = KycPolicyAuthoringSupport.classify("PEP screening must be completed for all applicants.");
        assertThat(c.unsupportedCapability()).isTrue();
        assertThat(c.dslExpression().get("mappingStatus")).isEqualTo("DATA_SOURCE_REQUIRED");
        assertThat(c.primaryFact()).isEqualTo("kyc.pep.screened");
    }

    @Test
    void kycRuleProducesExistingDslWithoutProviderActions() {
        var c = KycPolicyAuthoringSupport.classify("PAN must be verified for all applicants.");
        String json = String.valueOf(c.dslExpression());
        assertThat(json).doesNotContain("CALL_PROVIDER");
        assertThat(json).doesNotContain("RUN_VKYC");
        assertThat(c.dslExpression()).containsKey("op");
    }

    @Test
    void policyDoesNotSelectProviderOrEnqueueWorkflow() {
        Map<String, Object> boundary = KycPolicyWorkflowBoundary.ownershipManifest();
        assertThat(boundary.get("policySelectsProvider")).isEqualTo(false);
        assertThat(boundary.get("policyEnqueuesWorkflowStep")).isEqualTo(false);
        assertThat(boundary.get("allowCanonicalAuthority")).isEqualTo(false);

        Map<String, Object> meta = DecisionPolicyRuleMetadata.stamp(
                null, DecisionPolicyDomain.KYC, KycRequirementType.VERIFICATION,
                PolicyGuardrailClass.NBFC_CONFIGURABLE);
        assertThat(meta.get(DecisionPolicyRuleMetadata.KEY_POLICY_SELECTS_PROVIDER)).isEqualTo(false);
        assertThat(meta.get(DecisionPolicyRuleMetadata.KEY_POLICY_ENQUEUES_WORKFLOW)).isEqualTo(false);
    }

    @Test
    void detectKindPreservesBankingAndBureau() throws Exception {
        String banking = Files.readString(Path.of(
                "src/test/resources/policy-fixtures/banking-bre/Banking_BRE.txt"));
        String bureau = Files.readString(Path.of(
                "src/test/resources/policy-fixtures/bureau-bre/Bureau_BRE.txt"));
        String kyc = Files.readString(Path.of(
                "src/test/resources/policy-fixtures/kyc-bre/Kyc_Eligibility_Validation_Sample.txt"));
        assertThat(extractor.detectKind(banking)).isEqualTo(PolicyClauseExtractor.FixtureKind.BANKING_BRE);
        assertThat(extractor.detectKind(bureau)).isEqualTo(PolicyClauseExtractor.FixtureKind.BUREAU_BRE);
        assertThat(extractor.detectKind(kyc)).isEqualTo(PolicyClauseExtractor.FixtureKind.KYC_BRE);
    }

    @Test
    void creditOnlyPolicyDefaultsToCreditDomain() {
        assertThat(DecisionPolicyDomain.fromMetadata(null)).isEqualTo(DecisionPolicyDomain.CREDIT);
        Map<String, Object> meta = DecisionPolicyRuleMetadata.stamp(
                Map.of("provider", "x"), DecisionPolicyDomain.CREDIT, null, PolicyGuardrailClass.UNRESOLVED);
        assertThat(DecisionPolicyRuleMetadata.domainOf(meta, Map.of())).isEqualTo(DecisionPolicyDomain.CREDIT);
    }

    @Test
    void mixedKycCreditDocumentClassified() {
        String text = """
                DEMO POLICY / VALIDATION SAMPLE
                KYC & Eligibility
                • PAN must be verified for all applicants.
                Credit Underwriting
                • Bureau score must be equal or above 650 for scored applicants.
                """;
        assertThat(extractor.detectKind(text)).isEqualTo(PolicyClauseExtractor.FixtureKind.KYC_BRE);
        var clauses = extractor.extract(UUID.randomUUID(), text);
        assertThat(clauses.stream().anyMatch(KycPolicyAuthoringSupport::isKycClause)).isTrue();
        assertThat(clauses.stream().anyMatch(c ->
                c.getSourceText() != null && c.getSourceText().toLowerCase().contains("bureau score"))).isTrue();
    }

    @Test
    void productionRuntimeUnchangedInSource() throws Exception {
        Path flow = Path.of("src/main/java/com/los/core/service/loan/LoanApplicationFlowService.java");
        if (!Files.exists(flow)) {
            flow = Path.of("los-core-service/src/main/java/com/los/core/service/loan/LoanApplicationFlowService.java");
        }
        String src = Files.readString(flow);
        assertThat(src).contains("underwriteApplication");
        assertThat(src).contains("computeKycOutcome");
        assertThat(src).doesNotContain("KycPolicyAuthoringSupport");
        assertThat(src).doesNotContain("autoUnderwriteAfterKyc");

        Path kyc = Path.of("src/main/java/com/los/core/service/kyc/KycOrchestrationServiceImpl.java");
        if (!Files.exists(kyc)) {
            kyc = Path.of("los-core-service/src/main/java/com/los/core/service/kyc/KycOrchestrationServiceImpl.java");
        }
        String kycSrc = Files.readString(kyc);
        assertThat(kycSrc).doesNotContain("KycPolicyAuthoringSupport");
        assertThat(kycSrc).doesNotContain("DecisionPolicyRuleMetadata");
    }
}
