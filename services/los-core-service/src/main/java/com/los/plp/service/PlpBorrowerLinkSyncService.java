package com.los.plp.service;

import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.LoanApplicationRepository;
import com.los.plp.client.PlpIntegrationClient;
import com.los.plp.client.PlpIntegrationException;
import com.los.plp.dto.PlpApiResponse;
import com.los.plp.dto.request.PlpSubProgramBorrowerLinkRequest;
import com.los.plp.dto.response.PlpSubProgramBorrowerLinkData;
import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.model.enums.PlpSyncStatus;
import com.los.plp.repository.SubProgramMasterRepository;
import com.los.plp.support.PlpApplicationSyncStatusReconcile;
import com.los.plp.support.PlpSyncSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlpBorrowerLinkSyncService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final SubProgramMasterRepository subProgramMasterRepository;
    private final PlpIntegrationClient plpIntegrationClient;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LoanApplication syncLinkStep(LoanApplication app) {
        if (app.getPlpBorrowerId() == null) {
            markLinkFailed(app, "plp_borrower_id missing — run borrower sync first");
            return loanApplicationRepository.save(app);
        }
        final var subProgramId = app.getSubProgramId();
        SubProgramMaster subProgram = subProgramMasterRepository.findById(subProgramId)
                .orElseThrow(() -> new IllegalArgumentException("Sub-program not found: " + subProgramId));
        if (subProgram.getPlpSubProgramId() == null) {
            markLinkFailed(app, "Sub-program not synced to PLP");
            return loanApplicationRepository.save(app);
        }

        if (app.getPlpSubProgramBorrowerId() != null) {
            log.info("PLP borrower link already exists for app={}, skipping", app.getId());
            if (PlpApplicationSyncStatusReconcile.reconcile(app)) {
                return loanApplicationRepository.save(app);
            }
            return app;
        }

        BigDecimal limit = app.getSanctionedAmount() != null ? app.getSanctionedAmount() : app.getRequestedAmount();
        if (limit == null) {
            markLinkFailed(app, "Sanctioned amount required for PLP borrower link");
            return loanApplicationRepository.save(app);
        }

        try {
            PlpSubProgramBorrowerLinkRequest request = PlpSubProgramBorrowerLinkRequest.builder()
                    .subProgramId(subProgram.getPlpSubProgramId().toString())
                    .borrowerId(app.getPlpBorrowerId().toString())
                    .borrowerLimit(limit)
                    .utilizedLimit(BigDecimal.ZERO)
                    .availableLimit(limit)
                    .build();
            PlpApiResponse<PlpSubProgramBorrowerLinkData> response =
                    plpIntegrationClient.syncSubProgramBorrowerLink(request);
            PlpSubProgramBorrowerLinkData data = response.getData();
            UUID linkId = PlpSyncSupport.parseUuid(data.getPlpSubProgramBorrowerId());
            if ("ACTIVE".equalsIgnoreCase(data.getStatus())
                    && linkId != null
                    && linkId.equals(app.getPlpSubProgramBorrowerId())) {
                log.info("PLP borrower link already ACTIVE for app={}, reconciling status", app.getId());
                if (PlpApplicationSyncStatusReconcile.reconcile(app)) {
                    return loanApplicationRepository.save(app);
                }
                return app;
            }
            app.setPlpSubProgramBorrowerId(linkId);
            app.setPlpLinkSyncStatus(PlpSyncStatus.SYNC_SUCCESS);
            app.setPlpLinkSyncedAt(PlpSyncSupport.now());
            log.info("PLP borrower link success: app={}, linkId={}, status={}",
                    app.getId(), data.getPlpSubProgramBorrowerId(), data.getStatus());
        } catch (PlpIntegrationException e) {
            markLinkFailed(app, e.getMessage());
            log.error("PLP borrower link failed for app {}: {}", app.getId(), e.getMessage());
        }
        return loanApplicationRepository.save(app);
    }

    private void markLinkFailed(LoanApplication app, String message) {
        app.setPlpLinkSyncStatus(PlpSyncStatus.SYNC_FAILED);
        app.setPlpProgramSyncStatus(PlpSyncStatus.SYNC_FAILED);
        app.setPlpProgramSyncError(PlpSyncSupport.truncateError(message));
    }
}
