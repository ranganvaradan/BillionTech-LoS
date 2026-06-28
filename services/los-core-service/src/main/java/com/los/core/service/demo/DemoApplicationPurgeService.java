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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Deletes all loan applications and common dependent records for a clean local/demo database.
 * Also removes auto-provisioned borrower {@code los_users} (not seed accounts) and LOS-local
 * PLP master cache ({@code sub_program_masters}, {@code program_masters}, {@code anchor_masters}).
 * Does not call the remote PLP app — use PLP's own reset for that.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoApplicationPurgeService {

    private final ApplicationStatusHistoryRepository applicationStatusHistoryRepository;
    private final StepExecutionRecordRepository stepExecutionRecordRepository;
    private final KycStepResultRepository kycStepResultRepository;
    private final DocumentRepository documentRepository;
    private final ApiAuditLogRepository apiAuditLogRepository;
    private final AuditEventRepository auditEventRepository;
    private final EsignRequestRepository esignRequestRepository;
    private final ApplicationNoteRepository applicationNoteRepository;
    private final ManualKycReviewRepository manualKycReviewRepository;
    private final KfsDocumentRepository kfsDocumentRepository;
    private final CoLendingAllocationRepository coLendingAllocationRepository;
    private final CollateralValuationRepository collateralValuationRepository;
    private final TransactionRepository transactionRepository;
    private final NachMandateRepository nachMandateRepository;
    private final AaConsentRepository aaConsentRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final LosUserRepository losUserRepository;
    private final DemoPreservedUserEmails demoPreservedUserEmails;
    private final SubProgramMasterRepository subProgramMasterRepository;
    private final ProgramMasterRepository programMasterRepository;
    private final AnchorMasterRepository anchorMasterRepository;

    @Transactional(rollbackFor = Exception.class)
    public DemoPurgeResult purgeAllDemoData() {
        int deletedApplications = deleteAllApplicationsAndDependents();
        int deletedBorrowers = deleteProvisionedBorrowerUsers();
        DemoLosPlpMasterPurgeCounts plpMasters = deleteLosPlpMasterEntries();
        return new DemoPurgeResult(
                deletedApplications,
                deletedBorrowers,
                plpMasters.subPrograms(),
                plpMasters.programs(),
                plpMasters.anchors());
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteAllApplicationsAndDependents() {
        List<UUID> ids = loanApplicationRepository.findAll()
                .stream()
                .map(LoanApplication::getId)
                .toList();
        int found = ids.size();
        log.info("Found {} application(s) to delete", found);
        if (ids.isEmpty()) {
            log.info("No applications to delete; skipping child deletes");
            return 0;
        }
        deleteApplicationDependentsByIds(ids);
        log.info("Deleted applications successfully: {} row(s) removed from loan_applications", found);
        return found;
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteProvisionedBorrowerUsers() {
        List<String> preserved = demoPreservedUserEmails.preservedEmailsLower();
        if (preserved.isEmpty()) {
            log.warn("No preserved borrower emails configured; skipping borrower user purge");
            return 0;
        }
        int removed = losUserRepository.deleteBorrowersNotInPreservedEmails(preserved);
        log.info("Deleted {} auto-provisioned borrower user(s); preserved {}", removed, preserved);
        return removed;
    }

    /**
     * Removes PLP-linked master rows stored in LOS (sync cache). Applications must be deleted first
     * because {@code loan_applications.sub_program_id} references {@code sub_program_masters}.
     */
    @Transactional(rollbackFor = Exception.class)
    public DemoLosPlpMasterPurgeCounts deleteLosPlpMasterEntries() {
        long subCount = subProgramMasterRepository.count();
        long programCount = programMasterRepository.count();
        long anchorCount = anchorMasterRepository.count();
        if (subCount + programCount + anchorCount == 0) {
            log.info("No LOS PLP master rows to delete");
            return new DemoLosPlpMasterPurgeCounts(0, 0, 0);
        }
        subProgramMasterRepository.deleteAllInBatch();
        programMasterRepository.deleteAllInBatch();
        anchorMasterRepository.deleteAllInBatch();
        log.info(
                "Deleted LOS PLP master cache: {} sub-program(s), {} program(s), {} anchor(s)",
                subCount,
                programCount,
                anchorCount);
        return new DemoLosPlpMasterPurgeCounts((int) subCount, (int) programCount, (int) anchorCount);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteApplicationDependentsByIds(List<UUID> applicationIds) {
        if (applicationIds == null || applicationIds.isEmpty()) {
            return;
        }
        List<UUID> ids = List.copyOf(applicationIds);
        log.info("Deleting child records for {} application id(s)", ids.size());
        applicationStatusHistoryRepository.deleteByApplicationIdIn(ids);
        stepExecutionRecordRepository.deleteByApplicationIdIn(ids);
        kycStepResultRepository.deleteByApplicationIdIn(ids);
        documentRepository.deleteByApplicationIdIn(ids);
        apiAuditLogRepository.deleteByApplicationIdIn(ids);
        auditEventRepository.deleteByApplicationIdIn(ids);
        esignRequestRepository.deleteByApplicationIdIn(ids);
        applicationNoteRepository.deleteByApplicationIdIn(ids);
        manualKycReviewRepository.deleteByApplicationIdIn(ids);
        kfsDocumentRepository.deleteByApplicationIdIn(ids);
        coLendingAllocationRepository.deleteByApplicationIdIn(ids);
        collateralValuationRepository.deleteByApplicationIdIn(ids);
        transactionRepository.deleteByApplicationIdIn(ids);
        nachMandateRepository.deleteByApplicationIdIn(ids);
        aaConsentRepository.deleteByApplicationIdIn(ids);
        loanApplicationRepository.deleteAllByIdInBatch(ids);
    }
}
