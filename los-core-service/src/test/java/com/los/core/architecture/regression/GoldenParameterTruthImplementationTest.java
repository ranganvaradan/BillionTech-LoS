package com.los.core.architecture.regression;

import com.los.core.creditintelligence.policystudio.parameters.derived.AuthoredDerivedCalculationSupport;
import com.los.core.creditintelligence.policystudio.parameters.derived.BusinessCalculationAssistant;
import com.los.core.creditintelligence.policystudio.parameters.derived.CiGacatDerivedCalculationDefinition;
import com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationDefinitionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;
import com.los.core.creditintelligence.policystudio.sourceintegration.CanonicalSourceIntegrationAuthority;
import com.los.core.creditintelligence.policystudio.sourceintegration.PlatformSourceConnectorCatalog;
import com.los.core.creditintelligence.policystudio.truth.BusinessReadiness;
import com.los.core.creditintelligence.policystudio.truth.BusinessReadinessReason;
import com.los.core.creditintelligence.policystudio.truth.CanonicalParameterStateService;
import com.los.core.creditintelligence.policystudio.truth.SurfaceCanonicalTruthFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GOLDEN-PARAMETER-TRUTH-IMPLEMENTATION-1 — readiness + source integration gates.
 */
class GoldenParameterTruthImplementationTest {

    private static final String DPD30 = "bureau.dpd_30_plus_count_6m";
    private static final String CC_OVERDUE = "bureau.cc_overdue_amount";
    private static final String CLEAN = "bureau.credit_after_overdue.clean_history_months";
    private static final String SCORE = "bureau.score";
    private static final String INQUIRY = "bureau.inquiry.amount";
    private static final String MANUAL = "application.proposed_edi";

    private static final Map<String, Object> DPD30_COUNT_EXPR = Map.of(
            "op", "COUNT_PERIODS_MATCHING",
            "history", Map.of("op", "REF", "id", "bureau.tradeline.payment_history"),
            "matchField", "dpd",
            "matchOp", "GTE",
            "matchValue", 30,
            "dateField", "month",
            "windowMonths", 6,
            "distinctPeriods", true,
            "asOf", Map.of("op", "EVAL_AS_OF"));

    private final Map<String, CiGacatDerivedCalculationDefinition> store = new ConcurrentHashMap<>();
    private AuthoredDerivedCalculationSupport overlaySupport;

    @BeforeEach
    void setUp() {
        store.clear();
        DerivedCalculationDefinitionService definitions = mock(DerivedCalculationDefinitionService.class);
        when(definitions.latestFor(any(), any())).thenAnswer(inv ->
                Optional.ofNullable(store.get(inv.getArgument(0))));
        ExecutionCapabilityAuthority.install(ExecutionSpineProducerBootstrap.standalone(definitions));
        CanonicalSourceIntegrationAuthority.clearLenderProbe();
        overlaySupport = new AuthoredDerivedCalculationSupport(definitions);
        overlaySupport.register();

        store.put(DPD30, CiGacatDerivedCalculationDefinition.builder()
                .id(UUID.randomUUID())
                .canonicalParameterId(DPD30)
                .scope("PLATFORM")
                .status(DerivedCalculationDefinitionService.STATUS_TESTED)
                .expressionJson(new LinkedHashMap<>(DPD30_COUNT_EXPR))
                .dependencyIds(List.of("bureau.tradeline.payment_history"))
                .description("DPD30 test fixture")
                .versionNo(1)
                .build());
        store.put(CLEAN, CiGacatDerivedCalculationDefinition.builder()
                .id(UUID.randomUUID())
                .canonicalParameterId(CLEAN)
                .scope("PLATFORM")
                .status(DerivedCalculationDefinitionService.STATUS_TESTED)
                .expressionJson(BusinessCalculationAssistant.monthsSinceLastMatchExpression(
                        BusinessCalculationAssistant.HISTORY_PAYMENT, 0))
                .dependencyIds(List.of(BusinessCalculationAssistant.HISTORY_PAYMENT))
                .description("Clean history test fixture")
                .versionNo(1)
                .build());
        putBuiltIn(CC_OVERDUE, "MAX of credit-card overdue amounts", List.of("bureau.tradeline.overdue_amount"));
        putBuiltIn("bureau.overdue.amount", "SUM of non-CC overdue", List.of("bureau.tradeline.overdue_amount"));
        putBuiltIn("bureau.overdue.age_months", "MAX overdue age months",
                List.of("bureau.tradeline.overdue_amount", "bureau.tradeline.dpd_month"));
    }

