package com.los.plp.service;

import com.los.core.model.catalog.StandardLoanProduct;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.IntakeSegment;
import com.los.core.repository.LoanApplicationRepository;
import com.los.plp.client.PlpIntegrationClient;
import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.model.enums.PlpSyncStatus;
import com.los.plp.repository.SubProgramMasterRepository;
import com.los.plp.support.PlpApplicationSyncStatusReconcile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlpSanctionSyncOrchestrator {

    private final LoanApplicationRepository loanApplicationRepository;
    private final SubProgramMasterRepository subProgramMasterRepository;
    private final PlpIntegrationClient plpIntegrationClient;
    private final PlpSubProgramSyncService plpSubProgramSyncService;
    private final PlpBorrowerSyncService plpBorrowerSyncService;
    private final PlpBorrowerLinkSyncService plpBorrowerLinkSyncService;
    private final PlpBorrowerProgramMappingSyncService plpBorrowerProgramMappingSyncService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LoanApplication syncAfterSanction(UUID applicationId) {
        if (!plpIntegrationClient.isEnabled()) {
            log.debug("PLP sync disabled — skipping sanction sync for {}", applicationId);
            return loanApplicationRepository.findById(applicationId).orElse(null);
        }

        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));

        if (app.getIntakeSegment() == IntakeSegment.ANCHOR) {
            log.debug("Skipping PLP sanction sync for anchor intake application {}", applicationId);
            return app;
        }

        if (!StandardLoanProduct.BUSINESS_WC_INVOICE_DISCOUNTING.equals(app.getLoanProduct())) {
            log.debug("Skipping PLP sanction sync — not invoice discounting: {}", app.getLoanProduct());
            return app;
        }

        if (app.getSubProgramId() == null) {
            app.setPlpProgramSyncStatus(PlpSyncStatus.SYNC_FAILED);
            app.setPlpProgramSyncError("sub_program_id is required for PLP sync");
            return loanApplicationRepository.save(app);
        }

        ensureMastersSynced(app.getSubProgramId());

        app = reload(applicationId);
        app = reconcileAndPersist(applicationId, app);

        if (app.getPlpBorrowerId() == null) {
            app = plpBorrowerSyncService.syncBorrowerStep(app);
            app = reload(applicationId);
            if (app.getPlpBorrowerId() == null) {
                return app;
            }
        }

        if (app.getPlpSubProgramBorrowerId() == null) {
            app = plpBorrowerLinkSyncService.syncLinkStep(app);
            app = reload(applicationId);
            if (app.getPlpSubProgramBorrowerId() == null) {
                return app;
            }
        }

        if (app.getPlpBorrowerProgramMappingId() == null) {
            app = plpBorrowerProgramMappingSyncService.syncMappingStep(app);
        }

        app = reload(applicationId);
        return reconcileAndPersist(applicationId, app);
    }

    private LoanApplication reconcileAndPersist(UUID applicationId, LoanApplication app) {
        if (PlpApplicationSyncStatusReconcile.reconcile(app)) {
            app = loanApplicationRepository.save(app);
            log.info("PLP sync status reconciled from existing PLP IDs for application {}", applicationId);
        }
        return app;
    }

    private void ensureMastersSynced(UUID subProgramId) {
        SubProgramMaster subProgram = subProgramMasterRepository.findById(subProgramId)
                .orElseThrow(() -> new IllegalArgumentException("Sub-program not found: " + subProgramId));
        if (subProgram.getPlpSubProgramId() == null
                || subProgram.getPlpSubProgramSyncStatus() != PlpSyncStatus.SYNC_SUCCESS) {
            plpSubProgramSyncService.sync(subProgramId);
        }
    }

    private LoanApplication reload(UUID applicationId) {
        return loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
    }
}
