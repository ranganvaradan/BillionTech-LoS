package com.los.plp.service;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LosUser;
import com.los.core.repository.LosUserRepository;
import com.los.core.service.audit.AuditService;
import com.los.plp.config.PlpProperties;
import com.los.plp.client.PlpApiAuditContext;
import com.los.plp.client.PlpIntegrationClient;
import com.los.plp.client.PlpIntegrationException;
import com.los.plp.dto.PlpApiResponse;
import com.los.plp.dto.response.PlpProgramStatusData;
import com.los.plp.model.dto.ProgramApprovalResponse;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.enums.ProgramApprovalStatus;
import com.los.plp.repository.ProgramMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramApprovalService {

    private final ProgramMasterRepository programMasterRepository;
    private final LosUserRepository losUserRepository;
    private final PlpIntegrationClient plpIntegrationClient;
    private final PlpProgramStatusMirrorService plpProgramStatusMirrorService;
    private final PlpProperties plpProperties;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<ProgramApprovalResponse> listPendingForUser(UUID userId) {
        return programMasterRepository.findByApprovalStatus(ProgramApprovalStatus.DRAFT).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProgramApprovalResponse getApproval(UUID programId) {
        ProgramMaster program = programMasterRepository.findById(programId)
                .orElseThrow(() -> new IllegalArgumentException("Program not found: " + programId));
        return toResponse(program);
    }

    @Transactional
    public void initDraftFromPlp(ProgramMaster program) {
        program.setApprovalStatus(ProgramApprovalStatus.DRAFT);
        program.setPlpOperationalStatus("DRAFT");
    }

    @Transactional
    public ProgramMaster mirrorPlpOperationalStatus(ProgramMaster program, String plpStatus) {
        plpProgramStatusMirrorService.mirror(program, plpStatus);
        return program;
    }

    @Transactional
    public ProgramApprovalResponse refreshFromPlp(UUID programId) {
        ProgramMaster program = programMasterRepository.findById(programId)
                .orElseThrow(() -> new IllegalArgumentException("Program not found: " + programId));
        if (!plpProperties.isEnabled()) {
            throw new BusinessRuleException("PLP integration is disabled — cannot refresh program status");
        }
        try {
            String previousStatus = program.getPlpOperationalStatus();
            ProgramApprovalStatus previousApproval = program.getApprovalStatus();
            UUID anchorApplicationId = program.getAnchorApplicationId();
            String losProgramId = program.getId().toString();
            PlpApiResponse<PlpProgramStatusData> response =
                    PlpApiAuditContext.callWithApplication(anchorApplicationId, () ->
                            plpIntegrationClient.getProgramStatus(losProgramId));
            PlpProgramStatusData data = response.getData();
            if (data != null && data.getStatus() != null) {
                plpProgramStatusMirrorService.mirror(program, data);
            }
            program = programMasterRepository.save(program);
            log.info("Refreshed PLP status for LOS program {} — plpStatus={}", programId, program.getPlpOperationalStatus());
            UUID applicationId = program.getAnchorApplicationId();
            if (applicationId != null) {
                Map<String, Object> state = new LinkedHashMap<>();
                state.put("programId", program.getId().toString());
                state.put("previousPlpStatus", previousStatus != null ? previousStatus : "");
                state.put("plpOperationalStatus", program.getPlpOperationalStatus() != null ? program.getPlpOperationalStatus() : "");
                state.put("previousApprovalStatus", previousApproval != null ? previousApproval.name() : "");
                state.put("approvalStatus", program.getApprovalStatus() != null ? program.getApprovalStatus().name() : "");
                if (program.getApprovalNotes() != null) {
                    state.put("approvalNotes", program.getApprovalNotes());
                }
                String action = "PROGRAM_STATUS_REFRESHED";
                String description = "PLP program status refreshed";
                if (program.getApprovalStatus() == ProgramApprovalStatus.APPROVED
                        && previousApproval != ProgramApprovalStatus.APPROVED) {
                    action = "PROGRAM_APPROVED_FROM_PLP";
                    description = "Program approved in PLP (maker-checker)";
                } else if (program.getApprovalStatus() == ProgramApprovalStatus.SENT_BACK
                        && previousApproval != ProgramApprovalStatus.SENT_BACK) {
                    action = "PROGRAM_SENT_BACK_FROM_PLP";
                    description = "Program sent back from PLP for revision";
                } else if (program.getApprovalStatus() == ProgramApprovalStatus.PENDING_L2
                        && previousApproval != ProgramApprovalStatus.PENDING_L2) {
                    action = "PROGRAM_PENDING_L2_FROM_PLP";
                    description = "Program pending L2 approval in PLP";
                }
                auditService.logEvent(applicationId, "PLP_PROGRAM", action, null, null, state, description);
            }
        } catch (PlpIntegrationException e) {
            throw new BusinessRuleException("Failed to refresh PLP program status: " + e.getMessage());
        }
        return toResponse(program);
    }

    @Transactional
    public void assignApproversOnCreate(ProgramMaster program) {
        initDraftFromPlp(program);
    }

    @Transactional
    public ProgramApprovalResponse submitToL2(UUID programId, UUID actorUserId) {
        throw losApprovalDisabled();
    }

    @Transactional
    public ProgramApprovalResponse sendBack(UUID programId, UUID actorUserId, String notes) {
        throw losApprovalDisabled();
    }

    @Transactional
    public ProgramApprovalResponse approve(UUID programId, UUID actorUserId) {
        throw losApprovalDisabled();
    }

    private BusinessRuleException losApprovalDisabled() {
        return new BusinessRuleException(
                "Program L1/L2 approval is managed in PLP (maker-checker workbench). "
                        + "Refresh PLP status on LOS after PLP approval.");
    }

    public boolean isApproved(ProgramMaster program) {
        return program != null && program.getApprovalStatus() == ProgramApprovalStatus.APPROVED;
    }

    private ProgramApprovalResponse toResponse(ProgramMaster program) {
        String l1Name = resolveUserName(program.getAssignedL1UserId());
        String l2Name = resolveUserName(program.getAssignedL2UserId());
        return ProgramApprovalResponse.builder()
                .programId(program.getId())
                .programName(program.getProgramName())
                .programCode(program.getProgramCode())
                .approvalStatus(program.getApprovalStatus())
                .assignedL1UserId(program.getAssignedL1UserId())
                .assignedL1UserName(l1Name)
                .assignedL2UserId(program.getAssignedL2UserId())
                .assignedL2UserName(l2Name)
                .approvalNotes(program.getApprovalNotes())
                .anchorApplicationId(program.getAnchorApplicationId())
                .approvedAt(program.getApprovedAt())
                .approvedByUserId(program.getApprovedByUserId())
                .plpOperationalStatus(program.getPlpOperationalStatus())
                .build();
    }

    private String resolveUserName(UUID userId) {
        if (userId == null) {
            return null;
        }
        return losUserRepository.findById(userId).map(LosUser::getName).orElse(null);
    }
}
