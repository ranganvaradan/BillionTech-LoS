package com.los.plp.mapper;

import com.los.plp.dto.request.PlpProgramSyncRequest;
import com.los.plp.model.entity.ProgramMaster;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlpProgramPayloadMapperTest {

    @Test
    void toRequest_mapsInterestRateTenureAndValidity() {
        UUID id = UUID.randomUUID();
        LocalDate validFrom = LocalDate.of(2026, 1, 1);
        LocalDate validTo = LocalDate.of(2027, 12, 31);
        ProgramMaster program = ProgramMaster.builder()
                .id(id)
                .programCode("PRG-001")
                .programName("Acme Pay Day Loan")
                .productType("PAY_DAY_LOAN")
                .plpLenderId(UUID.randomUUID())
                .programLimit(new BigDecimal("1000000"))
                .maxBorrowerLimit(new BigDecimal("100000"))
                .interestRate(new BigDecimal("12.00"))
                .tenureDays(90)
                .validityStartDate(validFrom)
                .validityEndDate(validTo)
                .build();

        PlpProgramSyncRequest request = PlpProgramPayloadMapper.toRequest(program);

        assertThat(request.getLosProgramId()).isEqualTo(id.toString());
        assertThat(request.getDefaultInterestRate()).isEqualByComparingTo("12.00");
        assertThat(request.getMaxTenureDays()).isEqualTo(90);
        assertThat(request.getValidFrom()).isEqualTo(validFrom);
        assertThat(request.getValidTo()).isEqualTo(validTo);
    }

    @Test
    void toRequest_mapsLmsConfig() {
        ProgramMaster program = ProgramMaster.builder()
                .id(UUID.randomUUID())
                .programCode("PRG-LMS")
                .programName("Invoice Program")
                .productType("INVOICE_DISCOUNTING")
                .plpLenderId(UUID.randomUUID())
                .lmsEntryIn("YES")
                .encoreProductCode("INVPROD01")
                .build();

        PlpProgramSyncRequest request = PlpProgramPayloadMapper.toRequest(program);

        assertThat(request.getLmsEntryIn()).isEqualTo("YES");
        assertThat(request.getEncoreProductCode()).isEqualTo("INVPROD01");
    }
}
