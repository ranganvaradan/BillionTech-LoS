package com.los.core.service.kfs;

import com.los.core.config.EdiKfsProperties;
import com.los.core.model.entity.KfsDocument;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.KfsDocumentRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.SanctionRecordRepository;
import com.los.core.service.kfs.edi.EdiKfsDocxPdfService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KfsPdfGenerationServiceEdiRoutingTest {

    @Mock
    private LoanApplicationRepository applicationRepository;
    @Mock
    private SanctionRecordRepository sanctionRecordRepository;
    @Mock
    private KfsDocumentRepository kfsDocumentRepository;
    @Mock
    private EdiKfsDocxPdfService ediKfsDocxPdfService;
    @Mock
    private EdiKfsProperties ediKfsProperties;

    @InjectMocks
    private KfsPdfGenerationService kfsPdfGenerationService;

    @Test
    void generateKfsPdf_delegatesToEdiServiceForEdiApplications() throws Exception {
        UUID appId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .applicationNumber("EDI-1")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("Personal")
                .lmsTenureUnit("Day")
                .build();
        KfsDocument kfs = KfsDocument.builder()
                .applicationId(appId)
                .version("v1")
                .sanctionedAmount(new BigDecimal("50000"))
                .interestRate(new BigDecimal("18"))
                .apr(new BigDecimal("20"))
                .tenureMonths(90)
                .emiAmount(new BigDecimal("600"))
                .totalInterest(BigDecimal.ZERO)
                .totalRepayment(new BigDecimal("54000"))
                .totalCostOfCredit(new BigDecimal("4000"))
                .processingFee(BigDecimal.ZERO)
                .stampDuty(BigDecimal.ZERO)
                .insurancePremium(BigDecimal.ZERO)
                .otherCharges(BigDecimal.ZERO)
                .status("GENERATED")
                .build();

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(ediKfsDocxPdfService.shouldUseEdiTemplate(app)).thenReturn(true);
        when(ediKfsDocxPdfService.generatePdf(kfs, app)).thenReturn(new byte[] {1, 2, 3});

        byte[] pdf = kfsPdfGenerationService.generateKfsPdf(kfs);

        verify(ediKfsDocxPdfService).generatePdf(kfs, app);
        assertThat(pdf).hasSize(3);
    }

    @Test
    void generateKfsPdf_usesOpenPdfForNonEdiApplications() throws Exception {
        UUID appId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .applicationNumber("EMI-1")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("Personal")
                .lmsTenureUnit("Month")
                .build();
        KfsDocument kfs = KfsDocument.builder()
                .applicationId(appId)
                .version("v1")
                .sanctionedAmount(new BigDecimal("50000"))
                .interestRate(new BigDecimal("18"))
                .apr(new BigDecimal("20"))
                .tenureMonths(12)
                .emiAmount(new BigDecimal("4500"))
                .totalInterest(new BigDecimal("4000"))
                .totalRepayment(new BigDecimal("54000"))
                .totalCostOfCredit(new BigDecimal("4000"))
                .processingFee(BigDecimal.ZERO)
                .stampDuty(BigDecimal.ZERO)
                .insurancePremium(BigDecimal.ZERO)
                .otherCharges(BigDecimal.ZERO)
                .status("GENERATED")
                .build();

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(ediKfsDocxPdfService.shouldUseEdiTemplate(app)).thenReturn(false);

        byte[] pdf = kfsPdfGenerationService.generateKfsPdf(kfs);

        verify(ediKfsDocxPdfService, never()).generatePdf(any(), any());
        assertThat(pdf.length).isGreaterThan(100);
    }

    @Test
    void generateKfsPdf_openPdfOmitsEncorePreOpenSummaryJsonFromAdditionalTerms() throws Exception {
        UUID appId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .applicationNumber("EMI-2")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("Personal")
                .lmsTenureUnit("Month")
                .build();
        KfsDocument kfs = KfsDocument.builder()
                .applicationId(appId)
                .version("v1")
                .sanctionedAmount(new BigDecimal("50000"))
                .interestRate(new BigDecimal("18"))
                .apr(new BigDecimal("20"))
                .tenureMonths(12)
                .emiAmount(new BigDecimal("4500"))
                .totalInterest(new BigDecimal("4000"))
                .totalRepayment(new BigDecimal("54000"))
                .totalCostOfCredit(new BigDecimal("4000"))
                .processingFee(BigDecimal.ZERO)
                .stampDuty(BigDecimal.ZERO)
                .insurancePremium(BigDecimal.ZERO)
                .otherCharges(BigDecimal.ZERO)
                .additionalTerms(java.util.Map.of(
                        "encorePreOpenSummaryJson", "{\"summaryList\":[{\"amount\":\"99999\"}]}",
                        "encoreRepaymentScheduleJson", "[{\"installment\":1,\"amount\":\"1000\"}]",
                        "kfsSource", "ENCORE_PRE_OPEN"))
                .status("GENERATED")
                .build();

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(ediKfsDocxPdfService.shouldUseEdiTemplate(app)).thenReturn(false);

        byte[] pdf = kfsPdfGenerationService.generateKfsPdf(kfs);

        verify(ediKfsDocxPdfService, never()).generatePdf(any(), any());
        assertThat(new String(pdf)).doesNotContain("encorePreOpenSummaryJson");
        assertThat(new String(pdf)).doesNotContain("encoreRepaymentScheduleJson");
        assertThat(new String(pdf)).doesNotContain("summaryList");
        assertThat(new String(pdf)).contains("kfsSource");
    }
}
