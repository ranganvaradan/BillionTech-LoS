package com.los.core.service.integration.providers.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.config.IntegrationProperties;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.ApiAuditLogRepository;
import com.los.core.repository.DocumentRepository;
import com.los.core.repository.KycStepResultRepository;
import com.los.core.service.credit.CreditControlKeys;
import com.los.core.service.credit.CreditControlService;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import com.los.core.service.credit.LimitSizingService;
import com.los.core.service.document.IDocumentService;
import com.los.core.service.document.OcrExtractionService;
import com.los.core.service.integration.providers.IBureauProvider;
import com.los.core.service.kyc.IKycOrchestrationService;
import com.los.core.service.underwriting.ScorecardValueProvenance;
import com.los.core.creditintelligence.bureau.service.CanonicalBureauContextBridge;
import com.los.plp.service.InvoiceDiscountingVintageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BUREAU-P0-3 — Equifax provider availability fail-closed goldens.
 */
@ExtendWith(MockitoExtension.class)
class EquifaxProviderAvailabilityP03Test {

    @Mock private ApiAuditLogRepository apiAuditLogRepository;
    @Mock private IDocumentService documentService;
    @Mock private IKycOrchestrationService kyc;
    @Mock private InvoiceDiscountingVintageService vintage;
    @Mock private DocumentRepository documentRepository;
    @Mock private KycStepResultRepository kycStepResultRepository;
    @Mock private OcrExtractionService ocrExtractionService;
    @Mock private LimitSizingService limitSizingService;
    @Mock private CanonicalBureauContextBridge canonicalBureauContextBridge;

    private IntegrationProperties integrationProperties;
    private EquifaxBureauProvider provider;
    private CreditControlService creditControl;
    private PolicyDslInterpreterV1 interpreter;

