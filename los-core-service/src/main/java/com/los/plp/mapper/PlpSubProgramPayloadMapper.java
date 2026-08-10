package com.los.plp.mapper;

import com.los.plp.dto.request.PlpSubProgramSyncRequest;
import com.los.plp.model.entity.AnchorMaster;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.entity.SubProgramMaster;

public final class PlpSubProgramPayloadMapper {

    private PlpSubProgramPayloadMapper() {
    }

    public static PlpSubProgramSyncRequest toRequest(
            SubProgramMaster subProgram,
            ProgramMaster program,
            AnchorMaster anchor) {
        return toRequest(subProgram, program, anchor, false);
    }

    /**
     * @param preApproved when true, PLP creates/activates the sub-program as ACTIVE (used for borrower sanction sync)
     */
    public static PlpSubProgramSyncRequest toRequest(
            SubProgramMaster subProgram,
            ProgramMaster program,
            AnchorMaster anchor,
            boolean preApproved) {
        return PlpSubProgramSyncRequest.builder()
                .losSubProgramId(subProgram.getId().toString())
                .plpProgramId(program.getPlpProgramId().toString())
                .anchorId(anchor.getPlpAnchorId().toString())
                .lenderId(program.getPlpLenderId().toString())
                .subProgramCode(subProgram.getSubProgramCode())
                .name(subProgram.getName())
                .flowType(subProgram.getFlowType())
                .anchorRole(subProgram.getAnchorRole())
                .borrowerRole(subProgram.getBorrowerRole())
                .subProgramLimit(subProgram.getSubProgramLimit())
                .interestRate(program.getInterestRate())
                .maxTenureDays(program.getTenureDays())
                .preApproved(preApproved)
                .build();
    }
}
