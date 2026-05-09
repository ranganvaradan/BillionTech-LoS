package com.los.core.service.demo;

import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.AaConsentRepository;
import com.los.core.repository.ApiAuditLogRepository;
import com.los.core.repository.ApplicationNoteRepository;
import com.los.core.repository.ApplicationStatusHistoryRepository;
import com.los.core.repository.AuditEventRepository;
import com.los.core.repository.CoLendingAllocationRepository;
import com.los.core.repository.CollateralValuationRepository;
import com.los.core.repository.DocumentRepository;
import com.los.core.repository.KfsDocumentRepository;
import com.los.core.repository.KycStepResultRepository;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.ManualKycReviewRepository;
import com.los.core.repository.NachMandateRepository;
import com.los.core.repository.StepExecutionRecordRepository;
import com.los.core.repository.TransactionRepository;
import com.los.core.repository.schema.los2.EsignRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoApplicationPurgeServiceTest {

    @Mock
    private ApplicationStatusHistoryRepository applicationStatusHistoryRepository;
    @Mock
    private StepExecutionRecordRepository stepExecutionRecordRepository;
    @Mock
    private KycStepResultRepository kycStepResultRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private ApiAuditLogRepository apiAuditLogRepository;
    @Mock
    private AuditEventRepository auditEventRepository;
    @Mock
    private EsignRequestRepository esignRequestRepository;
    @Mock
    private ApplicationNoteRepository applicationNoteRepository;
    @Mock
    private ManualKycReviewRepository manualKycReviewRepository;
    @Mock
    private KfsDocumentRepository kfsDocumentRepository;
    @Mock
    private CoLendingAllocationRepository coLendingAllocationRepository;
    @Mock
    private CollateralValuationRepository collateralValuationRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private NachMandateRepository nachMandateRepository;
    @Mock
    private AaConsentRepository aaConsentRepository;
    @Mock
    private LoanApplicationRepository loanApplicationRepository;

    private DemoApplicationPurgeService demoApplicationPurgeService;

    @BeforeEach
    void setUp() {
        demoApplicationPurgeService = new DemoApplicationPurgeService(
                applicationStatusHistoryRepository,
                stepExecutionRecordRepository,
                kycStepResultRepository,
                documentRepository,
                apiAuditLogRepository,
                auditEventRepository,
                esignRequestRepository,
                applicationNoteRepository,
                manualKycReviewRepository,
                kfsDocumentRepository,
                coLendingAllocationRepository,
                collateralValuationRepository,
                transactionRepository,
                nachMandateRepository,
                aaConsentRepository,
                loanApplicationRepository);
    }

    @Test
    void whenNoApplications_returnsZeroAndDoesNotCallDeletes() {
        when(loanApplicationRepository.findAll()).thenReturn(List.of());
        assertThat(demoApplicationPurgeService.deleteAllApplicationsAndDependents()).isZero();
        verify(applicationStatusHistoryRepository, never()).deleteByApplicationIdIn(anyList());
        verify(stepExecutionRecordRepository, never()).deleteByApplicationIdIn(anyList());
        verify(loanApplicationRepository, never()).deleteAllByIdInBatch(anyList());
    }

    @Test
    void whenApplicationsExist_deletesDependentsBeforeApplications() {
        UUID id1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        LoanApplication app = new LoanApplication();
        app.setId(id1);
        when(loanApplicationRepository.findAll()).thenReturn(List.of(app));
        InOrder o = inOrder(
                applicationStatusHistoryRepository,
                stepExecutionRecordRepository,
                kycStepResultRepository,
                documentRepository,
                apiAuditLogRepository,
                auditEventRepository,
                esignRequestRepository,
                applicationNoteRepository,
                manualKycReviewRepository,
                kfsDocumentRepository,
                coLendingAllocationRepository,
                collateralValuationRepository,
                transactionRepository,
                nachMandateRepository,
                aaConsentRepository,
                loanApplicationRepository);
        assertThat(demoApplicationPurgeService.deleteAllApplicationsAndDependents()).isEqualTo(1);
        o.verify(applicationStatusHistoryRepository).deleteByApplicationIdIn(List.of(id1));
        o.verify(stepExecutionRecordRepository).deleteByApplicationIdIn(List.of(id1));
        o.verify(kycStepResultRepository).deleteByApplicationIdIn(List.of(id1));
        o.verify(documentRepository).deleteByApplicationIdIn(List.of(id1));
        o.verify(apiAuditLogRepository).deleteByApplicationIdIn(List.of(id1));
        o.verify(auditEventRepository).deleteByApplicationIdIn(List.of(id1));
        o.verify(esignRequestRepository).deleteByApplicationIdIn(List.of(id1));
        o.verify(applicationNoteRepository).deleteByApplicationIdIn(List.of(id1));
        o.verify(manualKycReviewRepository).deleteByApplicationIdIn(List.of(id1));
        o.verify(kfsDocumentRepository).deleteByApplicationIdIn(List.of(id1));
        o.verify(coLendingAllocationRepository).deleteByApplicationIdIn(List.of(id1));
        o.verify(collateralValuationRepository).deleteByApplicationIdIn(List.of(id1));
        o.verify(transactionRepository).deleteByApplicationIdIn(List.of(id1));
        o.verify(nachMandateRepository).deleteByApplicationIdIn(List.of(id1));
        o.verify(aaConsentRepository).deleteByApplicationIdIn(List.of(id1));
        o.verify(loanApplicationRepository).deleteAllByIdInBatch(List.of(id1));
    }
}
