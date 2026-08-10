package com.los.core.creditintelligence.validation;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.validation.domain.CutoverOutcome;
import com.los.core.creditintelligence.validation.domain.ValidationCaseCode;
import com.los.core.creditintelligence.validation.service.CanonicalCoverageCalculator;
import com.los.core.creditintelligence.validation.service.CreditEvidenceViewBuilder;
import com.los.core.creditintelligence.validation.service.CutoverReadinessAssessor;
import com.los.core.creditintelligence.validation.service.LegacyDefaultInventory;
import com.los.core.creditintelligence.validation.service.MultiSourceValidationHarness;
import com.los.core.creditintelligence.validation.service.PolicyAuthoringRegistry;
import com.los.core.creditintelligence.validation.service.SecurityValidationScanner;
import com.los.core.creditintelligence.validation.service.TenantIsolationValidator;
import com.los.core.creditintelligence.validation.service.ValidationBundleLoader;
import com.los.core.creditintelligence.validation.service.ValidationPerformanceHarness;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CutoverCoverageEvidenceTest {

    @Test
    void evidenceView_hasRequiredSections() {
        var bundle = new ValidationBundleLoader().load(ValidationCaseCode.CASE_A_STRONG);
        Map<String, Object> view = new CreditEvidenceViewBuilder().build(
                bundle, Map.of(), Map.of(), Map.of("overallCoveragePct", 70), List.of());
        assertThat(view).containsKeys(
                "DataCoverage", "Identity", "Bureau", "Banking", "GST", "ITR",
                "Obligations", "TurnoverTriangulation", "MaterialReconciliations",
                "DataQuality", "EvidenceStrength", "LegacyVsCanonical", "OpenInvestigationQuestions");
    }

    @Test
    void coverageCalculator_separatesCritical() {
        var bundle = new ValidationBundleLoader().load(ValidationCaseCode.CASE_A_STRONG);
        Map<String, Object> cov = new CanonicalCoverageCalculator().calculate(
                bundle.metricStubs(), bundle.sources(), Map.of());
        assertThat(cov.get("criticalCoveragePct")).isNotNull();
        assertThat(cov.get("overallCoveragePct")).isNotNull();
        assertThat(((Number) cov.get("criticalTotal")).intValue()).isEqualTo(
                CanonicalCoverageCalculator.CRITICAL_INPUTS.size());
    }

    @Test
    void cutover_notReadyWhileSilentDefaultsExist() {
        var assessment = new CutoverReadinessAssessor().assess(
                UUID.randomUUID(),
                true,
                BigDecimal.valueOf(70),
                BigDecimal.valueOf(0),
                new LegacyDefaultInventory().inventory().size(),
                true,
                true,
                true,
                0,
                true);
        assertThat(assessment.outcome()).isIn(
                CutoverOutcome.NOT_READY, CutoverOutcome.READY_WITH_LIMITATIONS);
        assertThat(assessment.outcome()).isNotEqualTo(CutoverOutcome.READY);
        assertThat(assessment.blockers()).isNotEmpty();
    }

    @Test
    void harnessCutover_notReady() {
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        props.getValidation().setEnabled(true);
        var result = new MultiSourceValidationHarness(props).runCase(ValidationCaseCode.CASE_A_STRONG);
        assertThat(result.cutoverOutcome()).isIn(
                CutoverOutcome.NOT_READY, CutoverOutcome.READY_WITH_LIMITATIONS);
        assertThat(result.cutoverOutcome()).isNotEqualTo(CutoverOutcome.READY);
    }

    @Test
    void tenantIsolation_ok() {
        var report = new TenantIsolationValidator().validate(new CreditIntelligenceProperties());
        assertThat(report.ok()).isTrue();
    }

    @Test
    void policyAuthoringRegistry_hasVocabulary() {
        Map<String, Object> reg = new PolicyAuthoringRegistry().registry();
        assertThat(reg.get("schemaVersion")).isEqualTo(PolicyAuthoringRegistry.SCHEMA_VERSION);
        assertThat(reg.get("metrics")).isInstanceOf(List.class);
        assertThat(reg.get("reconciliations")).isInstanceOf(List.class);
        assertThat(reg.get("operators")).isInstanceOf(List.class);
    }

    @Test
    void securityMasking_noCritical() {
        var report = new SecurityValidationScanner().scan();
        assertThat(report.maskingOk()).isTrue();
        assertThat(report.criticalOpen()).isZero();
    }

    @Test
    void performanceHarness_1kAnd10k_synthetic() {
        var harness = new ValidationPerformanceHarness();
        var results = harness.runDefaultSizes();
        assertThat(results).hasSize(2);
        assertThat(results.get(0).transactionCount()).isEqualTo(1000);
        assertThat(results.get(1).transactionCount()).isEqualTo(10_000);
        assertThat(results).allMatch(r -> r.origin().name().equals("SYNTHETIC"));
        assertThat(results.get(0).recommendation()).isNotBlank();
    }

    @Test
    void evaluationReplayPurity_acrossCases() {
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        props.getValidation().setEnabled(true);
        var harness = new MultiSourceValidationHarness(props);
        for (ValidationCaseCode code : ValidationCaseCode.values()) {
            var r = harness.runCase(code);
            assertThat(r.replayIdentical()).as(code.name()).isTrue();
            assertThat(r.deterministicEvaluationHash()).as(code.name()).isNotBlank();
            assertThat(r.replayHash()).isEqualTo(r.deterministicEvaluationHash());
        }
    }
}
