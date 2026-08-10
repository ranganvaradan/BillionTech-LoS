package com.los.plp.service;

import com.los.plp.config.PlpProperties;
import com.los.plp.model.dto.SubProgramMasterRequest;
import com.los.plp.model.entity.SubProgramMaster;
import com.los.plp.model.enums.PlpSyncStatus;
import com.los.plp.repository.SubProgramMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubProgramMasterService {

    private final SubProgramMasterRepository subProgramMasterRepository;
    private final PlpSubProgramSyncService plpSubProgramSyncService;
    private final PlpProperties plpProperties;

    @Transactional
    public SubProgramMaster create(SubProgramMasterRequest request) {
        SubProgramMaster subProgram = SubProgramMaster.builder()
                .subProgramCode(request.getSubProgramCode())
                .name(request.getName())
                .programId(request.getProgramId())
                .anchorId(request.getAnchorId())
                .flowType(request.getFlowType())
                .anchorRole(request.getAnchorRole())
                .borrowerRole(request.getBorrowerRole())
                .subProgramLimit(request.getSubProgramLimit())
                .plpSubProgramSyncStatus(PlpSyncStatus.NOT_SYNCED)
                .build();
        subProgram = subProgramMasterRepository.save(subProgram);
        if (plpProperties.isEnabled()) {
            try {
                plpSubProgramSyncService.sync(subProgram.getId());
            } catch (Exception e) {
                log.error("PLP sub-program sync failed after create for {}: {}", subProgram.getId(), e.getMessage(), e);
            }
            return subProgramMasterRepository.findById(subProgram.getId()).orElse(subProgram);
        }
        return subProgram;
    }

    @Transactional
    public SubProgramMaster update(UUID id, SubProgramMasterRequest request) {
        SubProgramMaster subProgram = subProgramMasterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sub-program not found: " + id));
        subProgram.setSubProgramCode(request.getSubProgramCode());
        subProgram.setName(request.getName());
        subProgram.setProgramId(request.getProgramId());
        subProgram.setAnchorId(request.getAnchorId());
        subProgram.setFlowType(request.getFlowType());
        subProgram.setAnchorRole(request.getAnchorRole());
        subProgram.setBorrowerRole(request.getBorrowerRole());
        subProgram.setSubProgramLimit(request.getSubProgramLimit());
        subProgram = subProgramMasterRepository.save(subProgram);
        if (plpProperties.isEnabled()) {
            try {
                plpSubProgramSyncService.sync(subProgram.getId());
            } catch (Exception e) {
                log.error("PLP sub-program sync failed after update for {}: {}", subProgram.getId(), e.getMessage(), e);
            }
            return subProgramMasterRepository.findById(subProgram.getId()).orElse(subProgram);
        }
        return subProgram;
    }

    @Transactional(readOnly = true)
    public SubProgramMaster get(UUID id) {
        return subProgramMasterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sub-program not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<SubProgramMaster> list() {
        return subProgramMasterRepository.findAll();
    }
}