    private void putBuiltIn(String id, String how, List<String> deps) {
        Map<String, Object> expr = new LinkedHashMap<>();
        expr.put("type", "BUILT_IN_CODE");
        expr.put("executor", "BureauMetricService");
        expr.put("calculationType", "BUILT_IN_CODE");
        expr.put("metricCode", id);
        store.put(id, CiGacatDerivedCalculationDefinition.builder()
                .id(UUID.randomUUID())
                .canonicalParameterId(id)
                .scope("PLATFORM")
                .status(DerivedCalculationDefinitionService.STATUS_TESTED)
                .calculationType(DerivedCalculationDefinitionService.CALCULATION_TYPE_BUILT_IN_CODE)
                .expressionJson(expr)
                .dependencyIds(deps)
                .description(how)
                .versionNo(1)
                .build());
    }

    @AfterEach
    void tearDown() {
        overlaySupport.unregister();
        ExecutionCapabilityAuthority.clear();
        CanonicalSourceIntegrationAuthority.clearLenderProbe();
    }

    @Test
    void testS1_equifaxPlatformIntegrated() {
        Map<String, Object> st = CanonicalSourceIntegrationAuthority.forKey(
                PlatformSourceConnectorCatalog.SourceKey.BUREAU_RETAIL, "Bureau Retail");
        assertThat(st.get("authority")).isEqualTo(CanonicalSourceIntegrationAuthority.AUTHORITY);
        assertThat(st.get("platformIntegrated")).isEqualTo(true);
        assertThat(st.get("platformStatus"))
                .isEqualTo(CanonicalSourceIntegrationAuthority.PLATFORM_INTEGRATED);
    }

    @Test
    void testS2_commercialBureauNotIntegrated() {
        Map<String, Object> st = CanonicalSourceIntegrationAuthority.forKey(
                PlatformSourceConnectorCatalog.SourceKey.BUREAU_COMMERCIAL, "Commercial Bureau");
        assertThat(st.get("platformIntegrated")).isEqualTo(false);
        assertThat(st.get("platformStatus"))
                .isEqualTo(CanonicalSourceIntegrationAuthority.PLATFORM_NOT_INTEGRATED);
    }

    @Test
    void testS3_catalogueExistenceFlagPresent() {
        Map<String, Object> st = CanonicalSourceIntegrationAuthority.forFamily("Bureau Retail", null);
        assertThat(st.get("catalogueExistenceIsNotIntegration")).isEqualTo(true);
    }

    @Test
    void testS4_applicantDataDoesNotChangeIntegration() {
        Map<String, Object> before = CanonicalSourceIntegrationAuthority.forFamily("Bureau Retail", null);
        Map<String, Object> score = CanonicalParameterStateService.state(SCORE);
        assertThat(score.get("execution")).isInstanceOf(Map.class);
        Map<String, Object> after = CanonicalSourceIntegrationAuthority.forFamily("Bureau Retail", null);
        assertThat(after.get("platformStatus")).isEqualTo(before.get("platformStatus"));
        assertThat(after.get("applicantDataDoesNotChangeIntegration")).isEqualTo(true);
    }

