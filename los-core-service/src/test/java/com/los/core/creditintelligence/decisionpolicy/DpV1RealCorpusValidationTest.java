package com.los.core.creditintelligence.decisionpolicy;

import com.los.core.creditintelligence.decisionpolicy.corpus.DecisionPolicyCorpusAnonymizer;
import com.los.core.creditintelligence.decisionpolicy.corpus.DecisionPolicyCorpusExportSpec;
import com.los.core.creditintelligence.decisionpolicy.corpus.DecisionPolicyCorpusOrigin;
import com.los.core.creditintelligence.decisionpolicy.corpus.DecisionPolicyCorpusSchemaValidator;
import com.los.core.creditintelligence.decisionpolicy.corpus.DecisionPolicyRealCorpusValidationService;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.decisionpolicy.sim.DecisionPolicyEndToEndSimulationService;
import com.los.core.creditintelligence.decisionpolicy.sim.ExactExecutablePackageLoader;
import com.los.core.creditintelligence.decisionpolicy.kyc.shadow.ShadowKycPolicyEvaluationService;
import com.los.core.creditintelligence.policy.repository.CiExecutablePolicyPackageRepository;
import com.los.core.repository.KycStepResultRepository;
import com.los.core.repository.LoanApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DP-V1 — real Decision Policy corpus readiness (honest INSUFFICIENT_EVIDENCE without fabrications).
 */
class DpV1RealCorpusValidationTest {

    private DecisionPolicyRealCorpusValidationService service;
    private LoanApplicationRepository loanApps;

    @BeforeEach
    void setUp() {
        loanApps = mock(LoanApplicationRepository.class);
        when(loanApps.count()).thenReturn(0L);
        KycStepResultRepository steps = mock(KycStepResultRepository.class);
        when(steps.count()).thenReturn(0L);

        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        props.setDefaultTenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        ObjectProvider<LoanApplicationRepository> appsProv = mock(ObjectProvider.class);
        when(appsProv.getIfAvailable()).thenReturn(loanApps);
        ObjectProvider<KycStepResultRepository> stepsProv = mock(ObjectProvider.class);
        when(stepsProv.getIfAvailable()).thenReturn(steps);
        ObjectProvider<?> empty = mock(ObjectProvider.class);
        when(empty.getIfAvailable()).thenReturn(null);

        ExactExecutablePackageLoader loader = new ExactExecutablePackageLoader(
                mock(ObjectProvider.class));
        DecisionPolicyEndToEndSimulationService e2e = new DecisionPolicyEndToEndSimulationService(
                new ShadowKycPolicyEvaluationService(), loader);

        @SuppressWarnings("unchecked")
        ObjectProvider<CiExecutablePolicyPackageRepository> pkgProv =
                (ObjectProvider<CiExecutablePolicyPackageRepository>) empty;

        service = new DecisionPolicyRealCorpusValidationService(
                props,
                appsProv,
                stepsProv,
                (ObjectProvider) empty,
                (ObjectProvider) empty,
                (ObjectProvider) empty,
                (ObjectProvider) empty,
                e2e);
    }

    @Test
    void originClassificationHonesty() {
        assertThat(DecisionPolicyCorpusOrigin.ANONYMIZED_REAL_DEV_DATA.countsTowardRealStoredCertification()).isTrue();
        assertThat(DecisionPolicyCorpusOrigin.STORED_PROVIDER_DATA.countsTowardRealStoredCertification()).isTrue();
        assertThat(DecisionPolicyCorpusOrigin.REPRESENTATIVE_FIXTURE.countsTowardRealStoredCertification()).isFalse();
        assertThat(DecisionPolicyCorpusOrigin.SYNTHETIC.countsTowardRealStoredCertification()).isFalse();
        assertThat(DecisionPolicyCorpusOrigin.USER_SUPPLIED_SAMPLE.countsTowardRealStoredCertification()).isFalse();
    }

