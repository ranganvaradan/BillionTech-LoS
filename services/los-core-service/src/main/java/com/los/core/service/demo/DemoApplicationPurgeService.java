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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Deletes all loan applications and common dependent records for a clean local/demo database.
 * Does not remove workflow configuration or reference master data.
 * Entire method runs in one transaction: any failure rolls back; no partial deletes.
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

    /**
     * Permanently removes dependent rows and the given loan application rows (e.g. borrower draft delete).
     */
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