    @Test
    void testR1_bureauScoreRawReadyEvenWithoutValue() {
        Map<String, Object> st = CanonicalParameterStateService.state(SCORE);
        assertThat(String.valueOf(((Map<?, ?>) st.get("semantic")).get("calculationMode")))
                .isEqualToIgnoringCase("RAW");
        assertThat(st.get("businessReadiness")).isEqualTo(BusinessReadiness.READY.name());
        assertThat(((Map<?, ?>) st.get("execution")).get("valueAvailable")).isEqualTo(false);
        assertThat(String.valueOf(st.get("primaryStatusLabel"))).doesNotContainIgnoringCase("Calculation");
    }

    @Test
    void testD1_dpd30Ready() {
        Map<String, Object> st = CanonicalParameterStateService.state(DPD30);
        assertThat(st.get("businessReadiness")).isEqualTo(BusinessReadiness.READY.name());
        assertThat(st.get("primaryStatus")).isEqualTo(BusinessReadiness.READY.name());
        // Derived semantic must not force INTERNAL source — bureau.* stays Equifax-integrated
        Map<?, ?> source = (Map<?, ?>) st.get("source");
        assertThat(source.get("platformIntegrated")).isEqualTo(true);
        assertThat(source.get("sourceKey"))
                .isEqualTo(PlatformSourceConnectorCatalog.SourceKey.BUREAU_RETAIL.name());
    }

    @Test
    void testS_derivedTypeDoesNotForceInternalSource() {
        Map<String, Object> st = CanonicalSourceIntegrationAuthority.forParameter(
                "Bureau Retail", DPD30, "DERIVED");
        assertThat(st.get("platformIntegrated")).isEqualTo(true);
        assertThat(st.get("sourceKey"))
                .isEqualTo(PlatformSourceConnectorCatalog.SourceKey.BUREAU_RETAIL.name());
    }

    @Test
    void testD2_dpd30ReadyWhileExecutionDependencyUnavailable() {
        Map<String, Object> st = CanonicalParameterStateService.state(DPD30);
        assertThat(st.get("businessReadiness")).isEqualTo(BusinessReadiness.READY.name());
        Object status = ((Map<?, ?>) st.get("execution")).get("status");
        // May be DEPENDENCY_NOT_AVAILABLE or DATA_NOT_AVAILABLE without spine data — never flips READY
        assertThat(st.get("businessReadiness")).isEqualTo(BusinessReadiness.READY.name());
        assertThat(Boolean.TRUE.equals(((Map<?, ?>) st.get("execution")).get("valueAvailable"))).isFalse();
        if (status != null) {
            assertThat(String.valueOf(status)).isNotEqualTo(BusinessReadiness.NOT_READY.name());
        }
    }

    @Test
    void testD3_cleanHistoryReady() {
        Map<String, Object> st = CanonicalParameterStateService.state(CLEAN);
        assertThat(st.get("businessReadiness")).isEqualTo(BusinessReadiness.READY.name());
    }

    @Test
    void testD4_thinFileNotReadyCalculationNotDefined() {
        Map<String, Object> st = CanonicalParameterStateService.state("bureau.thin_file_indicator");
        assertThat(st.get("businessReadiness")).isEqualTo(BusinessReadiness.NOT_READY.name());
        assertThat(st.get("businessReadinessReason"))
                .isEqualTo(BusinessReadinessReason.CALCULATION_NOT_DEFINED.name());
        assertThat(String.valueOf(st.get("primaryStatusLabel"))).doesNotContainIgnoringCase("Ready to test");
    }

    @Test
    void testD5_d6_overdueFamilyReadyViaBureauMetricService() {
        for (String id : new String[]{"bureau.overdue.amount", "bureau.overdue.age_months", CC_OVERDUE}) {
            Map<String, Object> st = CanonicalParameterStateService.state(id);
            assertThat(st.get("businessReadiness")).as(id).isEqualTo(BusinessReadiness.READY.name());
        }
    }

    @Test
    void testD7_catalogueImplementedDoesNotForceReady() {
        Map<String, Object> st = CanonicalParameterStateService.state("bureau.thin_file_indicator");
        assertThat(st.get("catalogueImplementedIsNotReadiness")).isEqualTo(true);
        assertThat(st.get("businessReadiness")).isEqualTo(BusinessReadiness.NOT_READY.name());
    }

