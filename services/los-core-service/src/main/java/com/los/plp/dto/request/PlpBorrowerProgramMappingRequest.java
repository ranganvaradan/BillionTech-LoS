package com.los.plp.dto.request;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PlpBorrowerProgramMappingRequest {
    private String sourceSystem;
    private String losApplicationId;
    private String losBorrowerId;
    private String plpBorrowerId;
    private String plpProgramId;
    private String plpSubProgramId;
    private BigDecimal approvedLimit;
    private String validFrom;
    private String validTo;
}
