package com.los.plp.service;

import com.los.core.service.audit.AuditService;
import com.los.plp.client.PlpApiAuditContext;
import com.los.plp.client.PlpIntegrationClient;
import com.los.plp.client.PlpIntegrationException;
import com.los.plp.dto.PlpApiResponse;
import com.los.plp.dto.request.PlpSubProgramActivateRequest;
import com.los.plp.dto.response.PlpSubProgramSyncData;
import com.los.plp.mapper.PlpSubProgramPayloadMapper;
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
public class PlpSubProgramSyncService {

    private final SubProgramMasterRepository subProgramMasterRepository;
    private final ProgramMasterRepository programMasterRepository;
    private final AnchorMasterRepository anchorMasterRepository;
    private final PlpIntegrationClient plpIntegrationClient;
    private final PlpProgramSyncService plpProgramSyncService;
    private final PlpAnchorSyncService plpAnchorSyncService;
    private final AuditService auditService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SubProgramMaster sync(UUID subProgramId) {
        return sync(subProgramId, false);
    }

    /**
     * @param preApproved when true, PLP creates the sub-program as ACTIVE (borrower sanction path)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SubProgramMaster sync(UUID subProgramId, boolean preApproved) {
        SubProgramMaster subProgram = subProgramMasterRepository.findById(subProgramId)
                .orElseThrow(() -> new IllegalArgumentException("Sub-program not found: " + subProgramId));
        return sync(subProgram, preApproved);
    }

    /** Sync using an already-persisted entity (avoids REQUIRES_NEW read-before-commit in create flows). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SubProgramMaster sync(SubProgramMaster subProgram) {
        return sync(subProgram, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SubProgramMaster sync(SubProgramMaster subProgram, boolean preApproved) {
        if (subProgram == null || subProgram.getId() == null) {
            throw new IllegalArgumentException("Sub-program id is required for PLP sync");
        }
        ProgramMaster program = ensureProgramSynced(subProgram.getProgramId());
        AnchorMaster anchor = ensureAnchorSynced(subProgram.getAnchorId());
        return sync(subProgram, program, anchor, preApproved);
    }

    /**
     * Sync using already-loaded program + anchor entities. Joins the caller's transaction (default REQUIRED)
     * so create flows that have just persisted (but not yet committed) the parent {@code program_masters} row
     * can call this without violating {@code sub_program_masters_program_id_fkey} — the previous
     * {@code REQUIRES_NEW} configuration could not see the outer tx's uncommitted INSERT, and Hibernate's
     * merge-on-detached-entity then attempted an INSERT in the inner tx that failed FK validation.
     * Retry callers go through the {@code sync(UUID)} overload above which keeps {@code REQUIRES_NEW}.
     */
    @Transactional
    public SubProgramMaster sync(SubProgramMaster subProgram, ProgramMaster program, AnchorMaster anchor) {
        return sync(subProgram, program, anchor, false);
    }

    @Transactional
    public SubProgramMaster sync(
            SubProgramMaster subProgram, ProgramMaster program, AnchorMaster anchor, boolean preApproved) {
        if (subProgram == null || subProgram.getId() == null) {
            throw new IllegalArgumentException("Sub-program id is required for PLP sync");
        }
        UUID subProgramId = subProgram.getId();

        if (program == null || program.getPlpProgramId() == null
                || anchor == null || anchor.getPlpAnchorId() == null) {
            subProgram.setPlpSubProgramSyncStatus(PlpSyncStatus.SYNC_FAILED);
            subProgram.setPlpSubProgramSyncError("Parent program or anchor not synced to PLP");
            return subProgramMasterRepository.save(subProgram);
        }

        UUID applicationId = firstNonNull(program.getAnchorApplicationId(), anchor.getSourceAnchorApplicationId());

        try {
            PlpApiResponse<PlpSubProgramSyncData> response = PlpApiAuditContext.callWithApplication(applicationId, () ->
                    plpIntegrationClient.syncSubProgram(
                            PlpSubProgramPayloadMapper.toRequest(subProgram, program, anchor, preApproved)));
            PlpSubProgramSyncData data = response.getData();
            subProgram.setPlpSubProgramId(PlpSyncSupport.parseUuid(data.getPlpSubProgramId()));
            subProgram.setPlpSubProgramSyncStatus(PlpSyncStatus.SYNC_SUCCESS);
            subProgram.setPlpSubProgramSyncError(null);
            subProgram.setPlpSubProgramSyncedAt(PlpSyncSupport.now());
            log.info("PLP sub-program sync success: losSubProgramId={}, plpSubProgramId={}, preApproved={}",
                    subProgramId, data.getPlpSubProgramId(), preApproved);
            auditSub(applicationId, "SUB_PROGRAM_SYNCED", Map.of(
                    "losSubProgramId", subProgramId.toString(),
                    "plpSubProgramId", String.valueOf(data.getPlpSubProgramId()),
                    "flowType", subProgram.getFlowType() != null ? subProgram.getFlowType() : "",
                    "preApproved", String.valueOf(preApproved)),
                    "Sub-program synced to PLP");
            if (preApproved) {
                ensureActivatedOnPlp(subProgram, applicationId);
            }
        } catch (PlpIntegrationException e) {
            subProgram.setPlpSubProgramSyncStatus(PlpSyncStatus.SYNC_FAILED);
            subProgram.setPlpSubProgramSyncError(PlpSyncSupport.truncateError(e.getMessage()));
            log.error("PLP sub-program sync failed for {}: {}", subProgramId, e.getMessage());
            auditSub(applicationId, "SUB_PROGRAM_SYNC_FAILED", Map.of(
                    "losSubProgramId", subProgramId.toString(),
                    "error", PlpSyncSupport.truncateError(e.getMessage())),
                    "Sub-program sync to PLP failed");
        }
        return subProgramMasterRepository.save(subProgram);
    }

