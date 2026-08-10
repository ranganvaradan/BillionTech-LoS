package com.los.plp.service;

import com.los.core.service.audit.AuditService;
import com.los.plp.client.PlpApiAuditContext;
import com.los.plp.client.PlpIntegrationClient;
import com.los.plp.client.PlpIntegrationException;
import com.los.plp.dto.PlpApiResponse;
import com.los.plp.dto.request.PlpProgramActivateRequest;
import com.los.plp.dto.response.PlpProgramSyncData;
import com.los.plp.mapper.PlpProgramPayloadMapper;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.enums.PlpSyncStatus;
import com.los.plp.repository.ProgramMasterRepository;
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
public class PlpProgramSyncService {

    private final ProgramMasterRepository programMasterRepository;
    private final PlpIntegrationClient plpIntegrationClient;
    private final PlpProgramStatusMirrorService plpProgramStatusMirrorService;
    private final AuditService auditService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProgramMaster sync(UUID programId) {
        ProgramMaster program = programMasterRepository.findById(programId)
                .orElseThrow(() -> new IllegalArgumentException("Program not found: " + programId));
        return sync(program);
    }

    /**
     * Sync using an already-loaded entity. Joins the caller's transaction (default REQUIRED) so create flows
     * that have just persisted (but not yet committed) the parent {@code program_masters} row can call this
     * without colliding with the outer pending INSERT — which under {@code REQUIRES_NEW} previously caused
     * Hibernate to merge the detached entity as a fresh INSERT and produce a duplicate-key error on commit.
     * Retry callers go through the {@code sync(UUID)} overload above which keeps {@code REQUIRES_NEW}.
     */
    @Transactional
    public ProgramMaster sync(ProgramMaster program) {
        if (program == null || program.getId() == null) {
            throw new IllegalArgumentException("Program id is required for PLP sync");
        }
        UUID programId = program.getId();
        UUID applicationId = program.getAnchorApplicationId();
        try {
            PlpApiResponse<PlpProgramSyncData> response = PlpApiAuditContext.callWithApplication(applicationId, () ->
                    plpIntegrationClient.syncProgram(PlpProgramPayloadMapper.toRequest(program)));
            PlpProgramSyncData data = response.getData();
            program.setPlpProgramId(PlpSyncSupport.parseUuid(data.getPlpProgramId()));
            program.setPlpProgramSyncStatus(PlpSyncStatus.SYNC_SUCCESS);
            program.setPlpProgramSyncError(null);
            program.setPlpProgramSyncedAt(PlpSyncSupport.now());
            String previousStatus = program.getPlpOperationalStatus();
            if (data.getStatus() != null) {
                plpProgramStatusMirrorService.mirror(program, data.getStatus());
            }
            log.info("PLP program sync success: losProgramId={}, plpProgramId={}, plpStatus={}",
                    programId, data.getPlpProgramId(), data.getStatus());
            auditProgram(applicationId, "PROGRAM_SYNCED", Map.of(
                    "losProgramId", programId.toString(),
                    "plpProgramId", String.valueOf(data.getPlpProgramId()),
                    "plpStatus", String.valueOf(data.getStatus()),
                    "previousPlpStatus", previousStatus != null ? previousStatus : "",
                    "approvalStatus", program.getApprovalStatus() != null ? program.getApprovalStatus().name() : ""),
                    "Program synced to PLP" + (data.getStatus() != null ? " (" + data.getStatus() + ")" : ""));
        } catch (PlpIntegrationException e) {
            program.setPlpProgramSyncStatus(PlpSyncStatus.SYNC_FAILED);
            program.setPlpProgramSyncError(PlpSyncSupport.truncateError(e.getMessage()));
            log.error("PLP program sync failed for {}: {}", programId, e.getMessage());
            auditProgram(applicationId, "PROGRAM_SYNC_FAILED", Map.of(
                    "losProgramId", programId.toString(),
                    "error", PlpSyncSupport.truncateError(e.getMessage())),
                    "Program sync to PLP failed");
        }
        return programMasterRepository.save(program);
    }

    @Transactional
    public ProgramMaster activate(ProgramMaster program) {
        if (program == null || program.getId() == null) {
            throw new IllegalArgumentException("Program id is required for PLP activation");
        }
        UUID applicationId = program.getAnchorApplicationId();
        try {
            PlpApiResponse<PlpProgramSyncData> response = PlpApiAuditContext.callWithApplication(applicationId, () ->
                    plpIntegrationClient.activateProgram(
                            PlpProgramActivateRequest.builder().losProgramId(program.getId().toString()).build()));
            PlpProgramSyncData data = response.getData();
            if (data != null && data.getPlpProgramId() != null) {
                program.setPlpProgramId(PlpSyncSupport.parseUuid(data.getPlpProgramId()));
            }
            program.setPlpProgramSyncStatus(PlpSyncStatus.SYNC_SUCCESS);
            program.setPlpProgramSyncError(null);
            program.setPlpProgramSyncedAt(PlpSyncSupport.now());
            if (data != null && data.getStatus() != null) {
                plpProgramStatusMirrorService.mirror(program, data.getStatus());
            }
            log.info("PLP program activated: losProgramId={}", program.getId());
            auditProgram(applicationId, "PROGRAM_ACTIVATED", Map.of(
                    "losProgramId", program.getId().toString(),
                    "plpStatus", data != null && data.getStatus() != null ? data.getStatus() : "",
                    "approvalStatus", program.getApprovalStatus() != null ? program.getApprovalStatus().name() : ""),
                    "Program activated on PLP");
        } catch (PlpIntegrationException e) {
            program.setPlpProgramSyncStatus(PlpSyncStatus.SYNC_FAILED);
            program.setPlpProgramSyncError(PlpSyncSupport.truncateError(e.getMessage()));
            log.error("PLP program activation failed for {}: {}", program.getId(), e.getMessage());
            auditProgram(applicationId, "PROGRAM_ACTIVATE_FAILED", Map.of(
                    "losProgramId", program.getId().toString(),
                    "error", PlpSyncSupport.truncateError(e.getMessage())),
                    "Program activation on PLP failed");
            throw e;
        }
        return programMasterRepository.save(program);
    }

    private void auditProgram(UUID applicationId, String action, Map<String, Object> details, String description) {
        if (applicationId == null) {
            return;
        }
        Map<String, Object> state = new LinkedHashMap<>(details);
        auditService.logEvent(applicationId, "PLP_PROGRAM", action, null, null, state, description);
    }
}
