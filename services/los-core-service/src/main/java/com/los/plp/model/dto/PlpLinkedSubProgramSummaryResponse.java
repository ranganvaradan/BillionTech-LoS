package com.los.plp.model.dto;

import com.los.plp.model.enums.PlpSyncStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class PlpLinkedSubProgramSummaryResponse {
    private UUID subProgramId;
    private UUID programId;
    private UUID anchorId;
    private String subProgramName;
    private String programName;
    private String programType;
    private String anchorName;
    private String anchorCode;
    private BigDecimal programLimit;
    private PlpSyncStatus programSyncStatus;
    private PlpSyncStatus subProgramSyncStatus;
    private UUID plpProgramId;
    private UUID plpSubProgramId;
}