    @Test
    void schemaRejectsRawPan() {
        Map<String, Object> bad = baseRecord();
        bad.put("pan", "ABCDE1234F");
        Map<String, Object> v = DecisionPolicyCorpusSchemaValidator.validate(bad);
        assertThat(v.get("ok")).isEqualTo(false);
        assertThat(String.valueOf(v.get("errors"))).contains("PII");
    }

    @Test
    void schemaAcceptsTokenisedRecord() {
        Map<String, Object> ok = baseRecord();
        Map<String, Object> v = DecisionPolicyCorpusSchemaValidator.validate(ok);
        assertThat(v.get("ok")).isEqualTo(true);
        assertThat(v.get("countsTowardRealStored")).isEqualTo(true);
    }

    @Test
    void missingValuesNotCoercedInAnonymizer() {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("borrower_type", "INDIVIDUAL");
        // pan absent — must stay absent
        Map<String, Object> out = DecisionPolicyCorpusAnonymizer.anonymizeShallow(in);
        assertThat(out.containsKey("pan")).isFalse();
        assertThat(out.get("borrower_type")).isEqualTo("INDIVIDUAL");
    }

    @Test
    void anonymizerTokensName() {
        String tok = DecisionPolicyCorpusAnonymizer.token("name", "Rahul Sharma");
        assertThat(tok).startsWith("TOK_");
        assertThat(tok).doesNotContain("Rahul");
    }

    @Test
    void discoveryReportsZeroRealStored() {
        Map<String, Object> d = service.discover();
        assertThat(d.get("realStoredCount")).isEqualTo(0);
        assertThat(d.get("loanApplicationsDiscovered")).isEqualTo(0);
        assertThat(d.get("allowCanonicalAuthority")).isEqualTo(false);
        assertThat(d.get("certificationStatus")).isEqualTo(DecisionPolicyCorpusExportSpec.CERT_INSUFFICIENT);
        assertThat(d.get("datasetExportSpec")).isInstanceOf(Map.class);
    }

    @Test
    void validationReturnsInsufficientEvidenceWithoutFabrication() {
        Map<String, Object> r = service.runValidation("test");
        assertThat(r.get("certificationStatus")).isEqualTo(DecisionPolicyCorpusExportSpec.CERT_INSUFFICIENT);
        assertThat(r.get("applicationMutated")).isEqualTo(false);
        assertThat(r.get("productionUnderwritingTriggered")).isEqualTo(false);
        assertThat(r.get("allowCanonicalAuthority")).isEqualTo(false);
        assertThat(r.get("productionKycUnchanged")).isEqualTo(true);
        assertThat(r.get("kycToUwGateUnchanged")).isEqualTo(true);
        assertThat(r.get("underwritingTriggerUnchanged")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> apps = (List<Map<String, Object>>) r.get("applications");
        assertThat(apps).isEmpty();
    }

    @Test
    void exportSpecRequiresMinimum20() {
        Map<String, Object> spec = DecisionPolicyCorpusExportSpec.fullSpec();
        assertThat(spec.get("minRealStoredApplications")).isEqualTo(20);
        assertThat(spec.get("importProcess")).isNotNull();
        assertThat(String.valueOf(spec.get("environment"))).contains("never production");
    }

    @Test
    void fixtureOriginDoesNotCount() {
        Map<String, Object> rec = baseRecord();
        rec.put("originClassification", "REPRESENTATIVE_FIXTURE");
        Map<String, Object> v = DecisionPolicyCorpusSchemaValidator.validate(rec);
        assertThat(v.get("countsTowardRealStored")).isEqualTo(false);
    }

    private static Map<String, Object> baseRecord() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("applicationToken", "TOK_APP_TEST_001");
        m.put("originClassification", "ANONYMIZED_REAL_DEV_DATA");
        m.put("productCode", "DIGILEAP");
        m.put("borrowerType", "INDIVIDUAL");
        m.put("requestedAmount", 500000);
        m.put("requestedTenureMonths", 24);
        m.put("evaluationBusinessDate", "2024-06-15");
        m.put("applicationCore", Map.of("borrower_type", "INDIVIDUAL"));
        return m;
    }
}
