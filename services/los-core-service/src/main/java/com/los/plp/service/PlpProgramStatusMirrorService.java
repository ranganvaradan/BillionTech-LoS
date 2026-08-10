package com.los.plp.service;

import com.los.plp.dto.response.PlpProgramStatusData;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.enums.ProgramApprovalStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** Mirrors PLP operational program status (and commercial fields) onto LOS {@link ProgramMaster}. */
@Slf4j
@Service
public class PlpProgramStatusMirrorService {

    public void mirror(ProgramMaster program, String plpStatus) {
        mirror(program, plpStatus, null);
    }

    public void mirror(ProgramMaster program, PlpProgramStatusData data) {
        if (data == null) {
            return;
        }
        mirror(program, data.getStatus(), resolveRemarks(data));
        applyCommercialFields(program, data);
    }

    public void mirror(ProgramMaster program, String plpStatus, String remarks) {
        if (program == null || plpStatus == null || plpStatus.isBlank()) {
            return;
        }
        String normalized = plpStatus.trim().toUpperCase();
        program.setPlpOperationalStatus(normalized);
        switch (normalized) {
            case "ACTIVE" -> {
                program.setApprovalStatus(ProgramApprovalStatus.APPROVED);
                if (program.getApprovedAt() == null) {
                    program.setApprovedAt(Instant.now());
                }
            }
            case "DRAFT" -> program.setApprovalStatus(ProgramApprovalStatus.DRAFT);
            case "PENDING_L2" -> program.setApprovalStatus(ProgramApprovalStatus.PENDING_L2);
            case "SENT_BACK" -> {
                program.setApprovalStatus(ProgramApprovalStatus.SENT_BACK);
                if (remarks != null && !remarks.isBlank()) {
                    program.setApprovalNotes(remarks.trim());
                }
            }
            case "PAUSED", "CLOSED" -> program.setApprovalStatus(ProgramApprovalStatus.REJECTED);
            default -> log.debug("Unmapped PLP program status {} for LOS program {}", normalized, program.getId());
        }
    }

    private static void applyCommercialFields(ProgramMaster program, PlpProgramStatusData data) {
        if (data.getDefaultInterestRate() != null) {
            program.setInterestRate(data.getDefaultInterestRate());
        }
        if (data.getProgramLimit() != null) {
            program.setProgramLimit(data.getProgramLimit());
        }
        if (data.getMaxBorrowerLimit() != null) {
            program.setMaxBorrowerLimit(data.getMaxBorrowerLimit());
        }
        if (data.getMaxTenureDays() != null) {
            program.setTenureDays(data.getMaxTenureDays());
        }
        if (data.getDependencyVintagePercent() != null) {
            program.setDependencyVintagePercent(data.getDependencyVintagePercent());
        }
        if (data.getAnchorRelationshipVintageMonths() != null) {
            program.setAnchorRelationshipVintageMonths(data.getAnchorRelationshipVintageMonths());
        }
        if (data.getLmsEntryIn() != null && !data.getLmsEntryIn().isBlank()) {
            program.setLmsEntryIn(data.getLmsEntryIn().trim().toUpperCase());
        }
        if (data.getEncoreProductCode() != null) {
            String code = data.getEncoreProductCode().trim();
            program.setEncoreProductCode(code.isEmpty() ? null : code);
        }
    }

    private static String resolveRemarks(PlpProgramStatusData data) {
        if (data.getApprovalRemarks() != null && !data.getApprovalRemarks().isBlank()) {
            return data.getApprovalRemarks();
        }
        if (data.getRemarks() != null && !data.getRemarks().isBlank()) {
            return data.getRemarks();
        }
        if (data.getApprovalNotes() != null && !data.getApprovalNotes().isBlank()) {
            return data.getApprovalNotes();
        }
        return null;
    }
}
