package com.los.plp.model.dto;

import com.los.plp.model.enums.PlpSyncStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class PlpAnchorBorrowerResponse {

    private UUID applicationId;
    private String applicationNumber;
    private String borrowerName;
    private String status;
    private UUID subProgramId;
    private String programName;
    private PlpSyncStatus borrowerSyncStatus;
    private PlpSyncStatus linkSyncStatus;
    private PlpSyncStatus mappingSyncStatus;
    private PlpSyncStatus overallSyncStatus;
}
