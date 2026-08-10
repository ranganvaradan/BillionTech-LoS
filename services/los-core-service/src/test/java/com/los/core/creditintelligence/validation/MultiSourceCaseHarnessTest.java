package com.los.core.creditintelligence.validation;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.validation.domain.DataOrigin;
import com.los.core.creditintelligence.validation.domain.PolicyDifferenceClass;
import com.los.core.creditintelligence.validation.domain.ValidationCaseCode;
import com.los.core.creditintelligence.validation.model.ValidationRunResult;
import com.los.core.creditintelligence.validation.service.MultiSourceValidationHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MultiSourceCaseHarnessTest {

    private MultiSourceValidationHarness harness;

    @BeforeEach
    void setUp() {
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        props.getValidation().setEnabled(true);
        harness = new MultiSourceValidationHarness(props);
    }

    @Test
    void caseA_strong_representativeOrigin_replayIdentical() {
        ValidationRunResult r = harness.runCase(ValidationCaseCode.CASE_A_STRONG);
        assertThat(r.dataOrigin()).isEqualTo(DataOrigin.REPRESENTATIVE_PROVIDER_FIXTURE);
        assertThat(r.replayIdentical()).isTrue();
        assertThat(r.summary().get("applicationStatusMutated")).isEqualTo(false);
        assertThat(r.evidenceView().get("EvidenceStrength")).isNotNull();
        assertThat(r.reconciliations()).containsKey("TURNOVER_TRIANGULATION");
    }

    @Test
    void caseB_legacyDefaultDependent() {
        ValidationRunResult r = harness.runCase(ValidationCaseCode.CASE_B_LEGACY_DEFAULT);
        assertThat(r.dataOrigin()).isEqualTo(DataOrigin.REPRESENTATIVE_PROVIDER_FIXTURE);
        assertThat(r.replayIdentical()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> comparisons =
                (List<Map<String, Object>>) r.dualPolicyComparison().get("comparisons");
        assertThat(comparisons).anyMatch(c ->
                PolicyDifferenceClass.LEGACY_DEFAULT_DEPENDENT.name()
                        .equals(c.get("differenceClass")));
    }

    @Test
    void caseC_turnoverConflict_materialVariance() {
        ValidationRunResult r = harness.runCase(ValidationCaseCode.CASE_C_TURNOVER_CONFLICT);
        Object tri = r.reconciliations().get("TURNOVER_TRIANGULATION");
        assertThat(tri).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) tri).get("outcome").toString()).containsAnyOf(
                "MATERIAL_VARIANCE", "CONFLICT", "ACCEPTABLE_VARIANCE");
        assertThat(r.investigationQuestions()).isNotEmpty();
    }

    @Test
    void caseD_obligationConflict_lenderMatch() {
        ValidationRunResult r = harness.runCase(ValidationCaseCode.CASE_D_OBLIGATION_CONFLICT);
        assertThat(r.obligationMatches()).isNotEmpty();
        Map<String, Object> match = r.obligationMatches().get(0);
        assertThat(match.get("bureauEmi")).isNotNull();
        assertThat(match.get("bankObservedEmi")).isNotNull();
        assertThat(match.get("matchStatus")).isIn("MATCH", "PROBABLE_MATCH");
    }

    @Test
    void caseE_incomplete_dataInsufficient() {
        ValidationRunResult r = harness.runCase(ValidationCaseCode.CASE_E_INCOMPLETE);
        assertThat(r.reconciliations().get("DATA_INSUFFICIENT_FLAGS")).isNotNull();
        assertThat(r.coverage().get("canonicalDataInsufficient")).isNotNull();
        int di = ((Number) r.coverage().get("canonicalDataInsufficient")).intValue();
        assertThat(di).isGreaterThan(0);
    }

    @Test
    void featureFlagOff_harnessReportsDisabled() {
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        props.getValidation().setEnabled(false);
        MultiSourceValidationHarness off = new MultiSourceValidationHarness(props);
        assertThat(off.isEnabled()).isFalse();
    }
}
