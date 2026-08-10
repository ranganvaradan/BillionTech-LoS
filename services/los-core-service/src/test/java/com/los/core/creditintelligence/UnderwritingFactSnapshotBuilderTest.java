package com.los.core.creditintelligence;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.domain.CiFactSnapshot;
import com.los.core.creditintelligence.domain.CiSourceRecord;
import com.los.core.creditintelligence.domain.CiUnderwritingFact;
import com.los.core.creditintelligence.domain.FactClassification;
import com.los.core.creditintelligence.domain.SnapshotStatus;
import com.los.core.creditintelligence.repository.CiFactSnapshotRepository;
import com.los.core.creditintelligence.repository.CiUnderwritingFactRepository;
import com.los.core.creditintelligence.service.SourceRegistryService;
import com.los.core.creditintelligence.service.UnderwritingFactSnapshotBuilder;
import com.los.core.creditintelligence.support.ContentHasher;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.BorrowerType;
import com.los.core.service.audit.AuditService;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnderwritingFactSnapshotBuilderTest {

    @Mock
    private CiFactSnapshotRepository snapshotRepository;
    @Mock
    private CiUnderwritingFactRepository factRepository;
    @Mock
    private SourceRegistryService sourceRegistryService;
    @Mock
    private AuditService auditService;
    @Mock
    private com.los.core.creditintelligence.bureau.repository.CiBureauReportRepository bureauReportRepository;
    @Mock
    private com.los.core.creditintelligence.core.repository.CiMetricResultRepository metricResultRepository;
    @Mock
    private com.los.core.creditintelligence.gst.repository.CiGstRegistrationRepository gstRegistrationRepository;
    @Mock
    private com.los.core.creditintelligence.gst.service.GstIngestionService gstIngestionService;
    @Mock
    private com.los.core.creditintelligence.banking.repository.CiBankAccountRepository bankAccountRepository;
    @Mock
    private com.los.core.creditintelligence.banking.service.BankingIngestionService bankingIngestionService;
    @Mock
    private com.los.core.creditintelligence.tax.repository.CiItrReturnRepository itrReturnRepository;
    @Mock
    private com.los.core.creditintelligence.tax.repository.CiAisSummaryRepository aisSummaryRepository;
    @Mock
    private com.los.core.creditintelligence.tax.repository.CiForm26AsSummaryRepository form26AsSummaryRepository;
    @Mock
    private com.los.core.creditintelligence.tax.service.TaxIngestionService taxIngestionService;
    @Mock
    private com.los.core.creditintelligence.reconciliation.service.ReconciliationIngestionService reconciliationIngestionService;

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
                reconciliationIngestionService);
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
    void classifiesDemoFallbackBureauAsDefaulted() {
        UUID appId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .applicationNumber("APP-DEMO")
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PERSONAL_LOAN")
                .requestedAmount(new BigDecimal("300000"))
                .tenureMonths(24)
                .personalInfo(Map.of("state", "KA", "city", "Bengaluru"))
                .build();

        Map<String, BigDecimal> sc = new LinkedHashMap<>();
        sc.put("BUREAU_SCORE", new BigDecimal("720"));
        sc.put("MONTHLY_INCOME", new BigDecimal("80000"));
        sc.put("MONTHLY_OBLIGATION", new BigDecimal("20000"));
        sc.put("LIVE_UNSECURED_LOAN_COUNT", new BigDecimal("2"));
        sc.put("DEMO_FALLBACK_ACTIVE", BigDecimal.ONE);

        EffectiveUnderwritingContext ctx = new EffectiveUnderwritingContext(
                720, true, new BigDecimal("80000"), new BigDecimal("20000"),
                "KA", "Bengaluru", "DEMO_FALLBACK", "DEMO_FALLBACK", "DEMO_FALLBACK", sc);

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

        ArgumentCaptor<CiUnderwritingFact> factCaptor = ArgumentCaptor.forClass(CiUnderwritingFact.class);
        verify(factRepository, atLeastOnce()).save(factCaptor.capture());

        boolean foundDefaultedScore = factCaptor.getAllValues().stream()
                .anyMatch(f -> "bureau.consumer.score".equals(f.getCanonicalPath())
                        && FactClassification.DEFAULTED.name().equals(f.getClassification()));
        boolean foundDefaultedLive = factCaptor.getAllValues().stream()
                .anyMatch(f -> "compat.LIVE_UNSECURED_LOAN_COUNT".equals(f.getCanonicalPath())
                        && FactClassification.DEFAULTED.name().equals(f.getClassification()));
        assertTrue(foundDefaultedScore);
        assertTrue(foundDefaultedLive);

        ArgumentCaptor<CiFactSnapshot> snapCaptor = ArgumentCaptor.forClass(CiFactSnapshot.class);
        verify(snapshotRepository, atLeastOnce()).save(snapCaptor.capture());
        assertTrue(snapCaptor.getAllValues().stream()
                .anyMatch(s -> SnapshotStatus.FROZEN.name().equals(s.getStatus())));
    }

    @Test
    void rejectsAddFactOnFrozenSnapshot() {
        UUID snapshotId = UUID.randomUUID();
        when(snapshotRepository.findById(snapshotId)).thenReturn(Optional.of(
                CiFactSnapshot.builder()
                        .id(snapshotId)
                        .status(SnapshotStatus.FROZEN.name())
                        .tenantId(UUID.randomUUID())
                        .build()));

        assertThrows(IllegalStateException.class, () ->
                builder.addFact(snapshotId, CiUnderwritingFact.builder()
                        .canonicalPath("compat.X")
                        .valueType("DECIMAL")
                        .value(Map.of("v", 1))
                        .classification(FactClassification.VERIFIED.name())
                        .build()));
    }
}
