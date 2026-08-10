package com.los.plp.service;

import com.los.plp.config.PlpProperties;
import com.los.plp.event.PlpMasterSyncRequestedEvent;
import com.los.plp.event.PlpMasterSyncType;
import com.los.plp.model.dto.ProgramMasterRequest;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.enums.PlpSyncStatus;
import com.los.plp.repository.ProgramMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramMasterService {

    private final ProgramMasterRepository programMasterRepository;
    private final PlpProperties plpProperties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ProgramMaster create(ProgramMasterRequest request) {
        UUID lenderId = parseLenderId();
        ProgramMaster program = ProgramMaster.builder()
                .programCode(request.getProgramCode())
                .programName(request.getProgramName())
                .productType(request.getProductType())
                .programLimit(request.getProgramLimit())
                .maxBorrowerLimit(request.getMaxBorrowerLimit())
                .workflowConfigId(request.getWorkflowConfigId())
                .plpLenderId(lenderId)
                .plpProgramSyncStatus(PlpSyncStatus.NOT_SYNCED)
                .build();
        program = programMasterRepository.save(program);
        publishSyncAfterCommit(program.getId());
        return program;
    }

    @Transactional
    public ProgramMaster update(UUID id, ProgramMasterRequest request) {
        ProgramMaster program = programMasterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Program not found: " + id));
        program.setProgramCode(request.getProgramCode());
        program.setProgramName(request.getProgramName());
        program.setProductType(request.getProductType());
        program.setProgramLimit(request.getProgramLimit());
        program.setMaxBorrowerLimit(request.getMaxBorrowerLimit());
        program.setWorkflowConfigId(request.getWorkflowConfigId());
        program = programMasterRepository.save(program);
        publishSyncAfterCommit(program.getId());
        return program;
    }

    @Transactional(readOnly = true)
    public ProgramMaster get(UUID id) {
        return programMasterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Program not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<ProgramMaster> list() {
        return programMasterRepository.findAll();
    }

    private void publishSyncAfterCommit(UUID programId) {
        if (!plpProperties.isEnabled()) {
            return;
        }
        eventPublisher.publishEvent(new PlpMasterSyncRequestedEvent(PlpMasterSyncType.PROGRAM, programId));
    }

    private UUID parseLenderId() {
        String raw = plpProperties.getLenderId();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("los.plp.lender-id (PLP_LENDER_ID) is required for program setup");
        }
        return UUID.fromString(raw.trim());
    }
}
