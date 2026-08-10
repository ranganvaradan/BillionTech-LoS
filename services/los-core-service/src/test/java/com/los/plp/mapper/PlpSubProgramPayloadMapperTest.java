package com.los.plp.mapper;

import com.los.plp.dto.request.PlpSubProgramSyncRequest;
import com.los.plp.model.entity.AnchorMaster;
import com.los.plp.model.entity.ProgramMaster;
import com.los.plp.model.entity.SubProgramMaster;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlpSubProgramPayloadMapperTest {

    @Test
    void toRequest_copiesInterestRateAndTenureFromParentProgram() {
        UUID subId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        UUID plpProgramId = UUID.randomUUID();
        UUID plpAnchorId = UUID.randomUUID();
        UUID lenderId = UUID.randomUUID();

        ProgramMaster program = ProgramMaster.builder()
                .id(programId)
                .plpProgramId(plpProgramId)
                .plpLenderId(lenderId)
                .interestRate(new BigDecimal("11.50"))
                .tenureDays(60)
                .build();

        SubProgramMaster subProgram = SubProgramMaster.builder()
                .id(subId)
                .programId(programId)
                .subProgramCode("SP-001")
                .name("Borrower flow")
                .flowType("INVOICE_DISCOUNTING")
                .subProgramLimit(new BigDecimal("500000"))
                .build();

        AnchorMaster anchor = AnchorMaster.builder()
                .id(UUID.randomUUID())
                .plpAnchorId(plpAnchorId)
                .build();

        PlpSubProgramSyncRequest request = PlpSubProgramPayloadMapper.toRequest(subProgram, program, anchor);

        assertThat(request.getLosSubProgramId()).isEqualTo(subId.toString());
        assertThat(request.getPlpProgramId()).isEqualTo(plpProgramId.toString());
        assertThat(request.getInterestRate()).isEqualByComparingTo("11.50");
        assertThat(request.getMaxTenureDays()).isEqualTo(60);
    }
}
