package com.los.core.service.flow.step;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.SanctionRecord;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.BorrowerType;
import com.los.core.model.catalog.StandardLoanProduct;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.config.EsignNotificationProperties;
import com.los.core.repository.KfsDocumentRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.SanctionRecordRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.esign.EsignSigningLinkNotifier;
import com.los.core.service.esign.EsignRequestTrackingService;
import com.los.core.service.integration.IIntegrationRouterService;
import com.los.core.service.kfs.KfsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EsignInitiateStepExecutorTest {

    @Mock
    private LoanApplicationRepository applicationRepository;
    @Mock
    private KfsDocumentRepository kfsDocumentRepository;
    @Mock
    private SanctionRecordRepository sanctionRecordRepository;
    @Mock
    private KfsService kfsService;
    @Mock
    private IIntegrationRouterService integrationRouter;
    @Mock
    private AuditService auditService;
    @Mock
    private EsignRequestTrackingService esignRequestTrackingService;
    @Mock
    private EsignSigningLinkNotifier esignSigningLinkNotifier;
    @Mock
    private EsignNotificationProperties esignNotificationProperties;

    @InjectMocks
    private EsignInitiateStepExecutor executor;

    @Test
    void onSuccess_persistsEsignRequestAndOutputShapeUnchanged() {
        UUID appId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .applicationNumber("APP-9")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PERSONAL")
                .status(ApplicationStatus.KFS_GENERATED)
                .build();
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));
        Map<String, Object> signer = Map.of("name", "X");
        IIntegrationRouterService.ESignRouteResult result =
                new IIntegrationRouterService.ESignRouteResult(
                        true, "TX-9", "https://sign.example", null, "EMSIGNER");
        when(integrationRouter.routeESignRequest(eq(appId), any()))
                .thenReturn(result);
        when(applicationRepository.save(org.mockito.ArgumentMatchers.any(LoanApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(esignNotificationProperties.isEnabled()).thenReturn(false);

        StepResult step = executor.execute(appId, Map.of("signerInfo", signer));
        assertTrue(step.success());
        assertEquals("TX-9", step.output().get("transactionId"));
        assertEquals("https://sign.example", step.output().get("signingUrl"));
        assertEquals(true, step.output().get("esignSuccess"));
        assertEquals("", step.output().get("errorMessage"));
        assertEquals("ESIGN_PENDING", step.output().get("status"));
        assertEquals("APP-9", step.output().get("applicationNumber"));
        assertEquals(appId, step.output().get("applicationId"));

        verify(esignRequestTrackingService).recordInitiationSuccess(
                eq(appId), eq("KFS_AGREEMENT"), eq("EMSIGNER"), eq("TX-9"), eq("https://sign.example"),
                any(), any(), eq("ESIGN_AGREEMENT"));
    }

    @Test
    void sanctionedInvoiceDiscountingBorrower_backfillsTermsAndInitiatesEsign() {
        UUID appId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .applicationNumber("ID-BOR-1")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.COMPANY)
                .loanProduct(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)
                .intakeSegment(IntakeSegment.BORROWER)
                .status(ApplicationStatus.SANCTIONED)
                .build();
        SanctionRecord rec = SanctionRecord.builder()
                .id(UUID.randomUUID())
                .applicationId(appId)
                .approvedAmount(new BigDecimal("500000"))
                .approvedTenure(12)
                .interestRate(new BigDecimal("14"))
                .build();
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(kfsDocumentRepository.findFirstByApplicationIdOrderByCreatedAtDesc(appId)).thenReturn(Optional.empty());
        when(sanctionRecordRepository.findTopByApplicationIdOrderByCreatedAtDesc(appId)).thenReturn(Optional.of(rec));
        when(applicationRepository.save(org.mockito.ArgumentMatchers.any(LoanApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        IIntegrationRouterService.ESignRouteResult result =
                new IIntegrationRouterService.ESignRouteResult(
                        true, "TX-ID", "https://sign.example/id", null, "EMSIGNER");
        when(integrationRouter.routeESignRequest(eq(appId), any())).thenReturn(result);
        when(esignNotificationProperties.isEnabled()).thenReturn(false);

        StepResult step = executor.execute(appId, Map.of("signerInfo", Map.of("name", "Borrower")));

        assertTrue(step.success());
        assertEquals("ESIGN_PENDING", step.output().get("status"));
        verify(kfsService).generateInvoiceDiscountingBorrowerTermsDocument(eq(appId), eq(rec), any());
    }

    @Test
    void sanctionedInvoiceDiscountingAnchor_backfillsProgramTermsAndInitiatesEsign() {
        UUID appId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .id(appId)
                .applicationNumber("ID-ANC-1")
                .customerId(UUID.randomUUID())
                .borrowerType(BorrowerType.COMPANY)
                .loanProduct(StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING)
                .intakeSegment(IntakeSegment.ANCHOR)
                .status(ApplicationStatus.SANCTIONED)
                .build();
        SanctionRecord rec = SanctionRecord.builder()
                .id(UUID.randomUUID())
                .applicationId(appId)
                .approvedAmount(new BigDecimal("5000000"))
                .approvedTenure(12)
                .interestRate(new BigDecimal("14"))
                .build();
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(kfsDocumentRepository.findFirstByApplicationIdOrderByCreatedAtDesc(appId)).thenReturn(Optional.empty());
        when(sanctionRecordRepository.findTopByApplicationIdOrderByCreatedAtDesc(appId)).thenReturn(Optional.of(rec));
        when(applicationRepository.save(org.mockito.ArgumentMatchers.any(LoanApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        IIntegrationRouterService.ESignRouteResult result =
                new IIntegrationRouterService.ESignRouteResult(
                        true, "TX-ANC", "https://sign.example/anc", null, "EMSIGNER");
        when(integrationRouter.routeESignRequest(eq(appId), any())).thenReturn(result);
        when(esignNotificationProperties.isEnabled()).thenReturn(false);

        StepResult step = executor.execute(appId, Map.of("signerInfo", Map.of("name", "Anchor")));

        assertTrue(step.success());
        assertEquals("ESIGN_PENDING", step.output().get("status"));
        verify(kfsService).generateAnchorProgramTermsDocument(eq(appId), eq(rec), any());
    }
}
