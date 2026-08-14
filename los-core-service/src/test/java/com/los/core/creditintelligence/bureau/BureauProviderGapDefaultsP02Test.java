package com.los.core.creditintelligence.bureau;

import com.los.core.creditintelligence.bureau.service.BureauMetricService;
import com.los.core.creditintelligence.bureau.service.CanonicalBureauContextBridge;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import com.los.core.creditintelligence.policystudio.metrics.PolicyBureauMetricService;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.DocumentRepository;
import com.los.core.repository.KycStepResultRepository;
import com.los.core.service.credit.CreditControlService;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import com.los.core.service.credit.LimitSizingService;
import com.los.core.service.document.OcrExtractionService;
import com.los.core.service.kyc.IKycOrchestrationService;
import com.los.core.service.underwriting.ScorecardValueProvenance;
import com.los.plp.service.InvoiceDiscountingVintageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * BUREAU-P0-2 — provider-gap defaults must never invent Bureau decision inputs.
 */
@ExtendWith(MockitoExtension.class)
class BureauProviderGapDefaultsP02Test {

    @Mock private IKycOrchestrationService kyc;
    @Mock private InvoiceDiscountingVintageService vintage;
    @Mock private DocumentRepository documentRepository;
    @Mock private KycStepResultRepository kycStepResultRepository;
    @Mock private OcrExtractionService ocrExtractionService;
    @Mock private LimitSizingService limitSizingService;
    @Mock private CanonicalBureauContextBridge canonicalBureauContextBridge;

    private CreditControlService creditControl;
    private PolicyBureauMetricService studioMetrics;
    private PolicyDslInterpreterV1 interpreter;

