package com.los.plp.service;

import com.los.plp.client.PlpIntegrationClient;
import com.los.plp.client.PlpIntegrationException;
import com.los.plp.dto.PlpApiResponse;
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

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlpProgramSyncService {

    private final ProgramMasterRepository programMasterRepository;
    private final PlpIntegrationClient plpIntegrationClient;

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
        try {
            PlpApiResponse<PlpProgramSyncData> response =
                    plpIntegrationClient.syncProgram(PlpProgramPayloadMapper.toRequest(program));
            PlpProgramSyncData data = response.getData();
            program.setPlpProgramId(PlpSyncSupport.parseUuid(data.getPlpProgramId()));
            program.setPlpProgramSyncStatus(PlpSyncStatus.SYNC_SUCCESS);
            program.setPlpProgramSyncError(null);
            program.setPlpProgramSyncedAt(PlpSyncSupport.now());
            log.info("PLP program sync success: losProgramId={}, plpProgramId={}", programId, data.getPlpProgramId());
        } catch (PlpIntegrationException e) {
            program.setPlpProgramSyncStatus(PlpSyncStatus.SYNC_FAILED);
            program.setPlpProgramSyncError(PlpSyncSupport.truncateError(e.getMessage()));
            log.error("PLP program sync failed for {}: {}", programId, e.getMessage());
        }
        return programMasterRepository.save(program);
    }
}
