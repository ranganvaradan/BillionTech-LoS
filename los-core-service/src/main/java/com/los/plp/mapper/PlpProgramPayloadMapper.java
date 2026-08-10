package com.los.plp.mapper;

import com.los.plp.dto.request.PlpProgramSyncRequest;
import com.los.plp.model.entity.ProgramMaster;

public final class PlpProgramPayloadMapper {

    private PlpProgramPayloadMapper() {
    }

    public static PlpProgramSyncRequest toRequest(ProgramMaster program) {
        return PlpProgramSyncRequest.builder()
                .losProgramId(program.getId().toString())
                .programCode(program.getProgramCode())
                .programName(program.getProgramName())
                .productType(program.getProductType())
                .lenderId(program.getPlpLenderId().toString())
                .programLimit(program.getProgramLimit())
                .maxBorrowerLimit(program.getMaxBorrowerLimit())
                .defaultInterestRate(program.getInterestRate())
                .maxTenureDays(program.getTenureDays())
                .validFrom(program.getValidityStartDate())
                .validTo(program.getValidityEndDate())
                .lmsEntryIn(program.getLmsEntryIn())
                .encoreProductCode(program.getEncoreProductCode())
                .preApproved(false)
                .dependencyVintagePercent(program.getDependencyVintagePercent())
                .anchorRelationshipVintageMonths(program.getAnchorRelationshipVintageMonths())
                .build();
    }
}