    @BeforeEach
    void setUp() {
        lenient().when(documentRepository.findByApplicationIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
        lenient().when(kycStepResultRepository.findTopByApplicationIdAndStepTypeOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        lenient().doNothing().when(limitSizingService).applyComputedMetrics(any(), any());
        lenient().when(canonicalBureauContextBridge.isEnabledFor(any())).thenReturn(true);
        lenient().when(canonicalBureauContextBridge.overlay(any())).thenReturn(Optional.empty());

        creditControl = new CreditControlService(
                kyc, vintage, documentRepository, kycStepResultRepository, ocrExtractionService, limitSizingService,
                canonicalBureauContextBridge);
        ReflectionTestUtils.setField(creditControl, "providerGapDefaultsEnabled", true);
        studioMetrics = new PolicyBureauMetricService();
        interpreter = new PolicyDslInterpreterV1();
    }

    @Test
    void gapFlagOn_doesNotInventBureauDecisionKeys() {
        LoanApplication app = app(700);
        EffectiveUnderwritingContext ctx = creditControl.resolveEffective(app, "PASS");

        assertThat(ctx.scorecard().get("PROVIDER_GAP_DEFAULT_ACTIVE").intValue()).isEqualTo(1);
        assertThat(ctx.scorecard()).containsKey("AVERAGE_BANK_BALANCE"); // non-bureau demo fill still allowed

        for (String key : CreditControlService.BUREAU_DECISION_SCORECARD_KEYS) {
            if ("BUREAU_SCORE".equals(key)) {
                continue; // score comes from app bureauScore, not gap
            }
            assertThat(ctx.scorecardProvenance().get(key))
                    .as("%s must not be GAP_DEFAULT", key)
                    .isNotEqualTo(ScorecardValueProvenance.GAP_DEFAULT);
            if (!ctx.scorecard().containsKey(key)) {
                continue;
            }
            assertThat(ctx.scorecardProvenance().get(key))
                    .as("%s provenance", key)
                    .isNotEqualTo(ScorecardValueProvenance.GAP_DEFAULT);
        }
        assertThat(ctx.scorecard()).doesNotContainKey("LIVE_UNSECURED_LOAN_COUNT");
        assertThat(ctx.scorecard()).doesNotContainKey("BUREAU_ENQUIRIES_3M");
        assertThat(ctx.scorecard()).doesNotContainKey("NTC_FLAG");
        assertThat(ctx.scorecard()).doesNotContainKey("MAX_DPD_6M");
        assertThat(ctx.scorecard()).doesNotContainKey("CC_UTILISATION_PCT");
    }

    @Test
    void realScore720_preserved() {
        when(canonicalBureauContextBridge.overlay(any())).thenReturn(Optional.of(overlay(720, Map.of(
                "BUREAU_SCORE", BigDecimal.valueOf(720)), Map.of(
                "BUREAU_SCORE", ScorecardValueProvenance.CANONICAL), Set.of("BUREAU_SCORE"))));
        EffectiveUnderwritingContext ctx = creditControl.resolveEffective(app(600), "PASS");
        assertThat(ctx.effectiveBureauScore()).isEqualTo(720);
        assertThat(ctx.scorecardProvenance().get("BUREAU_SCORE")).isEqualTo(ScorecardValueProvenance.CANONICAL);
    }

    @Test
    void equifaxNoHit_minusOneAndNtcNotConvertedToMissingOrGap() {
        Map<String, BigDecimal> sc = new LinkedHashMap<>();
        sc.put("BUREAU_SCORE", BigDecimal.valueOf(-1));
        sc.put("NTC_FLAG", BigDecimal.ONE);
        Map<String, String> prov = Map.of(
                "BUREAU_SCORE", ScorecardValueProvenance.CANONICAL,
                "NTC_FLAG", ScorecardValueProvenance.CANONICAL);
        when(canonicalBureauContextBridge.overlay(any())).thenReturn(Optional.of(
                overlay(-1, sc, prov, Set.of("BUREAU_SCORE", "NTC_FLAG"))));

        EffectiveUnderwritingContext ctx = creditControl.resolveEffective(app(-1), "PASS");
        assertThat(ctx.effectiveBureauScore()).isEqualTo(-1);
        assertThat(ctx.scorecard().get("NTC_FLAG")).isEqualByComparingTo("1");
        assertThat(ctx.scorecardProvenance().get("NTC_FLAG")).isEqualTo(ScorecardValueProvenance.CANONICAL);
        assertThat(ctx.scorecardProvenance().get("NTC_FLAG")).isNotEqualTo(ScorecardValueProvenance.GAP_DEFAULT);
    }

    @Test
    void realZeroEnquiry_preservedNotReplacedByGapFive() {
        Map<String, BigDecimal> sc = new LinkedHashMap<>();
        sc.put("BUREAU_ENQUIRIES_3M", BigDecimal.ZERO);
        Map<String, String> prov = Map.of("BUREAU_ENQUIRIES_3M", ScorecardValueProvenance.CANONICAL);
        when(canonicalBureauContextBridge.overlay(any())).thenReturn(Optional.of(
                overlay(720, sc, prov, Set.of("BUREAU_ENQUIRIES_3M"))));

        EffectiveUnderwritingContext ctx = creditControl.resolveEffective(app(720), "PASS");
        assertThat(ctx.scorecard().get("BUREAU_ENQUIRIES_3M")).isEqualByComparingTo("0");
        assertThat(ctx.scorecardProvenance().get("BUREAU_ENQUIRIES_3M"))
                .isEqualTo(ScorecardValueProvenance.CANONICAL);
    }

    @Test
    void missingEnquiry_policyTest_dataInsufficient_notZeroPass() {
        Map<String, Object> rule = PolicyDsl.lte(PolicyDsl.metric("bureau.recent_inquiries_90d"), 3);
        var ctx = PolicyDslInterpreterV1.EvaluationContext.of(Map.of(), Map.of(), null);
        String outcome = interpreter.evaluate(rule, ctx);
        assertThat(outcome).isEqualTo(PolicyDslInterpreterV1.DATA_INSUFFICIENT);
    }

    @Test
    void missingDpd_studioMetric_dataInsufficient() {
        Map<String, Object> r = studioMetrics.maxDpd6m(List.of(), java.time.LocalDate.of(2024, 6, 15));
        assertThat(r.get("outcome")).isEqualTo(PolicyBureauMetricService.OUTCOME_DI);
    }

    @Test
    void missingUnsecured_liveScorecard_notInventedFalseOrZero() {
        EffectiveUnderwritingContext ctx = creditControl.resolveEffective(app(720), "PASS");
        assertThat(ctx.scorecard()).doesNotContainKey("LIVE_UNSECURED_LOAN_COUNT");
    }

    @Test
    void malformedScore_studio_dataInsufficient() {
        assertThat(studioMetrics.consumerScore(null).get("outcome"))
                .isEqualTo(PolicyBureauMetricService.OUTCOME_DI);
    }

    @Test
    void providerUnavailable_noSyntheticBureauFactsOnScorecard() {
        when(canonicalBureauContextBridge.overlay(any())).thenReturn(Optional.empty());
        LoanApplication app = LoanApplication.builder()
                .id(UUID.randomUUID())
                .applicationNumber("NO-BUREAU")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PERSONAL_LOAN")
                .bureauScore(null)
                .build();
        EffectiveUnderwritingContext ctx = creditControl.resolveEffective(app, "PASS");
        assertThat(ctx.scorecard()).doesNotContainKey("LIVE_UNSECURED_LOAN_COUNT");
        assertThat(ctx.scorecard()).doesNotContainKey("NTC_FLAG");
        assertThat(ctx.scorecard()).doesNotContainKey("BUREAU_ENQUIRIES_3M");
        for (String key : CreditControlService.BUREAU_DECISION_SCORECARD_KEYS) {
            assertThat(ctx.scorecardProvenance().get(key))
                    .as("%s must not be GAP_DEFAULT when provider unavailable", key)
                    .isNotEqualTo(ScorecardValueProvenance.GAP_DEFAULT);
        }
    }

    @Test
    void explicitDemoFallback_bureauScoreUsesDemoProvenance_notGap() {
        Map<String, Object> fi = new HashMap<>();
        fi.put("demo", true);
        LoanApplication app = LoanApplication.builder()
                .id(UUID.randomUUID())
                .applicationNumber("DEMO")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PERSONAL_LOAN")
                .bureauScore(0)
                .financialInfo(fi)
                .build();
        EffectiveUnderwritingContext ctx = creditControl.resolveEffective(app, "PASS");
        assertThat(ctx.effectiveBureauScore()).isEqualTo(720);
        assertThat(ctx.scorecardProvenance().get("BUREAU_SCORE"))
                .isEqualTo(ScorecardValueProvenance.DEMO_DEFAULT);
        assertThat(ctx.scorecardProvenance().get("BUREAU_SCORE"))
                .isNotEqualTo(ScorecardValueProvenance.GAP_DEFAULT);
    }

    @Test
    void negativeGolden_syntheticLiveUnsecuredWouldFalselyPass_butMissingFailsClosed() {
        // Conceptual rule: LIVE_UNSECURED_LOAN_COUNT <= 5
        // Former gap default=2 would PASS; missing must be DATA_INSUFFICIENT.
        Map<String, Object> rule = PolicyDsl.lte(PolicyDsl.metric("bureau.live_unsecured_loan_count"), 5);
        var missingCtx = PolicyDslInterpreterV1.EvaluationContext.of(Map.of(), Map.of(), null);
        assertThat(interpreter.evaluate(rule, missingCtx))
                .isEqualTo(PolicyDslInterpreterV1.DATA_INSUFFICIENT);

        EffectiveUnderwritingContext live = creditControl.resolveEffective(app(720), "PASS");
        assertThat(live.scorecard()).doesNotContainKey("LIVE_UNSECURED_LOAN_COUNT");
    }

    @Test
    void negativeGolden_ntcFlagMissing_notInventedFalse() {
        Map<String, Object> rule = PolicyDsl.eq(PolicyDsl.fact("bureau.status_ntc"), false);
        var missingCtx = PolicyDslInterpreterV1.EvaluationContext.of(Map.of(), Map.of(), null);
        assertThat(interpreter.evaluate(rule, missingCtx))
                .isEqualTo(PolicyDslInterpreterV1.DATA_INSUFFICIENT);

        EffectiveUnderwritingContext live = creditControl.resolveEffective(app(720), "PASS");
        assertThat(live.scorecard()).doesNotContainKey("NTC_FLAG");
    }

    @Test
    void putBankGapDefault_refusesBureauKeysEvenIfCalled() {
        Map<String, BigDecimal> sc = new LinkedHashMap<>();
        Map<String, String> prov = new LinkedHashMap<>();
        // Simulate illegal call path
        ReflectionTestUtils.invokeMethod(
                CreditControlService.class,
                "putBankGapDefault",
                sc, prov, "NTC_FLAG", BigDecimal.ZERO);
        assertThat(sc).doesNotContainKey("NTC_FLAG");
    }

    @Test
    void stripIllegalBureauGapDefaults_removesGapProvenanceKeys() {
        Map<String, BigDecimal> sc = new LinkedHashMap<>();
        sc.put("NTC_FLAG", BigDecimal.ZERO);
        sc.put("AVERAGE_BANK_BALANCE", new BigDecimal("120000"));
        Map<String, String> prov = new LinkedHashMap<>();
        prov.put("NTC_FLAG", ScorecardValueProvenance.GAP_DEFAULT);
        prov.put("AVERAGE_BANK_BALANCE", ScorecardValueProvenance.GAP_DEFAULT);
        CreditControlService.stripIllegalBureauGapDefaults(sc, prov);
        assertThat(sc).doesNotContainKey("NTC_FLAG");
        assertThat(sc).containsKey("AVERAGE_BANK_BALANCE");
    }

    private static LoanApplication app(int bureauScore) {
        return LoanApplication.builder()
                .id(UUID.randomUUID())
                .applicationNumber("APP-P02")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PERSONAL_LOAN")
                .bureauScore(bureauScore)
                .build();
    }

    private static CanonicalBureauContextBridge.OverlayResult overlay(
            Integer score,
            Map<String, BigDecimal> sc,
            Map<String, String> prov,
            Set<String> keys) {
        return new CanonicalBureauContextBridge.OverlayResult(
                score, ScorecardValueProvenance.CANONICAL, sc, prov, keys);
    }
}
