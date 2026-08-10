package com.los.core.service.credit;

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
class CreditControlItrPreferenceTest {

    @Mock private IKycOrchestrationService kyc;
    @Mock private InvoiceDiscountingVintageService vintage;
    @Mock private DocumentRepository documentRepository;
    @Mock private KycStepResultRepository kycStepResultRepository;
    @Mock private OcrExtractionService ocrExtractionService;
    @Mock private LimitSizingService limitSizingService;

    @Test
    void resolveEffective_prefersItrReturnFormsMappedMetricsOverOcr() {
        UUID appId = UUID.randomUUID();
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("itrIncome", new BigDecimal("28921624"));
        metrics.put("pat", new BigDecimal("1026039"));
        metrics.put("ebitda", new BigDecimal("8620512"));
        metrics.put("tol", new BigDecimal("200309002"));
        metrics.put("tnw", new BigDecimal("68162682"));

        KycStepResult step = KycStepResult.builder()
                .applicationId(appId)
                .stepType(KycStepType.ITR_RETURN_FORMS)
                .provider(ProviderType.KARZA)
                .outcome(StepOutcome.SUCCESS)
                .parsedData(Map.of("mappedMetrics", metrics))
                .build();

        when(documentRepository.findByApplicationIdOrderByCreatedAtDesc(appId)).thenReturn(List.of());
        when(kycStepResultRepository.findTopByApplicationIdAndStepTypeOrderByCreatedAtDesc(
                        eq(appId), eq(KycStepType.ITR_RETURN_FORMS)))
                .thenReturn(Optional.of(step));
        when(kycStepResultRepository.findByApplicationIdOrderByCreatedAtAsc(appId)).thenReturn(List.of());
        org.mockito.Mockito.doNothing().when(limitSizingService).applyComputedMetrics(any(), any());

        CreditControlService svc = new CreditControlService(
                kyc, vintage, documentRepository, kycStepResultRepository, ocrExtractionService, limitSizingService);

        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .applicationNumber("N")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.PROPRIETOR)
                .loanProduct("BUSINESS_WC_INVOICE_DISCOUNTING")
                .bureauScore(720)
                .build();

        EffectiveUnderwritingContext ctx = svc.resolveEffective(app, "PASS");
        assertThat(ctx.scorecard().get("ITR_INCOME")).isEqualByComparingTo("28921624");
        assertThat(ctx.scorecard().get("PAT")).isEqualByComparingTo("1026039");
        assertThat(ctx.scorecard().get("TOL")).isEqualByComparingTo("200309002");
        assertThat(ctx.scorecard().get("TNW")).isEqualByComparingTo("68162682");
    }
}
