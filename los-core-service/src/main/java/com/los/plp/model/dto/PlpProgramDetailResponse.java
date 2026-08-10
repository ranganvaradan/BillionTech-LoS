package com.los.plp.model.dto;

import com.los.plp.model.enums.PlpSyncStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PlpProgramDetailResponse {

    private UUID programId;
    private String programCode;
    private String programName;
    private String programType;
    private BigDecimal creditLimit;
    private BigDecimal maxBorrowerLimit;
    private BigDecimal interestRate;
    private Integer tenureDays;
    private String currency;
    private LocalDate validityStartDate;
    private LocalDate validityEndDate;
    private PlpSyncStatus programSyncStatus;
    private String programSyncError;
    private Instant programSyncedAt;
    private UUID plpProgramId;
    private List<SubProgramView> subPrograms;
    private List<PlpBorrowerApplicationView> borrowers;

    @Data
    @Builder
    public static class SubProgramView {
        private UUID subProgramId;
        private String subProgramCode;
        private String name;
        private UUID anchorId;
        private String anchorName;
        private PlpSyncStatus anchorSyncStatus;
        private PlpSyncStatus subProgramSyncStatus;
        private String subProgramSyncError;
        private Instant subProgramSyncedAt;
        private UUID plpSubProgramId;
        private long borrowerCount;
    }

    @Data
    @Builder
    public static class PlpBorrowerApplicationView {
        private UUID applicationId;
        private String applicationNumber;
        private String borrowerName;
        private String status;
        private UUID subProgramId;
        private PlpSyncStatus borrowerSyncStatus;
        private PlpSyncStatus linkSyncStatus;
        private PlpSyncStatus mappingSyncStatus;
        private PlpSyncStatus overallSyncStatus;
    }
}
