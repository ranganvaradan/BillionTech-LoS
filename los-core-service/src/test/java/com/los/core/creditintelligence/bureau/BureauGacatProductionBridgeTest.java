package com.los.core.creditintelligence.bureau;

import com.los.core.creditintelligence.bureau.service.BureauMetricService;
import com.los.core.creditintelligence.bureau.service.CanonicalBureauContextBridge;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
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

@ExtendWith(MockitoExtension.class)
class BureauGacatProductionBridgeTest {

    @Mock private IKycOrchestrationService kyc;
    @Mock private InvoiceDiscountingVintageService vintage;
    @Mock private DocumentRepository documentRepository;
    @Mock private KycStepResultRepository kycStepResultRepository;
    @Mock private OcrExtractionService ocrExtractionService;
    @Mock private LimitSizingService limitSizingService;
    @Mock private CanonicalBureauContextBridge canonicalBureauContextBridge;

    @Test
    void productionUnderwriteUsesCanonicalBureauNotGapDefault() {
        UUID appId = UUID.randomUUID();
        lenient().when(documentRepository.findByApplicationIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
        lenient().when(kycStepResultRepository.findTopByApplicationIdAndStepTypeOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        lenient().doNothing().when(limitSizingService).applyComputedMetrics(any(), any());

        Map<String, BigDecimal> overlaySc = new LinkedHashMap<>();
        overlaySc.put("BUREAU_SCORE", BigDecimal.valueOf(758));
        overlaySc.put("LIVE_UNSECURED_LOAN_COUNT", BigDecimal.valueOf(3));
        overlaySc.put("MAX_DPD_6M", BigDecimal.valueOf(71));
        Map<String, String> overlayProv = Map.of(
                "BUREAU_SCORE", ScorecardValueProvenance.CANONICAL,
                "LIVE_UNSECURED_LOAN_COUNT", ScorecardValueProvenance.CANONICAL,
                "MAX_DPD_6M", ScorecardValueProvenance.CANONICAL);

        when(canonicalBureauContextBridge.isEnabledFor(any())).thenReturn(true);
        when(canonicalBureauContextBridge.overlay(any())).thenReturn(Optional.of(
                new CanonicalBureauContextBridge.OverlayResult(
                        758,
                        ScorecardValueProvenance.CANONICAL,
                        overlaySc,
                        overlayProv,
                        Set.of("BUREAU_SCORE", "LIVE_UNSECURED_LOAN_COUNT", "MAX_DPD_6M"))));

        CreditControlService creditControl = new CreditControlService(
                kyc, vintage, documentRepository, kycStepResultRepository, ocrExtractionService, limitSizingService,
                canonicalBureauContextBridge);
        ReflectionTestUtils.setField(creditControl, "providerGapDefaultsEnabled", true);

        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .applicationNumber("APP-GOLDEN")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("UNSECURED_WC")
                .bureauScore(600)
                .build();

        EffectiveUnderwritingContext ctx = creditControl.resolveEffective(app, "PASS");

        assertThat(ctx.effectiveBureauScore()).isEqualTo(758);
        assertThat(ctx.scorecard().get("LIVE_UNSECURED_LOAN_COUNT")).isEqualByComparingTo("3");
        assertThat(ctx.scorecard().get("LIVE_UNSECURED_LOAN_COUNT"))
                .isNotEqualByComparingTo("2");
        assertThat(ctx.scorecardProvenance().get("LIVE_UNSECURED_LOAN_COUNT"))
                .isEqualTo(ScorecardValueProvenance.CANONICAL);
        assertThat(ctx.scorecard().get("MAX_DPD_6M")).isEqualByComparingTo("71");
    }
}
