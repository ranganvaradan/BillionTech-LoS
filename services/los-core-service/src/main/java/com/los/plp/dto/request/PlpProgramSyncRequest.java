package com.los.plp.dto.request;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class PlpProgramSyncRequest {
    private String sourceSystem;
    private String losProgramId;
    private String programCode;
    private String programName;
    private String productType;
    private String lenderId;
    private BigDecimal programLimit;
    private BigDecimal maxBorrowerLimit;
    private BigDecimal defaultInterestRate;
    private Integer maxTenureDays;
    private LocalDate validFrom;
    private LocalDate validTo;
    private String lmsEntryIn;
    private String encoreProductCode;
}
