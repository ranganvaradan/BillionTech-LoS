package com.los.core.creditintelligence.service;

import com.los.core.creditintelligence.banking.service.BankingIngestionService;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.domain.CiFactSnapshot;
import com.los.core.creditintelligence.domain.CiSourceRecord;
import com.los.core.creditintelligence.domain.CiUnderwritingFact;
import com.los.core.creditintelligence.gst.service.GstIngestionService;
import com.los.core.creditintelligence.repository.CiFactSnapshotRepository;
import com.los.core.creditintelligence.repository.CiUnderwritingFactRepository;
import com.los.core.creditintelligence.support.ContentHasher;
import com.los.core.creditintelligence.tax.service.TaxIngestionService;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.BorrowerType;
import com.los.core.service.audit.AuditService;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Snapshot build must not trigger GST / banking / tax ensureIngested side effects.
 */
@ExtendWith(MockitoExtension.class)
class SnapshotBuilderNoIngestSideEffectTest {

    @Mock private CiFactSnapshotRepository snapshotRepository;
    @Mock private CiUnderwritingFactRepository factRepository;
    @Mock private SourceRegistryService sourceRegistryService;
    @Mock private AuditService auditService;
    @Mock private com.los.core.creditintelligence.bureau.repository.CiBureauReportRepository bureauReportRepository;
    @Mock private com.los.core.creditintelligence.core.repository.CiMetricResultRepository metricResultRepository;
    @Mock private com.los.core.creditintelligence.gst.repository.CiGstRegistrationRepository gstRegistrationRepository;
    @Mock private GstIngestionService gstIngestionService;
    @Mock private com.los.core.creditintelligence.banking.repository.CiBankAccountRepository bankAccountRepository;
    @Mock private BankingIngestionService bankingIngestionService;
    @Mock private com.los.core.creditintelligence.tax.repository.CiItrReturnRepository itrReturnRepository;
    @Mock private com.los.core.creditintelligence.tax.repository.CiAisSummaryRepository aisSummaryRepository;
    @Mock private com.los.core.creditintelligence.tax.repository.CiForm26AsSummaryRepository form26AsSummaryRepository;
    @Mock private TaxIngestionService taxIngestionService;
    @Mock private com.los.core.creditintelligence.reconciliation.service.ReconciliationIngestionService reconciliationIngestionService;

    private UnderwritingFactSnapshotBuilder builder;

    @BeforeEach
    void setUp() {
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        builder = new UnderwritingFactSnapshotBuilder(
                snapshotRepository, factRepository, sourceRegistryService,
                new ContentHasher(), props, auditService,
                bureauReportRepository, metricResultRepository,
                gstRegistrationRepository, gstIngestionService,
                bankAccountRepository, bankingIngestionService,
                itrReturnRepository, aisSummaryRepository, form26AsSummaryRepository, taxIngestionService,
                reconciliationIngestionService,
                mock(com.los.core.creditintelligence.bureau.repository.CiBureauTradelineRepository.class),
                mock(com.los.core.creditintelligence.bureau.repository.CiBureauPaymentHistoryRepository.class),
                mock(com.los.core.creditintelligence.bureau.repository.CiBureauInquiryRepository.class),
                mock(com.los.core.creditintelligence.bureau.repository.CiBureauReportSummaryRepository.class),
                mock(com.los.core.creditintelligence.bureau.repository.CiBureauScoringElementRepository.class));

        org.mockito.Mockito.lenient().when(bureauReportRepository.findFirstByApplicationIdOrderByCreatedAtDesc(any()))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(gstRegistrationRepository.findByApplicationIdOrderByCreatedAtDesc(any()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient().when(bankAccountRepository.findByApplicationIdOrderByCreatedAtDesc(any()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient().when(itrReturnRepository
                        .findByApplicationIdAndEffectiveTrueOrderByAssessmentYearDesc(any()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient().when(aisSummaryRepository.findByApplicationIdOrderByFinancialYearDesc(any()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient().when(form26AsSummaryRepository.findByApplicationIdOrderByFinancialYearDesc(any()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient().when(reconciliationIngestionService.isEnabledFor(any(LoanApplication.class)))
                .thenReturn(false);
    }

    @Test
    void buildAndFreezeNeverCallsEnsureIngested() {
        UUID appId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .applicationNumber("APP-NO-INGEST")
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PERSONAL_LOAN")
                .requestedAmount(new BigDecimal("100000"))
                .tenureMonths(12)
                .personalInfo(Map.of("state", "KA", "city", "Bengaluru"))
                .build();

        EffectiveUnderwritingContext ctx = new EffectiveUnderwritingContext(
                700, true, new BigDecimal("40000"), new BigDecimal("5000"),
                "KA", "Bengaluru", "BUREAU", "DECLARED", "KYC", Map.of());

        when(snapshotRepository.findForUpdateMaxVersion(appId)).thenReturn(Optional.empty());
        when(snapshotRepository.save(any())).thenAnswer(inv -> {
            CiFactSnapshot s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            return s;
        });
        when(sourceRegistryService.createOrGet(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> CiSourceRecord.builder().id(UUID.randomUUID()).build());
        when(factRepository.save(any())).thenAnswer(inv -> {
            CiUnderwritingFact f = inv.getArgument(0);
            f.setId(UUID.randomUUID());
            return f;
        });

        builder.buildAndFreeze(app, ctx, "PASS", "tester");

        verify(gstIngestionService, never()).ensureIngested(any());
        verify(bankingIngestionService, never()).ensureIngested(any());
        verify(taxIngestionService, never()).ensureIngested(any());
    }
}
