package com.los.plp.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlpProgramStatusData {
    private String plpProgramId;
    private String programCode;
    private String status;
    /** Send-back / approval remarks from PLP when present. */
    private String remarks;
    private String approvalRemarks;
    private String approvalNotes;

    /** Commercial fields mirrored from PLP after L1/L2 edits. */
    private BigDecimal defaultInterestRate;
    private BigDecimal programLimit;
    private BigDecimal maxBorrowerLimit;
    private Integer maxTenureDays;
    private BigDecimal dependencyVintagePercent;
    private Integer anchorRelationshipVintageMonths;
    private String lmsEntryIn;
    private String encoreProductCode;
}