    /**
     * Best-effort activate for sub-programs that already existed on PLP as DRAFT
     * (created earlier during program setup). No-op / non-fatal if already ACTIVE.
     */
    private void ensureActivatedOnPlp(SubProgramMaster subProgram, UUID applicationId) {
        try {
            PlpApiResponse<PlpSubProgramSyncData> response =
                    PlpApiAuditContext.callWithApplication(applicationId, () ->
                            plpIntegrationClient.activateSubProgram(
                                    PlpSubProgramActivateRequest.builder()
                                            .losSubProgramId(subProgram.getId().toString())
                                            .build()));
            PlpSubProgramSyncData data = response.getData();
            if (data != null && data.getPlpSubProgramId() != null) {
                subProgram.setPlpSubProgramId(PlpSyncSupport.parseUuid(data.getPlpSubProgramId()));
            }
            auditSub(applicationId, "SUB_PROGRAM_ACTIVATED", Map.of(
                    "losSubProgramId", subProgram.getId().toString(),
                    "plpSubProgramId", String.valueOf(subProgram.getPlpSubProgramId())),
                    "Sub-program activated on PLP for borrower sanction");
        } catch (PlpIntegrationException e) {
            // Already ACTIVE (or similar) is acceptable; only log — borrower link can still proceed.
            log.warn("PLP sub-program activate after preApproved sync for {}: {}",
                    subProgram.getId(), e.getMessage());
        }
    }

    @Transactional
    public SubProgramMaster activate(SubProgramMaster subProgram) {
        if (subProgram == null || subProgram.getId() == null) {
            throw new IllegalArgumentException("Sub-program id is required for PLP activation");
        }
        try {
            PlpApiResponse<PlpSubProgramSyncData> response = plpIntegrationClient.activateSubProgram(
                    PlpSubProgramActivateRequest.builder()
                            .losSubProgramId(subProgram.getId().toString())
                            .build());
            PlpSubProgramSyncData data = response.getData();
            if (data != null && data.getPlpSubProgramId() != null) {
                subProgram.setPlpSubProgramId(PlpSyncSupport.parseUuid(data.getPlpSubProgramId()));
            }
            subProgram.setPlpSubProgramSyncStatus(PlpSyncStatus.SYNC_SUCCESS);
            subProgram.setPlpSubProgramSyncError(null);
            subProgram.setPlpSubProgramSyncedAt(PlpSyncSupport.now());
            log.info("PLP sub-program activated: losSubProgramId={}", subProgram.getId());
        } catch (PlpIntegrationException e) {
            subProgram.setPlpSubProgramSyncStatus(PlpSyncStatus.SYNC_FAILED);
            subProgram.setPlpSubProgramSyncError(PlpSyncSupport.truncateError(e.getMessage()));
            log.error("PLP sub-program activation failed for {}: {}", subProgram.getId(), e.getMessage());
            throw e;
        }
        return subProgramMasterRepository.save(subProgram);
    }

    private ProgramMaster ensureProgramSynced(UUID programId) {
        ProgramMaster program = programMasterRepository.findById(programId)
                .orElseThrow(() -> new IllegalArgumentException("Program not found: " + programId));
        if (program.getPlpProgramId() == null || program.getPlpProgramSyncStatus() != PlpSyncStatus.SYNC_SUCCESS) {
            program = plpProgramSyncService.sync(programId);
        }
        return program;
    }

    private AnchorMaster ensureAnchorSynced(UUID anchorId) {
        AnchorMaster anchor = anchorMasterRepository.findById(anchorId)
                .orElseThrow(() -> new IllegalArgumentException("Anchor not found: " + anchorId));
        if (anchor.getPlpAnchorId() == null || anchor.getPlpAnchorSyncStatus() != PlpSyncStatus.SYNC_SUCCESS) {
            anchor = plpAnchorSyncService.sync(anchorId);
        }
        return anchor;
    }

    private void auditSub(UUID applicationId, String action, Map<String, Object> details, String description) {
        if (applicationId == null) {
            return;
        }
        Map<String, Object> state = new LinkedHashMap<>(details);
        auditService.logEvent(applicationId, "PLP_PROGRAM", action, null, null, state, description);
    }

    private static UUID firstNonNull(UUID a, UUID b) {
        return a != null ? a : b;
    }
}
