package com.los.core.service.readiness;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.DocumentStatus;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.model.entity.UnderwritingRuleSet;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.model.entity.WorkflowConfig;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LiveReadinessServicesTest {

    private final PolicyRequiredParameterExtractor extractor = new PolicyRequiredParameterExtractor();
    private final ProductReadinessValidator validator = new ProductReadinessValidator(extractor);
    private final DataParametersAdminService dataParams = new DataParametersAdminService();

    @Test
    void dataParameters_overview_exposesSourcesAndCounts() {
        Map<String, Object> overview = dataParams.overview();
        assertThat(overview.get("allowCanonicalAuthority")).isEqualTo(false);
        assertThat(overview.get("readModelOnly")).isEqualTo(true);
        assertThat(overview.get("sources")).asList().isNotEmpty();
        assertThat(overview.get("bySourceSummary")).asList().isNotEmpty();
        assertThat(overview.get("workflowProvides")).isInstanceOf(Map.class);
    }

    @Test
    void bureauPull_providesBureauScore() {
        Set<String> ids = WorkflowParameterProvidesCatalog.parametersProvidedByWorkflow(
                List.of(Map.of("step", "PAN_VERIFY"), Map.of("step", "BUREAU_PULL")),
                true, true);
        assertThat(ids).contains("bureau.score", "bureau.max_dpd_6m");
    }

    @Test
    void companyTermLoanWorkflow_withoutGstAnalysis_doesNotProvideGstTurnover() {
        Set<String> ids = WorkflowParameterProvidesCatalog.parametersProvidedByWorkflow(
                List.of(
                        Map.of("step", "PAN_VERIFY"),
                        Map.of("step", "GSTIN_VERIFY"),
                        Map.of("step", "BANK_PENNY_DROP")),
                true, true);
        assertThat(ids).contains("bureau.score"); // bureau via flag
        assertThat(ids).doesNotContain("gst.turnover.trailing_12m");
        assertThat(ids).doesNotContain("banking.avg_daily_balance_3m");
    }

    @Test
    void gstAnalysis_providesGstTurnover() {
        Set<String> ids = WorkflowParameterProvidesCatalog.parametersProvidedByWorkflowSteps(
                List.of(Map.of("stepType", "GST_ANALYSIS")));
        assertThat(ids).contains("gst.turnover.trailing_12m");
    }

    @Test
    void liveRuleSet_extractsBureauScoreRequirement() {
        UnderwritingRuleSet set = UnderwritingRuleSet.builder()
                .id(UUID.randomUUID())
                .name("Test")
                .borrowerType("COMPANY")
                .loanProduct("TERM_LOAN")
                .rulesJson(Map.of("rules", List.of(
                        Map.of("parameter", "BUREAU_SCORE", "operator", ">=", "value", 650))))
                .build();
        Map<String, Object> req = extractor.fromLiveRuleSet(set);
        assertThat(req.get("requiredParameterIds")).asList().contains("bureau.score");
    }

    @Test
    void readiness_detectsMissingAdbAndGst() {
        WorkflowConfig wf = WorkflowConfig.builder()
                .id(UUID.randomUUID())
                .name("Company Term Loan")
                .borrowerType("COMPANY")
                .loanProduct("TERM_LOAN")
                .active(true)
                .bureauEnabled(true)
                .autoPullBureauAfterKycSuccess(true)
                .steps(List.of(
                        Map.of("step", "PAN_VERIFY"),
                        Map.of("step", "GSTIN_VERIFY"),
                        Map.of("step", "BANK_PENNY_DROP")))
                .build();
        UnderwritingRuleSet rules = UnderwritingRuleSet.builder()
                .id(UUID.randomUUID())
                .name("SME Rules")
                .borrowerType("COMPANY")
                .loanProduct("TERM_LOAN")
                .active(true)
                .rulesJson(Map.of("rules", List.of(
                        Map.of("parameter", "BUREAU_SCORE"),
                        Map.of("parameter", "AVERAGE_BANK_BALANCE"),
                        Map.of("parameter", "ANNUAL_GST_TURNOVER"))))
                .build();
        UnderwritingScorecard sc = UnderwritingScorecard.builder()
                .id(UUID.randomUUID())
                .name("SME Risk")
                .borrowerType("COMPANY")
                .loanProduct("TERM_LOAN")
                .active(true)
                .scorecardJson(Map.of())
                .hardRulesJson(Map.of())
                .thresholdsJson(Map.of())
                .build();

        Map<String, Object> result = validator.validate(
                "COMPANY", "TERM_LOAN", wf, rules, sc, null, null, null);

        assertThat(result.get("status")).isEqualTo("NOT READY");
        assertThat(result.get("ready")).isEqualTo(false);
        assertThat(result.get("manualReviewPathExists")).isEqualTo(true);
        assertThat(result.get("rejectPathExists")).isEqualTo(true);
        assertThat(result.get("approveCamPathExists")).isEqualTo(true);
        assertThat(result.get("gaps")).asList().isNotEmpty();
        String gaps = String.valueOf(result.get("gaps"));
        assertThat(gaps.toLowerCase()).containsAnyOf("adb", "average daily", "gst");
        assertThat(result.get("allowCanonicalAuthority")).isEqualTo(false);
    }

    @Test
    void readiness_readyWhenOnlyBureauRequiredAndBureauEnabled() {
        WorkflowConfig wf = WorkflowConfig.builder()
                .id(UUID.randomUUID())
                .name("Simple")
                .borrowerType("INDIVIDUAL")
                .loanProduct("PERSONAL_LOAN")
                .active(true)
                .bureauEnabled(true)
                .steps(List.of(Map.of("step", "PAN_VERIFY")))
                .build();
        UnderwritingRuleSet rules = UnderwritingRuleSet.builder()
                .id(UUID.randomUUID())
                .name("Bureau only")
                .borrowerType("INDIVIDUAL")
                .loanProduct("PERSONAL_LOAN")
                .active(true)
                .rulesJson(Map.of("rules", List.of(Map.of("parameter", "BUREAU_SCORE"))))
                .build();
        UnderwritingScorecard sc = UnderwritingScorecard.builder()
                .id(UUID.randomUUID())
                .name("PL Score")
                .borrowerType("INDIVIDUAL")
                .loanProduct("PERSONAL_LOAN")
                .active(true)
                .scorecardJson(Map.of())
                .hardRulesJson(Map.of())
                .thresholdsJson(Map.of())
                .build();

        Map<String, Object> result = validator.validate(
                "INDIVIDUAL", "PERSONAL_LOAN", wf, rules, sc, null, null, null);
        assertThat(result.get("workflowSuppliesRequiredAutomaticData")).isEqualTo(true);
        assertThat(result.get("scopeCompatible")).isEqualTo(true);
        assertThat(result.get("status")).isEqualTo("READY");
    }

    @Test
    void studioSession_skipsClassificationOnlyRules() {
        PolicyStudioSession session = new PolicyStudioSession();
        CiPolicyDocument doc = CiPolicyDocument.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .name("P")
                .status(DocumentStatus.DRAFT_READY.name())
                .documentVersion(1)
                .metadata(new LinkedHashMap<>())
                .build();
        session.setDocument(doc);
        session.getRuleCandidates().add(CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .systemRuleId("CLASSIF_1")
                .metadata(Map.of("classificationOnly", true, "parameterId", "bureau.score"))
                .build());
        session.getRuleCandidates().add(CiPolicyRuleCandidate.builder()
                .id(UUID.randomUUID())
                .systemRuleId("UW_BUREAU")
                .metadata(Map.of(
                        "cmAuthored", true,
                        "parameterId", "bureau.score",
                        "dataUsed", List.of("bureau.score")))
                .build());

        Map<String, Object> req = extractor.fromStudioSession(session);
        assertThat(req.get("requiredParameterIds")).asList().contains("bureau.score");
    }

    @Test
    void workflowProvidesCatalogue_isReadModelOnly() {
        Map<String, Object> cat = WorkflowParameterProvidesCatalog.catalogueView();
        assertThat(cat.get("readModelOnly")).isEqualTo(true);
        assertThat(cat.get("allowCanonicalAuthority")).isEqualTo(false);
        assertThat(cat.get("workflowSteps")).asList().isNotEmpty();
    }
}
