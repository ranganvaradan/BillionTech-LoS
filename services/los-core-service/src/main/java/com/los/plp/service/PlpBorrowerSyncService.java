package com.los.plp.service;

import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import com.los.plp.client.PlpApiAuditContext;
import com.los.plp.client.PlpIntegrationClient;
import com.los.plp.client.PlpIntegrationException;
import com.los.plp.dto.PlpApiResponse;
import com.los.plp.dto.response.PlpBorrowerSyncData;
import com.los.plp.mapper.PlpBorrowerPayloadMapper;
import com.los.plp.model.entity.AnchorMaster;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.model.enums.PlpSyncStatus;
import com.los.plp.repository.AnchorMasterRepository;
import com.los.plp.repository.ProgramMasterRepository;
import com.los.plp.repository.SubProgramMasterRepository;
import com.los.plp.support.PlpSyncSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlpBorrowerSyncService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final SubProgramMasterRepository subProgramMasterRepository;
    private final ProgramMasterRepository programMasterRepository;
    private final AnchorMasterRepository anchorMasterRepository;
    private final PlpIntegrationClient plpIntegrationClient;
    private final AuditService auditService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LoanApplication syncForCustomer(UUID customerId, UUID applicationId) {
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        if (!customerId.equals(app.getCustomerId())) {
            throw new IllegalArgumentException("Customer does not match application");
        }
        return syncBorrowerStep(app);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LoanApplication syncBorrowerStep(LoanApplication app) {
        SubProgramContext ctx = resolveSubProgramContext(app);
        if (ctx.program.getPlpProgramId() == null || ctx.anchor.getPlpAnchorId() == null) {
            markBorrowerFailed(app, "Program or anchor not synced to PLP");
            return loanApplicationRepository.save(app);
        }
        try {
            PlpApiResponse<PlpBorrowerSyncData> response = PlpApiAuditContext.callWithApplication(app.getId(), () ->
                    plpIntegrationClient.syncBorrower(
                            PlpBorrowerPayloadMapper.toRequest(app, ctx.program, ctx.anchor)));
            app.setPlpBorrowerId(PlpSyncSupport.parseUuid(response.getData().getPlpBorrowerId()));
            app.setPlpBorrowerSyncStatus(PlpSyncStatus.SYNC_SUCCESS);
            app.setPlpBorrowerSyncedAt(PlpSyncSupport.now());
            log.info("PLP borrower sync success: app={}, plpBorrowerId={}", app.getId(), response.getData().getPlpBorrowerId());
            auditBorrower(app.getId(), "BORROWER_SYNCED", Map.of(
                    "plpBorrowerId", String.valueOf(response.getData().getPlpBorrowerId()),
                    "status", PlpSyncStatus.SYNC_SUCCESS.name()), "Borrower synced to PLP");
        } catch (PlpIntegrationException e) {
            markBorrowerFailed(app, e.getMessage());
            log.error("PLP borrower sync failed for app {}: {}", app.getId(), e.getMessage());
            auditBorrower(app.getId(), "BORROWER_SYNC_FAILED", Map.of(
                    "error", PlpSyncSupport.truncateError(e.getMessage())), "Borrower sync to PLP failed");
        }
        return loanApplicationRepository.save(app);
    }

    private SubProgramContext resolveSubProgramContext(LoanApplication app) {
        if (app.getSubProgramId() == null) {
            throw new IllegalArgumentException("Application has no sub_program_id — required for PLP sync");
        }
        SubProgramMaster subProgram = subProgramMasterRepository.findById(app.getSubProgramId())
                .orElseThrow(() -> new IllegalArgumentException("Sub-program not found: " + app.getSubProgramId()));
        ProgramMaster program = programMasterRepository.findById(subProgram.getProgramId())
                .orElseThrow(() -> new IllegalArgumentException("Program not found: " + subProgram.getProgramId()));
        AnchorMaster anchor = anchorMasterRepository.findById(subProgram.getAnchorId())
                .orElseThrow(() -> new IllegalArgumentException("Anchor not found: " + subProgram.getAnchorId()));
        return new SubProgramContext(subProgram, program, anchor);
    }

    private void markBorrowerFailed(LoanApplication app, String message) {
        app.setPlpBorrowerSyncStatus(PlpSyncStatus.SYNC_FAILED);
        app.setPlpProgramSyncStatus(PlpSyncStatus.SYNC_FAILED);
        app.setPlpProgramSyncError(PlpSyncSupport.truncateError(message));
    }

    private record SubProgramContext(SubProgramMaster subProgram, ProgramMaster program, AnchorMaster anchor) {
    }

    private void auditBorrower(UUID applicationId, String action, Map<String, Object> details, String description) {
        if (applicationId == null) {
            return;
        }
        Map<String, Object> state = new LinkedHashMap<>(details);
        auditService.logEvent(applicationId, "PLP_SYNC", action, null, null, state, description);
    }
}
