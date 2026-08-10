package com.los.plp.service;

import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import com.los.plp.client.PlpApiAuditContext;
import com.los.plp.client.PlpIntegrationClient;
import com.los.plp.client.PlpIntegrationException;
import com.los.plp.dto.PlpApiResponse;
import com.los.plp.dto.request.PlpBorrowerProgramMappingRequest;
import com.los.plp.dto.response.PlpBorrowerProgramMappingData;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.model.enums.PlpSyncStatus;
import com.los.plp.repository.ProgramMasterRepository;
import com.los.plp.repository.SubProgramMasterRepository;
import com.los.plp.support.PlpApplicationSyncStatusReconcile;
import com.los.plp.support.PlpSyncSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlpBorrowerProgramMappingSyncService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final LoanApplicationRepository loanApplicationRepository;
    private final SubProgramMasterRepository subProgramMasterRepository;
    private final ProgramMasterRepository programMasterRepository;
    private final PlpIntegrationClient plpIntegrationClient;
    private final AuditService auditService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LoanApplication syncMappingStep(LoanApplication app) {
        if (app.getPlpBorrowerId() == null) {
            markMappingFailed(app, "plp_borrower_id missing — run borrower sync first");
            return loanApplicationRepository.save(app);
        }
        final var subProgramId = app.getSubProgramId();
        SubProgramMaster subProgram = subProgramMasterRepository.findById(subProgramId)
                .orElseThrow(() -> new IllegalArgumentException("Sub-program not found: " + subProgramId));
        ProgramMaster program = programMasterRepository.findById(subProgram.getProgramId())
                .orElseThrow(() -> new IllegalArgumentException("Program not found: " + subProgram.getProgramId()));

        if (subProgram.getPlpSubProgramId() == null || program.getPlpProgramId() == null) {
            markMappingFailed(app, "Program or sub-program not synced to PLP");
            return loanApplicationRepository.save(app);
        }

        if (app.getPlpBorrowerProgramMappingId() != null) {
            log.info("PLP mapping already exists for app={}, skipping", app.getId());
            if (PlpApplicationSyncStatusReconcile.reconcile(app)) {
                return loanApplicationRepository.save(app);
            }
            return app;
        }

        BigDecimal approvedLimit = app.getSanctionedAmount() != null ? app.getSanctionedAmount() : app.getRequestedAmount();
        if (approvedLimit == null) {
            markMappingFailed(app, "Sanctioned amount required for PLP mapping");
            return loanApplicationRepository.save(app);
        }

        LocalDate validFrom = LocalDate.now();
        int tenure = app.getTenureMonths() != null ? app.getTenureMonths() : 12;
        LocalDate validTo = validFrom.plusMonths(tenure);

        try {
            PlpBorrowerProgramMappingRequest request = PlpBorrowerProgramMappingRequest.builder()
                    .losApplicationId(app.getId().toString())
                    .losBorrowerId(app.getCustomerId().toString())
                    .plpBorrowerId(app.getPlpBorrowerId().toString())
                    .plpProgramId(program.getPlpProgramId().toString())
                    .plpSubProgramId(subProgram.getPlpSubProgramId().toString())
                    .approvedLimit(approvedLimit)
                    .validFrom(validFrom.format(DATE_FMT))
                    .validTo(validTo.format(DATE_FMT))
                    .build();
            PlpApiResponse<PlpBorrowerProgramMappingData> response =
                    PlpApiAuditContext.callWithApplication(app.getId(), () ->
                            plpIntegrationClient.syncBorrowerProgramMapping(request));
            PlpBorrowerProgramMappingData data = response.getData();
            if (!"PENDING_APPROVAL".equalsIgnoreCase(data.getMappingStatus())
                    && data.getMappingStatus() != null) {
                log.info("PLP mapping status for app={}: {}", app.getId(), data.getMappingStatus());
            }
            app.setPlpBorrowerProgramMappingId(PlpSyncSupport.parseUuid(data.getPlpBorrowerProgramMappingId()));
            app.setPlpMappingSyncStatus(PlpSyncStatus.SYNC_SUCCESS);
            app.setPlpMappingSyncedAt(PlpSyncSupport.now());
            app.setPlpProgramSyncStatus(PlpSyncStatus.SYNC_SUCCESS);
            app.setPlpProgramSyncError(null);
            app.setPlpProgramSyncedAt(PlpSyncSupport.now());
            log.info("PLP mapping success: app={}, mappingId={}", app.getId(), data.getPlpBorrowerProgramMappingId());
            auditMapping(app.getId(), "BORROWER_MAPPING_SYNCED", Map.of(
                    "plpBorrowerProgramMappingId", String.valueOf(data.getPlpBorrowerProgramMappingId()),
                    "mappingStatus", String.valueOf(data.getMappingStatus())), "Borrower program mapping synced to PLP");
        } catch (PlpIntegrationException e) {
            markMappingFailed(app, e.getMessage());
            log.error("PLP mapping failed for app {}: {}", app.getId(), e.getMessage());
            auditMapping(app.getId(), "BORROWER_MAPPING_SYNC_FAILED", Map.of(
                    "error", PlpSyncSupport.truncateError(e.getMessage())), "Borrower program mapping sync failed");
        }
        return loanApplicationRepository.save(app);
    }

    private void markMappingFailed(LoanApplication app, String message) {
        app.setPlpMappingSyncStatus(PlpSyncStatus.SYNC_FAILED);
        app.setPlpProgramSyncStatus(PlpSyncStatus.SYNC_FAILED);
        app.setPlpProgramSyncError(PlpSyncSupport.truncateError(message));
    }

    private void auditMapping(UUID applicationId, String action, Map<String, Object> details, String description) {
        if (applicationId == null) {
            return;
        }
        Map<String, Object> state = new LinkedHashMap<>(details);
        auditService.logEvent(applicationId, "PLP_SYNC", action, null, null, state, description);
    }
}
