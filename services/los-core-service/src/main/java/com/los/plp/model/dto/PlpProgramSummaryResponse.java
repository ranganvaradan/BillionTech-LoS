package com.los.plp.model.dto;

import com.los.plp.model.enums.PlpSyncStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class PlpProgramSummaryResponse {

    private UUID programId;
    private UUID subProgramId;
    private String programName;
    private String programCode;
    private String programType;
    private BigDecimal creditLimit;
    private BigDecimal interestRate;
    private Integer tenureDays;
    private String currency;
    private LocalDate validityStartDate;
    private LocalDate validityEndDate;
    private UUID anchorId;
    private String anchorName;
    private String anchorCode;
    private PlpSyncStatus programSyncStatus;
    private PlpSyncStatus subProgramSyncStatus;
    private long borrowerCount;
    private String flowType;
    private String lmsEntryIn;
    private String encoreProductCode;
}
