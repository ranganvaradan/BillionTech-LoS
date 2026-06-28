package com.los.plp.dto.request;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PlpSubProgramSyncRequest {
    private String sourceSystem;
    private String losSubProgramId;
    private String plpProgramId;
    private String anchorId;
    private String lenderId;
    private String subProgramCode;
    private String name;
    private String flowType;
    private String anchorRole;
    private String borrowerRole;
    private BigDecimal subProgramLimit;
    private BigDecimal interestRate;
    private Integer maxTenureDays;
}
