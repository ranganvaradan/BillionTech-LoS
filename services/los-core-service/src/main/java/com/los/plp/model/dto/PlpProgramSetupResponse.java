package com.los.plp.model.dto;

import com.los.plp.model.enums.PlpSyncStatus;
import com.los.plp.model.enums.ProgramApprovalStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class PlpProgramSetupResponse {

    private UUID programId;
    private UUID subProgramId;
    private UUID anchorId;
    private String programName;
    private String programType;
    private BigDecimal creditLimit;
    private BigDecimal interestRate;
    private Integer tenureDays;
    private String currency;
    private LocalDate validityStartDate;
    private LocalDate validityEndDate;
    private PlpSyncStatus programSyncStatus;
    private String programSyncError;
    private PlpSyncStatus subProgramSyncStatus;
    private String subProgramSyncError;
    private UUID plpProgramId;
    private UUID plpSubProgramId;
    private Instant programSyncedAt;
    private Instant subProgramSyncedAt;
    private ProgramApprovalStatus approvalStatus;
    private String approvalNotes;
    private UUID assignedL1UserId;
    private UUID assignedL2UserId;
    private BigDecimal dependencyVintagePercent;
    private Integer anchorRelationshipVintageMonths;
}
