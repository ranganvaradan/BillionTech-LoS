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
import com.los.core.repository.LosUserRepository;
import com.los.core.repository.ManualKycReviewRepository;
import com.los.core.repository.NachMandateRepository;
import com.los.core.repository.StepExecutionRecordRepository;
import com.los.core.repository.TransactionRepository;
import com.los.core.repository.schema.los2.EsignRequestRepository;
import com.los.plp.repository.AnchorMasterRepository;
import com.los.plp.repository.ProgramMasterRepository;
import com.los.plp.repository.SubProgramMasterRepository;
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
    @Mock
    private LosUserRepository losUserRepository;
    @Mock
    private DemoPreservedUserEmails demoPreservedUserEmails;
    @Mock
    private SubProgramMasterRepository subProgramMasterRepository;
    @Mock
    private ProgramMasterRepository programMasterRepository;
    @Mock
    private AnchorMasterRepository anchorMasterRepository;

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
                loanApplicationRepository,
                losUserRepository,
                demoPreservedUserEmails,
                subProgramMasterRepository,
                programMasterRepository,
                anchorMasterRepository);
    }

    @Test
    void whenNoApplications_stillPurgesBorrowersAndLosPlpMasters() {
        when(loanApplicationRepository.findAll()).thenReturn(List.of());
        when(demoPreservedUserEmails.preservedEmailsLower()).thenReturn(List.of("borrower@credinnov.com"));
        when(losUserRepository.deleteBorrowersNotInPreservedEmails(List.of("borrower@credinnov.com"))).thenReturn(2);
        when(subProgramMasterRepository.count()).thenReturn(1L);
        when(programMasterRepository.count()).thenReturn(2L);
        when(anchorMasterRepository.count()).thenReturn(1L);

        DemoPurgeResult result = demoApplicationPurgeService.purgeAllDemoData();

        assertThat(result.deletedApplications()).isZero();
        assertThat(result.deletedBorrowerUsers()).isEqualTo(2);
        assertThat(result.deletedLosPlpSubPrograms()).isEqualTo(1);
        assertThat(result.deletedLosPlpPrograms()).isEqualTo(2);
        assertThat(result.deletedLosPlpAnchors()).isEqualTo(1);
        verify(subProgramMasterRepository).deleteAllInBatch();
        verify(programMasterRepository).deleteAllInBatch();
        verify(anchorMasterRepository).deleteAllInBatch();
    }

    @Test
    void whenApplicationsExist_deletesInOrderThenLosPlpMasters() {
        UUID id1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        LoanApplication app = new LoanApplication();
        app.setId(id1);
        when(loanApplicationRepository.findAll()).thenReturn(List.of(app));
        when(demoPreservedUserEmails.preservedEmailsLower()).thenReturn(List.of("borrower@credinnov.com"));
        when(losUserRepository.deleteBorrowersNotInPreservedEmails(List.of("borrower@credinnov.com"))).thenReturn(1);
        when(subProgramMasterRepository.count()).thenReturn(0L);
        when(programMasterRepository.count()).thenReturn(0L);
        when(anchorMasterRepository.count()).thenReturn(0L);

        InOrder o = inOrder(
                applicationStatusHistoryRepository,
                loanApplicationRepository,
                losUserRepository,
                subProgramMasterRepository,
                programMasterRepository,
                anchorMasterRepository);
        DemoPurgeResult result = demoApplicationPurgeService.purgeAllDemoData();
        assertThat(result.deletedApplications()).isEqualTo(1);
        o.verify(loanApplicationRepository).deleteAllByIdInBatch(List.of(id1));
        o.verify(losUserRepository).deleteBorrowersNotInPreservedEmails(anyList());
        verify(subProgramMasterRepository, never()).deleteAllInBatch();
    }
}