    @Test
    void testR5_ingredientNeverCalculationSetup() {
        Map<String, Object> st = CanonicalParameterStateService.state(INQUIRY);
        assertThat(String.valueOf(st.get("primaryStatusLabel"))).doesNotContainIgnoringCase("Calculation needs setup");
        assertThat(String.valueOf(st.get("businessReadinessReason")))
                .isNotEqualTo(BusinessReadinessReason.CALCULATION_NOT_DEFINED.name());
    }

    @Test
    void testM1_m2_manualReadyWithManualReason() {
        Map<String, Object> st = CanonicalParameterStateService.state(MANUAL);
        assertThat(st.get("businessReadiness")).isEqualTo(BusinessReadiness.READY.name());
        assertThat(st.get("businessReadinessReason")).isEqualTo(BusinessReadinessReason.MANUAL_INPUT.name());
        Object execStatus = ((Map<?, ?>) st.get("execution")).get("status");
        // Runtime may be DATA_NOT_AVAILABLE / INPUT_REQUIRED — still READY structurally
        assertThat(st.get("businessReadiness")).isEqualTo(BusinessReadiness.READY.name());
        if (execStatus != null) {
            assertThat(String.valueOf(execStatus)).isIn(
                    ExecutionStatus.DATA_NOT_AVAILABLE.name(),
                    ExecutionStatus.INPUT_REQUIRED.name(),
                    ExecutionStatus.VALUE_AVAILABLE.name());
        }
    }

    @Test
    void testSurfacesAgree() {
        for (String id : new String[]{DPD30, CC_OVERDUE, SCORE}) {
            Map<String, Object> base = CanonicalParameterStateService.state(id);
            for (String surface : CanonicalParameterStateService.CONSUMER_SURFACES) {
                Map<String, Object> view = SurfaceCanonicalTruthFacade.forSurface(surface, id);
                assertThat(view.get("primaryStatus")).as(id + "/" + surface)
                        .isEqualTo(base.get("primaryStatus"));
            }
        }
    }

    @Test
    void architectureGuard_consumersMustNotRecomputeReadiness() throws Exception {
        Path root = Path.of("src/main/java");
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return s.contains("/service/readiness/DataParametersAdminService.java")
                                || s.contains("/ScorecardConvergenceService.java")
                                || s.contains("/PolicyRuleGraphService.java")
                                || s.contains("/SurfaceCanonicalTruthFacade.java");
                    })
                    .forEach(p -> {
                        try {
                            String src = Files.readString(p);
                            assertThat(src).as(p.toString())
                                    .doesNotContain("resolveSourcePlatform(");
                            // Must stamp/read CanonicalParameterStateService for readiness
                            if (p.toString().contains("DataParametersAdminService")
                                    || p.toString().contains("ScorecardConvergenceService")
                                    || p.toString().contains("PolicyRuleGraphService")) {
                                assertThat(src).as(p.toString())
                                        .contains("CanonicalParameterStateService");
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        // Semantics delegates platform to authority
        String sem = Files.readString(Path.of(
                "src/main/java/com/los/core/service/readiness/DataParametersCapabilitySemantics.java"));
        assertThat(sem).contains("CanonicalSourceIntegrationAuthority");
    }

    @Test
    void liveUnderwritingDoesNotReferenceBusinessReadiness() throws Exception {
        String flow = Files.readString(Path.of(
                "src/main/java/com/los/core/service/loan/LoanApplicationFlowService.java"));
        assertThat(flow).doesNotContain("businessReadiness");
        assertThat(flow).doesNotContain("BusinessReadiness");
        String flags = Files.readString(Path.of(
                "src/main/java/com/los/core/creditintelligence/policystudio/runtime/ownership/DecisionOwnershipFlags.java"));
        assertThat(flags).contains("LEGACY_FROZEN");
        assertThat(flags).contains("LIVE_DECISION_AUTHORITY_CHANGED = false");
    }
}
