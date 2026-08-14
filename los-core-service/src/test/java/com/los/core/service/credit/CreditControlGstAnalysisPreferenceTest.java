package com.los.core.service.credit;

import com.los.core.creditintelligence.bureau.service.CanonicalBureauContextBridge;
import com.los.core.model.entity.KycStepResult;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.KycStepType;
import com.los.core.model.enums.ProviderType;
import com.los.core.model.enums.StepOutcome;
import com.los.core.repository.DocumentRepository;
import com.los.core.repository.KycStepResultRepository;
import com.los.core.service.document.OcrExtractionService;
import com.los.core.service.kyc.IKycOrchestrationService;
import com.los.plp.service.InvoiceDiscountingVintageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditControlGstAnalysisPreferenceTest {

    @Mock private IKycOrchestrationService kyc;
    @Mock private InvoiceDiscountingVintageService vintage;
    @Mock private DocumentRepository documentRepository;
    @Mock private KycStepResultRepository kycStepResultRepository;
    @Mock private OcrExtractionService ocrExtractionService;
    @Mock private LimitSizingService limitSizingService;
    @Mock private CanonicalBureauContextBridge canonicalBureauContextBridge;

    @Test
    void resolveEffective_prefersGstAnalysisReportMappedMetrics() {
        UUID appId = UUID.randomUUID();
        // Values above SCF_MIN_ANNUAL_GST_TURNOVER so gap defaults do not replace real extract metrics.
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("annualGstTurnover", new BigDecimal("56542043.96"));
        metrics.put("gstIncome", new BigDecimal("46542043.92"));
        metrics.put("avgGmv3m", new BigDecimal("1565422.67"));
        metrics.put("active90days", 1);

        KycStepResult step = KycStepResult.builder()
                .applicationId(appId)
                .stepType(KycStepType.GST_ANALYSIS)
                .provider(ProviderType.KARZA)
                .outcome(StepOutcome.SUCCESS)
                .parsedData(Map.of(
                        "phase", "REPORT",
                        "mappedMetrics", metrics))
                .build();

        when(documentRepository.findByApplicationIdOrderByCreatedAtDesc(appId)).thenReturn(List.of());
        when(kycStepResultRepository.findTopByApplicationIdAndStepTypeOrderByCreatedAtDesc(
                        eq(appId), eq(KycStepType.ITR_RETURN_FORMS)))
                .thenReturn(Optional.empty());
        when(kycStepResultRepository.findByApplicationIdOrderByCreatedAtAsc(appId))
                .thenReturn(List.of(step));
        org.mockito.Mockito.doNothing().when(limitSizingService).applyComputedMetrics(any(), any());
        org.mockito.Mockito.lenient().when(canonicalBureauContextBridge.isEnabledFor(any())).thenReturn(false);

        CreditControlService svc = new CreditControlService(
                kyc, vintage, documentRepository, kycStepResultRepository, ocrExtractionService, limitSizingService,
                canonicalBureauContextBridge);

        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .applicationNumber("N")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.PROPRIETOR)
                .loanProduct("BUSINESS_WC_INVOICE_DISCOUNTING")
                .bureauScore(720)
                .build();

        EffectiveUnderwritingContext ctx = svc.resolveEffective(app, "PASS");
        assertThat(ctx.scorecard().get("ANNUAL_GST_TURNOVER")).isEqualByComparingTo("56542043.96");
        assertThat(ctx.scorecard().get("GST_INCOME")).isEqualByComparingTo("46542043.92");
        assertThat(ctx.scorecard().get("avgGmv3m")).isEqualByComparingTo("1565422.67");
        assertThat(ctx.scorecard().get("active90days")).isEqualByComparingTo("1");
    }
}