    @BeforeEach
    void setUp() {
        integrationProperties = new IntegrationProperties();
        provider = new EquifaxBureauProvider(
                integrationProperties, apiAuditLogRepository, new ObjectMapper(), documentService);
        lenient().when(documentRepository.findByApplicationIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
        lenient().when(kycStepResultRepository.findTopByApplicationIdAndStepTypeOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        lenient().doNothing().when(limitSizingService).applyComputedMetrics(any(), any());
        lenient().when(canonicalBureauContextBridge.isEnabledFor(any())).thenReturn(false);
        creditControl = new CreditControlService(
                kyc, vintage, documentRepository, kycStepResultRepository, ocrExtractionService, limitSizingService,
                canonicalBureauContextBridge);
        ReflectionTestUtils.setField(creditControl, "providerGapDefaultsEnabled", true);
        interpreter = new PolicyDslInterpreterV1();
    }

    @Test
    void case1_missingCredentials_simulationDisabled_providerUnavailable() {
        clearCredentials();
        integrationProperties.getEquifax().setSimulation(false);

        IBureauProvider.BureauPullResult r = provider.pullReport(Map.of(
                "name", "Test", "panNumber", "ABCDE1234F"));

        assertThat(r.success()).isFalse();
        assertThat(r.errorMessage()).contains("PROVIDER_UNAVAILABLE");
        assertThat(r.reportData().get("providerAvailability"))
                .isEqualTo(EquifaxBureauProvider.AVAILABILITY_PROVIDER_UNAVAILABLE);
        assertThat(r.reportData().get("simulated")).isEqualTo(false);
        assertThat(r.reportData()).doesNotContainKeys(
                "creditScore", "recentEnquiries", "dpd30Plus", "totalAccounts");
        assertThat(r.creditScore()).isZero();
    }

    @Test
    void case2_missingCredentials_simulationEnabled_simulatedProvenance() {
        clearCredentials();
        integrationProperties.getEquifax().setSimulation(true);

        IBureauProvider.BureauPullResult r = provider.pullReport(Map.of(
                "name", "Test", "panNumber", "ABCDE1234F"));

        assertThat(r.success()).isTrue();
        assertThat(r.reportData().get("simulated")).isEqualTo(true);
        assertThat(r.reportData().get("dataProvenance"))
                .isEqualTo(EquifaxBureauProvider.PROVENANCE_SIMULATED);
        assertThat(r.reportData().get("providerAvailability"))
                .isEqualTo(EquifaxBureauProvider.AVAILABILITY_EXPLICIT_SIMULATION);
        assertThat(r.reportData().get("dataProvenance"))
                .isNotEqualTo(EquifaxBureauProvider.PROVENANCE_PROVIDER);
    }

    @Test
    void case2b_simulationEnabled_usesConfiguredFixtureContent() {
        // Confirms EQUIFAX_SIMULATION=true (no fixture key needed) parses the real
        // classpath fixture rather than falling back to the hardcoded synthetic response —
        // pins the specific score so a future fixture swap is a deliberate, visible change.
        clearCredentials();
        integrationProperties.getEquifax().setSimulation(true);

        IBureauProvider.BureauPullResult r = provider.pullReport(Map.of(
                "name", "Test", "panNumber", "ABCDE1234F"));

        assertThat(r.success()).isTrue();
        assertThat(r.creditScore()).isEqualTo(808);
        assertThat(r.reportData().get("simulated")).isEqualTo(true);
    }

    @Test
    void case3_credentialsConfigured_providerSuccess_providerProvenance() throws Exception {
        configureCredentials();
        String xml = loadSampleXml();
        provider.setHttpTransportForTests((req, cfg) -> mockHttp(200, xml));

        IBureauProvider.BureauPullResult r = provider.pullReport(Map.of(
                "name", "Test", "panNumber", "ABCDE1234F"));

        assertThat(r.success()).isTrue();
        assertThat(r.creditScore()).isPositive();
        assertThat(r.reportData().get("dataProvenance"))
                .isEqualTo(EquifaxBureauProvider.PROVENANCE_PROVIDER);
        assertThat(r.reportData().get("simulated")).isEqualTo(false);
        assertThat(r.reportData().get("providerAvailability"))
                .isEqualTo(EquifaxBureauProvider.AVAILABILITY_PROVIDER_READY);
    }

    @Test
    void case4_providerTimeout_failsClosed_noSimulation() {
        configureCredentials();
        provider.setHttpTransportForTests((req, cfg) -> {
            throw new TimeoutException("connect timed out");
        });

        IBureauProvider.BureauPullResult r = provider.pullReport(Map.of(
                "name", "Test", "panNumber", "ABCDE1234F"));

        assertThat(r.success()).isFalse();
        assertThat(r.reportData().get("providerAvailability"))
                .isEqualTo(EquifaxBureauProvider.AVAILABILITY_PROVIDER_FAILED);
        assertThat(r.reportData().get("simulated")).isEqualTo(false);
        assertThat(r.errorMessage()).containsIgnoringCase("error");
    }

    @Test
    void case5_providerHttpError_failsClosed() {
        configureCredentials();
        provider.setHttpTransportForTests((req, cfg) -> mockHttp(503, "unavailable"));

        IBureauProvider.BureauPullResult r = provider.pullReport(Map.of(
                "name", "Test", "panNumber", "ABCDE1234F"));

        assertThat(r.success()).isFalse();
        assertThat(r.errorMessage()).contains("HTTP 503");
        assertThat(r.reportData().get("providerAvailability"))
                .isEqualTo(EquifaxBureauProvider.AVAILABILITY_PROVIDER_FAILED);
        assertThat(r.reportData().get("simulated")).isEqualTo(false);
    }

    @Test
    void case6_malformedResponse_dataInsufficient_noSyntheticValues() {
        configureCredentials();
        provider.setHttpTransportForTests((req, cfg) -> mockHttp(200, "<not-valid-xml"));

        IBureauProvider.BureauPullResult r = provider.pullReport(Map.of(
                "name", "Test", "panNumber", "ABCDE1234F"));

        assertThat(r.success()).isFalse();
        assertThat(r.errorMessage()).containsIgnoringCase("parse");
        assertThat(r.reportData() == null
                || !Boolean.TRUE.equals(r.reportData().get("simulated"))).isTrue();
    }

    @Test
    void case7_equifaxNoHit_minusOneAndNtc() {
        String noHit = """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body>
                    <sch:InquiryResponse xmlns:sch="http://services.equifax.com/eport/ws/schemas/1.0">
                      <sch:InquiryResponseHeader>
                        <sch:SuccessCode>1</sch:SuccessCode>
                        <sch:ErrorMessage>Consumer record not found</sch:ErrorMessage>
                      </sch:InquiryResponseHeader>
                    </sch:InquiryResponse>
                  </soapenv:Body>
                </soapenv:Envelope>
                """;
        IBureauProvider.BureauPullResult r = provider.parseEquifaxResponse(noHit, "EQX-TEST");
        assertThat(r.success()).isTrue();
        assertThat(r.creditScore()).isEqualTo(-1);
        assertThat(r.reportData().get("noRecordFound")).isEqualTo(true);
    }

    @Test
    void case8_positiveScore720() {
        String hit = """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body>
                    <sch:InquiryResponse xmlns:sch="http://services.equifax.com/eport/ws/schemas/1.0">
                      <sch:InquiryResponseHeader>
                        <sch:SuccessCode>1</sch:SuccessCode>
                      </sch:InquiryResponseHeader>
                      <sch:Score><sch:Value>720</sch:Value><sch:Name>ERS 3.0</sch:Name></sch:Score>
                    </sch:InquiryResponse>
                  </soapenv:Body>
                </soapenv:Envelope>
                """;
        IBureauProvider.BureauPullResult r = provider.parseEquifaxResponse(hit, "EQX-TEST");
        assertThat(r.success()).isTrue();
        assertThat(r.creditScore()).isEqualTo(720);
        assertThat(r.reportData().get("noRecordFound")).isNull();
    }

    @Test
    void case9_missingScoreWithoutNoHit_notConvertedToNtc() {
        String missing = """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body>
                    <sch:InquiryResponse xmlns:sch="http://services.equifax.com/eport/ws/schemas/1.0">
                      <sch:InquiryResponseHeader>
                        <sch:SuccessCode>1</sch:SuccessCode>
                      </sch:InquiryResponseHeader>
                    </sch:InquiryResponse>
                  </soapenv:Body>
                </soapenv:Envelope>
                """;
        IBureauProvider.BureauPullResult r = provider.parseEquifaxResponse(missing, "EQX-TEST");
        assertThat(r.creditScore()).isNotEqualTo(-1);
        assertThat(r.reportData().get("noRecordFound")).isNull();
        assertThat(r.success()).isFalse();
    }

    @Test
    void case10_p02_gapDefaultsStillBlockedOnMissingBureau() {
        EffectiveUnderwritingContext ctx = creditControl.resolveEffective(app(720), "PASS");
        assertThat(ctx.scorecard()).doesNotContainKey("LIVE_UNSECURED_LOAN_COUNT");
        assertThat(ctx.scorecard()).doesNotContainKey("BUREAU_ENQUIRIES_3M");
        assertThat(ctx.scorecard()).doesNotContainKey("NTC_FLAG");
        assertThat(ctx.scorecardProvenance().get("LIVE_UNSECURED_LOAN_COUNT"))
                .isNotEqualTo(ScorecardValueProvenance.GAP_DEFAULT);
    }

    @Test
    void simulationSource_mapsToSimulatedProvenance_notRealProvider() {
        LoanApplication app = app(720);
        Map<String, Object> fi = new java.util.LinkedHashMap<>();
        Map<String, Object> cc = new java.util.LinkedHashMap<>();
        cc.put(CreditControlKeys.DECISION_SOURCES, Map.of("bureauScoreSource", CreditControlKeys.SRC_SIMULATED));
        fi.put(CreditControlKeys.ROOT, cc);
        app.setFinancialInfo(fi);

        EffectiveUnderwritingContext ctx = creditControl.resolveEffective(app, "PASS");
        assertThat(ctx.bureauSource()).isEqualTo(CreditControlKeys.SRC_SIMULATED);
        assertThat(ctx.scorecardProvenance().get("BUREAU_SCORE"))
                .isEqualTo(ScorecardValueProvenance.SIMULATED);
        assertThat(ctx.scorecardProvenance().get("BUREAU_SCORE"))
                .isNotEqualTo(ScorecardValueProvenance.REAL_PROVIDER);
        assertThat(ScorecardValueProvenance.isNonAuthoritative(ScorecardValueProvenance.SIMULATED)).isTrue();
    }

    @Test
    void policyTest_missingBureauMetric_dataInsufficient_parityWithUnavailable() {
        Map<String, Object> rule = PolicyDsl.lte(PolicyDsl.metric("bureau.recent_inquiries_90d"), 3);
        String outcome = interpreter.evaluate(
                rule, PolicyDslInterpreterV1.EvaluationContext.of(Map.of(), Map.of(), null));
        assertThat(outcome).isEqualTo(PolicyDslInterpreterV1.DATA_INSUFFICIENT);
    }

    @Test
    void simulationCannotSilentlyEnableBecauseCredsMissing() {
        clearCredentials();
        integrationProperties.getEquifax().setSimulation(false);
        // Partial credential (customerId only) still not configured
        integrationProperties.getEquifax().setCustomerId("ONLY_ID");
        IBureauProvider.BureauPullResult r = provider.pullReport(Map.of(
                "name", "Test", "panNumber", "ABCDE1234F"));
        assertThat(r.success()).isFalse();
        assertThat(r.errorMessage()).contains("PROVIDER_UNAVAILABLE");
    }

    @Test
    void fixtureKeyIgnoredWhenInternalIngestDisabled() {
        clearCredentials();
        integrationProperties.getEquifax().setSimulation(false);
        integrationProperties.getEquifax().setInternalFixtureIngestEnabled(false);

        IBureauProvider.BureauPullResult r = provider.pullReport(Map.of(
                "name", "Test",
                "panNumber", "ABCDE1234F",
                EquifaxBureauProvider.FIXTURE_SOURCE_KEY, EquifaxBureauProvider.FIXTURE_CLASSPATH_SAMPLE));

        assertThat(r.success()).isFalse();
        assertThat(r.errorMessage()).contains("PROVIDER_UNAVAILABLE");
        assertThat(r.reportData().get("simulated")).isEqualTo(false);
    }

    @Test
    void internalFixtureIngestUsesClasspathXmlAtProviderBoundary() {
        clearCredentials();
        integrationProperties.getEquifax().setSimulation(false);
        integrationProperties.getEquifax().setInternalFixtureIngestEnabled(true);

        IBureauProvider.BureauPullResult r = provider.pullReport(Map.of(
                "name", "Test",
                "panNumber", "ABCDE1234F",
                EquifaxBureauProvider.FIXTURE_SOURCE_KEY, EquifaxBureauProvider.FIXTURE_CLASSPATH_SAMPLE));

        assertThat(r.success()).isTrue();
        assertThat(r.creditScore()).isPositive();
        assertThat(r.reportData().get("simulated")).isEqualTo(true);
        assertThat(r.reportData().get("dataProvenance"))
                .isEqualTo(EquifaxBureauProvider.PROVENANCE_SIMULATED);
        assertThat(r.reportData().get("simulatedSource"))
                .isEqualTo("simulated/equifax-sample-inquiry-response.xml");
    }

    @Test
    void liveCredentialsWinOverInternalFixtureRequest() throws Exception {
        configureCredentials();
        integrationProperties.getEquifax().setInternalFixtureIngestEnabled(true);
        String xml = loadSampleXml();
        provider.setHttpTransportForTests((req, cfg) -> mockHttp(200, xml));

        IBureauProvider.BureauPullResult r = provider.pullReport(Map.of(
                "name", "Test",
                "panNumber", "ABCDE1234F",
                EquifaxBureauProvider.FIXTURE_SOURCE_KEY, EquifaxBureauProvider.FIXTURE_CLASSPATH_SAMPLE));

        assertThat(r.success()).isTrue();
        assertThat(r.reportData().get("dataProvenance"))
                .isEqualTo(EquifaxBureauProvider.PROVENANCE_PROVIDER);
        assertThat(r.reportData().get("simulated")).isEqualTo(false);
    }

    private void clearCredentials() {
        IntegrationProperties.EquifaxProperties eq = integrationProperties.getEquifax();
        eq.setCustomerId("");
        eq.setUserId("");
        eq.setPassword("");
        eq.setMemberNumber("");
        eq.setSecurityCode("");
    }

    private void configureCredentials() {
        IntegrationProperties.EquifaxProperties eq = integrationProperties.getEquifax();
        eq.setCustomerId("CUST");
        eq.setUserId("USER");
        eq.setPassword("PASS");
        eq.setMemberNumber("MEM");
        eq.setSecurityCode("SEC");
        eq.setSimulation(false);
        eq.setUrl("https://example.test/equifax");
    }

    private static LoanApplication app(int bureauScore) {
        return LoanApplication.builder()
                .id(UUID.randomUUID())
                .applicationNumber("APP-P03")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PERSONAL_LOAN")
                .bureauScore(bureauScore)
                .build();
    }

    private static String loadSampleXml() throws Exception {
        ClassPathResource resource = new ClassPathResource("simulated/equifax-sample-inquiry-response.xml");
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> mockHttp(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        lenient().when(response.statusCode()).thenReturn(status);
        lenient().when(response.body()).thenReturn(body);
        return response;
    }
}
