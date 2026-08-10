package com.los.plp.model.dto;

import com.los.plp.model.enums.ProgramApprovalStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ProgramApprovalResponse {

    private UUID programId;
    private String programName;
    private String programCode;
    private ProgramApprovalStatus approvalStatus;
    private UUID assignedL1UserId;
    private String assignedL1UserName;
    private UUID assignedL2UserId;
    private String assignedL2UserName;
    private String approvalNotes;
    private UUID anchorApplicationId;
    private Instant approvedAt;
    private UUID approvedByUserId;
    /** PLP operational status mirrored from PLP (DRAFT, ACTIVE, …). */
    private String plpOperationalStatus;
}
